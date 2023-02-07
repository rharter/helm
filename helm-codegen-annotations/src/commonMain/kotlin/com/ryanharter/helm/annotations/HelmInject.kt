package com.ryanharter.helm.annotations

import com.ryanharter.helm.Screen
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class HelmInject(
    val screen: KClass<out Screen>,
    val scope: KClass<out Any>,
)
