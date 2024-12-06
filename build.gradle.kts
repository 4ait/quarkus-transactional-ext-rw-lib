import org.jreleaser.model.Active

group = "ru.code4a"
version = file("version").readText().trim()

plugins {
  kotlin("jvm") version "2.0.0"

  id("org.kordamp.gradle.jandex") version "2.0.0"

  `java-library`
  `maven-publish`
  id("org.jreleaser") version "1.13.1"
}

java {
  withJavadocJar()
  withSourcesJar()
}

publishing {
  publications {
    create<MavenPublication>("mavenJava") {
      artifactId = "quarkus-transactional-ext-rw-lib"

      from(components["java"])

      pom {
        name = "Quarkus Transactional RW Extensions"
        description =
          "This library provides additional extensions for the Quarkus Transactional Read-Write Library, " +
            "offering enhanced transaction management capabilities for Hibernate and " +
            "serializable transactions in Quarkus applications."
        url = "https://github.com/4ait/quarkus-transactional-ext-rw-lib"
        inceptionYear = "2024"
        licenses {
          license {
            name = "The Apache License, Version 2.0"
            url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
          }
        }
        developers {
          developer {
            id = "tikara"
            name = "Evgeniy Simonenko"
            email = "tiikara93@gmail.com"
            organization.set("4A LLC")
            roles.set(
              listOf(
                "Software Developer",
                "Head of Development"
              )
            )
          }
        }
        organization {
          name = "4A LLC"
          url = "https://4ait.ru"
        }
        scm {
          connection = "scm:git:git://github.com:4ait/quarkus-transactional-ext-rw-lib.git"
          developerConnection = "scm:git:ssh://github.com:4ait/quarkus-transactional-ext-rw-lib.git"
          url = "https://github.com/4ait/quarkus-transactional-ext-rw-lib"
        }
      }
    }
  }
  repositories {
    maven {
      url =
        layout.buildDirectory
          .dir("staging-deploy")
          .get()
          .asFile
          .toURI()
    }
  }
}

repositories {
  mavenCentral()
}

tasks.withType<Test> {
  useJUnitPlatform()
  dependsOn(tasks["jandex"])
}

dependencies {
  implementation("io.quarkus:quarkus-arc:3.17.3")
  implementation("io.quarkus:quarkus-narayana-jta:3.17.3")

  compileOnly("ru.code4a:quarkus-transactional-rw-lib:0.4.1")
  compileOnly("io.quarkus:quarkus-hibernate-orm:3.12.3")
  compileOnly("org.eclipse.microprofile.config:microprofile-config-api:3.1")
  compileOnly("org.postgresql:postgresql:42.7.2")
}

tasks.named("compileTestKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask::class.java) {
  compilerOptions {
    freeCompilerArgs.add("-Xdebug")
  }
}

jreleaser {
  project {
    copyright.set("4A LLC")
  }
  gitRootSearch.set(true)
  signing {
    active.set(Active.ALWAYS)
    armored.set(true)
  }
  release {
    github {
      overwrite.set(true)
      branch.set("master")
    }
  }
  deploy {
    maven {
      mavenCentral {
        create("maven-central") {
          active.set(Active.ALWAYS)
          url.set("https://central.sonatype.com/api/v1/publisher")
          stagingRepositories.add("build/staging-deploy")
          retryDelay.set(30)
        }
      }
    }
  }
}
