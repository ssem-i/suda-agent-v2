package com.suda.agent.core

import android.util.Log
import kotlin.math.min
import com.suda.agent.MainActivity.Companion.MASTER_TAG

class CircularAudioBuffer(sampleRate: Int, bufferSizeInSample: Int) {
    private val TAG = CircularAudioBuffer::class.simpleName + MASTER_TAG
    private val bufferSize = bufferSizeInSample

    //private val bufferSize = sampleRate * bufferSizeInSeconds
    private val buffer = ShortArray(bufferSize)
    private var writeIndex = 0
    private var isBufferFull = false

    fun writeData(data: ShortArray, length: Int) {
        val writeLength = min(length, data.size)
        if (writeLength > bufferSize) {
            throw IllegalArgumentException("Data length exceeds buffer size!")
        }
        synchronized(this) {
            for (i in 0 until writeLength) {
                buffer[writeIndex] = data[i]
                writeIndex = (writeIndex + 1) % bufferSize
                if (writeIndex == 0) {
                    isBufferFull = true
                }
            }
        }
    }

    fun copyData(): ShortArray {
        synchronized(this) {
            val actualSize = if (isBufferFull) bufferSize else writeIndex
            val result = ShortArray(actualSize)
            if (isBufferFull) {// 버퍼가 꽉 찼을 때
                System.arraycopy(buffer, writeIndex, result, 0, bufferSize - writeIndex) // 버퍼 writeIndex => result 0부터 bufferSize - writeIndex까지 복사
                System.arraycopy(buffer, 0, result, bufferSize - writeIndex, writeIndex) // 버퍼 0 => result bufferSize - writeIndex부터 writeIndex까지 복사
            } else { // 버퍼가 꽉 차지 않았을 때
                System.arraycopy(buffer, 0, result, 0, writeIndex) 
            }
            return result
        }
    }

    fun logBufferData() {
        val data = copyData()
        val dataString = data.joinToString(separator = ", ", prefix = "[", postfix = "]")
        Log.d(TAG, "SILOGOOD CircularAudioBuffer Data: $dataString")
    }

    fun releaseBuffer() {
        synchronized(this) {
            buffer.fill(0)
            writeIndex = 0
            isBufferFull = false
        }
    }
}
