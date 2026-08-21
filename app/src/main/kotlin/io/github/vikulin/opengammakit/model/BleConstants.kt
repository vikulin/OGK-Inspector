package io.github.vikulin.opengammakit.model

import java.util.UUID

/**
 * GATT UUIDs for the Wurth Proteus-III's "WESPP" serial-port-profile-like service
 * used by the device's Bluetooth module (Core/Src/bluetooth_module.c talks to this
 * chip internally over SPI; over the air, in BLE peripheral mode, it exposes this
 * GATT profile to the phone). Confirmed against Wurth's own official companion app
 * source (github.com/WurthElektronik/Proteus-Connect, BLEProfiles/WESPPProfile.ts
 * and BLEModules/ModuleProfile.ts, which maps BLEModuleType.ProteusIII -> WESPPProfile
 * specifically - Proteus-IV and later modules use a different "WESPP2" profile with
 * different UUIDs). Not Nordic UART Service compatible, despite sharing the same
 * 6e400001/2/3 prefix - the base UUID suffix differs.
 */
object BleConstants {
    val UART_SERVICE_UUID: UUID = UUID.fromString("6e400001-c352-11e5-953d-0002a5d5c51b")

    // Write (without response), phone -> device
    val UART_RX_CHARACTERISTIC_UUID: UUID = UUID.fromString("6e400002-c352-11e5-953d-0002a5d5c51b")

    // Notify, device -> phone
    val UART_TX_CHARACTERISTIC_UUID: UUID = UUID.fromString("6e400003-c352-11e5-953d-0002a5d5c51b")

    val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /**
     * RF header byte the WESPP profile expects prefixed on every RX-characteristic
     * write, tagging it as plain data (as opposed to 0x02 CMD / 0x04 FRAGDATA - see
     * Wurth's Proteus-Connect BLEModules/Proteus/ProteusHeader.ts +
     * Proteus.ts#formatdatatx()). Outgoing only: this is what the app sends, not
     * something the device firmware needs to add or strip on its own side.
     */
    const val RF_HEADER_TYPE_DATA: Byte = 0x01

    /**
     * Prefix of the Wurth Proteus-III's factory-default BLE advertised (local) name -
     * the firmware doesn't currently set a custom one. Used to filter scan/bonded
     * results down to just the target device instead of every nearby BLE peripheral.
     * Single place to correct if the real advertised name differs.
     */
    const val DEVICE_NAME_PREFIX = "Prot3"
}
