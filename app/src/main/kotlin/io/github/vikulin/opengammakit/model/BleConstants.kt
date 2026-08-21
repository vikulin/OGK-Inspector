package io.github.vikulin.opengammakit.model

import java.util.UUID

/**
 * GATT UUIDs for the Wurth Proteus-III "UART Peripheral" service used by the
 * device's Bluetooth module (Core/Src/bluetooth_module.c talks to this chip
 * internally over SPI; over the air it exposes a Nordic-UART-Service-compatible
 * GATT profile). Centralized here as the single place to correct them if the
 * firmware's actual BLE service configuration differs.
 */
object BleConstants {
    val UART_SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

    // Write, phone -> device
    val UART_RX_CHARACTERISTIC_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")

    // Notify, device -> phone
    val UART_TX_CHARACTERISTIC_UUID: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

    val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
