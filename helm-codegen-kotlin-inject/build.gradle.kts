plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.mavenPublish)
}

dependencies {
  compileOnly(libs.ksp.api)
  implementation(libs.kotlin.inject.runtime)
  implementation(libs.kotlinpoet)
  implementation(libs.kotlinpoet.ksp)

  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.kotlin.compile.testing)
  testImplementation(libs.kotlin.test)
  testImplementation(projects.helm)
  testImplementation(projects.helmCodegenAnnotations)
}

publishing {
  publications {
    create<MavenPublication>("kotlin-inject-codegen") {
      artifactId = "helm-codegen-kotlin-inject"
      from(components["kotlin"])
      pom {
        name.set("Helm Codegen for Kotlin Inject")
        description.set("KSP plugin to generate runtime implementation of Helm annotated classes for injection with Kotlin Inject.")
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