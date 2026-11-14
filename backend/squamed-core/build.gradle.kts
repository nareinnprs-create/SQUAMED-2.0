plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    kotlin("plugin.jpa") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
    id("jacoco")
}

group = "com.squamed"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// Infra-dependent tests (Testcontainers or the compose dev stack) live outside :test so the
// backend-gates job (ktlintCheck + test + coverage) never needs Docker. The integration-gates
// job starts the compose stack and runs the "integrationTest" task by exact name.
val integrationTestSourceSet = sourceSets.create("integrationTest")

configurations {
    named("integrationTestImplementation") { extendsFrom(configurations.testImplementation.get()) }
    named("integrationTestRuntimeOnly") { extendsFrom(configurations.testRuntimeOnly.get()) }
}

dependencies {
    "integrationTestImplementation"(sourceSets.main.get().output)
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("io.micrometer:micrometer-registry-prometheus")

    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    implementation("org.postgresql:postgresql")
    implementation("org.springframework.kafka:spring-kafka")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.0")
    implementation("com.networknt:json-schema-validator:1.5.8")

    implementation("org.opensearch.client:opensearch-java:2.11.1")
    implementation("org.apache.httpcomponents.client5:httpclient5")
    // 00.07: patient photo object storage — S3 API (MinIO pgsty/silo in dev, S3 in prod)
    implementation(platform("software.amazon.awssdk:bom:2.25.70"))
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:apache-client")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // vetted TOTP (RFC 6238) — replaces the hand-rolled HMAC implementation (00.03 hardening)
    implementation("dev.samstevens.totp:totp:1.7.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.mockk:mockk:1.13.13")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
        allWarningsAsErrors.set(true)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
        showStandardStreams = false
    }
    maxHeapSize = "1g"
}

jacoco {
    toolVersion = "0.8.12"
}

val jacocoExcludes = listOf(
    "com/squamed/core/SquamedApplication*",
    "**/common/api/*",
    "**/config/**",
    "**/domain/**",
    "**/*Dto*",
    "**/*View*",
    "**/*Request*",
    "**/*Response*",
    "**/*Result*",
    "**/*Payload*",
    "**/api/*Entities*",
    "**.Inlined*",
)

fun jacocoClassDirectories(): FileTree = sourceSets.main.get().output.asFileTree.matching {
    exclude(jacocoExcludes)
}

// Explicit exec-data source: a fileTree over build/jacoco/*.exec so the report/verification tasks
// merge test.exec and integrationTest.exec when both exist, WITHOUT depending on the integrationTest
// task (the backend-gates job runs only `test`; withType(Test)-based defaults would force it to run).
val jacocoExecFiles: FileTree = fileTree("${layout.buildDirectory.get()}/jacoco") {
    include("*.exec")
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    executionData.setFrom(jacocoExecFiles)
    // SC-10: in CI (integration-gates) the report is requested together with
    // integrationTest; order after it so the merged exec fileTree already holds
    // PIT data (no forced run for standalone invocations).
    mustRunAfter(tasks.named("integrationTest"))
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
    classDirectories.setFrom(jacocoClassDirectories())
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    executionData.setFrom(jacocoExecFiles)
    // SC-10: same ordering contract — verified against unit+PIT exec merged.
    mustRunAfter(tasks.named("integrationTest"))
    classDirectories.setFrom(jacocoClassDirectories())
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestReport, tasks.jacocoTestCoverageVerification)
}

val ktlintCli by configurations.creating

dependencies {
    ktlintCli("com.pinterest.ktlint:ktlint-cli:1.8.0")
}

// Task name "integrationTest" is load-bearing: ci.yml integration-gates runs `./gradlew integrationTest`.
tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Infra-dependent integration tests; defaults to the compose dev stack (override with SQUAMED_IT_EXTERNAL=false for Testcontainers)"
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    shouldRunAfter(tasks.test)
    environment("SQUAMED_IT_EXTERNAL", System.getenv("SQUAMED_IT_EXTERNAL") ?: "true")
}

val ktlintCheck = tasks.register<JavaExec>("ktlintCheck") {
    group = "verification"
    description = "Enforce ktlint formatting over Kotlin sources"
    classpath = ktlintCli
    mainClass.set("com.pinterest.ktlint.Main")
    args(
        "src/**/*.kt",
        "src/**/*.kts",
        "--reporter=plain",
        "--reporter=json,output=${layout.buildDirectory.get()}/reports/ktlint/ktlintCheck.json",
    )
}

val ktlintFormat = tasks.register<JavaExec>("ktlintFormat") {
    group = "formatting"
    description = "Auto-format Kotlin sources to satisfy ktlint"
    classpath = ktlintCli
    mainClass.set("com.pinterest.ktlint.Main")
    args("-F", "src/**/*.kt", "src/**/*.kts")
    outputs.upToDateWhen { false }
}

tasks.check {
    dependsOn(ktlintCheck)
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.Embeddable")
    annotation("jakarta.persistence.MappedSuperclass")
}

springBoot {
    buildInfo {
        properties {
            additional = mapOf("environment" to provider { System.getenv("SQUAMED_ENV") ?: "local" })
        }
    }
}