package com.cbt.musicplayer.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer

class AudioEffects {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    fun attach(audioSessionId: Int) {
        release()
        if (audioSessionId <= 0) return
        try {
            equalizer = Equalizer(0, audioSessionId).apply { enabled = true }
            bassBoost = BassBoost(0, audioSessionId).apply { enabled = true }
            virtualizer = Virtualizer(0, audioSessionId).apply { enabled = true }
        } catch (_: Exception) { release() }
    }

    fun bandCount(): Int = equalizer?.numberOfBands?.toInt() ?: 0
    fun bandRange(): ShortArray = equalizer?.bandLevelRange ?: shortArrayOf(-1500, 1500)
    fun bandCenter(band: Int): Int = equalizer?.getCenterFreq(band.toShort())?.div(1000) ?: 0
    fun setBand(band: Int, value: Int) { try { equalizer?.setBandLevel(band.toShort(), value.coerceIn(bandRange()[0].toInt(), bandRange()[1].toInt()).toShort()) } catch (_: Exception) {} }
    fun setBass(value: Int) { try { bassBoost?.setStrength(value.coerceIn(0, 1000).toShort()) } catch (_: Exception) {} }
    fun setWidth(value: Int) { try { virtualizer?.setStrength(value.coerceIn(0, 1000).toShort()) } catch (_: Exception) {} }
    fun release() {
        try { equalizer?.release() } catch (_: Exception) {}
        try { bassBoost?.release() } catch (_: Exception) {}
        try { virtualizer?.release() } catch (_: Exception) {}
        equalizer = null; bassBoost = null; virtualizer = null
    }
}
