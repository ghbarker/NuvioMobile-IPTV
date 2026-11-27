package com.nuvio.app.features.livetv.data

import com.nuvio.app.features.livetv.model.LiveChannel
import io.ktor.client.HttpClient

/** Which service turns a server's name (e.g. "line.provider.com") into its address. */
internal enum class DnsChoice { SYSTEM, CLOUDFLARE, GOOGLE }

/**
 * The Live TV DNS setting (Android only; [supported] is false where the system's lookup can't be
 * swapped). Android phones can share their selected resolver through Setup Sync.
 */
internal expect object LiveTvDns {
    val supported: Boolean
    fun current(): DnsChoice
    fun set(value: DnsChoice)
    /** The servers a playlist's channels play from, so playback gets the automatic retry for them. */
    fun noteChannels(channels: List<LiveChannel>)
}

/** The client for Xtream logins (player_api.php): follows redirects, uses the provider lookup. */
internal expect fun createXtreamHttpClient(): HttpClient
