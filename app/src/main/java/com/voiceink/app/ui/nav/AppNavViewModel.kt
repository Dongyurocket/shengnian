package com.voiceink.app.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceink.app.data.repo.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppNavViewModel @Inject constructor(
    settings: SettingsRepository
) : ViewModel() {
    /** 「打开 App 直接进速记」设置项；null = 尚未加载 */
    val openDirectCapture: StateFlow<Boolean?> = settings.openDirectCapture
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
