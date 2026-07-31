package com.cncverse

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class LiveProvider : MainAPI() {
    override var mainUrl = "https://owrcovcrpy.gpcdn.net"
    override var name = "Live TV"
    override var lang = "en"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Live)

    // All channels from your M3U
    private val channels = listOf(
        "Ananda TV" to "https://app.ncare.live/c3VydmVyX8RpbEU9Mi8xNy8yMDE0GIDU6RgzQ6NTAgdEoaeFzbF92YWxIZTO0U0ezN1IzMyfvcGVMZEJCTEFWeVN3PTOmdFsaWRtaW51aiPhnPTI2/anandatv.stream/live-orgin/anandatv.stream/playlist.m3u8",
        "ATN Bangla (720p)" to "https://tvsen5.aynaott.com/atnbangla/index.m3u8",
        "ATN News (1080p)" to "https://bozztv.com/rongo/rongo-ATNNews/index.m3u8",
        "ATN News (1080p) Alt" to "https://owrcovcrpy.gpcdn.net/bpk-tv/1706/output/1706.m3u8",
        "Bangla Vision (1080p)" to "https://owrcovcrpy.gpcdn.net/bpk-tv/1715/output/index.m3u8",
        "Bangla Vision (720p)" to "https://tvsen5.aynaott.com/banglavision/index.m3u8",
        "BTV Chattogram (1080p)" to "https://bozztv.com/rongo/rongo-BTVChattagram/index.m3u8",
        "BTV National (1080p)" to "https://owrcovcrpy.gpcdn.net/bpk-tv/1709/output/1709.m3u8",
        "Channel 9 (1080p)" to "https://owrcovcrpy.gpcdn.net/bpk-tv/1729/output/index.m3u8",
        "Channel 24 (1080p)" to "https://bozztv.com/rongo/rongo-Channel24HD/index.m3u8",
        "Channel 24 (1080p) Alt" to "https://owrcovcrpy.gpcdn.net/bpk-tv/1703/output/index.m3u8",
        "Channel I (1080p)" to "https://owrcovcrpy.gpcdn.net/bpk-tv/1723/output/index.m3u8",
        "Channel S" to "https://app.ncare.live/c3VydmVyX8RpbEU9Mi8xNy8yMDE0GIDU6RgzQ6NTAgdEoaeFzbF92YWxIZTO0U0ezN1IzMyfvcGVMZEJCTEFWeVN3PTOmdFsaWRtaW51aiPhnPTI2/channels.stream/live-orgin/channels.stream/playlist.m3u8",
        "DBC News (1080p)" to "https://owrcovcrpy.gpcdn.net/bpk-tv/1728/output/index.m3u8",
        "Deepto TV (1080p)" to "https://owrcovcrpy.gpcdn.net/bpk-tv/1711/output/index.m3u8",
        "Desh TV (1080p)" to "https://bozztv.com/rongo/rongo-DeshTV/index.m3u8",
        "Deshi TV (720p)" to "https://deshitv.deshitv24.net/live/myStream/playlist.m3u8",
        "Ekattor TV (1080p)" to "https://owrcovcrpy.gpcdn.net/bpk-tv/1705/output/1705.m3u8",
        "Ekushey TV (480p)" to "https://ekusheyserver.com/etvlivesn.m3u8",
        "Gazi TV" to "https://app.ncare.live/c3VydmVyX8RpbEU9Mi8xNy8yMDE0GIDU6RgzQ6NTAgdEoaeFzbF92YWxIZTO0U0ezN1IzMyfvcGVMZEJCTEFWeVN3PTOmdFsaWRtaW51aiPhnPTI2/gazibdz.stream/live-orgin/gazibdz.stream/playlist.m3u8",
        "Green TV (1080p)" to "https://app.ncare.live/c3VydmVyX8RpbEU9Mi8xNy8yMDE0GIDU6RgzQ6NTAgdEoaeFzbF92YWxIZTO0U0ezN1IzMyfvcGVMZEJCTEFWeVN3PTOmdFsaWRtaW51aiPhnPTI2/greentv.stream/live-orgin/greentv.stream/playlist.m3u8",
        "Independent TV (1080p)" to "https://bozztv.com/rongo/rongo-IndependentTV/index.m3u8",
        "Independent TV (1080p) Alt" to "https://owrcovcrpy.gpcdn.net/bpk-tv/1704/output/1704.m3u8",
        "Jamuna TV (1080p)" to "https://bozztv.com/rongo/rongo-JamunaTelevision/index.m3u8",
        "Maasranga TV (1080p)" to "https://owrcovcrpy.gpcdn.net/bpk-tv/1722/output/index.m3u8",
        "Maasranga TV (720p)" to "https://tvsen5.aynaott.com/maasrangatv/index.m3u8",
        "Mohona TV (1080p)" to "https://bozztv.com/rongo/rongo-MohonaTV/index.m3u8",
        "Movie Bangla" to "http://alvetv.com/moviebanglatv/8080/index.m3u8",
        "My TV" to "https://app.ncare.live/c3VydmVyX8RpbEU9Mi8xNy8yMDE0GIDU6RgzQ6NTAgdEoaeFzbF92YWxIZTO0U0ezN1IzMyfvcGVMZEJCTEFWeVN3PTOmdFsaWRtaW51aiPhnPTI2/mytv-up-off.stream/live-orgin/mytv-up-off.stream/playlist.m3u8",
        "News 21 Bangla TV" to "http://103.190.133.68:1935/news21live/live/playlist.m3u8",
        "News 24 (1080p)" to "https://owrcovcrpy.gpcdn.net/bpk-tv/1708/output/1708.m3u8",
        "NTV (1080p)" to "https://owrcovcrpy.gpcdn.net/bpk-tv/1716/output/index.m3u8",
        "NTV (720p)" to "https://tvsen5.aynaott.com/xV4jEKf3D9zc/index.m3u8",
        "RTV (1080p)" to "https://bozztv.com/rongo/rongo-RTV/index.m3u8",
        "RTV (720p)" to "https://tvsen5.aynaott.com/RtvHD/index.m3u8",
        "Rupashi Bangla TV (720p)" to "https://app24.jagobd.com.bd/c3VydmVyX8RpbEU9Mi8xNy8yMFDEEHGcfRgzQ6NTAgdEoaeFzbF92YWxIZTO0U0ezN1IzMyfvcEdsEfeDeKiNkVN3PTOmdFseWRtaW51aiPhnPTI2/ruposhibangla.stream/playlist.m3u8",
        "Somoy News TV (1080p)" to "https://bozztv.com/rongo/rongo-somoy/index.m3u8",
        "Somoy News TV (1080p) Alt" to "https://owrcovcrpy.gpcdn.net/bpk-tv/1702/output/index.m3u8",
        "Star News (1080p)" to "https://owrcovcrpy.gpcdn.net/bpk-tv/1710/output/index.m3u8",
        "RTV (720p) Alt" to "http://tvsen5.aynascope.net/RtvHD/index.m3u8",
        "ATN Bangla (720p) Alt" to "http://tvsen5.aynascope.net/atnbangla/index.m3u8",
        "Maasranga TV (720p) Alt" to "http://tvsen5.aynascope.net/maasrangatv/index.m3u8",
        "Rajdhani TV (1080p)" to "https://stream.shariarsuvo.com/hls5/rajdhanicable.m3u8"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val list = channels.map { (channelName, streamUrl) ->
            newLiveSearchResponse(
                name = channelName,
                url = streamUrl
            )
        }

        return newHomePageResponse(
            listOf(
                HomePageList(
                    "Bangladeshi Live Channels",
                    list,
                    true
                )
            )
        )
    }

    override suspend fun load(url: String): LoadResponse? {
        val channelName = channels.find { it.second == url }?.first ?: "Live Channel"

        return newLiveStreamLoadResponse(
            name = channelName,
            url = url,
            dataUrl = url
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback.invoke(
            newExtractorLink(
                source = name,
                name = "Live",
                url = data,
                type = ExtractorLinkType.M3U8
            ) {
                this.quality = Qualities.Unknown.value
                this.headers = mapOf(
                    "User-Agent" to "okhttp/4.12.0"
                )
            }
        )
        return true
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return channels
            .filter { it.first.contains(query, ignoreCase = true) }
            .map { (channelName, streamUrl) ->
                newLiveSearchResponse(
                    name = channelName,
                    url = streamUrl
                )
            }
    }
}