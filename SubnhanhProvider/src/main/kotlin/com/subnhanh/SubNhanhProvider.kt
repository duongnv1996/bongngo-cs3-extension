package com.subnhanh

import android.net.Uri
import android.util.Log
import android.util.Patterns
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.ui.search.SearchFragment.Companion.DEFAULT_QUERY_SEARCH
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.collections.ArrayList

class SubNhanhProvider : MainAPI() {
    override var mainUrl = "https://subnhanh.vip"
    override var name = "SubNhanh"
     val defaultPageUrl: String
        get() = "${mainUrl}/the-loai/phim-bo"
    override val hasQuickSearch: Boolean
        get() = true
    override var lang = "vi"
    override val hasMainPage: Boolean
        get() = true

    override val hasChromecastSupport: Boolean
        get() = false

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
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val html = app.get(mainUrl).text
        val doc = Jsoup.parse(html)
        val listHomePageList = arrayListOf<HomePageList>()
        doc.select(".section .container").forEach {
            val name = it.select(".section-title").text().trim()
            val listMovie = arrayListOf<SearchResponse>()
            it.select(".item-list .item").forEach {
                listMovie.add(getItemMovie(it))
            }
            if (listMovie.isNotEmpty())
                listHomePageList.add(HomePageList(name, listMovie))
        }
        val listGenre = arrayListOf<Page>()
        doc.select(".top_menu .sub-menu").first()!!.select("li").forEach {
            val url = fixUrl(it.selectFirst("a")!!.attr("href"))
            val name = it.selectFirst("a")!!.text().trim()
            listGenre.add(Page(name,url,nameApi = this.name))
        }
        val listCountry = arrayListOf<Page>()
        doc.select(".top_menu .sub-menu")[1].select("li").forEach {
            val url = fixUrl(it.selectFirst("a")!!.attr("href"))
            val name = it.selectFirst("a")!!.text().trim()
            listCountry.add(Page(name,url,nameApi = this.name))
        }

