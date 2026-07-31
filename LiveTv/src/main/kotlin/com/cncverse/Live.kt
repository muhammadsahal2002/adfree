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
        return newHomePageResponse(
            listOf(
                HomePageList(
                    "Channels",
                    listOf(
                        newLiveSearchResponse(
                            "My Channel",
                            "https://owrcovcrpy.gpcdn.net/bpk-tv/1706/output/1706.m3u8"
                        )
                    )
                )
            )
        )
    }

    override suspend fun load(url: String): LoadResponse? {
        return newLiveStreamLoadResponse(
            name = "Live",
            url = url,
            loadLinks = { cb ->
                cb(newExtractorLink(name, "Source", url, ExtractorLinkType.M3U8, QUALITY_AUTO))
                true
            }
        )
    }

    override suspend fun search(query: String): List<SearchResponse> = emptyList()
}