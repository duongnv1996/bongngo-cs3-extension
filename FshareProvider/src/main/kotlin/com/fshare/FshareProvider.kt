package com.fshare

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.phimhd.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class FshareProvider : MainAPI() {
    override var name = "Fshare"
    override var mainUrl = DOMAIN
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val hasChromecastSupport: Boolean
        get() = true
    override val supportedTypes: Set<TvType>
        get() = setOf(
            TvType.Movie,
            TvType.TvSeries,
            TvType.Anime,
        )

    companion object {
        const val DOMAIN = "https://thuvienhd.com"
        const val POST_PER_PAGE = 6
        const val URL_DETAIL = "$DOMAIN/?feed=fsharejson&id="
        const val URL_DETAIL_FILE_FSHARE = "https://www.fshare.vn/file/"
        const val URL_DETAIL_FSHARE =
            "https://www.fshare.vn/api/v3/files/folder?sort=type,name&page=0&per-page=99999&linkcode="
    }

    override val mainPage: List<MainPageData>
        get() = mainPageOf(
            "${mainUrl}/recent" to "Phim mới",
            "${mainUrl}/trending" to "Trending",
            "${mainUrl}/genre/phim-le" to "Phim Lẻ",
            "${mainUrl}/genre/series" to "Phim Bộ",
            "${mainUrl}/genre/tvb" to "Phim TVB",
            "${mainUrl}/genre/thuyet-minh-tieng-viet" to "Phim Thuyết Minh",

            )

    override suspend fun getMenus(): List<Pair<String, List<Page>>>? {
        try {
            val response = app.get(mainUrl).text
            val html = Jsoup.parse(response)
            val menuPhimLe = html.select("#menu-item-98416 li").map { li ->
                Page(
                    name = li.selectFirst("a")?.text() ?: "",
                    url = li.selectFirst("a")?.attr("href") ?: "",
                    nameApi = name
                )
            }
            val menuPhimBo = html.select("#menu-item-98409 li").map { li ->
                Page(
                    name = li.selectFirst("a")?.text() ?: "",
                    url = li.selectFirst("a")?.attr("href") ?: "",
                    nameApi = name
                )
            }
            val list = arrayListOf<Pair<String, List<Page>>>()
            list.add(Pair("Phim lẻ", menuPhimLe))
            list.add(Pair("Phim bộ", menuPhimBo))
            return list
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    override suspend fun loadPage(url: String): PageResponse? {
        val response = app.get(url).text
//        val listType: Type = object : TypeToken<ArrayList<HomeItem>>() {}.getType()
        val html = Jsoup.parse(response)
        val list = html.select(".items .item").map { itemHtml ->
            MovieSearchResponse(
                name = itemHtml.selectFirst("h3")?.text() ?: "",
                url = "$URL_DETAIL${itemHtml.attr("id").replace("post-", "")}",
                apiName = name,
                type = TvType.TvSeries,
                posterUrl = itemHtml.selectFirst("img")?.attr("src")
            )
        }
        return PageResponse(list = list, getPagingResult(html))
    }

    private fun getPagingResult(document: Document): String? {
        val tagPageResult: Element? = document.selectFirst(".pagination a")
        if (tagPageResult == null) { // only one page

            //LogUtils.d("no more page")
        } else {
            val listLiPage = document.select(".pagination")?.first()?.children()
            if (listLiPage != null && !listLiPage.isEmpty()) {
                for (i in listLiPage.indices) {
                    val li = listLiPage[i]
                    if ((li).attr("class") != null && (li).attr("class").contains("current")) {

                        if (i == listLiPage.size - 1) {
                            //last page
                            //LogUtils.d("no more page")
                        } else {
                            if (listLiPage[i + 1] != null) {
                                val nextLi = listLiPage[i + 1]
                                val a = nextLi
                                if (a != null) {
                                    var nextUrl = a.attr("href")
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
        val response = app.get("${request.data}/page/${page}").text
//        val listType: Type = object : TypeToken<ArrayList<HomeItem>>() {}.getType()
        val html = Jsoup.parse(response)
        val list = html.select(".items .item").map { itemHtml ->
            MovieSearchResponse(
                name = itemHtml.selectFirst("h3")?.text() ?: "",
                url = "$URL_DETAIL${itemHtml.attr("id").replace("post-", "")}",
                apiName = name,
                type = TvType.TvSeries,
                posterUrl = itemHtml.selectFirst("img")?.attr("src")
            )
        }
        return newHomePageResponse(request.name, list, true)
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        return quickSearch(query)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? {
        val response = app.get("https://thuvienhd.com/?feed=fsharejson&search=${query}").text
        val itemType = object : TypeToken<List<DetailMovie>>() {}.type
        var listRes = Gson().fromJson<List<DetailMovie>>(response, itemType)
        val listResult = arrayListOf<MovieSearchResponse>()
        val list = listRes?.forEach { itemData ->
            listResult.add(itemData.toSearchResponse())
        }
        if (MainAPI.settingsForProvider.enableAdult) {
            val headersTimFshare =
                mapOf("Authorization" to "Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJuYW1lIjoiZnNoYXJlIiwidXVpZCI6IjcxZjU1NjFkMTUiLCJ0eXBlIjoicGFydG5lciIsImV4cGlyZXMiOjAsImV4cGlyZSI6MH0.WBWRKbFf7nJ7gDn1rOgENh1_doPc07MNsKwiKCJg40U")
            val responseTimFshare = app.post(
                "https://api.timfshare.com/v1/string-query-search?query=${query}",
                headers = headersTimFshare
            ).parsedSafe<SearchFshareResponse>()
            responseTimFshare
                ?.data?.filter {
                    it.name.contains("mp4")
                            || it.name.contains("mkv")
                            || it.name.contains("wmv")
                            || it.name.contains("mpg")
                            || it.name.contains("avi")
                }
                ?.forEach {
                    listResult.add(it.toSearchResponse())
                }
        }
        return listResult
    }

    override suspend fun load(url: String): LoadResponse? {
        if (url.contains("https://www.fshare.vn/")) {
            //timfSHARE.com
            val name = url.split("|")[1]
            val size = url.split("|")[2]
            val urlFile = url.split("|")[0]
            return MovieLoadResponse(
                name = name,
                dataUrl = urlFile,
                url = urlFile,
                apiName = "Fshare",
                type = TvType.Movie,
                plot = name,
                posterUrl = "https://media.comicbook.com/files/img/default-movie.png"
            )
        }
        val movie = app.get(url).parsedSafe<DetailMovie>()
        val listLink = arrayListOf<Link>()

        val linkFileFshare = movie?.link?.filter { it -> !it.link.contains("/folder/") }
        linkFileFshare?.let {
            listLink.addAll(it)
        }

        val linkFolderFshare = movie?.link?.filter { it -> it.link.contains("/folder/") }
        val folders = linkFolderFshare?.apmap {
            app.get(URL_DETAIL_FSHARE + it.link.split("/").last()).parsedSafe<FshareFolder>()
        }
        folders?.forEach { fshareFolder ->
            fshareFolder?.items?.forEach { fshareItem ->
                listLink.add(Link(fshareItem.name, URL_DETAIL_FILE_FSHARE + fshareItem.linkcode))
            }
        }
        movie?.let { movie ->

            return TvSeriesLoadResponse(
                name = movie.title.split("&&")[0],
                url = url,
                apiName = name,
                type = TvType.TvSeries,
                episodes = listLink.map { link ->
                    Episode(
                        data = link.link,
                        name = link.title,
                        description = link.title
                    )
                },
                posterUrl = movie.image,
                plot = movie.description,
                tags = movie.category.map { cate -> cate.name }
            )
        } ?: kotlin.run {
            return null
        }

    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val login = app.post(
            "https://api2.fshare.vn/api/user/login/",
            json = mapOf(
                "user_email" to "boxphim705@gmail.com",
                "password" to "asdfghjkl",
                "app_key" to "tVDNf8QcQ5Sfasmm5ennLIjF5D11k21xCruVaAeJ",
            ).toJson(),
            headers = mapOf(
                "user-agent" to "Dalvik/2.1.0 (Linux; U; Android 6.0.1; FPT Play Box Build/Normal-6.7.106-20190111)"
            )
        ).parsedSafe<FshareLoginInfo>()
        login?.token?.let {
            val download = app.post(
                "https://api2.fshare.vn/api/Session/download",
                json = mapOf("url" to data, "token" to login.token).toJson(),
                headers = mapOf(
                    "user-agent" to "Dalvik/2.1.0 (Linux; U; Android 6.0.1; FPT Play Box Build/Normal-6.7.106-20190111)",
                    "cookie" to "session_id=${login.session_id}"
                )
//                cookies = mapOf("session_id" to login.session_id)
            ).parsedSafe<FshareDownloadInfo>()
            download?.let {
                callback.invoke(
                    ExtractorLink(
                        source = it.location,
                        name = data,
                        url = it.location,
                        referer = "",
                        isM3u8 = false,
                        headers = emptyMap(),
                        quality = Qualities.Unknown.value
                    )
                )
            }
        }

        return true
    }


    data class FshareDownloadInfo(
        @JsonProperty("location") val location: String,
    )

    data class FshareLoginInfo(
        @JsonProperty("token") val token: String,
        @JsonProperty("session_id") val session_id: String,
    )

    data class FshareFolder(
        @JsonProperty("current") val current: FshareItem,
        @JsonProperty("items") val items: List<FshareItem>,

        )

    data class FshareItem(
        @JsonProperty("id") val id: String,
        @JsonProperty("linkcode") val linkcode: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("type") val type: Int,
        @JsonProperty("size") val size: Double,
    )

    data class Link(
        @JsonProperty("title") val title: String,
        @JsonProperty("link") val link: String,
    )

    data class MetaData(
        @JsonProperty("name") val name: String,
        @JsonProperty("tax") val tax: String,
    )

    data class DetailMovie(
        @JsonProperty("id") val id: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("year") val year: String,
        @JsonProperty("pubDate") val pubDate: String,
        @JsonProperty("description") val description: String,
        @JsonProperty("image") val image: String,
        @JsonProperty("total_part") val total_part: String,
        @JsonProperty("current_part") val current_part: String,
        @JsonProperty("view") val view: String,
        @JsonProperty("link") val link: List<Link>,
        @JsonProperty("category") val category: List<MetaData>,
    )

    data class HomeItem(
        @JsonProperty("_id") val _id: String,
        @JsonProperty("post_title") val post_title: String,
        @JsonProperty("year") val year: String,
        @JsonProperty("img") val img: String,
        @JsonProperty("view") val view: String,
    )

    fun HomeItem.toSearchResponse(): MovieSearchResponse {
        return MovieSearchResponse(
            name = post_title,
            url = "$URL_DETAIL${_id}",
            apiName = name,
            type = TvType.TvSeries,
            posterUrl = img
        )
    }

    fun DetailMovie.toSearchResponse(): MovieSearchResponse {
        return MovieSearchResponse(
            name = title,
            url = "$URL_DETAIL${id}",
            apiName = name,
            type = TvType.TvSeries,
            posterUrl = image
        )
    }


    //// TIMFSHARE
    data class SearchFshareResponse(
        @JsonProperty("data") val data: List<DataItem>,
    )

    data class DataItem(
        @JsonProperty("id") val id: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("url") val url: String,
        @JsonProperty("size") val size: Long,
        @JsonProperty("file_type") val file_type: Int,
    )

    fun DataItem.toSearchResponse(): MovieSearchResponse {
        return MovieSearchResponse(
            name = name,
            url = "${url}|${name}|${size}",
            apiName = "Fshare",
            type = TvType.TvSeries,
            posterUrl = "https://media.comicbook.com/files/img/default-movie.png"
        )
    }
}