        return HomePageResponse(listHomePageList)
    }

    override suspend fun getMenus(): List<Pair<String, List<Page>>> {
        val html = app.get(mainUrl).text
        val doc = Jsoup.parse(html)
        val listGenre = arrayListOf<Page>()
        doc.select(".top_menu .sub-menu").first()!!.select("li").forEach {
            val url = fixUrl(it.selectFirst("a")!!.attr("href"))
            val name = it.selectFirst("a")!!.text().trim()
            listGenre.add(Page(name,url,nameApi = this.name))
        }
        val listCountry = arrayListOf<Page>()
        doc.select(".top_menu .sub-menu")[1].select("li").forEach {
            val url = fixUrl(it.selectFirst("a")!!.attr("href"))
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

        val list =  document.select("#p0 .item").map {
            getItemMovie(it)
        }
        return PageResponse(list,getPagingResult(document))
    }
    private fun getPagingResult(document: Document): String? {
        val tagPageResult: Element? = document.selectFirst("#pagination")
        if (tagPageResult == null) { // only one page
            //LogUtils.d("no more page")
        } else {
            val listLiPage = document.select("li")
            if (listLiPage != null && !listLiPage.isEmpty()) {
                for (i in listLiPage.indices) {
                    val li = listLiPage[i]
                    if ((li).attr("class") != null && (li).attr("class").contains("active")) {
                        if (i == listLiPage.size - 1) {
                            //last page
                            //LogUtils.d("no more page")
                        } else {
                            if (listLiPage[i + 1] != null) {
                                val nextLi = listLiPage[i + 1]
                                val a = nextLi.getElementsByTag("a")
                                if (a != null && !a.isEmpty()) {
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
    override suspend fun search(query: String): List<SearchResponse>? {
        val url = if(query == DEFAULT_QUERY_SEARCH) defaultPageUrl else  "$mainUrl/search?query=${query}"
        Log.d("DuongKK","URL SEARCH $url")
        val html = app.get(url).text
        val document = Jsoup.parse(html)

        return document.select("#all-items .item").map {
            getItemMovie(it)
        }
    }

    private fun getItemMovie(it: Element): MovieSearchResponse {
        val title = it.select(".item-block-title").last()!!.text()
        val href = fixUrl(it.selectFirst(".item-block-title")!!.attr("href"))
        val year = 0
        var variable = it.getElementsByClass("item-image-block").first()!!.attr("style")
        var image =
            extractUrlPhoto(variable)?.replace("background-image:url('", "")
                ?.replace("')", "");
        return MovieSearchResponse(
            title,
            href,
            this.name,
            TvType.Movie,
            image,
            year
        )
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val dataDecrypted = decryptData(data)

        var doc: Document = Jsoup.connect(dataDecrypted).timeout(60 * 1000).get()
        var html = doc.html()

        val idMovie = this.getParamFromJS(html, "fcurrentid")
        val idEpisode = this.getParamFromJS(html, "fcurrentEpId")
        val ajaxPlayerUrl = this.getParamFromJS(html, "ajaxPlayerUrl")

        try {
            val urlRequest =
                "${mainUrl}/${ajaxPlayerUrl.substring(1)}" //'https://subnhanh.net/frontend/default/ajax-player'
            val response = app.post(
                urlRequest,
                mapOf("X-Requested-With" to "XMLHttpRequest"),
                data = mapOf("epId" to idEpisode, "type" to "hls")
            ).okhttpResponse

            if (!response.isSuccessful || response.body == null) {
                //LogUtils.e(response.message)
                return false
            }
            doc = Jsoup.parse(response.body?.string())
            val linkIframe = doc.select("iframe").attr("src")
            //LogUtils.d("linkIframe", linkIframe)
            val urlMapQuery = getMapQueryFromUrl(linkIframe.split("?")[1])
            var id = urlMapQuery["id"];
            val uri = Uri.parse(linkIframe)
            if (!id.isNullOrEmpty()) {
                val hostname = uri.host
                val urlPlaylist =
                    "https://${hostname}/playlist/${id}/${Date().time}.m3u8"
                callback.invoke(
                    ExtractorLink(
                        urlPlaylist,
                        this.name,
                        urlPlaylist,
                        linkIframe,
                        getQualityFromName("720"),
                        true
                    )
                )
            } else if (!urlMapQuery["vid"].isNullOrEmpty()) {
                //            //https://bitvtom100.xyz/hls/v2/ba9c172a350c798bf33811f5c809f5ab/playlist.m3u8
                id = urlMapQuery["vid"]
                val hostname = uri.host
                val urlPlaylist = "https://${hostname}/hls/v2/${id}/playlist.m3u8"
                callback.invoke(
                    ExtractorLink(
                        urlPlaylist,
                        this.name,
                        urlPlaylist,
                        linkIframe,
                        getQualityFromName("720"),
                        true
                    )
                )
            }
        } catch (error: java.lang.Exception) {
            error.printStackTrace()
            //  throw error
        }
        return true
    }

    override suspend fun load(url: String): LoadResponse? {
        val html = app.get(url).text
        val doc: Document = Jsoup.parse(html)
        val realName = doc.select(".header-title").text().split(',')[0].trim()
        val other = mainUrl + "/" + doc.select(".button_xemphim").attr("href").substring(1)
        val listDataHtml = doc.select(".header-short-description > div")
        var duration = ""
        var year = ""
        var urlBackdoor = ""
        for (index in listDataHtml.indices) {
            val data = (listDataHtml[index]).text();
            if (data.contains("Thể loại:")) {
                val genre = data.replace("Thể loại:", "").trim()
//                movie.category = genre
            } else if (data.contains("Quốc gia:")) {
//                    movie = data.replace("Quốc gia:", "").trim()
            } else if (data.contains("Diễn viên:")) {
//                movie.actor = data.replace("Diễn viên:", "").trim()
            } else if (data.contains("Đạo diễn:")) {
//                movie.director = data.replace("Đạo diễn:", "").trim()
            } else if (data.contains("Thời lượng:")) {
                duration = data.replace("Thời lượng:", "").trim()
            } else if (data.contains("Năm")) {
                year = data.replace("Năm sản xuất:", "").trim()
            }
        }
        val lichChieu = (listDataHtml.last())!!.text().replace("subnhanh.net", "phimhd")
//            movie.rate = "6"
        val description = lichChieu + "<br>" + doc.select("#review .rtb").text()
            .replace("subnhanh.net", "phimhd")
        val meta = doc.select(".header-short-description meta")
        for (index in meta.indices) {
            val data = (meta[index]);
            if ((data).attr("itemprop").equals("thumbnailUrl")) {
                urlBackdoor = mainUrl + "/" + (data).attr("content").substring(1)
            }
        }
        val styleImage = doc.selectFirst(".dynamic-page-header")!!.attr("style")
        urlBackdoor = extractUrlPhoto(styleImage)?.replace("background-image:url('", "")
            ?.replace("')", "").toString()
        val listRelate =  doc.select(".item-list-wrapper .item").map {
            getItemMovie(it)
        }
        return TvSeriesLoadResponse(
            name = realName,
            url = url,
            apiName = this.name,
            type = TvType.TvSeries,
            posterUrl = urlBackdoor,
            year = year.toIntOrNull(),
            plot = description,
            showStatus = null,
            episodes = getDataEpisode(other),
            recommendations = listRelate
        )
    }

    suspend fun getDataEpisode(
        url: String,
    ): List<Episode> {
        try {
            val html = app.get(url).text

            val idMovie = this.getParamFromJS(html, "fcurrentid")
            val idEpisode = this.getParamFromJS(html, "fcurrentEpId")
            val ajaxPlayerUrl = this.getParamFromJS(html, "ajaxPlayerUrl")
            val response = app.post(
                "${mainUrl}/ajax/list-episode",
                mapOf("X-Requested-With" to "XMLHttpRequest"),
                data = mapOf("ep_id" to idEpisode, "film_id" to idMovie)
            ).okhttpResponse
            if (response.isSuccessful && response.body != null) {
                val textHtml = response.body!!.string()
                val doc = Jsoup.parse(textHtml)
                //console.log(`DATA = ${ajaxPlayerUrl} , ${idEpisode} , ${idMovie}`)

                val epg = doc.select(".collection-list-wrapper") // list group ep
                // parse list group episode
                val list = arrayListOf<Episode>();
                for (index in epg.indices) {
                    val groupEpisodeHtml = epg[index];
                    val titleGroup = (groupEpisodeHtml).select("h4").text()

                    val liListEpisodeHtml =
                        (groupEpisodeHtml).select(".collection-list .collection-item")
                    // parse list episode inside group
                    for (indexEpisode in liListEpisodeHtml.indices) {
                        val episodeHtml = liListEpisodeHtml[indexEpisode];
                        val url =
                            mainUrl + "/" + (episodeHtml).select("a").attr("href").substring(1);
                        val name = (episodeHtml).select("a").text();
                        list.add(Episode(url,name, index, null, description = titleGroup))
                    }
                }
                return list
            } else {
                return emptyList()
            }
        } catch (error: Exception) {
            error.printStackTrace()
            return emptyList()
        }
    }

    // ----------- Utils --------------------------------------------------------------------------------------------------
    /**
     *              var fcurrentid = '2260';
    var fcurrentEpId = '44056';
    var ajaxPlayerUrl = '/frontend/default/ajax-player';
    var urlUpdateView = '/update-view';
     * @param {*} str
     * @param {*} key
     * @returns
     */
    private fun getParamFromJS(str: String, key: String): String {
        val firstIndex = str.indexOf(key) + key.length + 4; // 4 to index point to first char.
        val temp = str.substring(firstIndex);
        val lastIndex = temp.indexOf(";") - 1;
        val idMovie = temp.substring(0, lastIndex); // var fcurrentid = '2260'; -> 2260
        return idMovie
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


    fun getMapQueryFromUrl(query: String): HashMap<String, String> {
        val params = query.split("&").toTypedArray()
        val map = HashMap<String, String>()
        for (param in params) {
            val name = param.split("=").toTypedArray()[0]
            var value = ""
            if (param.split("=").toTypedArray().size > 1) {
                value = param.split("=").toTypedArray()[1]
            }
            map[name] = value
        }
        return map
    }

    private fun extractUrlPhoto(input: String) =
        input
            .split(" ")
            .firstOrNull { Patterns.WEB_URL.matcher(it).find() }

}