import org.jetbrains.dokka.gradle.DokkaTask

group = "com.github.pemistahl"
version = "0.4.0-SNAPSHOT"
description = "A natural language detection library for Kotlin and Java, suitable for long and short text alike"

plugins {
    kotlin("jvm") version "1.3.20"
    id("com.adarshr.test-logger") version "1.6.0"
    id("org.jetbrains.dokka") version "0.9.17"
    jacoco
}

jacoco { toolVersion = "0.8.3" }

sourceSets {
    main {
        resources {
            exclude("training-data/**", "language-models/*/sixgrams.json")
        }
    }
}

tasks.test {
    useJUnitPlatform { failFast = true }
    filter { includeTestsMatching("*Test") }
}

tasks.jacocoTestReport {
    dependsOn("test")
    reports {
        xml.isEnabled = true
        csv.isEnabled = false
        html.isEnabled = true
    }
}

tasks.register<Test>("accuracyReports") {
    val allowedDetectors = listOf("Optimaize", "Tika", "Lingua")
    val detectors = if (project.hasProperty("detectors"))
        project.property("detectors").toString().split(Regex("\\s*,\\s*"))
    else allowedDetectors

    detectors.filterNot { it in allowedDetectors }.forEach {
        throw GradleException(
            """
            detector '$it' does not exist
            supported detectors: ${allowedDetectors.joinToString(", ")}
            """.trimIndent()
        )
    }

    val allowedLanguages = listOf(
        "Arabic", "Belarusian", "Bulgarian", "Croatian", "Czech", "Danish",
        "Dutch", "English", "Estonian", "Finnish", "French", "German", "Hungarian",
        "Italian", "Latin", "Latvian", "Lithuanian", "Persian", "Polish",
        "Portuguese", "Romanian", "Russian", "Spanish", "Swedish", "Turkish"
    )

    val languages = if (project.hasProperty("languages"))
        project.property("languages").toString().split(Regex("\\s*,\\s*"))
    else allowedLanguages

    languages.filterNot { it in allowedLanguages }.forEach {
        throw GradleException("language '$it' is not supported")
    }

    val accuracyReportPackage = "com.github.pemistahl.lingua.report"

    maxHeapSize = "2048m"
    useJUnitPlatform { failFast = true }
    testlogger {
        showPassed = false
        showSkipped = false
        showStandardStreams = true
    }
    filter {
        detectors.forEach { detector ->
            languages.forEach { language ->
                includeTestsMatching(
                    "$accuracyReportPackage.${detector.toLowerCase()}.${language}DetectionAccuracyReport"
                )
            }
        }
    }
}

tasks.dokka {
    jdkVersion = 6
    reportUndocumented = false
}

tasks.register<DokkaTask>("dokkaJavadoc") {
    jdkVersion = 6
    reportUndocumented = false
    outputFormat = "javadoc"
    outputDirectory = "$buildDir/dokkaJavadoc"
}

tasks.register<Jar>("dokkaJavadocJar") {
    dependsOn("dokkaJavadoc")
    classifier = "javadoc"
    from("$buildDir/dokkaJavadoc")
}

tasks.register<Jar>("sourcesJar") {
    classifier = "sources"
    from("src/main/kotlin")
}

tasks.register<Jar>("jarWithDependencies") {
    classifier = "with-dependencies"
    dependsOn(configurations.runtimeClasspath)
    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
    manifest { attributes("Main-Class" to "com.github.pemistahl.lingua.AppKt") }
    exclude("META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.SF")
}

tasks.register<JavaExec>("startRepl") {
    main = "com.github.pemistahl.lingua.AppKt"
    standardInput = System.`in`
    classpath = sourceSets["main"].runtimeClasspath
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    implementation("com.google.code.gson:gson:2.8.5")
    implementation("org.mapdb:mapdb:3.0.7") {
        exclude("org.jetbrains.kotlin:kotlin-stdlib")
    }

    val junitVersion = "5.3.2"

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    testImplementation("org.assertj:assertj-core:3.11.1")
    testImplementation("io.mockk:mockk:1.9")

    testImplementation("com.optimaize.languagedetector:language-detector:0.6")
    testImplementation("org.apache.tika:tika-langdetect:1.20")

    val slf4jVersion = "1.7.25"

    testImplementation("org.slf4j:slf4j-api:$slf4jVersion")
    testImplementation("org.slf4j:slf4j-log4j12:$slf4jVersion")
}

repositories {
    jcenter()
}
