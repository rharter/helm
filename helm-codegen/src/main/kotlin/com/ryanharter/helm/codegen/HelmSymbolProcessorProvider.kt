package com.ryanharter.helm.codegen

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSType
import com.ryanharter.helm.Navigator
import com.ryanharter.helm.Presenter
import com.ryanharter.helm.Screen
import com.ryanharter.helm.Ui
import com.ryanharter.helm.annotations.HelmInject
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import dagger.BindsInstance
import dagger.Module
import dagger.Provides
import dagger.Subcomponent
import dagger.hilt.InstallIn
import dagger.multibindings.IntoSet

class HelmSymbolProcessorProvider : SymbolProcessorProvider {
  override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
    return HelmSymbolProcessor(environment.logger, environment.codeGenerator)
  }
}

private val helmInjectAnnotationName = HelmInject::class.qualifiedName!!

class HelmSymbolProcessor(
  private val logger: KSPLogger,
  private val codeGenerator: CodeGenerator
) : SymbolProcessor {
  override fun process(resolver: Resolver): List<KSAnnotated> {
    val symbols = HelmSymbols(resolver)
    resolver.getSymbolsWithAnnotation(helmInjectAnnotationName)
      .forEach { element ->
        if (element !is KSClassDeclaration) {
          error("Can only generate code for classes and functions.")
        }
        generatePresenterSubcomponent(element, symbols)
      }
    return emptyList()
  }

  private fun generatePresenterSubcomponent(element: KSAnnotated, symbols: HelmSymbols) {
    if (element !is KSClassDeclaration) {
      error("Can only generate code for classes and functions.")
    }
    val elementType = element.asType(emptyList())
    val isPresenter = symbols.presenter.starProjection().isAssignableFrom(elementType)
    val isUi = symbols.ui.starProjection().isAssignableFrom(elementType)
    require(isPresenter xor isUi) { "HiltInject annotated classed must implement Presenter<*, *> or Ui<*, *>" }

    val annotation = element.annotations.first { it.annotationType.resolve() == symbols.helmInject }
    val screen = annotation.arguments.first { it.name?.asString() == "screen" }.value as KSType
    val parentComponent =
      annotation.arguments.first { it.name?.asString() == "scope" }.value as KSType

    val componentClassName =
      ClassName(element.packageName.asString(), "${element.simpleName.asString()}_HelmComponent")
    val factoryClassName = componentClassName.nestedClass("Factory")
    val parentModuleName = componentClassName.nestedClass("ParentModule")

    val subcomponentTypeSpec = TypeSpec.interfaceBuilder(componentClassName)
      .addAnnotation(Subcomponent::class)
      // TODO Add generated annotation
      .addFunction(
        FunSpec.builder("element")
          .addModifiers(KModifier.ABSTRACT)
          .returns(element.toClassName())
          .build()
      )
      .addType(
        generateSubcomponentFactory(
          factoryClassName,
          componentClassName,
          screen.toClassName(),
          isPresenter,
          symbols,
        )
      )
      .addType(
        generateParentModule(
          parentModuleName,
          componentClassName,
          parentComponent.toClassName(),
        ) {
          if (isPresenter) {
            generateProvidesPresenterFactory(
              factoryClassName,
              screen.toClassName(),
              symbols,
            )
          } else {
            generateProvidesUiFactory(
              factoryClassName,
              screen.toClassName(),
              symbols,
            )
          }
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

  private fun generateSubcomponentFactory(
    className: ClassName,
    subcomponent: TypeName,
    screen: ClassName,
    hasNavigator: Boolean,
    symbols: HelmSymbols
  ): TypeSpec {
    val createParams = mutableListOf(
      ParameterSpec.builder("screen", screen)
        .addAnnotation(BindsInstance::class)
        .build()
    )
    if (hasNavigator) {
      createParams += ParameterSpec
        .builder("navigator", symbols.navigator.toClassName())
        .addAnnotation(BindsInstance::class)
        .build()
    }

    val createFun = FunSpec.builder("create")
      .addModifiers(KModifier.ABSTRACT)
      .addParameters(createParams)
      .returns(subcomponent)
      .build()

    return TypeSpec.interfaceBuilder(className)
      .addAnnotation(Subcomponent.Factory::class)
      .addFunction(createFun)
      .build()
  }

  private fun generateParentModule(
    className: ClassName,
    componentClassName: ClassName,
    parentComponent: ClassName,
    funSpecProducer: () -> FunSpec,
  ): TypeSpec = TypeSpec.classBuilder(className)
    .addAnnotation(
      AnnotationSpec.builder(Module::class)
        .addMember("subcomponents = [%T::class]", componentClassName)
        .build()
    )
    .addAnnotation(
      AnnotationSpec.builder(InstallIn::class)
        .addMember("%T::class", parentComponent)
        .build()
    )
    .addFunction(funSpecProducer())
    .build()

  private fun generateProvidesPresenterFactory(
    factoryClassName: ClassName,
    screenClassName: ClassName,
    symbols: HelmSymbols,
  ) = FunSpec.builder("presenterFactory")
    .addAnnotation(Provides::class)
    .addAnnotation(IntoSet::class)
    .addParameter("componentFactory", factoryClassName)
    .returns(symbols.presenterFactory.toClassName())
    .addCode(
      CodeBlock.builder()
        .beginControlFlow("return object : %T", symbols.presenterFactory.toClassName())
        .beginControlFlow(
          "override fun create(screen: %T, navigator: %T): %T?",
          symbols.screen.toClassName(), symbols.navigator.toClassName(),
          symbols.presenter.toClassName().parameterizedBy(STAR, STAR)
        )
        .beginControlFlow("return if (screen is %T)", screenClassName)
        .addStatement("componentFactory.create(screen, navigator).element()")
        .nextControlFlow("else")
        .addStatement("null")
        .endControlFlow()
        .endControlFlow()
        .endControlFlow()
        .build()
    )
    .build()

  private fun generateProvidesUiFactory(
    factoryClassName: ClassName,
    screenClassName: ClassName,
    symbols: HelmSymbols,
  ) = FunSpec.builder("uiFactory")
    .addAnnotation(Provides::class)
    .addAnnotation(IntoSet::class)
    .addParameter("componentFactory", factoryClassName)
    .returns(symbols.uiFactory.toClassName())
    .addCode(
      CodeBlock.builder()
        .beginControlFlow("return object : %T", symbols.uiFactory.toClassName())
        .beginControlFlow(
          "override fun create(screen: %T): %T?",
          symbols.screen.toClassName(),
          symbols.ui.toClassName().parameterizedBy(STAR, STAR)
        )
        .beginControlFlow("return if (screen is %T)", screenClassName)
        .addStatement("componentFactory.create(screen).element()")
        .nextControlFlow("else")
        .addStatement("null")
        .endControlFlow()
        .endControlFlow()
        .endControlFlow()
        .build()
    )
    .build()
}

private class HelmSymbols(resolver: Resolver) {
  val helmInject = resolver.loadKSType<HelmInject>()
  val presenterFactory = resolver.loadKSType<Presenter.Factory>()
  val uiFactory = resolver.loadKSType<Ui.Factory>()
  val screen = resolver.loadKSType<Screen>()
  val navigator = resolver.loadKSType<Navigator>()
  val presenter = resolver.loadKSType<Presenter<*, *>>()
  val ui = resolver.loadKSType<Ui<*, *>>()
}

private inline fun <reified T> Resolver.loadKSType(): KSType =
  loadOptionalKSType<T>() ?: error("Failed to load ${T::class.qualifiedName} from classpath.")

private inline fun <reified T> Resolver.loadOptionalKSType(): KSType? =
  loadKSName<T>()?.let { getClassDeclarationByName(it) }?.asType(emptyList())

private inline fun <reified T> Resolver.loadKSName(): KSName? =
  when (val name = T::class.qualifiedName) {
    null -> null
    else -> getKSNameFromString(name)
  }