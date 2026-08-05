package com.cncverse

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document

@CloudstreamPlugin
class ICCFTPServerPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(ICCFTPServerProvider())
    }
}

class ICCFTPServerProvider : MainAPI() {
    override var mainUrl = "http://10.16.100.244"
    override var name = "ICC FTP Server"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.Cartoon,
        TvType.Documentary
    )

    private var currentSession: String? = null
    private var currentToken: String? = null

    companion object {
        private val QUALITY_PATTERNS = listOf(
            "2160p" to Qualities.P2160.value,
            "4k" to Qualities.P2160.value,
            "1080p" to Qualities.P1080.value,
            "720p" to Qualities.P720.value,
            "480p" to Qualities.P480.value,
            "360p" to Qualities.P360.value
        )
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val session = getSession()
        val url = "$mainUrl/dashboard.php?session=$session&category=0"
        val document = app.get(url, headers = getHeaders()).document

        val home = ArrayList<HomePageList>()
        val homeList = mutableListOf<SearchResponse>()

        // Featured slider
        document.select(".slider.multipost .item").forEach { item ->
            val link = item.select("a").attr("href")
            val title = item.select(".title span").text()
            val image = item.select(".img").attr("style")
                .substringAfter("url('").substringBefore("')")

            if (link.isNotBlank() && title.isNotBlank()) {
                val id = link.substringAfter("play=").substringBefore("&")
                homeList.add(
                    newMovieSearchResponse(title, createLink(id)) {
                        this.posterUrl =
                            if (image.startsWith("http")) image else "$mainUrl/$image"
                    }
                )
            }
        }

        // Categories
        document.select(".navbar-nav > li.dropdown").forEach { category ->
            val categoryName = category.select("> a").text().replace(" ↓", "")
            if (categoryName.isNotBlank()) {
                val items = mutableListOf<SearchResponse>()

                category.select(".dropdown-menu li a").forEach { subItem ->
                    val fullText = subItem.text()
                    val subTitle = fullText.substringBefore(" <b")
                    val subLink = subItem.attr("href")

                    if (subLink.isNotBlank() && subTitle.isNotBlank()) {
                        val categoryId =
                            subLink.substringAfter("category=").substringBefore("&")
                        items.add(
                            newMovieSearchResponse(
                                subTitle,
                                "$mainUrl/dashboard.php?category=$categoryId"
                            ) {
                                this.posterUrl = ""
                            }
                        )
                    }
                }

                if (items.isNotEmpty()) {
                    home.add(HomePageList(categoryName, items))
                }
            }
        }

        // Latest
        val trendingItems = mutableListOf<SearchResponse>()
        document.select(".news-gallery .post").take(20).forEach { post ->
            val link = post.select("a.image").attr("href")
            val title = post.select(".title").text()
            val image = post.select(".image img").attr("src")

            if (link.isNotBlank() && title.isNotBlank()) {
                val id = link.substringAfter("play=").substringBefore("&")
                if (id.isNotBlank()) {
                    trendingItems.add(
                        newMovieSearchResponse(title, createLink(id)) {
                            this.posterUrl =
                                if (image.startsWith("http")) image else "$mainUrl/$image"
                        }
                    )
                }
            }
        }

        if (trendingItems.isNotEmpty()) {
            home.add(0, HomePageList("Latest Releases", trendingItems))
        }
        if (homeList.isNotEmpty()) {
            home.add(1, HomePageList("Featured", homeList))
        }

        return newHomePageResponse(home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val session = getSession()
        val token = getToken(session)

        val url = "$mainUrl/dashboard.php?session=$session&category=0"
        val response = app.post(
            url,
            data = mapOf(
                "token" to token,
                "psearch" to query
            ),
            headers = getHeaders() + mapOf(
                "Content-Type" to "application/x-www-form-urlencoded"
            )
        )

        val document = response.document
        val results = mutableListOf<SearchResponse>()

        document.select(".news-gallery .post").forEach { post ->
            val link = post.select("a.image").attr("href")
            val title = post.select(".title").text()
            val image = post.select(".image img").attr("src")

            if (link.isNotBlank() && title.isNotBlank()) {
                val id = link.substringAfter("play=").substringBefore("&")
                if (id.isNotBlank()) {
                    results.add(
                        newMovieSearchResponse(title, createLink(id)) {
                            this.posterUrl =
                                if (image.startsWith("http")) image else "$mainUrl/$image"
                        }
                    )
                }
            }
        }

        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val id = extractId(url)
        val session = getSession()

        // Track view
        try {
            app.post(
                "$mainUrl/command.php",
                data = mapOf(
                    "id" to id,
                    "type" to "visit"
                ),
                headers = getHeaders() + mapOf(
                    "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"
                )
            )
        } catch (_: Exception) {
        }

        val playerUrl = "$mainUrl/player.php?session=$session&play=$id"
        val document = app.get(playerUrl, headers = getHeaders()).document

        val modal = document.select(".modal-dialog")
        var title = ""
        var poster = ""
        var year: Int? = null
        var genre = ""
        var description = ""
        var category = ""
        val videoUrls = mutableListOf<String>()

        modal.select("table.ewTable tr").forEach { row ->
            val cells = row.select("td")
            if (cells.size >= 2) {
                val label = cells[0].text().trim().replace(":", "")
                val value = cells[1].text().trim()
                when (label) {
                    "Generic Name" -> genre = value
                    "Category" -> category = value
                    "Year" -> year = value.toIntOrNull()
                    "Discription" -> description = value
                }
            }
        }

        poster = modal.select("img").attr("src")?.let {
            if (it.startsWith("http")) it else "$mainUrl/$it"
        } ?: ""

        title = modal.select(".modal-title").text().takeIf { it.isNotBlank() }
            ?: document.title().replace("ICC FTP SERVER", "").trim()

        // Collect video URLs from modal
        modal.select("a[href]").forEach { link ->
            val href = link.attr("href")
            if (href.isNotBlank() && (href.contains(".mp4") || href.contains(".mkv"))) {
                try {
                    app.post(
                        "$mainUrl/command.php",
                        data = mapOf(
                            "id" to id,
                            "type" to "download"
                        ),
                        headers = getHeaders() + mapOf(
                            "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"
                        )
                    )
                } catch (_: Exception) {
                }
                videoUrls.add(href)
            }
        }

        // Fallback from <video>
        if (videoUrls.isEmpty()) {
            document.select("video source").forEach { source ->
                val src = source.attr("src")
                if (src.isNotBlank()) videoUrls.add(src)
            }
        }

        val isSeries = title.contains("Season", ignoreCase = true) ||
            title.contains("Episode", ignoreCase = true) ||
            category.contains("Serials", ignoreCase = true)

        val tags = genre.split(",").map { it.trim() }.filter { it.isNotBlank() }

        return if (isSeries) {
            val episodes = getEpisodes(document, id)
            newTvSeriesLoadResponse(
                name = title,
                url = url,
                type = TvType.TvSeries,
                episodes = episodes
            ) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
            }
        } else {
            // Pass first video URL (or joined) as data for loadLinks
            val dataUrl = videoUrls.firstOrNull() ?: url
            newMovieLoadResponse(
                name = title,
                url = url,
                type = TvType.Movie,
                dataUrl = dataUrl
            ) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // data is either a direct video URL, or a player page / id link
        val videoCandidates = mutableListOf<String>()

        if (data.contains(".mp4") || data.contains(".mkv")) {
            videoCandidates.add(data)
        } else {
            // Re-load player page if needed
            val id = extractId(data)
            if (id.isNotBlank()) {
                val session = getSession()
                val playerUrl = "$mainUrl/player.php?session=$session&play=$id"
                val document = app.get(playerUrl, headers = getHeaders()).document

                document.select(".modal-dialog a[href]").forEach { link ->
                    val href = link.attr("href")
                    if (href.contains(".mp4") || href.contains(".mkv")) {
                        videoCandidates.add(href)
                    }
                }
                document.select("video source").forEach { source ->
                    val src = source.attr("src")
                    if (src.isNotBlank()) videoCandidates.add(src)
                }
            } else {
                videoCandidates.add(data)
            }
        }

        var found = false
        for (videoUrl in videoCandidates.distinct()) {
            val quality = extractQuality(videoUrl) ?: Qualities.Unknown.value
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = "ICC FTP",
                    url = videoUrl,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.quality = quality
                    this.headers = mapOf(
                        "Referer" to mainUrl,
                        "User-Agent" to "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36",
                        "Range" to "bytes=0-"
                    )
                }
            )
            found = true
        }
        return found
    }

    private suspend fun getEpisodes(document: Document, currentId: String): List<Episode> {
        val episodes = mutableListOf<Episode>()
        val seenIds = mutableSetOf<String>()

        document.select(".post").forEach { post ->
            val link = post.select("a.image").attr("href")
            val title = post.select(".title").text()

            if (link.isNotBlank() && title.isNotBlank()) {
                val epId = link.substringAfter("play=").substringBefore("&")
                if (epId.isNotBlank() && epId != currentId && !seenIds.contains(epId)) {
                    seenIds.add(epId)

                    val seasonMatch =
                        Regex("Season\\s*(\\d+)", RegexOption.IGNORE_CASE).find(title)
                    val episodeMatch =
                        Regex("Episode\\s*(\\d+)", RegexOption.IGNORE_CASE).find(title)
                    val sxeMatch =
                        Regex("S(\\d+)E(\\d+)", RegexOption.IGNORE_CASE).find(title)
                    val epNumberMatch =
                        Regex("E(\\d+)", RegexOption.IGNORE_CASE).find(title)

                    val season = seasonMatch?.groupValues?.get(1)?.toIntOrNull()
                        ?: sxeMatch?.groupValues?.get(1)?.toIntOrNull()
                        ?: 1
                    val episode = episodeMatch?.groupValues?.get(1)?.toIntOrNull()
                        ?: sxeMatch?.groupValues?.get(2)?.toIntOrNull()
                        ?: epNumberMatch?.groupValues?.get(1)?.toIntOrNull()
                        ?: episodes.size + 1

                    episodes.add(
                        newEpisode(createLink(epId)) {
                            this.name = title
                            this.season = season
                            this.episode = episode
                        }
                    )
                }
            }
        }

        return episodes.sortedWith(compareBy({ it.season }, { it.episode }))
    }

    private suspend fun getSession(): String {
        currentSession?.let { return it }

        val response = app.get(mainUrl)
        currentSession = response.cookies["PHPSESSID"]

        if (currentSession == null) {
            val html = response.text
            val sessionMatch = Regex("session=([a-f0-9]+)").find(html)
            currentSession = sessionMatch?.groupValues?.get(1)
        }

        return currentSession ?: ""
    }

    private suspend fun getToken(session: String): String {
        currentToken?.let { return it }

        val url = if (session.isNotEmpty()) {
            "$mainUrl/dashboard.php?session=$session&category=0"
        } else {
            "$mainUrl/dashboard.php?category=0"
        }

        val html = app.get(url, headers = getHeaders()).text
        val tokenMatch = Regex("name=\"token\" value=\"([^\"]+)\"").find(html)
        currentToken = tokenMatch?.groupValues?.get(1)

        return currentToken ?: ""
    }

    private fun getHeaders(): Map<String, String> {
        val headers = mutableMapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 12; SM-M025F Build/SP1A.210812.016) AppleWebKit/537.36",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "en-GB,en-US;q=0.9,en;q=0.8",
            "X-Requested-With" to "com.mycompany.app.soulbrowser",
            "Accept-Encoding" to "gzip, deflate"
        )

        currentSession?.let {
            headers["Cookie"] = "PHPSESSID=$it"
        }

        return headers
    }

    private fun createLink(id: String): String {
        val session = currentSession ?: ""
        return if (session.isNotEmpty()) {
            "$mainUrl/player.php?session=$session&play=$id"
        } else {
            "$mainUrl/player.php?play=$id"
        }
    }

    private fun extractId(url: String): String {
        return url.substringAfter("play=").substringBefore("&")
    }

    private fun extractQuality(text: String?): Int? {
        if (text.isNullOrEmpty()) return null
        val lowerText = text.lowercase()
        for ((pattern, quality) in QUALITY_PATTERNS) {
            if (lowerText.contains(pattern)) return quality
        }
        return null
    }
}