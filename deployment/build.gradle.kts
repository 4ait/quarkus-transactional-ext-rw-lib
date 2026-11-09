plugins {
  kotlin("jvm")

  `java-library`
  `maven-publish`
}

kotlin {
  jvmToolchain(25)
}

java {
  withJavadocJar()
  withSourcesJar()
}

repositories {
  mavenCentral()
}

dependencies {
  val quarkusVersion = providers.gradleProperty("quarkusVersion").get()

  implementation(platform("io.quarkus.platform:quarkus-bom:$quarkusVersion"))
  implementation(project(":"))
  implementation("io.quarkus:quarkus-core-deployment")
  implementation("io.quarkus:quarkus-arc-deployment")
  implementation("io.quarkus.gizmo:gizmo")

  // The application supplies Hibernate ORM; its version decides read-only sessions at build time.
  compileOnly("io.quarkus:quarkus-hibernate-orm")
}

publishing {
  publications {
    create<MavenPublication>("mavenJava") {
      artifactId = "quarkus-transactional-ext-rw-lib-deployment"

      from(components["java"])

      pom {
        name = "Quarkus Transactional RW Extensions Deployment"
        description = "Build-time part of the Quarkus Transactional RW Extensions."
      }
    }
  }
  repositories {
    // jreleaser releases both artifacts from the root staging directory.
    maven {
      url =
        rootProject.layout.buildDirectory
          .dir("staging-deploy")
          .get()
          .asFile
          .toURI()
    }
  }
}
