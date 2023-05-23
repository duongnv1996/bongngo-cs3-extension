package com.phimmoi

import android.util.Log
import android.util.Patterns
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.regex.Matcher
import java.util.regex.Pattern

class PhimmoiProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://phimmoichillc.net/"
    override var name = "Phimmoi"
    override val supportedTypes = setOf(TvType.Movie)
    companion object {
        const val HOST_STREAM = "dash.megacdn.xyz";
    }
    override var lang = "vi"

    // enable this when your provider has a main page
    override val hasMainPage = true

    // this function gets called when you search for something
    override suspend fun search(query: String): List<SearchResponse> {
        return listOf<SearchResponse>()
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val html = app.get(mainUrl).text
        val doc = Jsoup.parse(html)
        val listHomePageList = arrayListOf<HomePageList>()
        doc.select(".block").forEach {
            val name = it.select(".caption").text().trim()
            val urlMore = fixUrl(it.select(".see-more").attr("href"))
            val listMovie = it.select(".list-film .item").map {
                val title = it.select("p").last()!!.text()
                val href = fixUrl(it.selectFirst("a")!!.attr("href"))
                val year = 0
                val image = it.selectFirst("img")!!.attr("src")
                MovieSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.Movie,
                    image,
                    year,
                    posterHeaders = mapOf("referer" to mainUrl)
                )
            }
            if (listMovie.isNotEmpty())
                listHomePageList.add(HomePageList(name, listMovie ))
        }

        return HomePageResponse(listHomePageList)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("DuongKK", "data LoadLinks ---> $data ")
        val listEp = getDataEpisode(data)
        val idEp = listEp.find { data.contains(it.description!!) }?.description ?: data.substring(data.indexOf("-pm")+3)
        Log.d("DuongKK", "data LoadLinks ---> $data  --> $idEp")
        try {
            val urlRequest =
                "${this.mainUrl}/chillsplayer.php" //'https://subnhanh.net/frontend/default/ajax-player'
            val response = app.post(urlRequest, mapOf(), data = mapOf("qcao" to idEp)).okhttpResponse
            if (!response.isSuccessful || response.body == null) {
//                Log.e("DuongKK ${response.message}")
                return false
            }
            val doc: Document = Jsoup.parse(response.body?.string())
            val jsHtml = doc.html()
            if (doc.selectFirst("iframe") != null) {
                // link embed
                val linkIframe =
                    "http://ophimx.app/player.html?src=${doc.selectFirst("iframe")!!.attr("src")}"
                return false
            } else {
                // get url stream
                var keyStart = "iniPlayers(\""
                var keyEnd = "\""
                if (!jsHtml.contains(keyStart)) {
                    keyStart = "initPlayer(\""
                }
                var tempStart = jsHtml.substring(jsHtml.indexOf(keyStart) + keyStart.length)
                var tempEnd = tempStart.substring(0, tempStart.indexOf(keyEnd))
                val urlPlaylist = if (tempEnd.contains("https://")) {
                    tempEnd
                } else {
                    "https://${HOST_STREAM}/raw/${tempEnd}/index.m3u8"
                }
                callback.invoke(
                    ExtractorLink(
                        urlPlaylist,
                        this.name,
                        urlPlaylist,
                        mainUrl,
                        getQualityFromName("720"),
                        true
                    )
                )

                //get url subtitle
                keyStart = "tracks:"
                if (jsHtml.contains(keyStart)) {
                    keyEnd = "]"
                }
                tempStart = jsHtml.substring(jsHtml.indexOf(keyStart) + keyStart.length)
                tempEnd = tempStart.substring(0, tempStart.indexOf(keyEnd))
                val urls = extractUrls(tempEnd)
                urls?.forEach {
                    subtitleCallback.invoke(SubtitleFile("vi", it))
                }
            }
        } catch (error: Exception) {
        }
        return true
    }

    override suspend fun load(url: String): LoadResponse? {
        val html = app.get(url).text
        val doc: Document = Jsoup.parse(html)
        val realName = doc.select(".text h1").first()!!.text()
        val listDataHtml = doc.select(".entry-meta li")
        var year = ""
        var duration = ""
        for (index in listDataHtml.indices) {
            val data = (listDataHtml[index]).text().trim();
            if (data.contains("Thể loại: ")) {
                val genre = data.replace("Thể loại: ", "")
//                    movie.category = genre
            } else if (data.contains("Quốc gia:")) {
//                    movie. = data.replace("Quốc gia:", "")
            } else if (data.contains("Diễn viên: ")) {
//                    movie.actor = data.replace("Diễn viên:", "").trim()
            } else if (data.contains("Đạo diễn:")) {
//                    director = data.replace("Đạo diễn:", "").trim()
            } else if (data.contains("Thời lượng:")) {
                duration = data.replace("Thời lượng:", "")
            } else if (data.contains("Năm Phát Hành: ")) {
                year = data.replace("Năm Phát Hành: ", "")
            }
        }
        val isMovie = doc.selectFirst(".latest-episode") == null
        val description = doc.select("#film-content").text()
        val urlBackdoor = extractUrl(doc.select(".film-info .image").attr("style"))
//            movie.urlReview = movie.urlDetail
        val other = doc.select(".list-button .btn-see").first()!!.attr("href")
        val listRelate =  doc.selectFirst("#list-film-realted")!!.select(".item").map{
            val title = it.select("p").last()!!.text()
            val href = fixUrl(it.selectFirst("a")!!.attr("href"))
            val year = 0
            val image = it.selectFirst("img")!!.attr("src")
            MovieSearchResponse(
                title,
                href,
                this.name,
                TvType.Movie,
                image,
                year,
                posterHeaders = mapOf("referer" to mainUrl)
            )
        }
        val episodes = getDataEpisode(other)
        return if (episodes.isNullOrEmpty()) {
            MovieLoadResponse(
                name = realName,
                url = url,
                apiName = this.name,
                type = TvType.Movie,
                dataUrl = other,
                posterUrl = urlBackdoor,
                year = year.toInt(),
                plot = description,
                recommendations = listRelate,
                posterHeaders = mapOf("referer" to mainUrl)
            )
        } else {
            TvSeriesLoadResponse(
                name = realName,
                url = url,
                apiName = this.name,
                type = TvType.TvSeries,
                posterUrl = urlBackdoor,
                year = year.toIntOrNull(),
                plot = description,
                showStatus = null,
                episodes = episodes,
                recommendations = listRelate,
                posterHeaders = mapOf("referer" to mainUrl)
            )
        }
    }

    fun getDataEpisode(
        url: String,
    ): List<Episode> {
        val doc: Document = Jsoup.connect(url).timeout(60 * 1000).get()
        var idEpisode = ""
        var idMovie = ""
        var token = ""
        val listEpHtml = doc.select("#list_episodes li")
        val list = arrayListOf<Episode>();
        listEpHtml.forEach {
            val url = it.selectFirst("a")!!.attr("href")
            val name = it.selectFirst("a")!!.text()
            val id = it.selectFirst("a")!!.attr("data-id")
            val episode = Episode(url,name, 0, null, null, null, id);
            list.add(episode);
        }
        return list
    }

    private fun extractUrl(input: String) =
        input
            .split(" ")
            .firstOrNull { Patterns.WEB_URL.matcher(it).find() }
            ?.replace("url(", "")
            ?.replace(")", "")

    /**
     * Returns a list with all links contained in the input
     */
    fun extractUrls(text: String): List<String>? {
        val containedUrls: MutableList<String> = ArrayList()
        val urlRegex =
            "((https?|ftp|gopher|telnet|file):((//)|(\\\\))+[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]*)"
        val pattern: Pattern = Pattern.compile(urlRegex, Pattern.CASE_INSENSITIVE)
        val urlMatcher: Matcher = pattern.matcher(text)
        while (urlMatcher.find()) {
            containedUrls.add(
                text.substring(
                    urlMatcher.start(0),
                    urlMatcher.end(0)
                )
            )
        }
        return containedUrls
    }
}