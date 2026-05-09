package com.zipper.datingapp.ui.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Plays a short gift success SFX after [com.zipper.datingapp.ui.DatingUiState.giftSoundNonce] bumps.
 *
 * **Asset:** `app/src/main/res/raw/gift_sound.wav` or `gift_sound.mp3` (name must be `gift_sound`).
 * Avoids [android.media.RingtoneManager] fallbacks that fail on some devices / AppOps setups.
 */
object GiftSoundPlayback {
    private const val TAG = "GiftSound"

    fun playGiftSuccessSound(context: Context) {
        val app = context.applicationContext
        val resId = app.resources.getIdentifier("gift_sound", "raw", app.packageName)
        if (resId != 0) {
            runCatching {
                app.resources.openRawResourceFd(resId).use { afd ->
                    val mp = MediaPlayer()
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            mp.setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build()
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            mp.setAudioStreamType(AudioManager.STREAM_NOTIFICATION)
                        }
                        mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        mp.setOnCompletionListener { m -> runCatching { m.release() } }
                        mp.prepare()
                        mp.start()
                    } catch (e: Throwable) {
                        runCatching { mp.release() }
                        throw e
                    }
                }
            }.onFailure { e ->
                Log.e(
                    TAG,
                    "MediaPlayer gift sound failed: ${e.javaClass.name}: ${e.message}",
                    e
                )
                playFallbackAckTone()
            }
            return
        }

        Log.w(TAG, "res/raw/gift_sound missing — using short acknowledgment tone")
        playFallbackAckTone()
    }

    /** Short UI ack without [android.media.RingtoneManager] (fewer policy / attribution issues). */
    private fun playFallbackAckTone() {
        runCatching {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
            tg.startTone(ToneGenerator.TONE_PROP_ACK, 180)
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching { tg.release() }
            }, 240L)
        }.onFailure { e ->
            Log.e(
                TAG,
                "Fallback ack tone failed: ${e.javaClass.name}: ${e.message}",
                e
            )
        }
    }
}
