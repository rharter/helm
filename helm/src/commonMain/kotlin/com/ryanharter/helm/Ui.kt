package com.ryanharter.helm

import androidx.compose.runtime.Composable

interface Ui<UiModel : Any, UiEvent : Any> {
  @Composable
  fun Content(model: UiModel, onEvent: (UiEvent) -> Unit)

  interface Factory {
    fun create(screen: Screen): Ui<*, *>?
  }
}
