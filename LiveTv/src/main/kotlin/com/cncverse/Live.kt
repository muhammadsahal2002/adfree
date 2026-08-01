package com.cncverse

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson

class LiveProvider : MainAPI() {
    override var mainUrl = "https://raw.githubusercontent.com"
    override var name = "Live TV"
    override var lang = "en"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Live)

    // Your JSON link
    private val playlistUrl = "https://raw.githubusercontent.com/muhammadsahal2002/CS3/refs/heads/main/channels.json"

    data class Root(
        @JsonProperty("categories") val categories: List<Category> = emptyList()
    )

    data class Category(
        @JsonProperty("name") val name: String,
        @JsonProperty("channels") val channels: List<Channel> = emptyList()
    )

    data class Channel(
        @JsonProperty("name") val name: String,
        @JsonProperty("logo") val logo: String? = null,
        @JsonProperty("url") val url: String
    )

    private suspend fun loadData(): Root {
        return try {
            val json = app.get(playlistUrl).text
            parseJson<Root>(json) ?: Root()
        } catch (e: Exception) {
            Root()
        }
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val data = loadData()

        val homeLists = data.categories.map { category ->
            val list = category.channels.map { ch ->
                newLiveSearchResponse(
                    name = ch.name,
                    url = ch.url
                ) {
                    this.posterUrl = ch.logo
                }
            }
            HomePageList(category.name, list, isHorizontalImages = true)
        }

        return newHomePageResponse(homeLists)
    }

    override suspend fun load(url: String): LoadResponse? {
        val data = loadData()
        val channel = data.categories
            .flatMap { it.channels }
            .find { it.url == url }

        return newLiveStreamLoadResponse(
            name = channel?.name ?: "Live Channel",
            url = url,
            dataUrl = url
        ) {
            this.posterUrl = channel?.logo
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            "Accept" to "*/*",
            "Connection" to "keep-alive"
        )

        // Better live HLS handling
        try {
            M3u8Helper.generateM3u8(
                source = name,
                streamUrl = data,
                referer = "",
                quality = Qualities.Unknown.value,
                headers = headers
            ).forEach(callback)
        } catch (_: Exception) {
            // fallback if M3u8Helper fails
        }

        // Direct link as backup
        callback.invoke(
            newExtractorLink(
                source = name,
                name = "Live",
                url = data,
                type = ExtractorLinkType.M3U8
            ) {
                this.quality = Qualities.Unknown.value
                this.headers = headers
            }
        )

        return true
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val data = loadData()
        return data.categories
            .flatMap { it.channels }
            .filter { it.name.contains(query, ignoreCase = true) }
            .map { ch ->
                newLiveSearchResponse(
                    name = ch.name,
                    url = ch.url
                ) {
                    this.posterUrl = ch.logo
                }
            }
    }
}