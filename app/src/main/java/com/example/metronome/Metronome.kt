package com.example.metronome

import android.media.ToneGenerator
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

class Metronome {
    private val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    private val handler = Handler(Looper.getMainLooper())
    private var bpm = 120
    private var beatIntervalMs = 500L  // 60000 / bpm

    private var nextBeatTime = 0L
    private var running = false

    fun startMetronomeAtHostTime(startAtHostElapsedRealtime: Long, receivedAtClientElapsedRealtime: Long, bpm: Int) {
        this.bpm = bpm
        beatIntervalMs = (60000L / bpm)

        val delay = startAtHostElapsedRealtime - receivedAtClientElapsedRealtime

        if (delay <= 0) {
            startLocalMetronome(SystemClock.elapsedRealtime())
        } else {
            handler.postDelayed({
                startLocalMetronome(SystemClock.elapsedRealtime())
            }, delay)
        }
    }

    private fun startLocalMetronome(startTime: Long) {
        running = true
        nextBeatTime = startTime
        scheduleNextBeat()
    }

    private fun scheduleNextBeat() {
        if (!running) return

        val now = SystemClock.elapsedRealtime()

        if (now >= nextBeatTime) {
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP)
            nextBeatTime += beatIntervalMs
        }

        val delay = nextBeatTime - now
        handler.postDelayed({ scheduleNextBeat() }, delay.coerceAtLeast(0L))
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        toneGen.release()
    }
}
