package io.github.mfren022.nearbyphones

import android.bluetooth.BluetoothClass
import android.bluetooth.le.ScanRecord
import android.os.ParcelUuid

/**
 * Guesses whether a Bluetooth device is a phone.
 *
 * Phones don't announce "I am a phone" over BLE, so this combines several signals:
 * - Other phones running this app advertise [APP_SERVICE_UUID] (certain).
 * - Classic Bluetooth discovery reports a device class, which includes "phone".
 * - Vendor-specific advertisements (Apple Continuity, Google Nearby, Samsung).
 * - The advertised device name.
 */
object PhoneClassifier {

    /** Advertised by this app so that two phones running it always find each other. */
    val APP_SERVICE_UUID: ParcelUuid = ParcelUuid.fromString("6f2c1a5e-3b7d-4c8e-9a1f-2d4b6e8c0a17")

    private const val COMPANY_APPLE = 0x004C
    private const val COMPANY_MICROSOFT = 0x0006
    private const val COMPANY_SAMSUNG = 0x0075

    // Apple Continuity message types.
    private const val APPLE_IBEACON = 0x02
    private const val APPLE_AIRPODS = 0x07
    private const val APPLE_HANDOFF = 0x0C
    private const val APPLE_NEARBY_ACTION = 0x0F
    private const val APPLE_NEARBY_INFO = 0x10
    private const val APPLE_FIND_MY = 0x12

    private val GOOGLE_NEARBY_SHARE = uuid16(0xFEF3)
    private val GOOGLE_NEARBY_PRESENCE = uuid16(0xFCF1)
    private val GOOGLE_FAST_PAIR = uuid16(0xFE2C)

    private val ACCESSORY_WORDS = listOf(
        "buds", "airpods", "beats", "headphone", "headset", "earbud", "watch", "mi band",
        "speaker", "soundbar", "jbl", "bose", "keyboard", "mouse", "airtag", "fitbit",
        "garmin", "printer",
    )

    private val PHONE_WORDS = listOf(
        "iphone", "phone", "galaxy", "pixel", "oneplus", "redmi", "xiaomi", "poco", "moto",
        "nokia", "huawei", "honor", "oppo", "vivo", "realme", "xperia", "nothing", "fairphone",
    )

    fun classifyBle(record: ScanRecord?, name: String?): Classification {
        if (record == null) return classifyName(name)

        if (record.serviceUuids?.contains(APP_SERVICE_UUID) == true) {
            return Classification(Confidence.CONFIRMED, "Phone running Nearby Phones")
        }

        var result = classifyName(name)

        record.getManufacturerSpecificData(COMPANY_APPLE)?.let {
            result = Classification.best(result, classifyApple(it))
        }

        val serviceData = record.serviceData.orEmpty().keys + record.serviceUuids.orEmpty()
        when {
            GOOGLE_NEARBY_SHARE in serviceData || GOOGLE_NEARBY_PRESENCE in serviceData ->
                result = Classification.best(result, Classification(Confidence.LIKELY, "Likely Android phone (Google Nearby)"))
            GOOGLE_FAST_PAIR in serviceData && result.confidence <= Confidence.UNKNOWN ->
                result = Classification(Confidence.NOT_PHONE, "Fast Pair accessory")
        }

        if (record.getManufacturerSpecificData(COMPANY_SAMSUNG) != null) {
            result = Classification.best(result, Classification(Confidence.POSSIBLE, "Possibly Samsung phone"))
        }
        if (record.getManufacturerSpecificData(COMPANY_MICROSOFT) != null && result.confidence <= Confidence.UNKNOWN) {
            result = Classification(Confidence.UNKNOWN, "Microsoft device (PC or phone)")
        }
        return result
    }

    fun classifyClassic(btClass: BluetoothClass?, name: String?): Classification {
        val byName = classifyName(name)
        val byClass = when (btClass?.majorDeviceClass) {
            BluetoothClass.Device.Major.PHONE -> Classification(Confidence.LIKELY, "Phone (Bluetooth class)")
            BluetoothClass.Device.Major.COMPUTER -> Classification(Confidence.NOT_PHONE, "Computer")
            BluetoothClass.Device.Major.AUDIO_VIDEO -> Classification(Confidence.NOT_PHONE, "Audio/video device")
            BluetoothClass.Device.Major.WEARABLE -> Classification(Confidence.NOT_PHONE, "Wearable")
            BluetoothClass.Device.Major.PERIPHERAL -> Classification(Confidence.NOT_PHONE, "Peripheral")
            BluetoothClass.Device.Major.IMAGING -> Classification(Confidence.NOT_PHONE, "Printer/camera")
            BluetoothClass.Device.Major.HEALTH -> Classification(Confidence.NOT_PHONE, "Health device")
            BluetoothClass.Device.Major.TOY -> Classification(Confidence.NOT_PHONE, "Toy")
            else -> Classification.UNKNOWN
        }
        return Classification.best(byClass, byName)
    }

    private fun classifyName(name: String?): Classification {
        val lower = name?.lowercase() ?: return Classification.UNKNOWN
        return when {
            ACCESSORY_WORDS.any { lower.contains(it) } -> Classification(Confidence.NOT_PHONE, "Accessory (by name)")
            PHONE_WORDS.any { lower.contains(it) } -> Classification(Confidence.LIKELY, "Likely phone (by name)")
            else -> Classification.UNKNOWN
        }
    }

    /** Apple packs one or more type-length-value messages into its manufacturer data. */
    private fun classifyApple(data: ByteArray): Classification {
        var best = Classification(Confidence.UNKNOWN, "Apple device")
        var i = 0
        while (i + 1 < data.size) {
            val type = data[i].toInt() and 0xFF
            val length = data[i + 1].toInt() and 0xFF
            val c = when (type) {
                APPLE_NEARBY_INFO -> Classification(Confidence.LIKELY, "Likely iPhone (Apple Nearby)")
                APPLE_HANDOFF, APPLE_NEARBY_ACTION -> Classification(Confidence.POSSIBLE, "Possibly iPhone (Apple Continuity)")
                APPLE_AIRPODS -> Classification(Confidence.NOT_PHONE, "AirPods/Beats")
                APPLE_IBEACON -> Classification(Confidence.NOT_PHONE, "iBeacon")
                APPLE_FIND_MY -> Classification(Confidence.UNKNOWN, "Apple Find My device")
                else -> null
            }
            if (c != null) {
                // An accessory message is decisive only if nothing phone-like was seen.
                best = if (c.confidence == Confidence.NOT_PHONE && best.confidence <= Confidence.UNKNOWN) c
                else Classification.best(best, c)
            }
            i += 2 + length
        }
        return best
    }

    private fun uuid16(short: Int): ParcelUuid =
        ParcelUuid.fromString(String.format("0000%04x-0000-1000-8000-00805f9b34fb", short))
}
