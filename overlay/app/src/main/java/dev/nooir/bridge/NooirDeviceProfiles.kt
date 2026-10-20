package dev.nooir.bridge

/**
 * E-reader export presets. IDs "X3" and "X4" remain unchanged so existing
 * background jobs and saved settings continue working.
 *
 * Formats describe export compatibility, not network-upload support.
 * In particular, a Kindle receives EPUB using Send to Kindle, NOT the
 * XTEink-local file transfer protocol.
 *
 * Dimensions and format guidance adapted from MangaPoint's public device
 * profiles (MIT); firmware support can change and needs testing on hardware.
 */
data class NooirDeviceProfile(
    val id: String,
    val name: String,
    val width: Int,
    val height: Int,
    val autoFormat: ExportFormat,
    val formats: Set<ExportFormat>,
    val xteinkWifi: Boolean,
    val note: String = "",
) {
    fun accepts(format: ExportFormat): Boolean =
        format == ExportFormat.AUTO || format in formats
}

object NooirDeviceProfiles {
    private val xteink = setOf(ExportFormat.XTC, ExportFormat.XTCH, ExportFormat.EPUB, ExportFormat.CBZ)
    private val archive = setOf(ExportFormat.EPUB, ExportFormat.CBZ)
    private val kindle = setOf(ExportFormat.EPUB)

    // Preserve the old X3/X4 resolution and XTCH Auto behaviour.
    val builtins: List<NooirDeviceProfile> = listOf(
        NooirDeviceProfile("X4", "XTEink X4", 480, 800, ExportFormat.XTCH, xteink, true),
        NooirDeviceProfile("X3", "XTEink X3", 528, 792, ExportFormat.XTCH, xteink, true),
        NooirDeviceProfile("X4_PRO", "XTEink X4 Pro", 480, 800, ExportFormat.XTCH, xteink, true,
            "Local Wi-Fi transfer requires compatible custom firmware; stock support varies."),
        NooirDeviceProfile("KINDLE_PW12", "Kindle Paperwhite (12th gen)", 1264, 1680, ExportFormat.EPUB, kindle, false,
            "Send EPUB using Send to Kindle; not by copying EPUB over USB."),
        NooirDeviceProfile("KINDLE_PW11", "Kindle Paperwhite (11th gen)", 1236, 1648, ExportFormat.EPUB, kindle, false,
            "Send EPUB using Send to Kindle; not by copying EPUB over USB."),
        NooirDeviceProfile("KINDLE_11", "Kindle (11th gen)", 1072, 1448, ExportFormat.EPUB, kindle, false,
            "Send EPUB using Send to Kindle; not by copying EPUB over USB."),
        NooirDeviceProfile("KOBO_CLARA_BW", "Kobo Clara BW", 1072, 1448, ExportFormat.CBZ, archive, false),
        NooirDeviceProfile("KOBO_LIBRA_2", "Kobo Libra 2", 1264, 1680, ExportFormat.CBZ, archive, false),
        NooirDeviceProfile("KOBO_LIBRA_COLOUR", "Kobo Libra Colour", 1264, 1680, ExportFormat.CBZ, archive, false),
        NooirDeviceProfile("BOOX_PALMA", "Boox Palma", 824, 1648, ExportFormat.CBZ, archive, false),
        NooirDeviceProfile("BOOX_PAGE", "Boox Page", 1264, 1680, ExportFormat.CBZ, archive, false),
        NooirDeviceProfile("POCKETBOOK_VERSE_PRO", "PocketBook Verse Pro", 1072, 1448, ExportFormat.CBZ, archive, false),
    )

    private const val CUSTOM_PREFIX = "CUSTOM:"
    private val customPattern = Regex("^CUSTOM:([0-9]{2,4})x([0-9]{2,4})$")

    /** Keep large custom pages bounded to prevent runaway Android bitmap use. */
    fun custom(width: Int, height: Int): String {
        require(width in 64..4096 && height in 64..4096 &&
            width.toLong() * height <= 3_000_000L) {
            "Custom size must have 64–4096 px edges and no more than 3 million pixels"
        }
        return "$CUSTOM_PREFIX${width}x${height}"
    }

    fun isCustom(id: String) = id.startsWith(CUSTOM_PREFIX)

    fun forId(id: String): NooirDeviceProfile {
        builtins.firstOrNull { it.id == id }?.let { return it }
        val match = customPattern.matchEntire(id)
            ?: throw IllegalArgumentException("Unknown reader preset: $id")
        val width = match.groupValues[1].toInt()
        val height = match.groupValues[2].toInt()
        custom(width, height) // Validate untrusted settings / WorkManager data.
        return NooirDeviceProfile(
            id, "Custom (${width} × ${height})", width, height,
            ExportFormat.EPUB, xteink, false,
            "Format compatibility is unverified. Save to your phone and transfer using a compatible method.",
        )
    }
}
