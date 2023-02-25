# Helm

Helm is a multiplatform implementation of Cashapp's Broadway architecture based on a
[talk from Droidcon NYC 2022](https://www.droidcon.com/2022/09/29/architecture-at-scale/).

> This is an experimental library that's not really intended for public use yet. I'm using it to
explore some ideas, and would love feedback, but if you'd like something that feels a bit less 
**experimental-playground-to-test-ideas-for-funsies** then perhaps have a look at 
[Circuit](https://slackhq.github.io/circuit/).

## Usage

Helm is a navigation and presentation framework that allows you to build your UI and Presenter layer
using Jetpack Compose all the way down.

The basic implementation consists of a `Screen`, which is the data that represents a navigation
destination, a `Presenter`, a class containing a single Composable function that takes in a stream
of `UiEvent`s and converts them into `UiModel`s, and a `Ui`, which displays `UiModel`s and emits
`UiEvent`s.

These three basic building blocks allow for decoupled layers that are easy to test and wire
together.

## Example

Define your models, which consist of a `Screen` used to navigate to your Ui, a `UiModel` which your
presenter produces and your ui consumes, and a `UiEvent` which is emitted by your Ui and consumed by
your presenter.

```kotlin
// Define your counter screen
data class CounterScreen(val start: Int = 0) : Screen

// The UiModel is what your Presenter produces and your Ui consumes
data class CounterUiModel(
  val count: Int,
)

// The UiEvent is what your Ui emits and your Presenter consumes
sealed interface CounterUiEvent {
  data class Increment(val amount: Int = 1)
  data class Decrement(val amount: Int = 1)
}
```

Next, declare a `Ui` subclass, using the `@HelmInject` annotation to bind it to your screen. When
using the `helm-codegen` module for Android projects, your Ui will be bound in the Hilt scope
defined in the `HelmInject` annotation.  If using the `helm-codegen-kotlin-inject` module for
multiplatform projects, your Ui will be bound in the
[Kotlin Inject](https://github.com/evant/kotlin-inject) component passed to that annotation.

```kotlin
// The Ui displays UiModels and emits UiEvents
@Inject
@HelmInject(CounterScreen::class, scope = AppComponent::class)
class CounterUi : Ui<CounterUiModel, CounterUiEvent> {
  @Composable
  override fun Content(model: CounterUiModel, onEvent: (CounterUiEvent) -> Unit) {
    Column(
      verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.fillMaxSize(),
    ) {
      Text("Count: ${model.count}")
      Row {
        Button(onClick = { onEvent(Decrement(2)) }) {
          Text("-2")
        }
        Button(onClick = { onEvent(Decrement()) }) {
          Text("-1")
        }
        Button(onClick = { onEvent(Increment()) }) {
          Text("+1")
        }
        Button(onClick = { onEvent(Increment(2)) }) {
          Text("+2")
        }
      }
    }
  }
}
```

Now declare a `Presenter`, which uses a `Composable` function to consume `UiEvent`s and produce
`UiModel`s. Adding the `HelmInject` annotation will take care of wiring up your Presenter and
screen.

```kotlin
@Inject
@HelmInject(CounterScreen::class, scope = AppComponent::class)
class CounterPresenter(screen: CounterScreen) : Presenter<CounterUiModel, CounterUiEvent> {
  
  private val count by mutableStateOf(screen.start)
  
  @Composable
  override fun models(events: Flow<CounterUiEvent>): CounterUiModel {
    LaunchedEffect {
      events.collect { event ->
        when (event) {
          is Decrement -> count -= event.amount
          is Increment -> count += event.amount
        }
      }
    }
    
    return CounterUiModel(count = count)
  }
}
```

Finally, using your optional dependency injector of choice, get a `Router` to create a `Navigator`,
and use the composable `Navigator` function.

```kotlin
val appComponent = AppComponent::class.create()

@Composable
fun CounterApp() {
  val navigator = rememberNavigator(appComponent.router, CounterScreen(start = 10))

  ReadmeTheme {
    Surface {
      Navigator(navigator)
    }
  }
}
```

> Note: `Navigator(navigator)` feels a bit silly, but naming is hard. I might change this.

### Using Kotlin Inject

To tie things together with a Kotlin Inject component (for now until the codegen takes care of this)
you can add the generated `..._HelmComponent` interfaces to your component, and create a `Router` by
injecting the supplied sets of generated `Presenter.Factory` and `Ui.Factory`s.

```kotlin
@Component
abstract class AppComponent : CounterUi_HelmComponent, CounterPresenter_HelmComponent {
  abstract val router: Router

  @Provides
  protected fun provideRouter(
    presenterFactories: Set<Presenter.Factory>,
    uiFactories: Set<Ui.Factory>
  ): Router = Router(
    presenterFactories.toList(),
    uiFactories.toList(),
  )
}
```

## License

```
Copyright 2022 Ryan Harter

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```