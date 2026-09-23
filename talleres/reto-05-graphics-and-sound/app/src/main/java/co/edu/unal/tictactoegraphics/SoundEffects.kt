package co.edu.unal.tictactoegraphics

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Efectos de sonido de cada jugada. SoundPool en vez de MediaPlayer (que pide el enunciado):
 * está pensado para sonidos cortos que se repiten seguido, con mucha menos latencia.
 */
class SoundEffects(context: Context) {

    var muted: Boolean = false

    private val mSoundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val mHumanSoundId = mSoundPool.load(context, R.raw.move_human, 1)
    private val mComputerSoundId = mSoundPool.load(context, R.raw.move_computer, 1)

    /** Reproduce el efecto de [player], salvo que esté silenciado. */
    fun play(player: Char) {
        if (muted) return
        val soundId = if (player == TicTacToeGame.HUMAN_PLAYER) mHumanSoundId else mComputerSoundId
        mSoundPool.play(soundId, 1f, 1f, 1, 0, 1f)
    }

    fun release() {
        mSoundPool.release()
    }
}
