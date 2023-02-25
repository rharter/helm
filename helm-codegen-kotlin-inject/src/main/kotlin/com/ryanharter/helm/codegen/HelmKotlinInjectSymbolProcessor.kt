package com.ryanharter.helm.codegen

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.IntoSet
import me.tatarka.inject.annotations.Provides
import java.util.Locale

class HelmKotlinInjectSymbolProcessorProvider : SymbolProcessorProvider {
  override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
    return HelmKotlinInjectSymbolProcessor(environment.logger, environment.codeGenerator)
  }
}

private const val helmPackageName = "com.ryanharter.helm"
private const val helmInjectAnnotationName = "$helmPackageName.annotations.HelmInject"

class HelmKotlinInjectSymbolProcessor(
  private val logger: KSPLogger,
  private val codeGenerator: CodeGenerator
) : SymbolProcessor {
  override fun process(resolver: Resolver): List<KSAnnotated> {
    val symbols = HelmSymbols(resolver)
    resolver.getSymbolsWithAnnotation(helmInjectAnnotationName)
      .forEach { element ->
        if (element !is KSClassDeclaration) {
          error("Can only generate code for classes.")
        }
        generateHelmSubcomponent(element, symbols)
      }
    return emptyList()
  }

  private fun generateHelmSubcomponent(element: KSAnnotated, symbols: HelmSymbols) {
    if (element !is KSClassDeclaration) {
      error("Can only generate code for classes.")
    }
    val elementType = element.asType(emptyList())
    val isPresenter = symbols.presenter.starProjection().isAssignableFrom(elementType)
    val isUi = symbols.ui.starProjection().isAssignableFrom(elementType)
    require(isPresenter xor isUi) { "HelmInject annotated classes must implement Presenter<*, *> or Ui<*, *>" }

    val annotation = element.annotations.first { it.annotationType.resolve() == symbols.helmInject }
    val screen = annotation.arguments.first { it.name?.asString() == "screen" }.value as KSType

    val componentClassName =
      ClassName(element.packageName.asString(), "${element.simpleName.asString()}_HelmComponent")
    val subcomponentClassName = componentClassName.nestedClass("Subcomponent")

    val subcomponentTypeSpec = TypeSpec.interfaceBuilder(componentClassName)
      // TODO Add generated annotation
      .addType(
        generateSubcomponent(
          subcomponentClassName,
          componentClassName,
          element.toClassName(),
          screen.toClassName(),
          isPresenter,
          symbols,
        )
      )
      .addFunction(
        if (isPresenter) {
          generateProvidesPresenterFactory(
            componentClassName,
            subcomponentClassName,
            element.toClassName(),
            screen.toClassName(),
            symbols,
          )
        } else {
          generateProvidesUiFactory(
            componentClassName,
            subcomponentClassName,
            element.toClassName(),
            screen.toClassName(),
            symbols,
          )
        }
      )
      .let { builder ->
        element.containingFile?.let { file -> builder.addOriginatingKSFile(file) }
          ?: builder
      }
      .build()

    FileSpec.builder(elementType.toClassName().packageName, componentClassName.simpleName)
      .addType(subcomponentTypeSpec)
      .build()
      .writeTo(codeGenerator = codeGenerator, aggregating = false)
  }

  // Generates
  //
  //  @Component
  //  public abstract class Subcomponent(
  //    @Component val parent: LoginView_HelmComponent,
  //    @get:Provides val screen: LoginScreen,
  //  ) {
  //    abstract fun element(): LoginView
  //  }
  private fun generateSubcomponent(
    className: ClassName,
    component: TypeName,
    element: ClassName,
    screen: ClassName,
    hasNavigator: Boolean,
    symbols: HelmSymbols
  ): TypeSpec {
    val ctorParams = mutableListOf(
      ParameterSpec.builder("parent", component).build(),
      ParameterSpec.builder("screen", screen).build()
    )
    val properties = mutableListOf(
      PropertySpec
        .builder("parent", component, KModifier.PUBLIC)
        .addAnnotation(Component::class)
        .initializer("parent")
        .build(),
      PropertySpec
        .builder("screen", screen, KModifier.PUBLIC)
        .addAnnotation(
          AnnotationSpec.builder(Provides::class)
            .useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
            .build()
        )
        .initializer("screen")
        .build(),
    )
    if (hasNavigator) {
      ctorParams += ParameterSpec
        .builder("navigator", symbols.navigator.toClassName())
        .build()
      properties += PropertySpec
        .builder("navigator", symbols.navigator.toClassName())
        .addAnnotation(
          AnnotationSpec.builder(Provides::class)
            .useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
            .build()
        )
        .initializer("navigator")
        .build()
    }

    return TypeSpec.classBuilder(className)
      .addModifiers(KModifier.PUBLIC, KModifier.ABSTRACT)
      .addAnnotation(Component::class)
      .primaryConstructor(
        FunSpec.constructorBuilder()
          .addParameters(ctorParams)
          .build()
      )
      .addProperties(properties)
      .addFunction(
        FunSpec.builder("element")
          .addModifiers(KModifier.PUBLIC, KModifier.ABSTRACT)
          .returns(element)
          .build()
      )
      .build()
  }

  // Generates
  //
  //  @Provides
  //  @IntoSet
  //  public fun loginPresenter_presenterFactory(): Presenter.Factory = object : Presenter.Factory {
  //    override fun create(screen: Screen, navigator: Navigator): Presenter<*, *>? {
  //      return if (screen is LoginScreen) {
  //        Subcomponent::class.create(
  //          this@LoginPresenter_HelmComponent,
  //          screen,
  //          navigator,
  //        ).element()
  //      } else {
  //        null
  //      }
  //    }
  //  }
  private fun generateProvidesPresenterFactory(
    component: ClassName,
    subcomponent: ClassName,
    element: ClassName,
    screenClassName: ClassName,
    symbols: HelmSymbols,
  ): FunSpec {
    val presenterFactory = TypeSpec.anonymousClassBuilder()
      .addSuperinterface(symbols.presenterFactory.toClassName())
      .addFunction(
        FunSpec.builder("create")
          .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
          .addParameter("screen", symbols.screen.toClassName())
          .addParameter("navigator", symbols.navigator.toClassName())
          .returns(symbols.presenter.toClassName().parameterizedBy(STAR, STAR).copy(nullable = true))
          .addCode(
            CodeBlock.builder()
              .beginControlFlow("return if (screen is %T)", screenClassName)
              .addStatement(
                "%T::class.create(this@%T, screen, navigator).element()",
                subcomponent,
                component,
              )
              .nextControlFlow("else")
              .addStatement("null")
              .endControlFlow()
              .build()
          )
          .build()
      )
      .build()

    return FunSpec
      .builder("${element.simpleName.replaceFirstChar { it.lowercase(Locale.ROOT) }}_presenterFactory")
      .addModifiers(KModifier.PUBLIC)
      .addAnnotation(Provides::class)
      .addAnnotation(IntoSet::class)
      .returns(symbols.presenterFactory.toClassName())
      .addStatement("return %L", presenterFactory)
      .build()
  }

  // Generates
  //
  //  @Provides
  //  @IntoSet
  //  public fun loginView_uiFactory(): Ui.Factory = object : Ui.Factory {
  //    override fun create(screen: Screen): Ui<*, *>? {
  //      return if (screen is LoginScreen) {
  //        Subcomponent::class.create(this@LoginView_HelmComponent, screen).element()
  //      } else {
  //        null
  //      }
  //    }
  //  }
  private fun generateProvidesUiFactory(
    component: ClassName,
    subcomponent: ClassName,
    element: ClassName,
    screen: ClassName,
    symbols: HelmSymbols,
  ): FunSpec {
    val uiFactory = TypeSpec.anonymousClassBuilder()
      .addSuperinterface(symbols.uiFactory.toClassName())
      .addFunction(
        FunSpec.builder("create")
          .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
          .addParameter("screen", symbols.screen.toClassName())
          .returns(symbols.ui.toClassName().parameterizedBy(STAR, STAR).copy(nullable = true))
          .addCode(
            CodeBlock.builder()
              .beginControlFlow("return if (screen is %T)", screen)
              .addStatement(
                "%T::class.create(this@%T, screen).element()",
                subcomponent,
                component,
              )
              .nextControlFlow("else")
              .addStatement("null")
              .endControlFlow()
              .build()
          )
          .build()
      )
      .build()

    return FunSpec
      .builder("${element.simpleName.replaceFirstChar { it.lowercase(Locale.ROOT) }}_uiFactory")
      .addModifiers(KModifier.PUBLIC)
      .addAnnotation(Provides::class)
      .addAnnotation(IntoSet::class)
      .returns(symbols.uiFactory.toClassName())
      .addStatement("return %L", uiFactory)
      .build()
  }
}

private class HelmSymbols(resolver: Resolver) {
  val helmInject = resolver.loadKSType(helmInjectAnnotationName)
  val presenterFactory = resolver.loadKSType("$helmPackageName.Presenter.Factory")
  val uiFactory = resolver.loadKSType("$helmPackageName.Ui.Factory")
  val screen = resolver.loadKSType("$helmPackageName.Screen")
  val navigator = resolver.loadKSType("$helmPackageName.Navigator")
  val presenter = resolver.loadKSType("$helmPackageName.Presenter")
  val ui = resolver.loadKSType("$helmPackageName.Ui")
}

private fun Resolver.loadKSType(name: String): KSType =
  loadOptionalKSType(name) ?: error("Failed to load $name from classpath.")

private fun Resolver.loadOptionalKSType(name: String): KSType? =
  getClassDeclarationByName(getKSNameFromString(name))?.asType(emptyList())
