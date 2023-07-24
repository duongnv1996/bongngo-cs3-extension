package com.phimmoi

import android.util.Log
import android.util.Patterns
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.ui.search.SearchFragment
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.regex.Matcher
import java.util.regex.Pattern

class PhimmoiProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://phimmoichilld.net/"
    override var name = "Phimmoi"
    override val supportedTypes = setOf(TvType.Movie)
    private val defaultPageUrl: String
        get() = "${mainUrl}/genre/phim-chieu-rap/"
    companion object {
        const val HOST_STREAM = "dash.megacdn.xyz";
    }
    override var lang = "vi"

    // enable this when your provider has a main page
    override val hasMainPage = true

    // this function gets called when you search for something
    override suspend fun search(query: String): List<SearchResponse>? {
        return quickSearch(query)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? {
        val url =  "${mainUrl}tim-kiem/${query}/"
        val html = app.get(url).text
        val document = Jsoup.parse(html)

        return document.select("#binlist .item").map {
            getItemMovie(it)
        }
    }


    override suspend fun getMenus(): List<Pair<String, List<Page>>>? {
        val html = app.get(mainUrl).text
        val doc = Jsoup.parse(html)
        val listGenre = arrayListOf<Page>()
        doc.select("#main-menu .sub-menu").first()!!.select("li").forEach {
            val url = it.selectFirst("a")!!.attr("href")
            val name = it.selectFirst("a")!!.text().trim()
            listGenre.add(Page(name, url, nameApi = this.name))
        }
        val listCountry = arrayListOf<Page>()
        doc.select("#main-menu .sub-menu")[1].select("li").forEach {
            val url = it.selectFirst("a")!!.attr("href")
            val name = it.selectFirst("a")!!.text().trim()
            listCountry.add(Page(name, url, nameApi = this.name))
        }
        return arrayListOf<Pair<String, List<Page>>>(
            Pair("Thể loại", listGenre),
            Pair("Quốc gia", listCountry)
        )
    }
    private fun getItemMovie(it: Element): MovieSearchResponse {
        val title = it.select("h3").last()!!.text()
        val href = fixUrl(it.selectFirst("a")!!.attr("href"))
        val year = 0
        val image = it.selectFirst("img")!!.attr("src")
        //            val isMovie = href.contains("/movie/")
        return MovieSearchResponse(
            title,
            href,
            this.name,
            TvType.Movie,
            image,
            year,
            posterHeaders = mapOf("referer" to mainUrl)
        )
    }
    override suspend fun loadPage(url: String): PageResponse? {
        val html = app.get(url).text
        val document = Jsoup.parse(html)

        val list =  document.select("#binlist .item").map {
            getItemMovie(it)
        }
        return PageResponse(list,getPagingResult(document))
    }
    private fun getPagingResult( document: Document): String? {
        val tagPageResult: Element? = document.selectFirst(".pagination li")
        if (tagPageResult == null) { // only one page

            //LogUtils.d("no more page")
        } else {
            val listLiPage = document.select(".pagination li")
            if (!listLiPage.isEmpty()) {
                for (i in listLiPage.indices) {
                    val li = listLiPage[i].select("a")
                    if ((li).attr("class").contains("current")) {

                        if (i == listLiPage.size - 1) {
                            //last page
                            //LogUtils.d("no more page")
                        } else {
                            if ( listLiPage[i + 1] != null) {
                                val nextLi = listLiPage[i + 1]
                                val a = nextLi.getElementsByTag("a")
                                if (!a.isEmpty()) {
                                    var nextUrl = fixUrl(a.first()!!.attr("href"))

                                    //LogUtils.d("has more page")
                                    return nextUrl
                                } else {
                                    //LogUtils.d("no more page")

                                }
                            } else {
                                //LogUtils.d("no more page")
                            }
                        }
                        break
                    }
                }
            } else {
                //LogUtils.d("no more page")
            }
        }
        return null
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
                LiveTvSearchResponse(
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
                listHomePageList.add(HomePageList(name, listMovie,isHorizontalImages = true ))
        }

        return HomePageResponse(listHomePageList)
    }

//    override suspend fun loadLinks(
//        data: String,
//        isCasting: Boolean,
//        subtitleCallback: (SubtitleFile) -> Unit,
//        callback: (ExtractorLink) -> Unit
//    ): Boolean {
//        Log.d("DuongKK", "data LoadLinks ---> $data ")
//        val listEp = getDataEpisode(data)
//        val idEp = listEp.find { data.contains(it.description!!) }?.description ?: data.substring(data.indexOf("-pm")+3)
//        Log.d("DuongKK", "data LoadLinks ---> $data  --> $idEp")
//        try {
//            val urlRequest =
//                "${this.mainUrl}/chillsplayer.php" //'https://subnhanh.net/frontend/default/ajax-player'
//            val response = app.post(urlRequest, mapOf(), data = mapOf("qcao" to idEp)).okhttpResponse
//            if (!response.isSuccessful || response.body == null) {
////                Log.e("DuongKK ${response.message}")
//                return false
//            }
//            val doc: Document = Jsoup.parse(response.body?.string())
//            val jsHtml = doc.html()
//            if (doc.selectFirst("iframe") != null) {
//                // link embed
//                val linkIframe =
//                    "http://ophimx.app/player.html?src=${doc.selectFirst("iframe")!!.attr("src")}"
//                return false
//            } else {
//                // get url stream
//                var keyStart = "iniPlayers(\""
//                var keyEnd = "\""
//                if (!jsHtml.contains(keyStart)) {
//                    keyStart = "initPlayer(\""
//                }
//                var tempStart = jsHtml.substring(jsHtml.indexOf(keyStart) + keyStart.length)
//                var tempEnd = tempStart.substring(0, tempStart.indexOf(keyEnd))
//                val urlPlaylist = if (tempEnd.contains("https://")) {
//                    tempEnd
//                } else {
//                    "https://${HOST_STREAM}/raw/${tempEnd}/index.m3u8"
//                }
//                callback.invoke(
//                    ExtractorLink(
//                        urlPlaylist,
//                        this.name,
//                        urlPlaylist,
//                        mainUrl,
//                        getQualityFromName("720"),
//                        true
//                    )
//                )
//
//                //get url subtitle
//                keyStart = "tracks:"
//                if (jsHtml.contains(keyStart)) {
//                    keyEnd = "]"
//                }
//                tempStart = jsHtml.substring(jsHtml.indexOf(keyStart) + keyStart.length)
//                tempEnd = tempStart.substring(0, tempStart.indexOf(keyEnd))
//                val urls = extractUrls(tempEnd)
//                urls?.forEach {
//                    subtitleCallback.invoke(SubtitleFile("vi", it))
//                }
//            }
//        } catch (error: Exception) {
//        }
//        return true
//    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val dataDecrypted = decryptData(data)

        val document = app.get(dataDecrypted).document

        val key = document.select("div#content script")
            .find { it.data().contains("filmInfo.episodeID =") }?.data()?.let { script ->
                val id = script.substringAfter("filmInfo.episodeID = parseInt('")
                app.post(
                    // Not mainUrl
                    url = "${this.mainUrl}/chillsplayer.php",
                    data = mapOf("qcao" to id, "sv" to "0"),
                    referer = dataDecrypted,
                    headers = mapOf(
                        "X-Requested-With" to "XMLHttpRequest",
                        "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"
                    )
                ).text.substringAfterLast("iniPlayers(\"")
                    .substringBefore("\",")
            }

        listOf(
            Pair("https://so-trym.topphimmoi.org/raw/$key/index.m3u8", "PMFAST"),
            Pair("https://dash.megacdn.xyz/raw/$key/index.m3u8", "PMHLS"),
            Pair("https://so-trym.phimchill.net/dash/$key/index.m3u8", "PMPRO"),
            Pair("https://dash.megacdn.xyz/dast/$key/index.m3u8", "PMBK")
        ).apmap { (link, source) ->
            safeApiCall {
                callback.invoke(
                    ExtractorLink(
                        source,
                        source,
                        link,
                        referer = "$mainUrl/",
                        quality = Qualities.P1080.value,
                        isM3u8 = true,
                    )
                )
            }
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
            val image = it.selectFirst("img")!!.attr("data-src")
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