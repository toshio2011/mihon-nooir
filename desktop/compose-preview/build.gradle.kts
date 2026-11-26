plugins {
    kotlin("jvm") version "2.1.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
    id("org.jetbrains.compose") version "1.8.2"
}
repositories { google(); mavenCentral() }
kotlin {
    jvmToolchain(21)
    sourceSets.named("main") {
        kotlin.srcDir("../suwayomi")
        kotlin.include("Main.kt", "SuwayomiClient.kt", "MiniJson.kt")
    }
}
dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
}
compose.desktop {
    application { mainClass = "dev.nooir.desktop.compose.MainKt" }
}
