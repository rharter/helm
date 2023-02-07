package com.ryanharter.helm

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow

interface Presenter<UiModel : Any, UiEvent : Any> {
    @Composable
    fun models(events: Flow<UiEvent>): UiModel

    interface Factory {
        fun create(
            screen: Screen,
            navigator: Navigator
        ): Presenter<*, *>?
    }
}