import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "com.chsdngm"
version = "1.0"
java.sourceCompatibility = JavaVersion.VERSION_1_8

val kotlinVersion = "1.3.70"
val springVersion = "2.2.0.RELEASE"
val jupiterVersion = "5.5.2"

repositories {
  mavenCentral()
  maven(url = "https://repo.spring.io/milestone")
  maven(url = "https://jcenter.bintray.com")
}

plugins {
  id("com.google.cloud.tools.jib") version "0.10.0"
  id("org.springframework.boot") version "2.2.0.RELEASE"
  id("io.spring.dependency-management") version "1.0.8.RELEASE"
  kotlin("jvm") version "1.3.70"
  kotlin("plugin.spring") version "1.3.70"
}

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
  implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
  implementation("org.telegram:telegrambots-spring-boot-starter:4.7")
  implementation("org.postgresql:postgresql:42.2.6")
  implementation("org.springframework.boot:spring-boot-starter-data-jdbc:$springVersion")
  implementation("org.springframework.boot:spring-boot-configuration-processor")
  implementation("com.github.kilianB:JImageHash:3.0.0")
  implementation("org.jsoup:jsoup:1.13.1")

  testImplementation("org.junit.jupiter:junit-jupiter-api:$jupiterVersion")
  testImplementation("org.junit.jupiter:junit-jupiter:$jupiterVersion")
  testImplementation("org.springframework.boot:spring-boot-starter-test:$springVersion")
}

tasks.bootJar {
  "${project.name}.jar"
}

jib {
  from {
    image = "openjdk:11-jre-slim"
  }
  to {
    auth {
      username =
          if (project.hasProperty("dockerUser"))
            project.property("dockerUser") as String
          else "dumb"
      password =
          if (project.hasProperty("dockerPassword"))
            project.property("dockerPassword") as String
          else "dumb"
    }
    val imageVersion =
        if (project.hasProperty("tag")) project.property("tag") as String
        else "latest"
    image = "registry.hub.docker.com/imbananko/${project.name}:$imageVersion"
  }
}

tasks.withType<Test> {
  useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
  kotlinOptions {
    jvmTarget = "1.8"
    freeCompilerArgs = listOf("-Xallow-result-return-type")
  }
}
