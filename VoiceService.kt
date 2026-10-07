package com.gameboost.lite

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.IBinder

class VoiceService : Service() {
    @Volatile private var run = false
    private var thread: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(2, fgNotification("Voice changer running"))
        run = false
        thread?.join(500)
        val factor = intent?.getFloatExtra("factor", 1f) ?: 1f
        run = true
        thread = Thread { loop(factor) }.also { it.start() }
        return START_NOT_STICKY
    }

    private fun loop(factor: Float) {
        val rate = 16000
        val minRec = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val minPlay = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        var rec: AudioRecord? = null
        var track: AudioTrack? = null
        try {
            rec = AudioRecord(
                MediaRecorder.AudioSource.MIC, rate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minRec, 4096)
            )
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                )
                .setBufferSizeInBytes(maxOf(minPlay, 4096))
                .build()
            val n = 320 // 20 ms
            val inBuf = ShortArray(n)
            val out = ShortArray(n)
            rec.startRecording()
            track.play()
            while (run) {
                val r = rec.read(inBuf, 0, n)
                if (r <= 0) continue
                for (i in 0 until r) {
                    val pos = (i * factor) % r
                    val i0 = pos.toInt()
                    val i1 = if (i0 + 1 < r) i0 + 1 else i0
                    val fr = pos - i0
                    out[i] = (inBuf[i0] * (1 - fr) + inBuf[i1] * fr).toInt().toShort()
                }
                track.write(out, 0, r)
            }
        } catch (_: Exception) {
        } finally {
            try { rec?.stop() } catch (_: Exception) {}
            rec?.release()
            try { track?.stop() } catch (_: Exception) {}
            track?.release()
        }
    }

    override fun onDestroy() {
        run = false
        super.onDestroy()
    }
}
