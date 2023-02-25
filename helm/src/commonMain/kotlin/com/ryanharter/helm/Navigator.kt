package com.ryanharter.helm

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.with
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

interface Navigator {
  fun goTo(screen: Screen)
  fun popBackStack()

  /** Continues popping the back stack while the predicate is true. */
  fun popWhile(predicate: (screen: Screen) -> Boolean)
}

@Stable
class ComposeNavigator(
  private val router: Router,
  initialScreen: Screen,
) : Navigator {

  internal val backStack = mutableStateListOf(initialScreen)

  @Composable
  internal fun Content(screen: Screen) {
    val presenter = remember(screen) {
      @Suppress("UNCHECKED_CAST")
      router.createPresenter(screen, this) as Presenter<Any, Any>
    }
    val ui = remember(screen) {
      @Suppress("UNCHECKED_CAST")
      router.createUi(screen) as Ui<Any, Any>
    }
    val events = remember(screen) { Channel<Any>() }

    val model = presenter.models(events = events.receiveAsFlow())
    ui.Content(model = model, onEvent = { events.trySend(it) })
  }

  override fun goTo(screen: Screen) {
    backStack.add(screen)
  }

  override fun popBackStack() {
    if (backStack.size > 1) backStack.removeLast()
  }

  override fun popWhile(predicate: (screen: Screen) -> Boolean) {
    while (backStack.lastOrNull()?.let(predicate) == true) popBackStack()
  }
}

internal expect fun ComposeNavigatorSaver(
  router: Router,
  initialScreen: Screen
): Saver<ComposeNavigator, *>


@Composable
fun rememberNavigator(
  router: Router,
  initialScreen: Screen,
): Navigator =
  rememberSaveable(inputs = arrayOf(router), saver = ComposeNavigatorSaver(router, initialScreen)) {
    ComposeNavigator(router, initialScreen)
  }

@Composable
expect fun BackHandler(
  enabled: Boolean = true,
  onBack: () -> Unit,
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun Navigator(
  navigator: Navigator,
  modifier: Modifier = Modifier,
) {
  require(navigator is ComposeNavigator)

  BackHandler(enabled = navigator.backStack.size > 1, onBack = navigator::popBackStack)

  val screen = navigator.backStack.lastOrNull()
  if (screen != null) {
    val previousBackStackSize = rememberSaveable { navigator.backStack.size }
    val direction = (navigator.backStack.size - previousBackStackSize).coerceIn(-1..1)
    AnimatedContent(
      targetState = screen,
      modifier = modifier,
      transitionSpec = {
        if (direction == 0) {
          fadeIn() with fadeOut()
        } else {
          slideInHorizontally(tween()) { (it * 0.05f).toInt() * direction } + fadeIn() with
              slideOutHorizontally(tween()) { (it * 0.05f).toInt() * -direction } + fadeOut()
        }.using(
          SizeTransform(clip = false)
        )
      }
    ) {
      navigator.Content(screen = screen)
    }
  }
}
