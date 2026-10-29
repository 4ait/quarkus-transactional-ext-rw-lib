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
include("integration-tests")
