package co.edu.unal.tictactoeonline.online

import co.edu.unal.tictactoeonline.TicTacToeGame
import co.edu.unal.tictactoeonline.TicTacToeGame.Companion.BOARD_SIZE
import co.edu.unal.tictactoeonline.TicTacToeGame.Companion.OPEN_SPOT

// En línea X es quien crea el juego y O quien se une. Mismos caracteres que el modo local: BoardView y SoundEffects no cambian.
const val PLAYER_X = TicTacToeGame.HUMAN_PLAYER
const val PLAYER_O = TicTacToeGame.COMPUTER_PLAYER

enum class GameStatus {
    /** Creado, esperando rival: es lo que muestra la lista de juegos. */
    WAITING,
    PLAYING,
    /** Alguien ganó o hubo empate; se puede pedir revancha. */
    FINISHED,
    /** Uno de los dos se fue: ya no se juega. El último en salir lo borra. */
    ABANDONED
}

/** Un juego tal como está en la nube (un nodo de /games). Sin nada de Firebase: se testea en JVM. */
data class OnlineGame(
    val id: String = "",
    val status: GameStatus = GameStatus.WAITING,
    val xUid: String = "",
    val xName: String = "",
    val oUid: String? = null,
    val oName: String? = null,
    val board: List<Char> = List(BOARD_SIZE) { OPEN_SPOT },
    /** Quién mueve ahora. */
    val turn: Char = PLAYER_X,
    /** Quién empezó esta partida: la revancha la empieza el otro. */
    val starter: Char = PLAYER_X,
    /** Resultado de [TicTacToeGame.checkForWinner]: 0 sigue, 1 empate, 2 ganó X, 3 ganó O. */
    val winner: Int = 0,
    val xWins: Int = 0,
    val oWins: Int = 0,
    val ties: Int = 0,
    /** Presencia: Firebase la pone en false solo cuando el jugador se desconecta (onDisconnect). */
    val xOnline: Boolean = false,
    val oOnline: Boolean = false,
    /** Hora del servidor al crearlo, en ms. */
    val createdAt: Long = 0
) {
    /** La ficha de [uid] en este juego, o null si no juega acá. */
    fun symbolOf(uid: String): Char? = when (uid) {
        xUid -> PLAYER_X
        oUid -> PLAYER_O
        else -> null
    }

    fun nameOf(symbol: Char): String? = if (symbol == PLAYER_X) xName else oName

    fun isOnline(symbol: Char): Boolean = if (symbol == PLAYER_X) xOnline else oOnline

    /** Lo que se guarda en Realtime Database. El id no va adentro: es la clave del nodo. */
    fun toMap(): Map<String, Any?> = mapOf(
        KEY_STATUS to status.name.lowercase(),
        KEY_X_UID to xUid,
        KEY_X_NAME to xName,
        KEY_O_UID to oUid,
        KEY_O_NAME to oName,
        // En la consola de Firebase un String de espacios no se ve: las casillas vacías se guardan como '-'
        KEY_BOARD to board.joinToString("") { if (it == OPEN_SPOT) EMPTY_CELL else it.toString() },
        KEY_TURN to turn.toString(),
        KEY_STARTER to starter.toString(),
        KEY_WINNER to winner,
        KEY_X_WINS to xWins,
        KEY_O_WINS to oWins,
        KEY_TIES to ties,
        KEY_X_ONLINE to xOnline,
        KEY_O_ONLINE to oOnline,
        KEY_CREATED_AT to createdAt
    )

    companion object {
        const val KEY_STATUS = "status"
        const val KEY_X_UID = "xUid"
        const val KEY_X_NAME = "xName"
        const val KEY_O_UID = "oUid"
        const val KEY_O_NAME = "oName"
        const val KEY_BOARD = "board"
        const val KEY_TURN = "turn"
        const val KEY_STARTER = "starter"
        const val KEY_WINNER = "winner"
        const val KEY_X_WINS = "xWins"
        const val KEY_O_WINS = "oWins"
        const val KEY_TIES = "ties"
        const val KEY_X_ONLINE = "xOnline"
        const val KEY_O_ONLINE = "oOnline"
        const val KEY_CREATED_AT = "createdAt"

        private const val EMPTY_CELL = "-"

        /** Valor de [KEY_STATUS] de los juegos que se listan. */
        val STATUS_WAITING = GameStatus.WAITING.name.lowercase()

        fun onlineKey(symbol: Char) = if (symbol == PLAYER_X) KEY_X_ONLINE else KEY_O_ONLINE

        /**
         * Lee un nodo de Realtime Database (los números llegan como Long).
         * Devuelve null si no parece un juego, p. ej. un nodo a medio borrar.
         */
        fun fromMap(id: String, map: Map<String, Any?>): OnlineGame? {
            val xUid = map[KEY_X_UID] as? String ?: return null
            val boardText = map[KEY_BOARD] as? String ?: return null
            if (boardText.length != BOARD_SIZE) return null
            val status = GameStatus.entries.firstOrNull { it.name.lowercase() == map[KEY_STATUS] } ?: return null

            return OnlineGame(
                id = id,
                status = status,
                xUid = xUid,
                xName = map[KEY_X_NAME] as? String ?: "",
                oUid = map[KEY_O_UID] as? String,
                oName = map[KEY_O_NAME] as? String,
                board = boardText.map { if (it == PLAYER_X || it == PLAYER_O) it else OPEN_SPOT },
                turn = symbol(map[KEY_TURN]),
                starter = symbol(map[KEY_STARTER]),
                winner = int(map[KEY_WINNER]),
                xWins = int(map[KEY_X_WINS]),
                oWins = int(map[KEY_O_WINS]),
                ties = int(map[KEY_TIES]),
                xOnline = map[KEY_X_ONLINE] as? Boolean ?: false,
                oOnline = map[KEY_O_ONLINE] as? Boolean ?: false,
                createdAt = (map[KEY_CREATED_AT] as? Number)?.toLong() ?: 0
            )
        }

        private fun symbol(value: Any?) = if (value == PLAYER_O.toString()) PLAYER_O else PLAYER_X

        private fun int(value: Any?) = (value as? Number)?.toInt() ?: 0
    }
}

