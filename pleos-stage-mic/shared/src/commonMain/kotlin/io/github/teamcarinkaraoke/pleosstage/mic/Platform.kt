package io.github.teamcarinkaraoke.pleosstage.mic

interface Platform {
    val name: String
    val isKoreanLocale: Boolean
}

expect fun getPlatform(): Platform

expect fun isDebugBuildAllowed(): Boolean
