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
  val isDanceMat: Boolean = false
) {
  val hexVendorId: String
    get() = "0x" + vendorId.toString(16).padStart(4, '0').uppercase()

  val hexProductId: String
    get() = "0x" + productId.toString(16).padStart(4, '0').uppercase()

  val formattedDetails: String
    get() = if (isConnected) {
      "$name (VID: $hexVendorId, PID: $hexProductId)"
    } else {
      "No controller connected"
    }

  companion object {
    val DISCONNECTED = ControllerInfo()

    // Generic USB Dance Mat identifiers (e.g. Mayflash, DragonRise, DDR Mat)
    const val DANCE_MAT_VENDOR_ID = 0x0079
    const val DANCE_MAT_PRODUCT_ID = 0x0006
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
