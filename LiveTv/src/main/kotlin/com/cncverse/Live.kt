package com.cncverse

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class LiveProvider : MainAPI() {
    override var mainUrl = "https://raw.githubusercontent.com"
    override var name = "Live TV"
    override var lang = "en"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Live)

    private val m3uSources = listOf(
        "https://raw.githubusercontent.com/drmlive/fancode-live-events/main/fancode.m3u"
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
                    currentName = trimmed.substringAfter(",").trim()
                    val logoMatch = Regex("tvg-logo=\"([^\"]+)\"").find(trimmed)
                    currentLogo = logoMatch?.groupValues?.get(1) ?: ""
                }
                trimmed.isNotBlank() && !trimmed.startsWith("#") -> {
                    if (currentName.isNotBlank()) {
                        channels.add(
                            newLiveSearchResponse(
                                name = currentName,
                                url = trimmed,
                                tvType = TvType.Live,
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

    override suspend fun load(url: String): LoadResponse? {
        return newLiveStreamLoadResponse(
            name = "Live Channel",
            dataUrl = url,
            loadLinks = { data, isCasting, subtitleCallback, callback ->
                loadStreamLinks(data, callback)
            }
        )
    }

    private suspend fun loadStreamLinks(
        url: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val link = ExtractorLink(
                source = name,
                name = "Source",
                url = url,
                type = ExtractorLinkType.M3U8,
                quality = "Auto",
                headers = mapOf(
                    "User-Agent" to "okhttp/4.12.0"
                )
            )
            callback(link)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return emptyList()
    }
}