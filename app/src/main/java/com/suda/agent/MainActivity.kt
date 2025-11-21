package com.suda.agent

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.widget.TextView
import android.widget.Toast
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.suda.agent.core.MainViewModel
import com.suda.agent.core.IpConfig
import com.suda.agent.service.ConversationService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    companion object {
        const val MASTER_TAG = "Suda"
    }

    private val viewModel: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val conversationService = ConversationService(applicationContext)
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(conversationService) as T
            }
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // 권한이 승인되면 ConversationService 초기화 시작
                lifecycleScope.launch {
                    viewModel.initializeApp()
                }
            } else {
                Toast.makeText(this, "마이크 권한이 필요합니다", Toast.LENGTH_LONG).show()
            }
        }

    // UI components
    private lateinit var ipConfigInput: TextInputEditText
    private lateinit var saveIpButton: MaterialButton
    private lateinit var conversationText: TextView
    private lateinit var initializationStatus: TextView
    private lateinit var startStopButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupClickListeners()
        observeViewModel()

        // 권한 확인 및 요청
        ensureMicPermission()
    }

    private fun initializeViews() {
        ipConfigInput = findViewById(R.id.ipConfigInput)
        saveIpButton = findViewById(R.id.saveIpButton)
        conversationText = findViewById(R.id.conversationText)
        initializationStatus = findViewById(R.id.initializationStatus)
        startStopButton = findViewById(R.id.startStopButton)
        // Load saved IP
        ipConfigInput.setText(IpConfig.get(this))
    }

    private fun setupClickListeners() {
        saveIpButton.setOnClickListener {
            val ipAddress = ipConfigInput.text?.toString().orEmpty()
            val saved = IpConfig.save(this, ipAddress)
            if (saved == null) {
                ipConfigInput.error = "Must start with http:// or https://"
                return@setOnClickListener
            }
            ipConfigInput.setText(saved)
            Toast.makeText(this, "IP saved", Toast.LENGTH_SHORT).show()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(ipConfigInput.windowToken, 0)
        }

        startStopButton.setOnClickListener {
            lifecycleScope.launch {
                when (viewModel.uiState.value) {
                    is ConversationService.State.STATE_IDLE -> {
                        viewModel.onMicPressed()
                    }
                    is ConversationService.State.STATE_RECORDING -> {
                        viewModel.onMicReleased()
                    }
                    else -> {
                        // Do nothing for other states
                    }
                }
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                updateUI(state)
            }
        }

        lifecycleScope.launch {
            viewModel.initLogs.collect { logs ->
                if (logs.isNotEmpty()) {
                    conversationText.text = logs.joinToString("\n")
                }
            }
        }
    }

    private fun updateUI(state: ConversationService.State) {
        when (state) {
            is ConversationService.State.STATE_INIT_NOW -> {
                initializationStatus.text = getString(R.string.initializing)
                initializationStatus.visibility = android.view.View.VISIBLE
                startStopButton.text = getString(R.string.start_listening)
                startStopButton.isEnabled = false
            }
            is ConversationService.State.STATE_IDLE -> {
                initializationStatus.visibility = android.view.View.GONE
                startStopButton.text = getString(R.string.start_listening)
                startStopButton.isEnabled = true
            }
            is ConversationService.State.STATE_RECORDING -> {
                startStopButton.text = getString(R.string.stop_listening)
                startStopButton.isEnabled = true
            }
            is ConversationService.State.STATE_STT_RUNNING -> {
                initializationStatus.text = getString(R.string.stt_running)
                initializationStatus.visibility = android.view.View.VISIBLE
                startStopButton.isEnabled = false
            }
            is ConversationService.State.STATE_LLM_RUNNING -> {
                initializationStatus.text = getString(R.string.llm_running)
                initializationStatus.visibility = android.view.View.VISIBLE
                startStopButton.isEnabled = false
            }
            is ConversationService.State.STATE_TTS_RUNNING -> {
                initializationStatus.text = getString(R.string.tts_running)
                initializationStatus.visibility = android.view.View.VISIBLE
                startStopButton.isEnabled = false
            }
            is ConversationService.State.STATE_PLAYING -> {
                initializationStatus.text = getString(R.string.speaking)
                initializationStatus.visibility = android.view.View.VISIBLE
                startStopButton.isEnabled = false
            }
            is ConversationService.State.STATE_ERROR -> {
                initializationStatus.text = "Error: ${state.cause}"
                initializationStatus.visibility = android.view.View.VISIBLE
                startStopButton.text = getString(R.string.start_listening)
                startStopButton.isEnabled = true
            }
            else -> {
                initializationStatus.text = "Unknown State"
                initializationStatus.visibility = android.view.View.VISIBLE
                startStopButton.isEnabled = false
            }
        }
    }

    private fun ensureMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // 권한이 없으면 요청
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            // 권한이 이미 있으면 바로 초기화 시작
            lifecycleScope.launch {
                viewModel.initializeApp()
            }
        }
    }
}