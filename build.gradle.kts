import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "com.chsdngm"
version = "1.1"

val jupiterVersion = "5.9.0"

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
    id("org.jetbrains.kotlin.jvm") version "1.7.10"
    // makes all classes open https://kotlinlang.org/docs/all-open-plugin.html#spring-support
    id("org.jetbrains.kotlin.plugin.spring") version "1.7.10"
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
//    compileOnly("org.jetbrains.kotlin:kotlin-script-runtime:$kotlinVersion")
//    compileOnly("org.jetbrains.kotlin:kotlin-main-kts:$kotlinVersion")
//    compileOnly("org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable:$kotlinVersion")

    // spring
    compileOnly("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.telegram:telegrambots-spring-boot-starter:6.1.0")
    implementation("org.springframework.boot:spring-boot-starter-log4j2")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.data:spring-data-elasticsearch")

    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("org.postgresql:postgresql:42.5.0")
    implementation("com.github.kilianB:JImageHash:3.0.0")

//    compileOnly("org.ktorm:ktorm-core:3.3.0")
//    compileOnly("org.ktorm:ktorm-support-postgresql:3.3.0")

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
        // K2 does not support plugins yet, so please remove -Xuse-k2 flag
        //freeCompilerArgs = listOf("-Xuse-k2")
    }
}
