package io.github.mfren022.nearbyphones

import kotlin.math.pow

/** How the device was found. */
enum class Source(val label: String) {
    BLE("BLE"),
    CLASSIC("Classic BT"),
}

/** How sure we are that a device is a phone. Ordered from least to most certain. */
enum class Confidence {
    NOT_PHONE,
    UNKNOWN,
    POSSIBLE,
    LIKELY,
    CONFIRMED;

    val isPhone: Boolean get() = this >= POSSIBLE
}

data class Classification(val confidence: Confidence, val label: String) {
    companion object {
        val UNKNOWN = Classification(Confidence.UNKNOWN, "Unknown device")

        /** Keeps whichever classification is more confident. */
        fun best(a: Classification, b: Classification): Classification =
            if (b.confidence > a.confidence) b else a
    }
}

data class NearbyDevice(
    val address: String,
    val name: String?,
    /** Smoothed signal strength in dBm. */
    val rssi: Int,
    /** Expected RSSI at 1 m, in dBm. */
    val measuredPowerAt1m: Int,
    val sources: Set<Source>,
    val classification: Classification,
    val lastSeenMillis: Long,
) {
    /**
     * Rough distance from the log-distance path loss model. Bluetooth RSSI is noisy, so
     * treat this as "near vs. far", not as an exact measurement.
     */
    val estimatedDistanceMeters: Double
        get() = 10.0.pow((measuredPowerAt1m - rssi) / (10.0 * PATH_LOSS_EXPONENT))

    companion object {
        /** ~2 in open space, higher indoors where walls and bodies absorb the signal. */
        const val PATH_LOSS_EXPONENT = 2.5

        /** Typical RSSI of a phone measured 1 m away when the advertiser doesn't say. */
        const val DEFAULT_MEASURED_POWER = -59

        /**
         * The BLE "TX power level" field is the power at the antenna (0 m). Free-space loss
         * over the first metre at 2.4 GHz is about 41 dB.
         */
        fun measuredPowerFromTxPower(txPower: Int?): Int =
            txPower?.let { it - 41 } ?: DEFAULT_MEASURED_POWER
    }
}
