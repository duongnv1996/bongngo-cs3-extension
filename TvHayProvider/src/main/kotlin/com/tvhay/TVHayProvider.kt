package com.tvhay

import android.util.Log
import android.util.Patterns
import com.google.gson.Gson
import com.lagradost.cloudstream3.*

import com.lagradost.cloudstream3.ui.home.ParentItemAdapter
import com.lagradost.cloudstream3.ui.search.SearchFragment
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.phimhd.AppController
import com.phimhd.ConfigHomeResponseData
import com.phimhd.toHomePageList
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.regex.Matcher
import java.util.regex.Pattern

class TVHayProvider : MainAPI() {
    override var mainUrl= "https://tvhaye.org"
    override var name= "TVHay"
     val defaultPageUrl: String
        get() = "${mainUrl}/phim-moi/"
    override val hasQuickSearch: Boolean
        get() = true
    override var lang = "vi"
    override val hasMainPage: Boolean
        get() = true

    override val hasChromecastSupport: Boolean
        get() = true

    override val hasDownloadSupport: Boolean
        get() = true

    override val supportedTypes: Set<TvType>
        get() = setOf(
            TvType.Movie,
            TvType.TvSeries,
        )
    override val vpnStatus: VPNStatus
        get() = VPNStatus.None

    companion object {
        const val HOST_STREAM = "so-trym.topphimmoi.org";
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val html = app.get(mainUrl).text
        val doc = Jsoup.parse(html)
        val listHomePageList = arrayListOf<HomePageList>()
        doc.select(".block").forEach {
            val nameClass = it.select(".blocktitle")
            if(nameClass.isNullOrEmpty()){
            }else{
                var name = nameClass.first()?.selectFirst(".title")?.text() ?: "Phim mới cập nhật"
                val urlMore = fixUrl(it.select(".more a").attr("href"))
                val listMovie = it.select(".list-film .inner").map {
                    val title = it.selectFirst(".info .name")!!.text()
                    val href = fixUrl(it.selectFirst("a")!!.attr("href"))
                    val year = it.selectFirst(".year")?.text()!!.trim().toInt()
                    val image = it.selectFirst("img")!!.attr("data-original")
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

        }

        return HomePageResponse(listHomePageList)
    }

    override suspend fun getMenus(): List<Pair<String, List<Page>>>? {
        val html = app.get(mainUrl).text
        val doc = Jsoup.parse(html)
        val listGenre = arrayListOf<Page>()
        doc.select(".menu-item .sub-menu")[0]!!.select("li").forEach {
            val url = fixUrl(it.selectFirst("a")!!.attr("href"))
            val name = it.selectFirst("a")!!.text().trim()
            listGenre.add(Page(name,url,nameApi = this.name))
        }
        val listCountry = arrayListOf<Page>()
        doc.select(".menu-item .sub-menu")[1].select("li").forEach {
            val url =fixUrl( it.selectFirst("a")!!.attr("href"))
            val name = it.selectFirst("a")!!.text().trim()
            listCountry.add(Page(name,url,nameApi = this.name))
        }
        return arrayListOf<Pair<String, List<Page>>>(
            Pair("Thể loại", listGenre),
            Pair("Quốc gia", listCountry)
        )
    }

    override suspend fun loadPage(url: String): PageResponse {
        val html = app.get(url).text
        val document = Jsoup.parse(html)

        val list =  document.select(".list-film .inner").map {
            getItemMovie(it)
        }
        return PageResponse(list,getPagingResult(document))
    }
    private fun getPagingResult( document: Document): String? {
        val tagPageResult: Element? = document.selectFirst(".wp-pagenavi a")
        if (tagPageResult == null) { // only one page

           //LogUtils.d("no more page")
        } else {
            val listLiPage = document.select(".wp-pagenavi")?.first()?.children()
            if (listLiPage != null && !listLiPage.isEmpty()) {
                for (i in listLiPage.indices) {
                    val li = listLiPage[i]
                    if ((li).attr("class") != null && (li).attr("class").contains("current")) {

                        if (i == listLiPage.size - 1) {
                            //last page
                           //LogUtils.d("no more page")
                        } else {
                            if ( listLiPage[i + 1] != null) {
                                val nextLi = listLiPage[i + 1]
                                val a = nextLi
                                if (a != null ) {
                                    var nextUrl = fixUrl(a.attr("href"))
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
    override suspend fun search(query: String): List<SearchResponse>? {
        val url = if(query == SearchFragment.DEFAULT_QUERY_SEARCH) defaultPageUrl else "$mainUrl/tag/${query}"//https://chillhay.net/search/boyhood
        val html = app.get(url).text
        val document = Jsoup.parse(html)

        return document.select(".list-film .inner").map {
            getItemMovie(it)
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? {
        val url =  "$mainUrl/tag/${query}"//https://chillhay.net/search/boyhood
        val html = app.get(url).text
        val document = Jsoup.parse(html)

        return document.select(".list-film .inner").map {
            getItemMovie(it)
        }
    }

    private fun getItemMovie(it: Element): MovieSearchResponse {
        val title = it.selectFirst(".info .name")!!.text()
        val href = fixUrl(it.selectFirst("a")!!.attr("href"))
        val year = it.selectFirst(".year")?.text()!!.trim().toInt()
        val image = it.selectFirst("img")!!.attr("data-original")
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
    fun findUrls(input: String): List<String> {
        val pattern = "(?i)\\b((?:https?://|www\\d{0,3}[.]|[a-z0-9.\\-]+[.][a-z]{2,4}/)(?:[^\\s()<>]+|\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\))+(?:\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\)|[^\\s`!()\\[\\]{};:'\".,<>?«»“”‘’]))"
        val regex = Regex(pattern)
        return regex.findAll(input).map { it.value }.toList()
    }
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("DuongKK", "data LoadLinks ---> $data ")
        val listEp = getDataEpisode(data)
        val path = data.split("/").last()
        val idEp = path.split(".")[1]
        val idMovie = path.split(".")[0].split("-").last()
        Log.d("DuongKK", "data LoadLinks ---> $data  --> $idEp --> $idMovie")
        try {
            val urlRequest =
                "${this.mainUrl}/ajax/player/" //'https://subnhanh.net/frontend/default/ajax-player'
            val response = app.post(urlRequest, mapOf(), data = mapOf("id" to idMovie , "ep" to idEp , "sv" to "0")).okhttpResponse
            if (!response.isSuccessful || response.body == null) {
               //LogUtils.e("DuongKK ${response.message}")
                return false
            }
            val doc: Document = Jsoup.parse(response.body?.string())
            val jsHtml = doc.html()
            Log.d("BLUE","jsHtml $jsHtml")
            if (doc.selectFirst("iframe") != null) {
                // link embed
                val linkIframe =
                    "http://ophimx.app/player.html?src=${doc.selectFirst("iframe")!!.attr("src")}"
               //LogUtils.d("DuongKK linkIframe ---> $linkIframe")
                return false
            } else {
                // get url stream
                var keyStart = "playerInstance.setup({sources:"
                var keyEnd = "],"
                if (!jsHtml.contains(keyStart)) {
                   //LogUtils.e("jsHtml does not contain keyStart")
                    return false
                }
                var tempStart = jsHtml.substring(jsHtml.indexOf(keyStart) + keyStart.length)
                var tempEnd = tempStart.substring(0, tempStart.indexOf(keyEnd))
               //LogUtils.d("DuongKK", "tempEnd ---> ${tempEnd}")
                val listUrl = findUrls(tempEnd)
                listUrl.forEachIndexed { index, url ->
                    callback.invoke(
                        ExtractorLink(
                            url,
                            this.name+ " - link ${index+1}",
                            url,
                            mainUrl,
                            getQualityFromName("720"),
                            true
                        )
                    )
                }
            }
        } catch (error: Exception) {
           //LogUtils.e("DuongKK", error.message)
        }
        return true
    }

    override suspend fun load(url: String): LoadResponse? {
        val html = app.get(url).text
        val doc: Document = Jsoup.parse(html)
        val realName = doc.select(".title").first()!!.text()
        var year = doc.select(".name2 .year").text().trim().replace("(","").replace(")","").toInt()
        var duration = ""
//        for (index in listDataHtml.indices) {
//            val data = (listDataHtml[index]).text().trim();
//            if (data.contains("Thể loại: ")) {
//                val genre = data.replace("Thể loại: ", "")
////                    movie.category = genre
//            } else if (data.contains("Quốc gia:")) {
////                    movie. = data.replace("Quốc gia:", "")
//            } else if (data.contains("Diễn viên: ")) {
////                    movie.actor = data.replace("Diễn viên:", "").trim()
//            } else if (data.contains("Đạo diễn:")) {
////                    director = data.replace("Đạo diễn:", "").trim()
//            } else if (data.contains("Thời lượng:")) {
//                duration = data.replace("Thời lượng:", "")
//            } else if (data.contains("Năm Phát Hành: ")) {
//                year = data.replace("Năm Phát Hành: ", "")
//            }
//        }
        val description = doc.select("#info-film").text()
        val urlBackdoor = fixUrl(doc.select(".poster img").attr("src"))
//            movie.urlReview = movie.urlDetail
        val urlWatch = doc.select(".btn-watch").attr("href")
        if(urlWatch.isNullOrBlank()){
            return  TvSeriesLoadResponse(
                name = realName,
                url = url,
                apiName = this.name,
                type = TvType.TvSeries,
                posterUrl = urlBackdoor,
                year = year,
                plot = description,
                comingSoon = true,
                showStatus = null,
                episodes = emptyList(),
                posterHeaders = mapOf("referer" to mainUrl)
            )
        }
        val episodes = getDataEpisode(urlWatch)
       return  TvSeriesLoadResponse(
           name = realName,
           url = url,
           apiName = this.name,
           type = TvType.TvSeries,
           posterUrl = urlBackdoor,
           year = year,
           plot = description,
           showStatus = null,
           episodes = episodes,
           posterHeaders = mapOf("referer" to mainUrl)
       )
    }

    fun getDataEpisode(
        url: String,
    ): List<Episode> {
        val doc: Document = Jsoup.connect(url).timeout(60 * 1000).get()
        val listEpHtml = doc.select(".episodelist li")
        val list = arrayListOf<Episode>();
        listEpHtml.forEach {
            val url = it.selectFirst("a")!!.attr("href")
            val name = it.selectFirst("a")!!.attr("title")
            val id = it.selectFirst("a")!!.attr("data-episode-id")
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