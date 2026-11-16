import tachiyomi.data.release.NooirReleaseApkSelector

private const val base = "https://github.com/toshio2011/mihon-nooir/releases/download/v1.0.18/"

private fun asset(name: String) = name to base + name

private fun expectUrl(
    assets: List<Pair<String, String>>,
    foss: Boolean,
    abis: List<String>,
    expected: String?,
) {
    val actual = NooirReleaseApkSelector.select(assets, foss, abis)
    check(actual == expected?.let { base + it }) {
        "Expected $expected for foss=$foss, abis=$abis; got $actual"
    }
}

fun main() {
    val official = listOf(
        asset("app-arm64-v8a-foss.apk"),
        asset("app-armeabi-v7a-foss.apk"),
        asset("app-universal-foss.apk"),
        asset("app-x86-foss.apk"),
        asset("app-x86_64-foss.apk"),
        asset("SHA256SUMS.txt"),
    )
    // The live v1.0.17 ordering previously made the old parser choose x86_64.
    val variants = listOf(official, official.reversed(), official.sortedBy { it.first })
    for (assets in variants) {
        expectUrl(assets, true, listOf("arm64-v8a", "armeabi-v7a"), "app-arm64-v8a-foss.apk")
        expectUrl(assets, true, listOf("x86_64", "x86"), "app-x86_64-foss.apk")
        expectUrl(assets, true, listOf("armeabi-v7a"), "app-armeabi-v7a-foss.apk")
        expectUrl(assets, true, listOf("riscv64"), "app-universal-foss.apk")
        expectUrl(assets, true, emptyList(), "app-universal-foss.apk")
    }

    // No incompatible architecture is ever selected when universal is absent.
    expectUrl(official.filterNot { it.first == "app-universal-foss.apk" },
        true, listOf("riscv64"), null)
    expectUrl(official.filterNot { it.first == "app-universal-foss.apk" },
        true, emptyList(), null)

    // Variant separation: regular APKs and FOSS APKs cannot be mixed up.
    val mixed = official + listOf(
        asset("app-arm64-v8a.apk"),
        asset("app-universal.apk"),
        asset("app-x86.apk"),
    )
    expectUrl(mixed, false, listOf("arm64-v8a"), "app-arm64-v8a.apk")
    expectUrl(mixed, false, listOf("x86_64"), "app-universal.apk")
    expectUrl(official, false, listOf("arm64-v8a"), null)

    // Ignore partial matches, text/checksums, insecure URLs, and fake files.
    val bad = listOf(
        asset("app-arm64-v8a-foss.apk.tmp"),
        asset("SHA256SUMS.txt"),
        "app-arm64-v8a-foss.apk" to "http://example.com/untrusted.apk",
        asset("other-app-arm64-v8a-foss.apk"),
        asset("app-x86_64-foss.apk"),
    )
    expectUrl(bad, true, listOf("arm64-v8a"), null)
    expectUrl(bad + asset("app-universal-foss.apk"), true,
        listOf("arm64-v8a"), "app-universal-foss.apk")

    println("Updater APK selection: all ARM, x86, FOSS, universal and order tests passed")
}
