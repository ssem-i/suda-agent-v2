package com.suda.agent.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suda.agent.service.ConversationService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class MainViewModel(
    private val conversationService: ConversationService
) : ViewModel() {

    val uiState: StateFlow<ConversationService.State> = conversationService.state
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ConversationService.State.STATE_INIT_NOW
        )

    val initLogs: StateFlow<List<String>> = conversationService.initLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    fun onMicPressed() {
        conversationService.handleEvent(ConversationService.Event.MicPressed)
    }

    fun onMicReleased() {
        conversationService.handleEvent(ConversationService.Event.MicReleased)
    }

    suspend fun initializeApp() {
        conversationService.initializeApp()
    }
}
