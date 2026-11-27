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
        kotlin.srcDir("../cli")
        kotlin.srcDir("../storage")
        kotlin.srcDir("../../overlay/app/src/main/java/dev/nooir/bridge")
        kotlin.srcDir("../../overlay/app/src/main/java/dev/nooir/mangabridge")
        kotlin.include("Main.kt", "ComposeExport.kt", "ComposeBackup.kt", "ComposeExtensions.kt", "ComposeBackend.kt", "DesktopDataHome.kt", "OfficialSuwayomiCache.kt", "SuwayomiProcessManager.kt", "DesktopBackupInspector.kt", "DesktopBackupTransport.kt", "DesktopBackupExtensions.kt", "DesktopLegacyRepoIndex.kt", "SuwayomiClient.kt", "MiniJson.kt", "SuwayomiToNooir.kt",
            "NooirDesktopCli.kt", "XtcCodec.kt", "XtcBookPreview.kt", "ExportFormats.kt", "NooirDeviceProfiles.kt",
            "BookArchiveWriter.kt", "Epub2Writer.kt", "GrayscaleConverter.kt", "GrayscalePng.kt", "NaturalPageOrder.kt",
            "PageSlicePlanner.kt", "PanelContextPeek.kt", "PanelDetectionCore.kt", "PanelViewportPlanner.kt",
            "PanelAutoFit.kt", "PanelWhitespaceCrop.kt", "PanelSafeSlicePlanner.kt", "PanelReadableSliceRefiner.kt",
            "XteinkTransferPolicy.kt", "ReflowEpubWriter.kt", "NooirEpubCssRules.kt", "MangaConversionSettingsCore.kt")
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

tasks.register<JavaExec>("qaExport") {
    dependsOn("testClasses")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.nooir.desktop.compose.ComposeExportSmokeKt")
    args(providers.gradleProperty("qaOutput").orElse("").get())
    systemProperty("java.awt.headless", "true")
}

tasks.register<JavaExec>("qaBackup") {
    dependsOn("testClasses")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.nooir.desktop.compose.ComposeBackupSmokeKt")
    systemProperty("java.awt.headless", "true")
}

tasks.register<JavaExec>("qaCatalogInstall") {
    dependsOn("testClasses")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.nooir.desktop.compose.ComposeCatalogInstallSmokeKt")
    systemProperty("java.awt.headless", "true")
}