/** Lo que una acción le hace a un juego. La transacción de Firebase y el fake de los tests aplican lo mismo. */
sealed interface GameChange {
    data class Update(val game: OnlineGame) : GameChange
    data object Delete : GameChange
    /** La acción no vale (no es tu turno, el juego ya se ocupó...): el juego no cambia. */
    data object Reject : GameChange
}

/**
 * Reglas del triqui en línea, sin Firebase ni Android.
 * Corren dentro de la transacción: siempre sobre la versión más nueva del juego, así que
 * dos jugadores que se unen o mueven a la vez no se pisan.
 */
object OnlineRules {

    fun newGame(uid: String, name: String) = OnlineGame(xUid = uid, xName = name, xOnline = true)

    /** [uid] se une como O. Solo a un juego que espera rival y que no es suyo. */
    fun join(game: OnlineGame, uid: String, name: String): GameChange {
        if (game.status != GameStatus.WAITING || game.oUid != null || game.xUid == uid) return GameChange.Reject
        return GameChange.Update(
            game.copy(status = GameStatus.PLAYING, oUid = uid, oName = name, oOnline = true, turn = game.starter)
        )
    }

    /** [uid] pone su ficha en [cell] (0-8). Valida turno y casilla, y cierra la partida si alguien ganó. */
    fun applyMove(game: OnlineGame, uid: String, cell: Int): GameChange {
        val symbol = game.symbolOf(uid)
        if (game.status != GameStatus.PLAYING || symbol == null || game.turn != symbol) return GameChange.Reject
        if (cell !in 0 until BOARD_SIZE || game.board[cell] != OPEN_SPOT) return GameChange.Reject

        val board = game.board.toMutableList().also { it[cell] = symbol }
        val winner = winnerOf(board)
        val moved = game.copy(board = board, winner = winner)
        return GameChange.Update(
            when (winner) {
                0 -> moved.copy(turn = other(symbol))
                1 -> moved.copy(status = GameStatus.FINISHED, ties = game.ties + 1)
                2 -> moved.copy(status = GameStatus.FINISHED, xWins = game.xWins + 1)
                else -> moved.copy(status = GameStatus.FINISHED, oWins = game.oWins + 1)
            }
        )
    }

    /** Otra partida en el mismo juego, con el marcador acumulado. La empieza quien no empezó la anterior. */
    fun rematch(game: OnlineGame, uid: String): GameChange {
        if (game.status != GameStatus.FINISHED || game.symbolOf(uid) == null) return GameChange.Reject
        val starter = other(game.starter)
        return GameChange.Update(
            game.copy(
                status = GameStatus.PLAYING,
                board = List(BOARD_SIZE) { OPEN_SPOT },
                winner = 0,
                starter = starter,
                turn = starter
            )
        )
    }

    /**
     * [uid] se va. Si nadie más lo va a usar se borra (el creador sin rival, o el segundo en salir);
     * si no, queda abandonado para que el rival se entere.
     */
    fun leave(game: OnlineGame, uid: String): GameChange = when {
        game.symbolOf(uid) == null -> GameChange.Reject
        game.status == GameStatus.WAITING || game.status == GameStatus.ABANDONED -> GameChange.Delete
        else -> GameChange.Update(game.copy(status = GameStatus.ABANDONED))
    }

    /** Mismo resultado que [TicTacToeGame.checkForWinner], sobre un tablero cualquiera. */
    fun winnerOf(board: List<Char>): Int =
        TicTacToeGame().apply { setBoardState(board.toCharArray()) }.checkForWinner()

    fun other(symbol: Char) = if (symbol == PLAYER_X) PLAYER_O else PLAYER_X
}
