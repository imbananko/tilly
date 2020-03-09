import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootJar

group = "com.imbananko"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_1_8

repositories {
    mavenCentral()
    maven(url = "https://repo.spring.io/milestone")
    maven(url = "https://jcenter.bintray.com")
}
dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.50")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.3.50")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.3.50")
    implementation("org.telegram:telegrambots-spring-boot-starter:4.4.0.1")
    implementation("org.postgresql:postgresql:42.2.6")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc:2.2.0.RELEASE")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    implementation("com.github.kilianB:JImageHash:3.0.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.5.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
    testImplementation("org.springframework.boot:spring-boot-starter-test:2.2.0.RELEASE")
}

plugins {
    id("com.google.cloud.tools.jib") version "0.10.0"
    id("org.springframework.boot") version "2.2.0.RELEASE"
    id("io.spring.dependency-management") version "1.0.8.RELEASE"
    kotlin("jvm") version "1.3.50"
    kotlin("plugin.spring") version "1.3.50"
}

tasks.getByName<BootJar>("bootJar") {
    val archiveFileName = "${project.name}.jar"
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
