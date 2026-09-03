// Wearsic Server — source-level rewrite of the previously jar-only backend.
// Builds against the dependency versions proven in the Termux deployment
// (Ktor 2.3.12, NewPipeExtractor v0.26.4 via JitPack, sqlite-jdbc).
//
// `./gradlew :wearsic-server:installDist` produces
// wearsic-server/build/install/wearsic-server/{bin,lib} — the same shape as
// the existing wearsic-server/{bin,lib} folders, so run-termux.sh keeps
// working unmodified.

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

version = "1.3.1"

val ktorVersion = "2.3.12"
val coroutinesVersion = "1.7.1"
val serializationVersion = "1.7.3"

dependencies {
    // --- Ktor server (CIO engine — matches original deployment) ---
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-host-common-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")

    // --- Ktor client (CIO engine — used for BOTH NewPipe extraction requests
    //     and the audio proxy, replacing the old raw HttpURLConnection path) ---
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")

    // --- Coroutines / serialization ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:$serializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")

    // --- Extraction ---
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.4")

    // --- Persistence ---
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")

    // --- Logging ---
    implementation("ch.qos.logback:logback-classic:1.5.15")

    // --- Tests ---
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")
}

application {
    // Matches the com.wearsic.server package used by the previous compiled
    // jar, so nothing about the deployment path changes.
    mainClass.set("com.wearsic.server.ApplicationKt")
}

kotlin {
    // Java 17 — matches the Termux deployment (openjdk-17).
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
