package com.suda.agent.core
import android.util.Log
import com.suda.agent.MainActivity.Companion.MASTER_TAG

class AudioBufferStore {

    companion object {
        private val TAG = AudioBufferStore::class.simpleName + MASTER_TAG

        private const val SAMPLE_RATE_FOR_AUDIOBUFFER = 16000
        private const val BUFFER_SIZE_IN_SECONDS_FOR_AUDIOBUFFER = 0.75
        private val BUFFER_SIZE_IN_SAMPLE_FOR_AUDIOBUFFER = (SAMPLE_RATE_FOR_AUDIOBUFFER * BUFFER_SIZE_IN_SECONDS_FOR_AUDIOBUFFER).toInt()

        private val circularBuffer = CircularAudioBuffer(SAMPLE_RATE_FOR_AUDIOBUFFER, BUFFER_SIZE_IN_SAMPLE_FOR_AUDIOBUFFER)

        fun writeData(data: ShortArray) {
            circularBuffer.writeData(data, data.size)
        }

        fun getStoredBuffer(): ShortArray {
            return circularBuffer.copyData()
        }

        fun logStoredBuffer() {
            val buffer = getStoredBuffer()
            Log.d(TAG, "AudioBufferStore Stored Audio Buffer Size: ${buffer.size}")
            Log.d(TAG, "AudioBufferStore Stored Audio Buffer Data: ${buffer.joinToString(limit = 100, prefix = "[", postfix = "]")}")
        }

        fun releaseBuffer() {
            circularBuffer.releaseBuffer()
        }
    }
}
