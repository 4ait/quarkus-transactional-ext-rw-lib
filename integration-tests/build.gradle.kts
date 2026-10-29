plugins {
    kotlin("jvm")
    id("io.quarkus")
}

kotlin {
    jvmToolchain(25)
}

repositories {
    mavenCentral()
}

dependencies {
    val consumerVersion = providers.gradleProperty("testQuarkusVersion")
    val quarkusVersion = consumerVersion.orElse(providers.gradleProperty("quarkusVersion")).get()
    val transactionalRwVersion = providers.gradleProperty("transactionalRwVersion").get()
    if (consumerVersion.isPresent) {
        // Reproduce the versions supplied by a consumer such as Domos.
        implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:$quarkusVersion"))
    } else {
        implementation(platform("io.quarkus.platform:quarkus-bom:$quarkusVersion"))
        implementation("org.postgresql:postgresql:${providers.gradleProperty("postgresqlVersion").get()}")
        implementation("org.eclipse.microprofile.config:microprofile-config-api:${providers.gradleProperty("microprofileConfigVersion").get()}")
    }
    implementation(project(":"))
    implementation("ru.code4a:quarkus-transactional-rw-lib:$transactionalRwVersion")
    implementation("io.quarkus:quarkus-kotlin")
    implementation("io.quarkus:quarkus-hibernate-orm")
    implementation("io.quarkus:quarkus-jdbc-postgresql")
    testImplementation("io.quarkus:quarkus-junit")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    maxHeapSize = "1g"
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
