package com.ryanharter.helm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver

internal actual fun ComposeNavigatorSaver(
  router: Router,
  initialScreen: Screen
): Saver<ComposeNavigator, *> = Saver<ComposeNavigator, Any>(
  save = {
    it
  },
  restore = {
    it as ComposeNavigator
  },
)

@Composable
actual fun BackHandler(
  enabled: Boolean,
  onBack: () -> Unit,
) {
  // TODO: Do something
}