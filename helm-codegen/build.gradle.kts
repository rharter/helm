plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.mavenPublish)
}

dependencies {
    compileOnly(libs.ksp.api)
    implementation(libs.hilt.core)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
    implementation(projects.helmCodegenAnnotations)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlin.compile.testing)
    testImplementation(libs.kotlin.test)
    testImplementation(projects.helm)
    testImplementation(projects.helmCodegenAnnotations)
}

publishing {
    publications {
        create<MavenPublication>("Codegen") {
            artifactId = "helm-codegen"
            from(components["kotlin"])
            pom {
                name.set("Helm Codegen")
                description.set("KSP plugin to generate runtime implementation of Helm annotated classes.")
                url.set("https://github.com/rharter/helm")
                licenses {
                    license {
                        name.set("Apache 2")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
            }
        }
    }
}