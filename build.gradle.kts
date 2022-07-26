import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "com.chsdngm"
version = "1.0"
java.sourceCompatibility = JavaVersion.VERSION_1_8

val kotlinVersion = "1.6.0"
val springVersion = "2.3.0.RELEASE"
val jupiterVersion = "5.5.2"

repositories {
    mavenCentral()
    maven(url = "https://repo.spring.io/milestone")
    maven(url = "https://jcenter.bintray.com")
    maven { url = uri("https://google.oss.sonatype.org/content/repositories/snapshots") }
}

plugins {
    id("com.google.cloud.tools.jib") version "0.10.0"
    id("org.springframework.boot") version "2.2.0.RELEASE"
    id("io.spring.dependency-management") version "1.0.8.RELEASE"
    kotlin("jvm") version "1.6.0"
    kotlin("plugin.spring") version "1.6.0"
    kotlin("plugin.jpa") version "1.6.0"
}

configurations {
    implementation {
        exclude(group = "ch.qos.logback", module = "logback-classic")
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
    }
}

dependencyManagement {
    imports {
        mavenBom("com.google.cloud:spring-cloud-gcp-dependencies:3.4.0-SNAPSHOT")
    }
}

dependencies {
    implementation("com.google.cloud:spring-cloud-gcp-starter-vision")
    implementation("com.google.cloud:google-cloud-monitoring")

    // exposed
    implementation("org.jetbrains.exposed", "exposed-core", "0.38.1")
    implementation("org.jetbrains.exposed", "exposed-dao", "0.38.1")
    implementation("org.jetbrains.exposed", "exposed-jdbc", "0.38.1")
    implementation("org.jetbrains.exposed", "exposed-java-time", "0.38.1")

    // kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinVersion")
//    compileOnly("org.jetbrains.kotlin:kotlin-script-runtime:$kotlinVersion")
//    compileOnly("org.jetbrains.kotlin:kotlin-main-kts:$kotlinVersion")
//    compileOnly("org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable:$kotlinVersion")

    // spring
    implementation("org.telegram:telegrambots-spring-boot-starter:6.1.0")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    compileOnly("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.springframework.boot:spring-boot-starter-log4j2")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.data:spring-data-elasticsearch:3.2.13.RELEASE")

    implementation("org.postgresql:postgresql:42.2.6")
    implementation("com.github.kilianB:JImageHash:3.0.0")

//    compileOnly("org.ktorm:ktorm-core:3.3.0")
//    compileOnly("org.ktorm:ktorm-support-postgresql:3.3.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:$jupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:$jupiterVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-test:2.4.5")
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
        freeCompilerArgs = listOf("-Xallow-result-return-type")
    }
}
