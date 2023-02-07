package com.ryanharter.helm.codegen

import kotlin.reflect.KClass

public infix fun KClass<*>.extends(other: KClass<*>): Boolean =
    other.java.isAssignableFrom(this.java)