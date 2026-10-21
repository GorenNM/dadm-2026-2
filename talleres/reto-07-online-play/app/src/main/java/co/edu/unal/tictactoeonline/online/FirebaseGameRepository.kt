package co.edu.unal.tictactoeonline.online

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [GameRepository] sobre Firebase Realtime Database + auth anónima.
 *
 * Cada juego es un nodo /games/{id} (ver [OnlineGame.toMap]). Todo lo que cambia el juego
 * (unirse, mover, revancha, salir) va en una transacción que aplica [OnlineRules]: si dos
 * clientes escriben a la vez, Firebase reintenta con el valor nuevo y gana uno solo.
 * Las reglas del lado del servidor están en database.rules.json (raíz del reto).
 */
class FirebaseGameRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    database: FirebaseDatabase = FirebaseDatabase.getInstance()
) : GameRepository {

    companion object {
        private const val GAMES_PATH = "games"
        private const val CONNECTED_PATH = ".info/connected"
    }

    private val mGames = database.getReference(GAMES_PATH)
    private val mConnected = database.getReference(CONNECTED_PATH)

    private val uid: String
        get() = checkNotNull(auth.currentUser?.uid) { "signIn() first" }

    // El usuario anónimo queda guardado en el dispositivo: después de matar el proceso es el mismo uid
    override suspend fun signIn(): String =
        auth.currentUser?.uid ?: checkNotNull(auth.signInAnonymously().await().user).uid

    override fun observeOpenGames(): Flow<List<OnlineGame>> =
        mGames.orderByChild(OnlineGame.KEY_STATUS).equalTo(OnlineGame.STATUS_WAITING).values { snapshot ->
            snapshot.children.mapNotNull { it.toGame() }.sortedByDescending { it.createdAt }
        }

    override fun observeGame(gameId: String): Flow<OnlineGame?> =
        mGames.child(gameId).values { it.toGame() }

    override suspend fun createGame(playerName: String): String {
        val ref = mGames.push()
        val game = OnlineRules.newGame(uid, playerName)
        ref.setValue(game.toMap() + (OnlineGame.KEY_CREATED_AT to ServerValue.TIMESTAMP)).await()
        // Si el creador se desconecta antes de que alguien se una, el juego desaparece de la lista
        ref.onDisconnect().removeValue().await()
        return checkNotNull(ref.key)
    }

    override suspend fun joinGame(gameId: String, playerName: String): Boolean =
        transact(gameId) { OnlineRules.join(it, uid, playerName) }

    override suspend fun makeMove(gameId: String, cell: Int): Boolean =
        transact(gameId) { OnlineRules.applyMove(it, uid, cell) }

    override suspend fun rematch(gameId: String): Boolean =
        transact(gameId) { OnlineRules.rematch(it, uid) }

    override suspend fun leaveGame(gameId: String) {
        val ref = mGames.child(gameId)
        // Lo primero: que un onDisconnect pendiente no reviva el nodo después de borrarlo
        ref.onDisconnect().cancel().await()
        transact(gameId) { OnlineRules.leave(it, uid) }
    }

    override suspend fun stayOnline(gameId: String, symbol: Char) {
        val ref = mGames.child(gameId)
        val flag = ref.child(OnlineGame.onlineKey(symbol))
        mConnected.values { it.getValue(Boolean::class.java) == true }.collect { connected ->
            if (!connected) return@collect
            // Ya hay rival: se cancela el "borrar si me desconecto" del creador y queda solo la presencia
            ref.onDisconnect().cancel().await()
            flag.onDisconnect().setValue(false).await()
            flag.setValue(true).await()
        }
    }

    /**
     * Aplica [change] al juego en una transacción. true si se escribió; false si [change] la
     * rechazó o el juego ya no existe.
     */
    private suspend fun transact(gameId: String, change: (OnlineGame) -> GameChange): Boolean =
        suspendCancellableCoroutine { cont ->
            // doTransaction corre en el hilo de Firebase y se puede repetir; onComplete, en el principal
            val applied = AtomicBoolean(false)
            mGames.child(gameId).runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    applied.set(false)
                    // Sin el valor en caché local la primera pasada llega vacía: se devuelve tal cual,
                    // el servidor ve que no coincide y vuelve a llamar con el valor real
                    val current = currentData.toGame(gameId) ?: return Transaction.success(currentData)
                    when (val result = change(current)) {
                        is GameChange.Update -> currentData.value = result.game.toMap()
                        GameChange.Delete -> currentData.value = null
                        GameChange.Reject -> return Transaction.abort()
                    }
                    applied.set(true)
                    return Transaction.success(currentData)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                    if (error != null) cont.resumeWithException(error.toException())
                    else cont.resume(committed && applied.get())
                }
            })
        }

    /** Escucha [this] y emite [transform] de cada valor nuevo. Deja de escuchar al cancelar el Flow. */
    private fun <T> Query.values(transform: (DataSnapshot) -> T): Flow<T> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(transform(snapshot))
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        addValueEventListener(listener)
        awaitClose { removeEventListener(listener) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun DataSnapshot.toGame(): OnlineGame? =
        (value as? Map<String, Any?>)?.let { OnlineGame.fromMap(checkNotNull(key), it) }

    @Suppress("UNCHECKED_CAST")
    private fun MutableData.toGame(gameId: String): OnlineGame? =
        (value as? Map<String, Any?>)?.let { OnlineGame.fromMap(gameId, it) }
}
