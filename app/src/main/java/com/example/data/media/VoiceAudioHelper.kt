package com.example.data.media

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import com.example.data.repository.MediaStorageHelper
import java.io.File
import java.util.UUID

class VoiceAudioHelper(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var activeRecordingFile: File? = null
    private var recordingStartMs: Long = 0L

    private var player: MediaPlayer? = null
    private var activePlayingUrl: String? = null

    fun startRecording(): Result<File> = runCatching {
        stopRecordingInternal(deleteFile = true)
        val outDir = File(context.cacheDir, "voice_notes").apply { if (!exists()) mkdirs() }
        val file = File(outDir, "voice_${UUID.randomUUID()}.m4a")
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mr.setAudioSource(MediaRecorder.AudioSource.MIC)
        mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mr.setAudioSamplingRate(22050)
        mr.setAudioEncodingBitRate(24000)
        mr.setMaxDuration(45_000)
        mr.setOutputFile(file.absolutePath)
        mr.prepare()
        mr.start()
        recorder = mr
        activeRecordingFile = file
        recordingStartMs = System.currentTimeMillis()
        file
    }

    fun stopRecording(): Pair<File, Int>? {
        val mr = recorder ?: return null
        val file = activeRecordingFile
        val durationSec = (((System.currentTimeMillis() - recordingStartMs) / 1000L).coerceAtLeast(1L)).toInt()
        recorder = null
        activeRecordingFile = null
        return try {
            mr.stop()
            mr.release()
            if (file != null && file.exists() && file.length() > 0L) file to durationSec else null
        } catch (_: Exception) {
            runCatching { mr.release() }
            null
        }
    }

    private fun stopRecordingInternal(deleteFile: Boolean) {
        recorder?.let { mr ->
            runCatching { mr.stop() }
            runCatching { mr.release() }
        }
        recorder = null
        if (deleteFile) {
            activeRecordingFile?.delete()
        }
        activeRecordingFile = null
    }

    fun togglePlayback(audioUriOrDataUri: String, onFinished: () -> Unit): Boolean {
        if (activePlayingUrl == audioUriOrDataUri && player?.isPlaying == true) {
            stopPlayback()
            return false
        }
        stopPlayback()
        return try {
            val playableUri = MediaStorageHelper.resolvePlayableUri(context, audioUriOrDataUri)
            val mp = MediaPlayer().apply {
                setDataSource(context, playableUri)
                setOnCompletionListener {
                    stopPlayback()
                    onFinished()
                }
                prepare()
                start()
            }
            player = mp
            activePlayingUrl = audioUriOrDataUri
            true
        } catch (_: Exception) {
            stopPlayback()
            onFinished()
            false
        }
    }

    fun stopPlayback() {
        player?.let { mp ->
            runCatching { if (mp.isPlaying) mp.stop() }
            runCatching { mp.release() }
        }
        player = null
        activePlayingUrl = null
    }

    fun releaseAll() {
        stopRecordingInternal(deleteFile = true)
        stopPlayback()
    }
}
