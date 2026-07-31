package com.cncverse

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class LiveProvider : MainAPI() {
    override var mainUrl = "https://owrcovcrpy.gpcdn.net"
    override var name = "My Live TV"
    override var lang = "en"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Live)

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        // Directly add the channel to the main page
        val channel = newLiveSearchResponse(
            name = "My Channel",
            url = "https://owrcovcrpy.gpcdn.net/bpk-tv/1706/output/1706.m3u8",
            tvType = TvType.Live
        )

        return newHomePageResponse(
            listOf(
                HomePageList(
                    "Channels",
                    listOf(channel),
                    true
                )
            )
        )
    }

    override suspend fun load(url: String): LoadResponse? {
        return newLiveStreamLoadResponse(
            name = "Live Channel",
            dataUrl = url,
            loadLinks = { _, _, _, callback ->
                callback(
                    ExtractorLink(
                        source = name,
                        name = "Source",
                        url = url,
                        type = ExtractorLinkType.M3U8,
                        quality = "Auto"
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