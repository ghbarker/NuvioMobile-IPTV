package com.nuvio.app.features.livetv.sports

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.precomposedStringWithCanonicalMapping

/** The Sports hub isn't built for iOS yet: no feeds are read. */
internal actual object SportsPlatform {
    actual suspend fun get(url: String, headers: Map<String, String>): String = error("Sports feeds aren't available on this platform yet")
    actual fun countryCode(): String = ""
    actual fun languageCode(): String = "en"
    actual fun pause(ms: Long) = Unit
    @OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
    actual fun composeUnicode(text: String): String = NSString.create(string = text).precomposedStringWithCanonicalMapping
    actual fun foldAccents(text: String): String = SportsAccents.fold(text)
}
