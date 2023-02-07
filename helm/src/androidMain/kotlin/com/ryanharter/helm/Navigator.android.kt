package com.ryanharter.helm

import android.os.Build
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.core.os.bundleOf

internal actual fun ComposeNavigatorSaver(
  router: Router,
  initialScreen: Screen,
): Saver<ComposeNavigator, *> = Saver<ComposeNavigator, Bundle>(
  save = {
    bundleOf(
      "backstack" to it.backStack.toTypedArray()
    )
  },
  restore = { bundle ->
    ComposeNavigator(router, initialScreen).apply {
      backStack.clear()
      backStack.addAll(
        if (Build.VERSION.SDK_INT >= 33) {
          bundle.getParcelableArray("backstack", Screen::class.java) as Array<Screen>
        } else {
          bundle.classLoader = Screen::class.java.classLoader
          @Suppress("DEPRECATION", "UNCHECKED_CAST")
          bundle.getParcelableArray("backstack") as Array<Screen>
        }
      )
    }
  }
)

@Composable
actual fun BackHandler(
  enabled: Boolean,
  onBack: () -> Unit,
) = androidx.activity.compose.BackHandler(enabled, onBack)