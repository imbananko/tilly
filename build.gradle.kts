import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "com.chsdngm"
version = "1.0"
java.sourceCompatibility = JavaVersion.VERSION_1_8

val kotlinVersion = "1.4.0"
val springVersion = "2.3.0.RELEASE"
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
  kotlin("jvm") version "1.4.0"
  kotlin("plugin.spring") version "1.4.0"
  kotlin("plugin.jpa") version "1.4.0"
}

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
  implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
  implementation("org.jetbrains.kotlin:kotlin-script-runtime:$kotlinVersion")
  implementation("org.jetbrains.kotlin:kotlin-main-kts:$kotlinVersion")
  implementation("org.telegram:telegrambots-spring-boot-starter:4.8.1")
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.postgresql:postgresql:42.2.6")
  implementation("org.springframework.boot:spring-boot-configuration-processor")
  implementation("com.github.kilianB:JImageHash:3.0.0")
  implementation("org.jetbrains.exposed:exposed:0.17.7")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinVersion")
  implementation("org.springframework.cloud:spring-cloud-gcp-starters:1.2.6.RELEASE")
  implementation("org.springframework.cloud:spring-cloud-gcp-starter-vision:1.2.6.RELEASE")
  implementation("com.vladmihalcea:hibernate-types-52:2.10.0")
  implementation("com.github.haifengl:smile-core:2.4.0")
  implementation("com.github.haifengl:smile-nlp:2.4.0")
  implementation("org.apache.opennlp:opennlp-tools:1.9.3")
  implementation("org.ktorm:ktorm-core:3.3.0")
  implementation("org.ktorm:ktorm-support-postgresql:3.3.0")

  testImplementation("org.junit.jupiter:junit-jupiter-api:$jupiterVersion")
  testImplementation("org.junit.jupiter:junit-jupiter:$jupiterVersion")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.bootJar {
  "${project.name}.jar"
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
