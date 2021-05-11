import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

group = "com.chsdngm"
version = "1.0"
java.sourceCompatibility = JavaVersion.VERSION_1_8

val kotlinVersion = "1.4.0"
val springVersion = "2.3.0.RELEASE"
val jupiterVersion = "5.5.2"

repositories {
  mavenCentral()
  maven(url = "https://repo.spring.io/milestone")
  maven(url = "https://repo.spring.io/release")
  maven(url = "https://jcenter.bintray.com")
}

plugins {
  id("com.google.cloud.tools.jib") version "0.10.0"
  id("org.springframework.boot") version "2.2.0.RELEASE"
  id("io.spring.dependency-management") version "1.0.8.RELEASE"
  id("org.springframework.experimental.aot").version("0.9.2")
  id("net.idlestate.gradle-duplicate-classes-check") version "1.0.2"
  kotlin("jvm") version "1.4.0"
  kotlin("plugin.spring") version "1.4.0"
  kotlin("plugin.jpa") version "1.4.0"
}

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
  implementation("org.jetbrains.kotlin:kotlin-script-runtime:$kotlinVersion")
  implementation("org.jetbrains.kotlin:kotlin-main-kts:$kotlinVersion")
  implementation("org.telegram:telegrambots-spring-boot-starter:4.8.1")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa:2.4.5")
  implementation("org.postgresql:postgresql:42.2.6")
  implementation("com.github.kilianB:JImageHash:3.0.0")
  implementation("org.jetbrains.exposed:exposed:0.17.7")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinVersion")
  implementation("org.springframework.cloud:spring-cloud-gcp-starters:1.2.8.RELEASE")
  implementation("org.springframework.cloud:spring-cloud-gcp-starter-vision:1.2.8.RELEASE")
  implementation("com.vladmihalcea:hibernate-types-52:2.10.0")
  implementation("org.springframework.experimental:spring-graal-native:0.6.0.RELEASE")

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
    jvmTarget = "11"
    freeCompilerArgs = listOf("-Xallow-result-return-type", "-Xlint:deprecation")
  }
}

tasks.getByName<BootBuildImage>("bootBuildImage") {
  builder = "paketobuildpacks/builder:tiny"
  environment = mapOf(
    "BP_NATIVE_IMAGE" to "true",
    "BP_JVM_VERSION" to "11",
    "BP_NATIVE_IMAGE_BUILD_ARGUMENTS" to "--no-server --no-fallback --allow-incomplete-classpath --report-unsupported-elements-at-runtime -H:+ReportExceptionStackTraces -Dspring.native.verify=true -Dspring.graal.mode=initialization-only -Dspring.graal.dump-config=/tmp/computed-reflect-config.json -Dspring.graal.verbose=true"
  )
}
