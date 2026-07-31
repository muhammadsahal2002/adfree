package com.phisher98.cloudplay

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class SimpleLiveProvider : MainAPI() {
    override var mainUrl = "https://raw.githubusercontent.com"
    override var name = "Simple Live TV"
    override var lang = "en"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Live)

    // Your M3U8 sources
    private val m3uSources = listOf(
        "https://raw.githubusercontent.com/drmlive/fancode-live-events/main/fancode.m3u"
        // Add more M3U URLs here later
    )

    private val headers = mapOf(
        "User-Agent" to "okhttp/4.12.0"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val allChannels = mutableListOf<SearchResponse>()

        for (m3uUrl in m3uSources) {
            try {
                val response = app.get(m3uUrl, headers = headers).text
                val channels = parseM3U(response)
                allChannels.addAll(channels)
            } catch (e: Exception) {
                // Skip failed sources
            }
        }

        return newHomePageResponse(
            listOf(
                HomePageList(
                    "Live Channels",
                    allChannels,
                    true
                )
            )
        )
    }

    private fun parseM3U(content: String): List<SearchResponse> {
        val channels = mutableListOf<SearchResponse>()
        val lines = content.split("\n")
        var currentName = ""
        var currentLogo = ""

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("#EXTINF:") -> {
                    // Extract name
                    currentName = trimmed.substringAfter(",").trim()
                    
                    // Extract logo if available
                    val logoMatch = Regex("tvg-logo=\"([^\"]+)\"").find(trimmed)
                    currentLogo = logoMatch?.groupValues?.get(1) ?: ""
                }
                trimmed.isNotBlank() && !trimmed.startsWith("#") -> {
                    // This is a URL
                    if (currentName.isNotBlank()) {
                        channels.add(
                            newLiveSearchResponse(
                                currentName,
                                trimmed,
                                TvType.Live,
                                posterUrl = currentLogo
                            )
                        )
                        currentName = ""
                        currentLogo = ""
                    }
                }
            }
        }

        return channels
    }

    override suspend fun load(
        url: String,
        completion: suspend (LoadResponse) -> Unit
    ): LoadResponse? {
        return newLiveStreamLoadResponse(
            title = "Live Channel",
            url = url,
            data = url,
            loadLinks = { data, isCasting, subtitleCallback, callback ->
                callback.invoke(
                    newExtractorLink(
                        name = "Source",
                        url = data,
                        type = ExtractorLinkType.M3U8,
                        headers = mapOf(
                            "User-Agent" to "okhttp/4.12.0"
                        )
                    )
                )
                true
            }
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // Simple search - just return empty for now
        return emptyList()
    }
}