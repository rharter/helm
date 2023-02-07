package com.ryanharter.helm

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
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
import androidx.core.os.bundleOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.receiveAsFlow

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

    fun saveState(): Bundle = bundleOf(
        "backstack" to backStack.toTypedArray()
    )

    fun restoreState(bundle: Bundle) {
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

private fun ComposeNavigatorSaver(
    router: Router,
    initialScreen: Screen,
): Saver<ComposeNavigator, *> = Saver<ComposeNavigator, Bundle>(
    save = { it.saveState() },
    restore = { ComposeNavigator(router, initialScreen).apply { restoreState(it) } }
)

@Composable
fun rememberNavigator(
    router: Router,
    initialScreen: Screen,
): ComposeNavigator =
    rememberSaveable(inputs = arrayOf(router), saver = ComposeNavigatorSaver(router, initialScreen)) {
        ComposeNavigator(router, initialScreen)
    }

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun Navigator(
    navigator: ComposeNavigator,
    modifier: Modifier = Modifier,
) {
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