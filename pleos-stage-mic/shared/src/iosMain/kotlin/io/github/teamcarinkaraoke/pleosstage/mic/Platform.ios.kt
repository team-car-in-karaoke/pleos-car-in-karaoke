package io.github.teamcarinkaraoke.pleosstage.mic

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.UIKit.UIDevice

class IOSPlatform : Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
    override val isKoreanLocale: Boolean
        get() = NSLocale.currentLocale.languageCode.startsWith("ko", ignoreCase = true)
}

actual fun getPlatform(): Platform = IOSPlatform()

@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
actual fun isDebugBuildAllowed(): Boolean = kotlin.native.Platform.isDebugBinary
