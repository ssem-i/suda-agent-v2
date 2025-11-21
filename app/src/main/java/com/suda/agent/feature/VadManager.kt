package com.suda.agent.feature

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.content.res.AssetManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.getVadModelConfig
import com.suda.agent.core.AudioBufferStore

class VadManager(
    private val context: Context,
    assetManager: AssetManager,
    private val onVoiceStart: () -> Unit = {},
    private val onVoiceEnd: (ArrayList<Float>) -> Unit = {},
) {
    private val TAG = VadManager::class.simpleName

    private val sampleRate = 16000
    private val bufferSize by lazy {
        AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
    }

    private var audioRecord: AudioRecord? = null

    private var isRecording: Boolean = false
    private var inSpeech: Boolean = false
    private val speechBuffer: ArrayList<Float> = ArrayList()
    private val speechBufferLock = Any()

    private val vad = Vad(
        assetManager = assetManager,
        getVadModelConfig(0)!!
    )

    fun startRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO permission not granted; cannot start recording")
            return
        }

        // AudioRecord 초기화 (권한이 확인된 후)
        if (audioRecord == null) {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        }

        val record = audioRecord
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed")
            Log.e(TAG, "AudioRecord state: ${record?.state}")
            Log.e(TAG, "Sample rate: $sampleRate, Buffer size: $bufferSize")
            Log.e(TAG, "Min buffer size: ${AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)}")
            return
        }

        isRecording = true
        record.startRecording()

        CoroutineScope(Dispatchers.IO).launch {
            val buffer = ShortArray(bufferSize)
            while (isRecording) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    AudioBufferStore.writeData(buffer.copyOf(read))

                    val floatBuffer = FloatArray(read) {buffer[it] / 32768.0f}

                    try {
                        vad.acceptWaveform(floatBuffer)
                        val isSpeechDetected = vad.isSpeechDetected()
                        if (isSpeechDetected) {
                            if (!inSpeech) {
                                Log.d(TAG, "Speech detected")
                                onVoiceStart()
                                inSpeech = true

                                // 저장된 pre-buffer 가져오기 및 float 변환
                                val preShortBuffer = AudioBufferStore.getStoredBuffer()
                                val preFloatBuffer = FloatArray(preShortBuffer.size) { preShortBuffer[it] / 32768.0f }

                                // preBuffer 음성 추가
                                synchronized(speechBufferLock) {
                                    speechBuffer.clear()
                                    speechBuffer.addAll(preFloatBuffer.toList())
                                }
                            } else {
                                // 실시간 녹음된 음성 버퍼 저장
                                synchronized(speechBufferLock) {
                                    speechBuffer.addAll(floatBuffer.toList())
                                }
                            }
                        } else {
                            if (inSpeech) {
                                Log.d(TAG, "Speech ended")
                                inSpeech = false
                                synchronized(speechBufferLock) {
                                    onVoiceEnd(speechBuffer)
                                }
                                stopRecording()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing audio", e)
                    }
                }
            }
        }
    }

    fun stopRecording() {
        if (isRecording) {
            isRecording = false
            audioRecord?.stop()
            AudioBufferStore.releaseBuffer()
            synchronized(speechBufferLock) {
                speechBuffer.clear()
            }
        }
    }

    fun release() {
        audioRecord?.release()
        audioRecord = null
    }


}