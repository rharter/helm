package com.ryanharter.helm

class Router(
    private val presenterFactories: List<Presenter.Factory>,
    private val viewFactories: List<Ui.Factory>
) {
    fun createPresenter(screen: Screen, navigator: Navigator): Presenter<*, *> {
        return presenterFactories.firstNotNullOf { it.create(screen, navigator) }
    }

    fun createUi(screen: Screen): Ui<*, *> {
        return viewFactories.firstNotNullOf { it.create(screen) }
    }
}
