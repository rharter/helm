import org.jetbrains.kotlin.gradle.plugin.KotlinJsCompilerType
import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME

plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.mavenPublish)
}

kotlin {
  android { publishLibraryVariants("release") }
  jvm("desktop")
//    js(KotlinJsCompilerType.IR) {
//        browser()
//    }
  macosX64()
  macosArm64()
  iosX64("uikitX64")
  iosArm64("uikitArm64")
  iosSimulatorArm64("uikitSimulatorArm64")

  sourceSets {
    val commonMain by getting {
      dependencies {
        api(libs.compose.runtime)
        api(libs.coroutines)
        api(libs.compose.runtime)
        api(libs.compose.animation)
        api(libs.compose.foundation)
      }
    }
    maybeCreate("androidMain").apply {
      dependencies {
        api(libs.androidx.activity.compose)
      }
    }
    val nativeMain by creating {
      dependsOn(commonMain)
    }
    val darwinMain by creating {
      dependsOn(nativeMain)
    }
    val macosMain by creating {
      dependsOn(darwinMain)
    }
    val macosX64Main by getting {
      dependsOn(macosMain)
    }
    val macosArm64Main by getting {
      dependsOn(macosMain)
    }
    val uikitMain by creating {
      dependsOn(darwinMain)
    }
    val uikitX64Main by getting {
      dependsOn(uikitMain)
    }
    val uikitArm64Main by getting {
      dependsOn(uikitMain)
    }
    val uikitSimulatorArm64Main by getting {
      dependsOn(uikitMain)
    }
  }
}

android {
  namespace = "com.ryanharter.helm"
  compileSdk = 33
}

dependencies { add(PLUGIN_CLASSPATH_CONFIGURATION_NAME, libs.androidx.compose.compiler) }