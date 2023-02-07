import org.jetbrains.kotlin.gradle.plugin.KotlinJsCompilerType

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
    iosX64()
    iosArm64()

    sourceSets {
        commonMain { dependencies { api(projects.helm) } }
    }
}

android {
    namespace = "com.ryanharter.helm.codegen.annotations"
    compileSdk = 33
}