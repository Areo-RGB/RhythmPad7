package com.example.core.model

/**
 * Controller device status and identification details.
 */
data class ControllerInfo(
  val isConnected: Boolean = false,
  val deviceId: Int = -1,
  val name: String = "No Controller Detected",
  val vendorId: Int = 0,
  val productId: Int = 0,
  val descriptor: String = "",
  val isDanceMat: Boolean = false,
  val isMicrontekProfileActive: Boolean = false
) {
  val hexVendorId: String
    get() = "0x" + vendorId.toString(16).padStart(4, '0').uppercase()

  val hexProductId: String
    get() = "0x" + productId.toString(16).padStart(4, '0').uppercase()

  val isMicrontek: Boolean
    get() = (vendorId == DANCE_MAT_VENDOR_ID && productId == DANCE_MAT_PRODUCT_ID) ||
        name.contains("microntek", ignoreCase = true)

  val formattedDetails: String
    get() = if (isConnected) {
      "$name (VID: $hexVendorId, PID: $hexProductId)"
    } else {
      "No controller connected"
    }

  companion object {
    val DISCONNECTED = ControllerInfo()

    // Confirmed Microntek USB Joystick / Dance Mat identifiers
    const val DANCE_MAT_VENDOR_ID = 0x0079
    const val DANCE_MAT_PRODUCT_ID = 0x0006
    const val CONFIRMED_CONTROLLER_NAME = "Microntek USB Joystick"
  }
}

/**
 * Raw input telemetry event for real-time monitoring and mapping configuration.
 */
data class RawInputEvent(
  val type: String, // "KEY" or "AXIS"
  val name: String,
  val code: Int,
  val action: String,
  val value: Float = 0f,
  val timestamp: Long = System.currentTimeMillis()
)
