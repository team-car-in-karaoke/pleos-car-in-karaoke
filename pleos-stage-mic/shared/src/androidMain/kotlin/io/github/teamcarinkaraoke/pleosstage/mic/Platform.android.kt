package io.github.teamcarinkaraoke.pleosstage.mic

import android.os.Build
import java.util.Locale

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
    override val isKoreanLocale: Boolean
        get() = Locale.getDefault().language.startsWith("ko", ignoreCase = true)
}

actual fun getPlatform(): Platform = AndroidPlatform()

actual fun isDebugBuildAllowed(): Boolean = AndroidPlatformContextHolder.isDebugBuild
