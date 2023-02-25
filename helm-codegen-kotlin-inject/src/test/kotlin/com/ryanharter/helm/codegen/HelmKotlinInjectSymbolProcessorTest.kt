package com.ryanharter.helm.codegen

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.SourceFile.Companion.kotlin
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.symbolProcessorProviders
import org.junit.Test

class HelmKotlinInjectSymbolProcessorTest {

  @Test
  fun `generates presenter subcomponent`() {
    val source = kotlin(
      "MyPresenter.kt",
      """
                package com.ryanharter.helm.codegen.test
                
                import androidx.compose.runtime.Composable
                import com.ryanharter.helm.Screen
                import com.ryanharter.helm.Presenter
                import com.ryanharter.helm.annotations.HelmInject
                import kotlinx.coroutines.flow.Flow

                abstract class AppComponent private constructor()
                data class MyScreen(val id: String) : Screen

                @HelmInject(MyScreen::class, AppComponent::class)
                class MyPresenter : Presenter<String, String> {
                    @Composable
                    override fun models(events: Flow<String>): String {
                        return "Foo"
                    }
                }
            """
    )

    val compilation = KotlinCompilation().apply {
      inheritClassPath = true
      sources = listOf(source)
      symbolProcessorProviders = listOf(HelmKotlinInjectSymbolProcessorProvider())
    }
    val result = compilation.compile()
    assertThat(result.exitCode).isEqualTo(ExitCode.OK)
    val generatedSources = compilation.kspSourcesDir.walkTopDown().filter {
      it.isFile
    }.toList()

    val genned = generatedSources.single { it.name.endsWith("MyPresenter_HelmComponent.kt") }
    assertThat(generatedSources.size).isEqualTo(1)
  }

  @Test
  fun `generates ui subcomponent`() {
    val source = kotlin(
      "MyUi.kt",
      """
                package com.ryanharter.helm.codegen.test
                
                import androidx.compose.runtime.Composable
                import com.ryanharter.helm.Screen
                import com.ryanharter.helm.Ui
                import com.ryanharter.helm.annotations.HelmInject
                import kotlinx.coroutines.flow.Flow

                abstract class AppComponent private constructor()
                data class MyScreen(val id: String) : Screen

                @HelmInject(MyScreen::class, AppComponent::class)
                class MyUi : Ui<String, String> {
                    @Composable
                    override fun Content(model: String, onEvent: (String) -> Unit) {
                    }
                }
            """
    )

    val compilation = KotlinCompilation().apply {
      inheritClassPath = true
      sources = listOf(source)
      symbolProcessorProviders = listOf(HelmKotlinInjectSymbolProcessorProvider())
    }
    val result = compilation.compile()
    assertThat(result.exitCode).isEqualTo(ExitCode.OK)
    val generatedSources = compilation.kspSourcesDir.walkTopDown().filter {
      it.isFile
    }.toList()
    val genned = generatedSources.first { it.name.endsWith("HelmComponent.kt") }.readText()
    assertThat(generatedSources.size).isEqualTo(1)
  }
}