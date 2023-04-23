import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "com.chsdngm"
version = "1.1"

repositories {
    mavenCentral()
    maven(url = "https://repo.spring.io/milestone")
    maven(url = "https://jcenter.bintray.com")
    maven { url = uri("https://google.oss.sonatype.org/content/repositories/snapshots") }
}

plugins {
    id("org.springframework.boot") version "2.7.4"
    // https://docs.spring.io/spring-boot/docs/current/gradle-plugin/reference/htmlsingle/#reacting-to-other-plugins.dependency-management
    id("io.spring.dependency-management") version "1.0.14.RELEASE"
    id("org.jetbrains.kotlin.jvm") version "1.8.10"
    // makes all classes open https://kotlinlang.org/docs/all-open-plugin.html#spring-support
    id("org.jetbrains.kotlin.plugin.spring") version "1.8.10"
    id("jacoco")
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
    // https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-bom/1.7.10/kotlin-bom-1.7.10.pom
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("co.elastic.clients:elasticsearch-java:8.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.4")

    // rxjava
    implementation("io.reactivex.rxjava3:rxjava:3.1.5")

    // spring
    compileOnly("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.telegram:telegrambots-spring-boot-starter:6.1.0")
    implementation("org.springframework.boot:spring-boot-starter-log4j2")
    implementation("org.springframework.boot:spring-boot-starter-web")

    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("org.postgresql:postgresql:42.5.0")
    implementation("com.github.kilianB:JImageHash:3.0.0")
    implementation("jakarta.json:jakarta.json-api:2.1.1")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.1.0")
}

tasks.bootJar {
    "${project.name}.jar"
}

tasks.withType<Test> {
    useJUnitPlatform()
}

//remove when K2 will be okay with spring gradle plugin
allOpen {
    annotation("org.springframework.context.annotation.Configuration")
    annotation("org.springframework.boot.autoconfigure.SpringBootApplication")
    annotation("org.springframework.stereotype.Repository")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xuse-k2")
    }
}

tasks.withType<JacocoReport> {
    afterEvaluate {
        classDirectories.setFrom(files(classDirectories.files.map {
            fileTree(it).apply {
                exclude("**/ExtendedCopyOnWriteArrayList**")
            }
        }))
    }
}

tasks.jacocoTestReport {
    reports {
        csv.required.set(true)
    }
}

kotlin {
    jvmToolchain(17)
}