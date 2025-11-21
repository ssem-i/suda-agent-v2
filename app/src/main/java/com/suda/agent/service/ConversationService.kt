package com.suda.agent.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import android.content.res.AssetManager
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import com.k2fsa.sherpa.onnx.getOfflineModelConfig
import com.suda.agent.core.AudioBufferStore
import com.suda.agent.core.LLMResponseParser
import com.suda.agent.core.SqliteHelper
import com.suda.agent.engine.LlmClient
import com.suda.agent.feature.VadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlin.system.measureTimeMillis
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import com.suda.agent.core.IpConfig

class ConversationService(
    private val context: Context
) {
    private val TAG = ConversationService::class.simpleName

    private val httpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    private val _state = MutableStateFlow<State>(State.STATE_INIT_NOW)
    val state = _state.asStateFlow()

    private val _initLogs = MutableStateFlow<List<String>>(emptyList())
    val initLogs = _initLogs.asStateFlow()

    private val vadManager: VadManager = VadManager(
        context = context,
        assetManager = context.assets,
        onVoiceStart = { handleEvent(Event.VadSpeechStart) },
        onVoiceEnd = { floatBuffer ->
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    val text = transcribe(floatBuffer)
                    handleEvent(Event.SttDone(text))
                }.onFailure { e ->
                    Log.e(TAG, "Transcription failed", e)
                    handleEvent(Event.SttDone(""))
                }
            }
        }
    )

    private val llmClient: LlmClient by lazy { LlmClient() }

    // Initialized models/components
    private var offlineRecognizer: OfflineRecognizer? = null
    private var tts: OfflineTts? = null
    private var modelPath: String = ""
    // Init timings
    private var sttInitTime: Long = 0
    private var ttsInitTime: Long = 0
    private var llmWarmupTime: Long = 0

    // TTS 관련 필드
    private lateinit var audioTrack: AudioTrack
    private var currentTTSJob: Job? = null

    private val dbHelper = SqliteHelper(context)

    @Volatile private var lastSttLatencyMs: Long = 0
    @Volatile private var lastLlmLatencyMs: Long = 0
    @Volatile private var lastTtsLatencyMs: Long = 0

    suspend fun initializeApp() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Build info - SDK: ${android.os.Build.VERSION.SDK_INT}, ABI: ${android.os.Build.SUPPORTED_ABIS.joinToString()}")
            Log.d(TAG, "App ABI: ${context.applicationInfo.nativeLibraryDir}")
            updateNotification("Initializing STT model...")
            sttInitTime = measureTimeMillis {
                initializeSTT(15)
            }
            Log.d(TAG, "STT model initialization took: $sttInitTime ms")
            delay(200)

            updateNotification("Initializing LLM...")
            val llmWarmupStart = System.currentTimeMillis()
            initializeLLAMA()
            llmWarmupTime = System.currentTimeMillis() - llmWarmupStart
            Log.d(TAG, "LLM initialization and warm-up took: $llmWarmupTime ms")
            delay(200)

            updateNotification("Initializing TTS...")
            ttsInitTime = measureTimeMillis {
                initTts()
            }
            Log.d(TAG, "TTS initialization took: $ttsInitTime ms")
            delay(200)

            updateNotification("Initializing AudioTrack...")
            initAudioTrack()
            delay(200)

            dbHelper.writableDatabase
            dbHelper.initApiParam()


            updateNotification("Initialization complete. Ready to use!")
            _state.value = State.STATE_IDLE

            // val assetManager = context.assets
            // val files = assetManager.list("")
            // if (files.isNullOrEmpty()) {
            //     Log.d(TAG, "assets File is null")
            // } else {
            //     val wavFiles = files.filter { file -> file.contains(".wav") }
            //     Log.d(TAG, "wavFiles : $wavFiles")
            // }
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
            updateNotification("Initialization failed: ${e.message}")
        }
    }

    private fun updateNotification(message: String) {
        // Hook for foreground service notification if needed
        Log.d(TAG, message)
        _initLogs.value = listOf(message)
    }

    private fun addLog(message: String) {
        val currentLogs = _initLogs.value
        val updated = currentLogs + message
        _initLogs.value = if (updated.size > 20) updated.takeLast(20) else updated
    }

    private fun initializeSTT(modelType: Int) {
        runCatching {
            val modelConfig = getOfflineModelConfig(modelType)
            requireNotNull(modelConfig) { "Invalid STT model type: $modelType" }
            val config = OfflineRecognizerConfig(modelConfig = modelConfig)
            offlineRecognizer = OfflineRecognizer(context.assets, config)
        }.onFailure { e ->
            Log.e(TAG, "STT initialization failed", e)
            offlineRecognizer = null
        }
    }

    private fun initializeLLAMA() {
        runCatching {
            modelPath = "/data/local/tmp/model/korean/llama32-1b-htp.json"
            if (llmClient.Init(modelPath) == 0) {
                Log.d(TAG, "LLM initialization success")
            } else {
                Log.e(TAG, "LLM initialization failed")
            }
        }.onFailure { e ->
            Log.e(TAG, "LLM initialization failed", e)
        }
    }

    private fun initTts() {
        runCatching {
            var modelDir: String?
            var modelName: String?
            var acousticModelName: String? = null
            var vocoder: String? = null
            var voices: String? = null
            var ruleFsts: String? = null
            var ruleFars: String? = ""
            var lexicon: String? = ""
            var dataDir: String? = null
            var dictDir: String? = null
            var assets: AssetManager? = context.assets
            val numThreads: Int = 2

            // Current default: Korean female
            modelDir = "tts_korean_female_v241021"
            modelName = "epoch=6999-step=70000_sherpa.onnx"
            dataDir = "tts_korean_female_v241021/espeak-ng-data"

            if (dataDir != null) {
                val newDir = copyDataDir(modelDir!!)
                modelDir = "$newDir/$modelDir"
                dataDir = "$newDir/$dataDir"
                assets = null
            }

            if (dictDir != null) {
                val newDir = copyDataDir(modelDir!!)
                modelDir = "$newDir/$modelDir"
                dictDir = "$modelDir/dict"
                ruleFsts = "$modelDir/phone.fst,$modelDir/date.fst,$modelDir/number.fst"
                assets = null
            }

            val config = getOfflineTtsConfig(
                modelDir = modelDir!!,
                modelName = modelName ?: "",
                acousticModelName = acousticModelName ?: "",
                vocoder = vocoder ?: "",
                voices = voices ?: "",
                lexicon = lexicon ?: "",
                dataDir = dataDir ?: "",
                dictDir = dictDir ?: "",
                ruleFsts = ruleFsts ?: "",
                ruleFars = ruleFars ?: "",
                numThreads = numThreads
            )

            tts = OfflineTts(assetManager = assets, config = config)
            Log.d(TAG, "Initialized TTS with model: ${if (assets==null) modelDir else "assets/$modelDir"}/$modelName")
        }.onFailure { e ->
            Log.e(TAG, "TTS initialization failed", e)
            tts = null
        }
    }

        private fun initAudioTrack() {
        Log.d(TAG, "AudioTrack initialization started")
        try {
            val localTts = tts
            if (localTts == null) {
                Log.e(TAG, "TTS is not initialized")
                return
            }
            
            val sampleRate = localTts.sampleRate()
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
            )
            Log.i(TAG, "Sample rate: $sampleRate, Buffer size: $bufferSize")

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setSampleRate(sampleRate)
                .build()

            // audioTrack이 이미 초기화된 경우 release()를 호출하여 메모리에서 해제하고 다시 초기화합니다.
            if (::audioTrack.isInitialized) {
                try {
                    Log.d(TAG, "Releasing existing AudioTrack")
                    audioTrack.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to release AudioTrack: ${e.message}")
                }
            }

            audioTrack = AudioTrack(
                audioAttributes, audioFormat, bufferSize, AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            audioTrack.play()
            Log.d(TAG, "AudioTrack initialization completed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun ensureAudioTrackInitialized() {
        if (!::audioTrack.isInitialized || audioTrack.state != AudioTrack.STATE_INITIALIZED) {
            Log.d(TAG, "Reinitializing AudioTrack as it is not initialized or in an invalid state.")
            initAudioTrack()
        }
    }

    private fun copyDataDir(dataDir: String): String {
        return try {
            Log.i(TAG, "data dir is $dataDir")
            copyAssets(dataDir)

            val newDataDir = context.getExternalFilesDir(null)?.absolutePath
                ?: context.filesDir.absolutePath
            Log.i(TAG, "newDataDir: $newDataDir")
            newDataDir
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy data directory: ${e.message}")
            e.printStackTrace()
            ""
        }
    }

    private fun copyAssets(path: String) {
        try {
            val assets: Array<String>? = context.assets.list(path)
            if (assets!!.isEmpty()) {
                copyFile(path)
            } else {
                val fullPath = "${(context.getExternalFilesDir(null) ?: context.filesDir).absolutePath}/$path"
                val dir = File(fullPath)
                dir.mkdirs()
                for (asset in assets) {
                    val p: String = if (path == "") "" else "$path/"
                    copyAssets("$p$asset")
                }
            }
        } catch (ex: IOException) {
            Log.e(TAG, "Failed to copy $path. $ex")
        }
    }

    private fun copyFile(filename: String) {
        try {
            val istream = context.assets.open(filename)
            val newFilename = ((context.getExternalFilesDir(null) ?: context.filesDir).absolutePath) + "/" + filename
            val ostream = FileOutputStream(newFilename)
            val buffer = ByteArray(1024)
            var read: Int
            while (istream.read(buffer).also { read = it } != -1) {
                ostream.write(buffer, 0, read)
            }
            istream.close()
            ostream.flush()
            ostream.close()
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to copy $filename, $ex")
        }
    }

    fun handleEvent(event: Event) {
        when (_state.value) {
            is State.STATE_IDLE -> when (event) {
                is Event.MicPressed -> startListening()
                else -> Unit
            }
            is State.STATE_RECORDING -> when (event) {
                is Event.VadSpeechStart -> _state.value = State.STATE_STT_RUNNING
                is Event.MicReleased -> stopListening()
                else -> Unit
            }
            is State.STATE_STT_RUNNING -> when (event) {
                is Event.SttDone -> startThinking(event.text)
                else -> Unit
            }
            is State.STATE_LLM_RUNNING -> when (event) {
                is Event.LlmDone -> startSpeaking(event.text)
                else -> Unit
            }
            is State.STATE_TTS_RUNNING, is State.STATE_PLAYING -> when (event) {
                is Event.TtsDone -> _state.value = State.STATE_IDLE
                else -> Unit
            }
            is State.STATE_INTERRUPTED -> when (event) {
                is Event.UserStop -> _state.value = State.STATE_IDLE
                else -> Unit
            }
            is State.STATE_ERROR -> when (event) {
                is Event.SystemError -> _state.value = State.STATE_IDLE
                else -> Unit
            }
            is State.STATE_INIT_NOW -> Unit
        }
    }

    private fun startListening() {
        _initLogs.value = emptyList()
        _state.value = State.STATE_RECORDING
        vadManager.startRecording()
    }

    private fun stopListening() {
        vadManager.stopRecording()
        _state.value = State.STATE_IDLE
    }

    fun stopTTS() {
        Log.d(TAG, "Stopping TTS")
        currentTTSJob?.cancel()
        currentTTSJob = null
        resetState()
        
        // AudioTrack 정리
        if (::audioTrack.isInitialized) {
            try {
                audioTrack.stop()
                audioTrack.release()
                Log.d(TAG, "AudioTrack stopped and released")
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping AudioTrack", e)
            }
        }
    }

    fun cleanup() {
        Log.d(TAG, "Cleaning up ConversationService")
        stopTTS()
        vadManager.release()
        
        // TTS 해제
        tts?.release()
        tts = null
        
        // STT 해제  
        offlineRecognizer?.release()
        offlineRecognizer = null
    }

    private fun transcribe(samples: ArrayList<Float>): String {
        val recognizer = offlineRecognizer ?: return ""
        return runCatching {
            val stream = recognizer.createStream()
            val startTime = System.currentTimeMillis()

            // Convert Float list to FloatArray for JNI - create a copy to avoid concurrent modification
            val floatSamples = samples.toTypedArray().toFloatArray()
            // Sherpa offline recognizer's stream accepts float32 PCM at 16kHz by default
            stream.acceptWaveform(floatSamples, 16000)
            recognizer.decode(stream)
            var resultText = recognizer.getResult(stream).text
            val endTime = System.currentTimeMillis()
            val sttLatencyMs = endTime - startTime
            lastSttLatencyMs = sttLatencyMs
            Log.d(TAG, "STT latency: ${sttLatencyMs} ms")
            CoroutineScope(Dispatchers.IO).launch { sendSttText(resultText, sttLatencyMs.toFloat()) }

            // Cleanup
            stream.release()

            if (resultText.isNotEmpty()) {
                Log.d(TAG, "STT result: $resultText")
            }

            resultText
        }.getOrElse { e ->
            Log.e(TAG, "Error during STT processing", e)
            ""
        }
    }

    private fun startThinking(text: String) {
        _state.value = State.STATE_LLM_RUNNING
        CoroutineScope(Dispatchers.IO).launch {
            // 입력 문장에서 마지막에 있는 "."과 "?"을 제거
            var filteredSttResult = text.trimEnd()
                .removeSuffix(".")
                .removeSuffix("?")
            val prompt =
                "<|im_start|>Below is the query from the users, please choose the correct function and generate the parameters to call the function. Query: ${filteredSttResult} Response:"
            Log.d(TAG, "INPUT : {$prompt}")
            val response = runCatching {
                val llmStart = System.currentTimeMillis()
                val r = llmClient.Infer(prompt)
                lastLlmLatencyMs = System.currentTimeMillis() - llmStart
                r
            }.getOrElse { e ->
                Log.e(TAG, "LLM inference failed, using echo response", e)
                "${text}"
            }

            launch(Dispatchers.IO) { sendLlmText(response, lastLlmLatencyMs.toFloat()) }
            var llmResponseList = LLMResponseParser.llmResponseParse(response)
            Log.d(TAG, "LLM Response List: $llmResponseList")
            var ttsText = getSimpleTtsText(llmResponseList[0].token, llmResponseList[0].parameters)
            Log.d(TAG, "TTS Text: $ttsText")

            handleEvent(Event.LlmDone(ttsText))
        }
    }

    private fun startSpeaking(text: String) {
        _state.value = State.STATE_TTS_RUNNING
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                playTTS(text)
            }.onFailure { e ->
                Log.e(TAG, "TTS failed, skipping audio playback", e)
            }
            handleEvent(Event.TtsDone)
        }
    }

    private fun resetState() {
        _state.value = State.STATE_IDLE
    }

    private suspend fun playTTS(text: String) {
        Log.d(TAG, "playTTS original TTS text: $text")
        _state.value = State.STATE_PLAYING

        val ttsText = text
        Log.d(TAG, "playTTS Playing TTS text: $ttsText")

        ensureAudioTrackInitialized()

        // 문장별로 분리 (더 정밀한 분리 로직)
        val textList = splitSentences(ttsText)
        Log.d(TAG, "Split into ${textList.size} sentences: $textList")

        currentTTSJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val audioChannel = Channel<Pair<String, GeneratedAudio>>(Channel.UNLIMITED)
                val ttsStartTime = System.currentTimeMillis()
                var sentFirstLatency = false

                // TTS 생성 작업
                val audioGenerationJob = launch(Dispatchers.IO) {
                    try {
                        for (splitTtsText in textList) {
                            if (_state.value == State.STATE_IDLE) {
                                break
                            }

                            Log.d(TAG, "TTS Generate......")
                            
                            val localTts = tts
                            if (localTts != null) {
                                val audioData = localTts.generate(text = splitTtsText.trim(), sid = 0, speed = 1.0f)
                                Log.d(TAG, "TTS Generate End......")
                                if (!sentFirstLatency) {
                                    lastTtsLatencyMs = System.currentTimeMillis() - ttsStartTime
                                    sentFirstLatency = true
                                    launch(Dispatchers.IO) { sendTtsText(ttsText, lastTtsLatencyMs.toFloat()) }
                                }
                                audioChannel.send(Pair(splitTtsText, audioData))
                            } else {
                                Log.e(TAG, "TTS is null, cancelling generation")
                                break
                            }
                        }
                    } finally {
                        audioChannel.close()
                    }
                }

                // TTS 재생 작업
                val audioPlaybackJob = launch(Dispatchers.IO) {
                    try {
                        for ((splitTtsText, audioData) in audioChannel) {
                            if (_state.value == State.STATE_IDLE) {
                                break
                            }
                            Log.d(TAG, "Playing: $splitTtsText")
                            playAudio(audioData)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during audio playback", e)
                    }
                }

                try {
                    audioGenerationJob.join()
                    audioPlaybackJob.join()
                } finally {
                    // 초기화
                    resetState()
                    currentTTSJob = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in playTTS", e)
                currentTTSJob = null
            }
        }.also {
            // 코루틴이 완료될 때까지 대기
            it.join()
        }
    }

    private fun splitSentences(text: String): List<String> {
        // 더 정밀한 문장 분리 로직
        val sentences = mutableListOf<String>()
        val delimiters = arrayOf(".", "!", "?", "~", "…")
        
        var currentSentence = ""
        var i = 0
        
        while (i < text.length) {
            val char = text[i]
            currentSentence += char
            
            // 구분자를 만났을 때
            if (delimiters.contains(char.toString())) {
                // 다음 문자가 공백이거나 문자열 끝이면 문장 끝으로 판단
                if (i == text.length - 1 || (i + 1 < text.length && text[i + 1].isWhitespace())) {
                    val trimmedSentence = currentSentence.trim()
                    if (trimmedSentence.isNotEmpty()) {
                        sentences.add(trimmedSentence)
                    }
                    currentSentence = ""
                }
            }
            i++
        }
        
        // 마지막 문장 처리
        val trimmedSentence = currentSentence.trim()
        if (trimmedSentence.isNotEmpty()) {
            sentences.add(trimmedSentence)
        }
        
        // 빈 문장들 제거
        return sentences.filter { it.isNotBlank() }
    }

    private fun playAudio(audioData: GeneratedAudio) {
        try {
            ensureAudioTrackInitialized()
            
            val samples = audioData.samples
            Log.d(TAG, "Playing audio with ${samples.size} samples")
            
            if (audioTrack.state == AudioTrack.STATE_INITIALIZED) {
                val written = audioTrack.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                Log.d(TAG, "AudioTrack wrote $written/${samples.size} samples")
                
                // 재생 완료까지 대기
                val durationMs = (samples.size * 1000L) / audioData.sampleRate
                Thread.sleep(durationMs + 100) // 약간의 버퍼 추가
                Log.d(TAG, "Audio playback completed in ${durationMs}ms")
            } else {
                Log.e(TAG, "AudioTrack not initialized properly")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during audio playback", e)
        }
    }

    private fun getSimpleTtsText(token: String, parameters: Map<String, String>): String {
        val apiCallParam = dbHelper.selectByMultiParam(token, parameters)
            ?: throw Exception("$token Type Not Found type : $parameters")

        return apiCallParam.answerKr
    }

    private fun getBaseUrl(): String = IpConfig.get(context)

    private suspend fun sendSttText(sttResult: String, sttLatency: Float) = withContext(Dispatchers.IO) {
        if (sttResult.isBlank()) return@withContext
        
        // 화면에 로그 표시
        withContext(Dispatchers.Main) {
            addLog("STT: $sttResult (${sttLatency.toInt()}ms)")
        }
        
        val json = JSONObject().apply {
            put("data", sttResult)
            put("response_time", sttLatency)
        }
        val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(getBaseUrl() + "suda/stt")
            .post(body)
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully sent STT data $sttResult")
                } else {
                    Log.e(TAG, "Failed to send STT data: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending STT data", e)
        }
    }

    private suspend fun sendLlmText(llmResult: String, llmLatency: Float) = withContext(Dispatchers.IO) {
        // 화면에 로그 표시
        withContext(Dispatchers.Main) {
            addLog("LLM: $llmResult (${llmLatency.toInt()}ms)")
        }
        
        val json = JSONObject().apply {
            put("data", llmResult)
            put("response_time", llmLatency)
        }
        val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(getBaseUrl() + "suda/llm")
            .post(body)
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully sent LLM data $llmResult")
                } else {
                    Log.e(TAG, "Failed to send LLM data: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending LLM data", e)
        }
    }

    private suspend fun sendTtsText(ttsResult: String, ttsFirstLatency: Float) = withContext(Dispatchers.IO) {
        // 화면에 로그 표시
        withContext(Dispatchers.Main) {
            addLog("TTS: $ttsResult (${ttsFirstLatency.toInt()}ms)")
        }
        
        val json = JSONObject().apply {
            put("data", ttsResult)
            put("response_time", ttsFirstLatency)
        }
        val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(getBaseUrl() + "suda/tts")
            .post(body)
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully sent TTS data $ttsResult")
                } else {
                    Log.e(TAG, "Failed to send TTS data: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending TTS data", e)
        }
    }

    sealed class State {
        object STATE_INIT_NOW : State()
        object STATE_IDLE : State()
        object STATE_RECORDING : State()
        object STATE_STT_RUNNING : State()
        object STATE_LLM_RUNNING : State()
        object STATE_TTS_RUNNING : State()
        object STATE_PLAYING : State()
        object STATE_INTERRUPTED : State()
        data class STATE_ERROR(val cause: String) : State()
    }

    sealed class Event {
        object MicPressed : Event()
        object MicReleased : Event()
        object VadSpeechStart : Event()
        object VadSpeechEnd : Event()
        data class SttDone(val text: String) : Event()
        data class LlmDone(val text: String) : Event()
        object TtsDone : Event()
        object UserStop : Event()
        data class SystemError(val error: String) : Event()
    }
}
