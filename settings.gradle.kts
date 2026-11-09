pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("org.jetbrains.kotlin.jvm") version providers.gradleProperty("kotlinVersion").get()
        id("org.kordamp.gradle.jandex") version providers.gradleProperty("jandexPluginVersion").get()
        id("org.jreleaser") version providers.gradleProperty("jreleaserVersion").get()
        id("io.quarkus") version providers.gradleProperty("testQuarkusVersion")
            .orElse(providers.gradleProperty("quarkusVersion")).get()
    }
}

rootProject.name = "quarkus-transactional-ext-rw-lib"
// The project name forms the coordinates that quarkus-extension.properties points to.
include("quarkus-transactional-ext-rw-lib-deployment")
project(":quarkus-transactional-ext-rw-lib-deployment").projectDir = file("deployment")
include("integration-tests")
