package com.Fibwatch

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.amap
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jsoup.nodes.Element

// ───────────────────────────── Plugin ─────────────────────────────

@CloudstreamPlugin
class FibwatchPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(Fibwatch())
        // Optional: keep separate providers if you still want them
        // registerMainAPI(Fibtoon())
        // registerMainAPI(Fibwatchdrama())
    }

    companion object {
        private const val DOMAINS_URL =
            "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/domains.json"

        @Volatile
        private var cachedDomains: Domains? = null

        suspend fun getDomains(forceRefresh: Boolean = false): Domains {
            if (cachedDomains != null && !forceRefresh) {
                return cachedDomains!!
            }

            return try {
                val domains = app.get(DOMAINS_URL, timeout = 12_000).parsedSafe<Domains>()
                if (domains != null) {
                    cachedDomains = domains
                    domains
                } else {
                    fallbackDomains()
                }
            } catch (e: Exception) {
                Log.e("Fibwatch", "Failed to fetch domains", e)
                fallbackDomains()
            }
        }

        private fun fallbackDomains() = Domains(
            fibwatch = "https://fibwatch.art",
            fibtoon = "https://fibtoon.top",
            fibdrama = "https://fibdrama.top"
        )

        data class Domains(
            @JsonProperty("fibwatch") val fibwatch: String,
            @JsonProperty("fibtoon") val fibtoon: String,
            @JsonProperty("fibdrama") val fibdrama: String,
        )
    }
}

// ───────────────────────────── Main Provider ─────────────────────────────

open class Fibwatch : MainAPI() {

