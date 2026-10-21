package co.edu.unal.tictactoeonline.online

import co.edu.unal.tictactoeonline.PlayerStore
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/** La "nube" en memoria. Varios [FakeGameRepository] (uno por jugador) comparten el mismo servidor. */
class FakeServer {
    val games = MutableStateFlow<Map<String, OnlineGame>>(emptyMap())

    /** Hora del servidor para createdAt. */
    var now = 10_000_000L

    private var mNextId = 1

    fun newId() = "game${mNextId++}"

    /** Igual que la transacción de Firebase: [change] corre sobre la versión actual y se aplica entera o nada. */
    fun transact(gameId: String, change: (OnlineGame) -> GameChange): Boolean {
        val current = games.value[gameId] ?: return false
        return when (val result = change(current)) {
            is GameChange.Update -> {
                games.value += gameId to result.game
                true
            }
            GameChange.Delete -> {
                games.value -= gameId
                true
            }
            GameChange.Reject -> false
        }
    }

    /** Lo que haría el onDisconnect de Firebase (o la reconexión). */
    fun setOnline(gameId: String, symbol: Char, online: Boolean) {
        val game = games.value[gameId] ?: return
        games.value += gameId to if (symbol == PLAYER_X) game.copy(xOnline = online) else game.copy(oOnline = online)
    }
}

/** [GameRepository] de un jugador ([uid]) contra un [FakeServer]. Usa las mismas [OnlineRules] que Firebase. */
class FakeGameRepository(private val server: FakeServer, private val uid: String) : GameRepository {

    /** Para simular que no hay red al iniciar sesión. */
    var failSignIn = false

    override suspend fun signIn(): String {
        if (failSignIn) throw IOException("offline")
        return uid
    }

    override fun observeOpenGames(): Flow<List<OnlineGame>> = server.games.map { all ->
        all.values.filter { it.status == GameStatus.WAITING }.sortedByDescending { it.createdAt }
    }

    override fun observeGame(gameId: String): Flow<OnlineGame?> =
        server.games.map { it[gameId] }.distinctUntilChanged()

    override suspend fun createGame(playerName: String): String {
        val id = server.newId()
        server.games.value += id to OnlineRules.newGame(uid, playerName).copy(id = id, createdAt = server.now)
        return id
    }

    override suspend fun joinGame(gameId: String, playerName: String) =
        server.transact(gameId) { OnlineRules.join(it, uid, playerName) }

    override suspend fun makeMove(gameId: String, cell: Int) =
        server.transact(gameId) { OnlineRules.applyMove(it, uid, cell) }

    override suspend fun rematch(gameId: String) =
        server.transact(gameId) { OnlineRules.rematch(it, uid) }

    override suspend fun leaveGame(gameId: String) {
        server.transact(gameId) { OnlineRules.leave(it, uid) }
    }

    override suspend fun stayOnline(gameId: String, symbol: Char) {
        server.setOnline(gameId, symbol, true)
        awaitCancellation()
    }
}

/** SharedPreferences del apodo, en memoria. */
class FakePlayerStore(var nickname: String = "") : PlayerStore {
    override fun loadNickname() = nickname
    override fun saveNickname(nickname: String) {
        this.nickname = nickname
    }
}
