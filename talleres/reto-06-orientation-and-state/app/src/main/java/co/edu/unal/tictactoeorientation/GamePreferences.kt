package co.edu.unal.tictactoeorientation

import android.content.Context
import androidx.core.content.edit
import co.edu.unal.tictactoeorientation.TicTacToeGame.DifficultyLevel

enum class GameMode { VS_ANDROID, TWO_PLAYERS }

/** Lo que sobrevive a cerrar la app (Back, reiniciar el teléfono): marcador y preferencias del jugador. */
data class StoredGame(
    val xWins: Int = 0,
    val oWins: Int = 0,
    val ties: Int = 0,
    val difficulty: DifficultyLevel = DifficultyLevel.Expert,
    val mode: GameMode = GameMode.VS_ANDROID,
    val muted: Boolean = false
)

/** Dónde se guarda [StoredGame]. Interfaz para que el ViewModel se pueda testear sin Android. */
interface GameStore {
    fun load(): StoredGame
    fun save(stored: StoredGame)
}

/**
 * [GameStore] sobre SharedPreferences ("ttt_prefs", como el enunciado).
 * Los enums se guardan como su ordinal: SharedPreferences.Editor no tiene putEnum (Extra Challenge 1).
 */
class GamePreferences(context: Context) : GameStore {

    companion object {
        private const val PREFS_NAME = "ttt_prefs"
        private const val KEY_HUMAN_WINS = "mHumanWins"
        private const val KEY_COMPUTER_WINS = "mComputerWins"
        private const val KEY_TIES = "mTies"
        private const val KEY_DIFFICULTY = "difficulty"
        private const val KEY_MODE = "mode"
        private const val KEY_MUTED = "muted"
    }

    private val mPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): StoredGame {
        val defaults = StoredGame()
        return StoredGame(
            xWins = mPrefs.getInt(KEY_HUMAN_WINS, 0),
            oWins = mPrefs.getInt(KEY_COMPUTER_WINS, 0),
            ties = mPrefs.getInt(KEY_TIES, 0),
            // Un ordinal fuera de rango (p. ej. si algún día se borra un nivel) cae al valor por defecto
            difficulty = DifficultyLevel.entries.getOrElse(mPrefs.getInt(KEY_DIFFICULTY, -1)) { defaults.difficulty },
            mode = GameMode.entries.getOrElse(mPrefs.getInt(KEY_MODE, -1)) { defaults.mode },
            muted = mPrefs.getBoolean(KEY_MUTED, defaults.muted)
        )
    }

    override fun save(stored: StoredGame) {
        mPrefs.edit {
            putInt(KEY_HUMAN_WINS, stored.xWins)
            putInt(KEY_COMPUTER_WINS, stored.oWins)
            putInt(KEY_TIES, stored.ties)
            putInt(KEY_DIFFICULTY, stored.difficulty.ordinal)
            putInt(KEY_MODE, stored.mode.ordinal)
            putBoolean(KEY_MUTED, stored.muted)
        }
    }
}
