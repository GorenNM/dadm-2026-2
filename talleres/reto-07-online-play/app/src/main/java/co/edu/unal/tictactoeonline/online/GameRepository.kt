package co.edu.unal.tictactoeonline.online

import kotlinx.coroutines.flow.Flow

/**
 * El servicio en la nube que coordina los juegos. Interfaz para que los ViewModels se testeen
 * con un fake en memoria; la de verdad es [FirebaseGameRepository].
 * Todo menos [signIn] supone que ya se inició sesión.
 */
interface GameRepository {

    /** Inicia sesión (la primera vez) y devuelve el uid de este jugador. */
    suspend fun signIn(): String

    /** Juegos que esperan rival, los más nuevos primero. Se actualiza solo. */
    fun observeOpenGames(): Flow<List<OnlineGame>>

    /** El juego [gameId] cada vez que cambia; null si no existe (o se borró). */
    fun observeGame(gameId: String): Flow<OnlineGame?>

    /** Crea un juego con este jugador como X y devuelve su id. */
    suspend fun createGame(playerName: String): String

    /** false si el juego ya no está disponible (otro se unió primero, o se borró). */
    suspend fun joinGame(gameId: String, playerName: String): Boolean

    /** false si la jugada no vale: no es tu turno, la casilla está ocupada... */
    suspend fun makeMove(gameId: String, cell: Int): Boolean

    suspend fun rematch(gameId: String): Boolean

    suspend fun leaveGame(gameId: String)

    /**
     * Marca a [symbol] como conectado en [gameId] mientras la corrutina siga viva, y vuelve a
     * marcarlo si la conexión se corta y vuelve. Si el proceso muere o se pierde la red,
     * el servidor lo pone en desconectado solo.
     */
    suspend fun stayOnline(gameId: String, symbol: Char)
}
