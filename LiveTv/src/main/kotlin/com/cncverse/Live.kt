package com.cncverse

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class LiveProvider : MainAPI() {
    override var mainUrl = "https://owrcovcrpy.gpcdn.net"
    override var name = "Live TV"
    override var lang = "en"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Live)

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val channel = newLiveSearchResponse(
            name = "My Channel",
            url = "https://owrcovcrpy.gpcdn.net/bpk-tv/1706/output/1706.m3u8"
        )

        return newHomePageResponse(
            listOf(
                HomePageList(
                    "Live Channels",
                    listOf(channel),
                    true
                )
            )
        )
    }

    override suspend fun load(url: String): LoadResponse? {
        return newLiveStreamLoadResponse(
            name = "Live Channel",
            url = url,
            loadLinks = { data, isCasting, subtitleCallback, callback ->
                callback(
                    newExtractorLink(
                        source = name,
                        name = "Source",
                        url = data,
                        type = ExtractorLinkType.M3U8,
                        quality = QUALITY_AUTO,
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
        return emptyList()
    }
}