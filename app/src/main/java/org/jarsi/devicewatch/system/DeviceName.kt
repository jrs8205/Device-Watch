package org.jarsi.devicewatch.system

/**
 * The phone's name for the screensaver's line above the clock, or null when there is no
 * name worth showing. [deviceName] is Android's own device name (Settings.Global.DEVICE_NAME:
 * "Pixel 8a", "Galaxy Z Flip4", or whatever the owner renamed it to); [model] is the
 * fallback, except a bare model code such as Samsung's "SM-F721B", which means nothing to
 * a person and is left out.
 */
internal fun screensaverDeviceName(deviceName: String?, model: String?, manufacturer: String?): String? {
    val modelName = model.tidy()
    // Plain Android names the device after the model when the maker sets no name.
    val named = deviceName.tidy()?.takeUnless { it == modelName && isModelCode(it) }
    val name = named ?: modelName?.takeUnless(::isModelCode) ?: return null
    return name.withoutMaker(manufacturer).ellipsized()
}

private fun String?.tidy(): String? =
    this?.trim()?.replace(WHITESPACE, " ")?.takeIf { it.isNotEmpty() }

private fun isModelCode(model: String): Boolean = MODEL_CODE.matches(model) && model.any(Char::isDigit)

/**
 * "Google Pixel 8a" reads as "Pixel 8a". The maker stays when it is the product line itself:
 * "OnePlus 12" and "Xiaomi 14 Ultra" go on with a number, "Nothing Phone (2)" with a word that
 * names nothing on its own.
 */
private fun String.withoutMaker(manufacturer: String?): String {
    val maker = manufacturer?.trim()?.takeIf { it.isNotEmpty() } ?: return this
    if (!startsWith("$maker ", ignoreCase = true)) return this
    val rest = substring(maker.length + 1)
    val firstWord = rest.substringBefore(' ')
    if (!firstWord.first().isLetter() || firstWord.equals("Phone", ignoreCase = true)) return this
    return rest
}

private fun String.ellipsized(): String {
    if (length <= MAX_DEVICE_NAME_LENGTH) return this
    var cut = MAX_DEVICE_NAME_LENGTH - 1
    // Never between the two halves of an emoji.
    if (Character.isHighSurrogate(this[cut - 1])) cut--
    return take(cut).trimEnd() + "…"
}

private val WHITESPACE = Regex("\\s+")
private val MODEL_CODE = Regex("[A-Z0-9-]{5,}")
private const val MAX_DEVICE_NAME_LENGTH = 24
