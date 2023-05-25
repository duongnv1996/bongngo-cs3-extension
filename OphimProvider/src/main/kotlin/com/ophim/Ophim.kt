package com.loklok

import android.text.Html
import android.util.Log

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*

fun MainAPI.fixUrl(url: String,domain : String): String {
    if (url.startsWith("http") ||
        // Do not fix JSON objects when passed as urls.
        url.startsWith("{\"")
    ) {
        return url
    }
    if (url.isEmpty()) {
        return ""
    }

    val startsWithNoHttp = url.startsWith("//")
    if (startsWithNoHttp) {
        return "https:$url"
    } else {
        if (url.startsWith('/')) {
            return domain + url
        }
        return "$domain/$url"
    }
}
open class Ophim : MainAPI() {
    override var name = API_NAME
    override var mainUrl = "https://ophim1.com"
    override var lang = "vi"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val instantLinkLoading = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama,
    )

    companion object {
        const val DOMAIN = "https://ophim1.com"
        const val DOMAIN_IMAGE = "https://img.ophim1.com/uploads/movies"
        const val API_NAME = "Ổ Phim"
        const val PREFIX_GENRE = "/v1/api/the-loai"
        const val PREFIX_COUNTRY = "/v1/api/quoc-gia"
        const val DOMAIN_DETAIL_MOVIE = "${DOMAIN}/v1/api/phim"
    }

    override suspend fun getMenus(): List<Pair<String, List<Page>>>? {
        val listResult = arrayListOf<Pair<String, List<Page>>>()
        val re = app.get("${mainUrl}${PREFIX_GENRE}").parsedSafe<ResponseMetaData>()?.data
        app.get("${mainUrl}${PREFIX_GENRE}").parsedSafe<ResponseMetaData>()?.data?.items?.map {
            it.toPage(PREFIX_GENRE)
        }?.let {
            listResult.add(Pair("Thể loại", it))
        }
        app.get("${mainUrl}${PREFIX_COUNTRY}").parsedSafe<ResponseMetaData>()?.data?.items?.map {
            it.toPage(PREFIX_COUNTRY)
        }?.let {
            listResult.add(Pair("Quốc gia", it))
        }
        return listResult
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        return quickSearch(query)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? {
        val response = app.get("${DOMAIN}/v1/api/tim-kiem?keyword=$query").parsedSafe<Home>()

        return response?.data?.items?.map { itemData -> itemData.toSearchResponse() }
    }
    override suspend fun loadPage(url: String): PageResponse? {
        val splitUrl = url.split("&")
        val originUrl = splitUrl[0]
        val page = if (splitUrl.size > 1) splitUrl[1].toInt() else 1
        val response = app.get("${originUrl}?page=${page}").parsedSafe<Home>()
        val listItem =
            response?.data?.items?.map { itemData -> itemData.toSearchResponse() } ?: listOf()
        return PageResponse(
            list = listItem,
            if (listItem.isEmpty()) null else "${originUrl}&${(page + 1)}"
        )
    }


    override val mainPage = mainPageOf(
        "${mainUrl}/v1/api/home" to "Phim Mới",
        "${mainUrl}/v1/api/danh-sach/phim-le" to "Phim Lẻ",
        "${mainUrl}/v1/api/danh-sach/phim-bo" to "Phim Bộ",
        "${mainUrl}/v1/api/danh-sach/hoat-hinh" to "Hoạt hình",
        "${mainUrl}/v1/api/danh-sach/tv-shows" to "TVShow",
        "${mainUrl}/v1/api/danh-sach/phim-vietsub" to "Phim Vietsub",
        "${mainUrl}/v1/api/danh-sach/phim-thuyet-minh" to "Phim Thuyết Minh",
        "${mainUrl}/v1/api/danh-sach/phim-long-tieng" to "Phim Lồng Tiếng",
        "${mainUrl}/v1/api/danh-sach/phim-bo-hoan-thanh" to "Phim Bộ Đã Hoàn Thành",
        "${mainUrl}/v1/api/danh-sach/subteam" to "Vietsub Độc Quyền",
    )
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val list = app.get("${request.data}?page=${page}")
            .parsedSafe<Home>()?.data?.items?.map { itemData ->
                itemData.toSearchResponse()
            }
        return newHomePageResponse(request.name,list ?: emptyList(),true)
    }

    private suspend fun extractItemFromUrl(
        name: String,
        url: String,
        home: ArrayList<HomePageList>
    ) {
        app.get(url)
            .parsedSafe<Home>()?.data?.items?.map { itemData ->
                itemData.toSearchResponse()
            }?.let {
                home.add(HomePageList(name, it))
            }
    }

    override suspend fun load(url: String): LoadResponse? {

        val response = app.get(url).parsedSafe<ResponseData>()
        val movieDetail = response?.data?.item
        movieDetail?.let { movieDetailItem ->
            val related =  loadPage(DOMAIN+ PREFIX_GENRE+"/"+movieDetail.category.first().slug)?.list
            return if (movieDetail.type.toType() == TvType.Movie) {
                val listEp = arrayListOf<com.lagradost.cloudstream3.Episode>()
                movieDetailItem.episodes.forEachIndexed { index, episode ->
                    listEp.addAll(episode.serverData.map { serverData ->
                        com.lagradost.cloudstream3.Episode(
                            data = serverData.link_m3u8,
                            name = serverData.name,
                            description = serverData.filename,
                            season = index + 1
                        )
                    }
                    )
                }
                MovieLoadResponse(
                    name = movieDetailItem.name,
                    url = url,
                    apiName = API_NAME,
                    type = TvType.Movie,
                    dataUrl = listEp.first().data,
                    posterUrl = fixUrl(movieDetailItem.thumb_url, DOMAIN_IMAGE),
                    year = movieDetailItem.year,
                    plot = movieDetailItem.content.replace("</p>", "").replace("<p>", ""),
                    rating = null,
//                    trailers = listOf<TrailerData>(
//                        TrailerData(
//                            extractorUrl = movieDetailItem.trailer_url,
//                            referer = null,
//                            raw = true
//                        )
//                    ).toMutableList(),
                    actors = movieDetailItem.actor.map { it -> ActorData(actor = Actor(it)) },
                    comingSoon = movieDetailItem.episodes.isEmpty(),
                    backgroundPosterUrl = fixUrl(movieDetailItem.poster_url, DOMAIN_IMAGE),
                    recommendations = related,
                    tags = movieDetail.category.map { it -> it.name }
                )
            } else {
                val listEp = arrayListOf<com.lagradost.cloudstream3.Episode>()
                movieDetailItem.episodes.forEachIndexed { index, episode ->
                    listEp.addAll(episode.serverData.map { serverData ->
                        com.lagradost.cloudstream3.Episode(
                            data = serverData.link_m3u8,
                            name = serverData.name,
                            description = serverData.filename,
                            season = index + 1,
                            posterUrl = fixUrl(movieDetailItem.thumb_url, DOMAIN_IMAGE)
                        )
                    }
                    )
                }
                TvSeriesLoadResponse(
                    name = movieDetailItem.name,
                    url = url,
                    apiName = API_NAME,
                    type = TvType.TvSeries,
                    episodes = listEp,
                    posterUrl = fixUrl(movieDetailItem.thumb_url, DOMAIN_IMAGE),
                    year = movieDetailItem.year,
                    plot = movieDetailItem.content.replace("</p>", "").replace("<p>", ""),
                    rating = null,
//                    trailers = listOf<TrailerData>(
//                        TrailerData(
//                            extractorUrl = movieDetailItem.trailer_url,
//                            referer = null,
//                            raw = false
//                        )
//                    ).toMutableList(),
                    actors = movieDetailItem.actor.map { it -> ActorData(actor = Actor(it)) },
                    comingSoon = movieDetailItem.episodes.isEmpty(),
                    backgroundPosterUrl = fixUrl(movieDetailItem.poster_url, DOMAIN_IMAGE),
                    recommendations = related,
                    tags = movieDetail.category.map { it -> it.name }

                )
            }
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
        callback.invoke(
            ExtractorLink(
                source = data,
                name = API_NAME,
                url = data,
                referer = "",
                quality = getQualityFromName("1080P"),
                isM3u8 = true
            )
        )
        return true
    }

    fun ItemData.toSearchResponse(): MovieSearchResponse {
        return MovieSearchResponse(
            name = name,
            url = fixUrl(slug, DOMAIN_DETAIL_MOVIE),
            apiName = API_NAME,
            type = type.toType(),
            posterUrl = fixUrl(thumb_url, DOMAIN_IMAGE),
            year = year.toInt(),
            quality = getQualityFromString(quality)
        )
    }

    fun String.toType(): TvType {
        return if (this == "single") {
            TvType.Movie
        } else if (this == "series") {
            TvType.TvSeries
        } else {
            TvType.Anime
        }
    }

    data class Home(
        @JsonProperty("data") val data: Data,
    )


    data class Data(
        @JsonProperty("items") val items: List<ItemData>,
        @JsonProperty("params") val params: Params,
    )

    data class Params(
        @JsonProperty("pagination") val pagination: Pagination,
    )

    data class Pagination(
        @JsonProperty("totalItems") val totalItems: Int,
        @JsonProperty("totalItemsPerPage") val totalItemsPerPage: Int,
        @JsonProperty("currentPage") val currentPage: Int,
        @JsonProperty("pageRanges") val pageRanges: Int,
    )

    data class ItemData(
        @JsonProperty("_id") val _id: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("origin_name") val origin_name: String,
        @JsonProperty("type") val type: String,
        @JsonProperty("thumb_url") val thumb_url: String,
        @JsonProperty("sub_docquyen") val sub_docquyen: Boolean,
        @JsonProperty("time") val time: String,
        @JsonProperty("episode_current") val episode_current: String,
        @JsonProperty("quality") val quality: String,
        @JsonProperty("lang") val lang: String,
        @JsonProperty("year") val year: String,
        @JsonProperty("category") val category: List<MetaData>,
        @JsonProperty("country") val country: List<MetaData>,

        )

    data class MetaData(
        @JsonProperty("name") val name: String,
        @JsonProperty("slug") val slug: String,
    )

    fun MetaData.toPage(prefix: String): Page {
        return Page(name = name, url = fixUrl(slug, mainUrl + prefix), nameApi = API_NAME)
    }

    //
    data class ResponseMetaData(
        @JsonProperty("status") val status: String,
        @JsonProperty("message") val message: String,
        @JsonProperty("data") val data: ListItems,
    )

    data class ListItems(
        @JsonProperty("items") val items: List<MetaData>,
    )


    // movie detail

    data class ResponseData(
        @JsonProperty("status") val status: String,
        @JsonProperty("message") val message: String,
        @JsonProperty("data") val data: MovieDetailData,
    )

    data class MovieDetailData(
        @JsonProperty("item") val item: MovieDetailItem,

        )

    data class MovieDetailItem(
        @JsonProperty("_id") val _id: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("origin_name") val origin_name: String,
        @JsonProperty("type") val type: String,
        @JsonProperty("thumb_url") val thumb_url: String,
        @JsonProperty("sub_docquyen") val sub_docquyen: Boolean,
        @JsonProperty("time") val time: String,
        @JsonProperty("episode_current") val episode_current: String,
        @JsonProperty("quality") val quality: String,
        @JsonProperty("lang") val lang: String,
        @JsonProperty("year") val year: Int,
        @JsonProperty("view") val view: Int,
        @JsonProperty("category") val category: List<MetaData>,
        @JsonProperty("country") val country: List<MetaData>,
        @JsonProperty("content") val content: String,
        @JsonProperty("status") val status: String,
        @JsonProperty("poster_url") val poster_url: String,
        @JsonProperty("is_copyright") val is_copyright: Boolean,
        @JsonProperty("chieurap") val chieurap: Boolean,
        @JsonProperty("trailer_url") val trailer_url: String,
        @JsonProperty("episode_total") val episode_total: String,
        @JsonProperty("notify") val notify: String,
        @JsonProperty("showtimes") val showtimes: String,
        @JsonProperty("actor") val actor: List<String>,
        @JsonProperty("director") val director: List<String>,
        @JsonProperty("episodes") val episodes: List<Episode>,
    )

    data class Episode(
        @JsonProperty("server_name") val server_name: String,
        @JsonProperty("server_data") val serverData: List<ServerData>,

        )

    data class ServerData(
        @JsonProperty("name") val name: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("filename") val filename: String,
        @JsonProperty("link_embed") val link_embed: String,
        @JsonProperty("link_m3u8") val link_m3u8: String,

        )

}

