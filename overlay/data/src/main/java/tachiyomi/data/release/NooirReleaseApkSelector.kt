package tachiyomi.data.release

/**
 * Selects an exact, architecture-compatible GitHub APK asset.
 *
 * GitHub does not guarantee asset ordering. Every MihonNooir FOSS asset ends
 * in "-foss.apk", so matching that substring alone overwrites the correct
 * architecture with the last asset (often x86_64 on ARM devices).
 *
 * Prefer the phone's ABIs in Android's declared order, then a universal APK.
 * Never silently fall back to a different architecture.
 */
internal object NooirReleaseApkSelector {
    fun select(
        assets: List<Pair<String, String>>,
        isFoss: Boolean,
        supportedAbis: List<String>,
    ): String? {
        val suffix = if (isFoss) "-foss.apk" else ".apk"
        val preferredNames =
            supportedAbis.map { "app-$it$suffix" } + "app-universal$suffix"

        for (filename in preferredNames.distinct()) {
            val asset = assets.firstOrNull { (name, url) ->
                name == filename && url.startsWith("https://")
            }
            if (asset != null) return asset.second
        }
        return null
    }
}