    override var mainUrl = "https://fibwatch.art"
    override var name = "FibWatch"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.AsianDrama
    )

    // All three domains for multi-search
    private suspend fun allDomains(): List<String> {
        val d = FibwatchPlugin.getDomains()
        return listOf(d.fibwatch, d.fibtoon, d.fibdrama).distinct()
    }

    override val mainPage = mainPageOf(
        "videos/trending" to "Trending Videos",
        "videos/top" to "Top Videos",
        "videos/latest" to "Latest Videos",
        "videos/category/1" to "Bangla–Kolkata Movies",
        "videos/category/852" to "Bangla Dubbed",
        "videos/category/3" to "Web Series",
        "videos/category/4" to "Hindi Movies",
        "videos/category/5" to "Hindi Dubbed Movies",
        "videos/category/9" to "Horror Movies",
        "videos/category/6" to "Tamil & Telugu Movies",
        "videos/category/11" to "Kannada Movies",
        "videos/category/10" to "Malayalam Movies",
        "videos/category/8" to "English Movies",
        "videos/category/12" to "Korean Movies",
        "videos/category/13" to "Marathi Movies",
        "videos/category/7" to "Cartoon Movies",
        "videos/category/853" to "Mixed Content",
        "videos/category/854" to "TV Shows",
        "videos/category/855" to "Natok",
        "videos/category/other" to "Other"
    )

    // ───────────── Main Page ─────────────
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        return try {
            val document = app.get("\( mainUrl/ \){request.data}?page_id=$page", timeout = 15_000).document
            val home = document.select("div.video-thumb").mapNotNull { it.toSearchResult() }

            newHomePageResponse(
                list = HomePageList(
                    name = request.name,
                    list = home,
                    isHorizontalImages = true
                ),
                hasNext = true
            )
        } catch (e: Exception) {
            Log.e(name, "getMainPage failed", e)
            newHomePageResponse(emptyList(), hasNext = false)
        }
    }

    // ───────────── Search (ALL 3 domains) ─────────────
    override suspend fun search(query: String, page: Int): SearchResponseList {
        val domains = allDomains()
        val results = mutableListOf<SearchResponse>()

        domains.amap { domain ->
            try {
                val document = app.get(
                    "\( domain/search?keyword= \){query.encodeURLParameter()}&page_id=$page",
                    timeout = 15_000
                ).document

                val items = document.select("div.video-thumb").mapNotNull { el ->
                    el.toSearchResult(domain)
                }
                synchronized(results) {
                    results.addAll(items)
                }
            } catch (e: Exception) {
                Log.w(name, "Search failed on $domain → ${e.message}")
            }
        }

        return results.distinctBy { it.url }.toNewSearchResponseList()
    }

    // ───────────── Load ─────────────
    override suspend fun load(url: String): LoadResponse = withContext(Dispatchers.IO) {
        val document = app.get(url, timeout = 20_000).document

        val rawTitle = document.selectFirst("meta[property=og:title]")?.attr("content") ?: "Unknown"
        val title = rawTitle.substringBefore("S0").trim()
        val poster = document.selectFirst("""meta[property="og:image"]""")?.attr("content")
        val description = document.selectFirst("meta[property=og:description]")?.attr("content") ?: ""
        val rawTitleLower = rawTitle.lowercase()

        val tvType = when {
            Regex("""s\d{1,2}e\d{1,3}""").containsMatchIn(rawTitleLower) -> TvType.TvSeries
            Regex("""\bs\d{1,2}\b""").containsMatchIn(rawTitleLower) -> TvType.TvSeries
            Regex("""\be\d{1,3}\b""").containsMatchIn(rawTitleLower) -> TvType.TvSeries
            else -> TvType.Movie
        }

        val videoId = document.selectFirst("input#video-id")?.attr("value")?.takeIf { it.isNotBlank() }

        val toLoadItem: (String?, String?, Boolean) -> LoadItem = { r, u, s ->
            LoadItem(quality = r?.trim().orEmpty(), url = u?.trim().orEmpty(), selected = s)
        }

        val dedupeByUrl: (List<LoadItem>) -> List<LoadItem> = { list ->
            val seen = LinkedHashSet<String>()
            list.filter { seen.add(it.url) }
        }

        val links: Links? = runCatching {
            if (videoId != null)
                app.get("$mainUrl/ajax/resolution_switcher.php?video_id=$videoId").parsedSafe()
            else null
        }.getOrNull()

        val currentRaw = links?.current
            ?.mapNotNull { c ->
                c.url?.trim().takeIf { !it.isNullOrEmpty() }
                    ?.let { toLoadItem(c.res, it, c.selected) }
            } ?: emptyList()

        val popupRaw = links?.popup
            ?.mapNotNull { p ->
                p.url?.trim().takeIf { !it.isNullOrEmpty() }
                    ?.let { toLoadItem(p.res, it, p.selected) }
            } ?: emptyList()

        val currentList = dedupeByUrl(currentRaw)
        val popupList = dedupeByUrl(popupRaw.filter { item -> currentList.none { it.url == item.url } })

        var out = LoadlinksOut(status = links?.status ?: "error", current = currentList, popup = popupList)

        if (out.current.isEmpty() && out.popup.isEmpty()) {
            try {
                val downloadEl = document.selectFirst("a.hidden-button.buttonDownloadnew")
                val dlUrl = downloadEl?.attr("href")?.substringAfter("url=")
                if (!dlUrl.isNullOrBlank()) {
                    val dlItem = toLoadItem(null, dlUrl.trim(), false)
                    out = LoadlinksOut(status = out.status, current = listOf(dlItem), popup = emptyList())
                }
            } catch (_: Throwable) {}
        }

        val recommendations = document
            .select("div.col-md-4.no-padding-left.mobile div.videos-list.pt_mn_wtch_rlts_prnt .video-wrapper")
            .mapNotNull { it.toSearchResult() }

        if (tvType == TvType.TvSeries) {
            val data: EpisodesResponse? = runCatching {
                if (videoId != null)
                    app.get("$mainUrl/ajax/episodes.php?video_id=$videoId").parsedSafe()
                else null
            }.getOrNull()

            val episodesList = data?.episodes.orEmpty()
            if (episodesList.isEmpty()) {
                return@withContext newTvSeriesLoadResponse(title, url, TvType.TvSeries, emptyList()) {
                    this.posterUrl = poster
                    this.plot = description
                    this.tags = document.select("div.tags-list a[rel='tag']").map { it.text() }
                    this.recommendations = recommendations
                }
            }

            val semaphore = Semaphore(6)
            val episodes = coroutineScope {
                episodesList.map { ep ->
                    async {
                        semaphore.withPermit {
                            try {
                                val epUrl = ep.url?.trim().orEmpty()
                                if (epUrl.isEmpty()) return@withPermit null

                                val epTitle = ep.title?.trim().orEmpty()
                                val lower = epTitle.lowercase()

                                var season: Int? = null
                                var episodeNum: Int? = null

                                Regex("""s(\d{1,2})e(\d{1,3})(?:-(\d{1,3}))?""").find(lower)?.let { m ->
                                    season = m.groupValues[1].toIntOrNull()
                                    episodeNum = m.groupValues[2].toIntOrNull()
                                } ?: run {
                                    Regex("""\bs(\d{1,2})\b""").find(lower)?.let { m ->
                                        season = m.groupValues[1].toIntOrNull()
                                    }
                                    Regex("""\be(\d{1,3})\b""").find(lower)?.let { m ->
                                        episodeNum = m.groupValues[1].toIntOrNull()
                                    }
                                }

                                val allqualities = runCatching {
                                    app.get(fixUrl(epUrl)).document
                                }.getOrNull() ?: return@withPermit null

                                val shouldRequestResSwitcher =
                                    allqualities.select("div.available-res:contains(Available in Other Parts:)").isNotEmpty()
                                val innerVideoId = allqualities.selectFirst("input#video-id")
                                    ?.attr("value")?.takeIf { it.isNotBlank() }

                                val epLinks: Links? = runCatching {
                                    if (shouldRequestResSwitcher && innerVideoId != null) {
                                        app.get("$mainUrl/ajax/resolution_switcher.php?video_id=$innerVideoId")
                                            .parsedSafe()
                                    } else null
                                }.getOrNull()

                                val epCurrentRaw = epLinks?.current
                                    ?.mapNotNull { c ->
                                        c.url?.trim().takeIf { !it.isNullOrEmpty() }
                                            ?.let { toLoadItem(c.res, it, c.selected) }
                                    } ?: emptyList()

                                val epPopupRaw = epLinks?.popup
                                    ?.mapNotNull { p ->
                                        p.url?.trim().takeIf { !it.isNullOrEmpty() }
                                            ?.let { toLoadItem(p.res, it, p.selected) }
                                    } ?: emptyList()

                                val epCurrentList = dedupeByUrl(epCurrentRaw)
                                val epPopupList = dedupeByUrl(
                                    epPopupRaw.filter { item -> epCurrentList.none { it.url == item.url } }
                                )

                                var epOut = LoadlinksOut(
                                    status = epLinks?.status ?: "error",
                                    current = epCurrentList,
                                    popup = epPopupList
                                )

                                if (epOut.current.isEmpty() && epOut.popup.isEmpty()) {
                                    try {
                                        val downloadEl = allqualities.selectFirst("a.hidden-button.buttonDownloadnew")
                                        val dlUrl = downloadEl?.attr("href")?.substringAfter("url=")
                                        if (!dlUrl.isNullOrBlank()) {
                                            val dlItem = toLoadItem(null, dlUrl.trim(), false)
                                            epOut = LoadlinksOut(
                                                status = "success",
                                                current = listOf(dlItem),
                                                popup = emptyList()
                                            )
                                        }
                                    } catch (_: Throwable) {}
                                }

                                newEpisode(epOut.toJson()) {
                                    this.name = epTitle
                                    season?.let { this.season = it }
                                    episodeNum?.let { this.episode = it }
                                    this.posterUrl = poster
                                }
                            } catch (_: Throwable) {
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            return@withContext newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.posterHeaders = mapOf("Referer" to mainUrl)
                this.plot = description
                this.tags = document.select("div.tags-list a[rel='tag']").map { it.text() }
                this.recommendations = recommendations
            }
        } else {
            return@withContext newMovieLoadResponse(title, url, TvType.Movie, out.toJson()) {
                this.posterUrl = poster
                this.posterHeaders = mapOf("Referer" to mainUrl)
                this.plot = description
                this.tags = document.select("div.tags-list a[rel='tag']").map { it.text() }
                this.recommendations = recommendations
            }
        }
    }

    // ───────────── Load Links ─────────────
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = tryParseJson<LoadlinksOut>(data) ?: return true

        val currentUrls = loadData.current.map { it.url.trim() }.toSet()
        val combined = ArrayList<LoadItem>(loadData.current.size + loadData.popup.size)
        combined += loadData.current
        combined += loadData.popup.filter { it.url.trim() !in currentUrls }

        combined.amap { item ->
            val url = item.url.trim()
            if (url.isEmpty()) return@amap

            val isDirectMedia = url.contains(".mkv", true) ||
                    url.contains(".mp4", true) ||
                    url.contains(".m3u8", true)

            val finalUrl = if (isDirectMedia) {
                url
            } else {
                runCatching {
                    val doc = app.get(fixUrl(url)).document
                    val onclick = doc.selectFirst("a.hidden-button.buttonDownloadnew")?.attr("href")
                        ?: return@runCatching null
                    onclick.substringAfter("url=").substringBefore("',").trim().takeIf { it.isNotEmpty() }
                }.getOrNull()
            }

            if (finalUrl.isNullOrEmpty()) {
                Log.w(name, "No download url for $url")
                return@amap
            }

            callback.invoke(
                newExtractorLink(mainUrl, name, finalUrl) {
                    this.quality = getQualityFromName(item.quality)
                    this.headers = mapOf("Referer" to mainUrl)
                }
            )
        }
        return true
    }

    // ───────────── Helpers ─────────────
    private fun Element.toSearchResult(domain: String = mainUrl): SearchResponse? {
        val rawTitle = this.selectFirst("p.hptag")?.text()
            ?: this.selectFirst("img")?.attr("alt")
            ?: return null

        val title = cleanTitle(rawTitle)
        val href = fixUrl(this.selectFirst("a")?.attr("href") ?: return null)
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.posterHeaders = mapOf("Referer" to domain)
        }
    }
}

// ───────────────────────────── Helpers & Models ─────────────────────────────

fun cleanTitle(raw: String?): String {
    if (raw.isNullOrBlank()) return "Unknown"
    val regex = Regex("""S(\d+)[Ee](\d+)(?:-(\d+))?""")
    val match = regex.find(raw) ?: return raw.trim()

    val season = match.groupValues[1].toInt()
    val epStart = match.groupValues[2].toInt()
    val epEnd = match.groupValues.getOrNull(3)?.takeIf { it.isNotEmpty() }?.toInt()

    val showName = raw.substringBefore(match.value).trim()
    val episodes = if (epEnd != null) "Episodes $epStart–$epEnd" else "Episode $epStart"

    return "$showName Season $season | $episodes"
}

data class Links(
    val status: String?,
    val current: List<Current> = emptyList(),
    val popup: List<Popup> = emptyList(),
)

data class Current(
    val res: String?,
    val url: String?,
    val selected: Boolean = false,
)

data class Popup(
    val res: String?,
    val url: String?,
    val selected: Boolean = false,
)

data class LoadItem(
    val quality: String,
    val url: String,
    val selected: Boolean = false
)

data class LoadlinksOut(
    val status: String,
    val current: List<LoadItem>,
    val popup: List<LoadItem>
)

data class EpisodesResponse(
    val status: String?,
    val episodes: List<EpisodeItem>?
)

data class EpisodeItem(
    val ep_key: String?,
    val display: String?,
    val title: String?,
    val url: String?,
    val is_current: Boolean?
)