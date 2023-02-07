package com.ryanharter.helm

interface Navigator {
    fun goTo(screen: Screen)
    fun popBackStack()

    /** Continues popping the back stack while the predicate is true. */
    fun popWhile(predicate: (screen: Screen) -> Boolean)
}
