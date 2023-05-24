package com.loklok

import android.util.Log

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.Gson
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.RequestBodyTypes
import com.loklok.LoklokUtils.getAesKey
import com.loklok.LoklokUtils.getDeviceId
import com.loklok.LoklokUtils.getLanguage
import com.loklok.LoklokUtils.getQuality
import com.loklok.LoklokUtils.getSign
import com.loklok.LoklokUtils.upgradeSoraUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString.Companion.encode
import java.net.URI
import java.util.concurrent.TimeUnit

open class Loklok : MainAPI() {
    override var name = "PhimHD"
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
        private const val geoblockError = "Loklok is Geoblock, use vpn or give up"
        private const val api = "https://web-api.netpop.app"
        private const val apiUrl = "$api/cms/web/pc"
        const val RSA_PUBLIC_KEY = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC/K5eyJ18Y2l/vgGClKLXGQ0oAO2YdZleu59Oh2wlrxcxgKmt6FJ6rjxDJhs3K3uHdjvZWJnIQjd+pkc0g2/Yh+n5el7zWTWavUQ+q/mMIIIubiDvIJrECPj8thFy7LMFqrM2Qek8wdGV3lPMn/Yq6siidALJwOrt+UBehcwoV2QIDAQAB"
        private val deviceId = getDeviceId()
        private val searchApi = base64Decode("aHR0cHM6Ly9sb2tsb2suY29t")
        private const val mainImageUrl = "https://images.weserv.nl"
        const val play = "https://ali-cdn-play-web.loklok.tv"
        const val preview = "https://ali-cdn-preview-web.loklok.tv"
        const val akm = "https://akm-cdn-play.loklok.tv"
    }

    override suspend fun getMenus(): List<Pair<String, List<Page>>>? {
        val params = emptyMap<String,String>()
        val homeCategoryResponse = Gson().fromJson<HomeCategoryResponse>(
            app.get(
                "${apiUrl}/search/list",
                headers = createHeaders(params),
                cacheTime = 4,
                cacheUnit = TimeUnit.HOURS
            ).text, HomeCategoryResponse::class.java
        )
        val menuTitleTvShow = homeCategoryResponse.data.find { it.id == 2 }?.name ?: ""
        val menuTitleMovie = homeCategoryResponse.data.find { it.id == 1 }?.name ?: ""
        val menu1 = Pair(menuTitleTvShow,
            homeCategoryResponse.toListTvShowPage(name)
        )
        val  menu2 = Pair(menuTitleMovie,
            homeCategoryResponse.toListMoviesPage(name)
        )
        return listOf(menu1,menu2)
    }
    private fun createHeaders(
        params: Map<String, String>,
        currentTime: String = System.currentTimeMillis().toString(),
    ): Map<String, String> {
        return mapOf(
            "lang" to "vi",
            "currentTime" to currentTime,
            "sign" to getSign(currentTime, params, deviceId).toString(),
            "aesKey" to getAesKey(deviceId).toString(),
        )
    }

    private fun encode(input: String): String =
        java.net.URLEncoder.encode(input, "utf-8").replace("+", "%20")

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val home = ArrayList<HomePageList>()
        for (i in 0..7) {
            val params = mapOf(
                "page" to "$i",
                "size" to "6",
            )
            app.get("$apiUrl/homePage/singleAlbums", params = params, headers = createHeaders(params))
                .parsedSafe<Home>()?.data?.recommendItems.orEmpty()
                .filterNot { it.homeSectionType == "BLOCK_GROUP" }
                .filterNot { it.homeSectionType == "BANNER" }
                .mapNotNull { res ->
                    val header = res.homeSectionName ?: return@mapNotNull null
                    val media = res.media?.mapNotNull { media -> media.toSearchResponse() } ?: throw ErrorLoadingException()
                    home.add(HomePageList(header, media))
                }
        }
        return HomePageResponse(home)
    }

    private fun Media.toSearchResponse(): SearchResponse? {

        return newMovieSearchResponse(
            title ?: name ?: return null,
            UrlData(id, category ?: domainType).toJson(),
            TvType.Movie,
        ) {
            this.posterUrl = (imageUrl ?: coverVerticalUrl)?.let {
                "$mainImageUrl/?url=${encode(it)}&w=175&h=246&fit=cover&output=webp"
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? {
        val body = mapOf(
            "searchKeyWord" to query,
            "size" to "50",
            "sort" to "",
            "searchType" to "",
        )
        return app.post(
            "$apiUrl/search/searchWithKeyWord",
            requestBody = body.toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull()),
            headers = createHeaders(body)
        ).parsedSafe<QuickSearchRes>()?.data?.searchResults?.mapNotNull { media ->
            media.toSearchResponse()
        }
    }

    override suspend fun search(query: String): List<SearchResponse>? = quickSearch(query)

//    override suspend fun search(query: String): List<SearchResponse> {
//        val res = app.get(
//            "$searchApi/search?keyword=$query",
//        ).document
//
//        val script = res.select("script").find { it.data().contains("function(a,b,c,d,e") }?.data()
//            ?.substringAfter("searchResults:[")?.substringBefore("]}],fetch")
//
//        return res.select("div.search-list div.search-video-card").mapIndexed { num, block ->
//            val name = block.selectFirst("h2.title")?.text()
//            val data = block.selectFirst("a")?.attr("href")?.split("/")
//            val id = data?.last()
//            val type = data?.get(2)?.toInt()
//            val image = Regex("coverVerticalUrl:\"(.*?)\",").findAll(script.toString())
//                .map { it.groupValues[1] }.toList().getOrNull(num)?.replace("\\u002F", "/")
//
//
//            newMovieSearchResponse(
//                "$name",
//                UrlData(id, type).toJson(),
//                TvType.Movie,
//            ) {
//                this.posterUrl = image
//            }
//
//        }
//
//    }

    override suspend fun load(url: String): LoadResponse? {
        val data = parseJson<UrlData>(url)
        val params = mapOf(
            "category" to "${data.category}",
            "id" to "${data.id}",
        )
        val res = app.get(
            "$apiUrl/movieDrama/get",
            params = params,
            headers = createHeaders(params)
        ).parsedSafe<Load>()?.data ?: throw ErrorLoadingException(geoblockError)

        val actors = res.starList?.mapNotNull {
            Actor(
                it.localName ?: return@mapNotNull null, it.image
            )
        }

        val episodes = res.episodeVo?.map { eps ->
            val definition = eps.definitionList?.map {
                Definition(
                    it.code,
                    it.description,
                )
            }
            val subtitling = eps.subtitlingList?.map {
                Subtitling(
                    it.languageAbbr,
                    it.language,
                    it.subtitlingUrl
                )
            }
            Episode(
                data = UrlEpisode(
                    data.id.toString(),
                    data.category,
                    eps.id,
                    definition,
                    subtitling
                ).toJson(),
                episode = eps.seriesNo
            )
        } ?: throw ErrorLoadingException("No Episode Found")
        val recommendations = res.likeList?.mapNotNull { rec ->
            rec.toSearchResponse()
        }

        val type = when {
            res.areaList?.firstOrNull()?.id == 44 && res.tagNameList?.contains("Anime") == true -> {
                TvType.Anime
            }
            data.category == 0 -> {
                TvType.Movie
            }
            else -> {
                TvType.TvSeries
            }
        }

        val animeType = if (type == TvType.Anime && data.category == 0) "movie" else "tv"
        val (malId, anilistId) = if (type == TvType.Anime) getTracker(
            res.name,
            animeType,
            res.year
        ) else Tracker()

        return newTvSeriesLoadResponse(
            res.name ?: return null,
            url,
            if (data.category == 0) TvType.Movie else type,
            episodes
        ) {
            this.posterUrl = res.coverVerticalUrl
            this.backgroundPosterUrl = res.coverHorizontalUrl
            this.year = res.year
            this.plot = res.introduction
            this.tags = res.tagNameList
            this.rating = res.score.toRatingInt()
            addActors(actors)
            addMalId(malId)
            addAniListId(anilistId?.toIntOrNull())
            this.recommendations = recommendations
        }

    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = parseJson<UrlEpisode>(data)

        res.definitionList?.apmap { video ->
            val body =
                """[{"category":${res.category},"contentId":"${res.id}","episodeId":${res.epId},"definition":"${video.code}"}]""".toRequestBody(
                    RequestBodyTypes.JSON.toMediaTypeOrNull()
                )
            val params = mapOf(
                "category" to res.category.toString(),
                "contentId" to res.id.toString(),
                "definition" to video.code.toString(),
                "episodeId" to res.epId.toString(),
            )
            val json = app.get(
                "$apiUrl/movieDrama/getPlayInfo",
                params = params,
                headers = createHeaders(params),
            ).parsedSafe<PreviewResponse>()?.data
            val expiry = System.currentTimeMillis() + 60 * 60 * 12 * 7
            val mac = "fuckfuck".encode().hmacSha256("$expiry".encode()).hex()
            callback.invoke(
                ExtractorLink(
                    this.name,
                    this.name,
                    if(json?.mediaUrl?.startsWith(preview) == true) upgradeSoraUrl(json.mediaUrl) else json?.mediaUrl ?: return@apmap,
                    if(json.mediaUrl.startsWith(play)) "https://loklok.com/" else "",
                    getQuality(json.currentDefinition ?: ""),
                    isM3u8 = URI(json.mediaUrl).path.endsWith(".m3u8"),
                    headers = mapOf("Cookie" to "hdntl=exp=$expiry-acl=%2f*-data=hdntl-hmac=$mac")
                )
            )
        }

        res.subtitlingList?.map { sub ->
            subtitleCallback.invoke(
                SubtitleFile(
                    getLanguage(sub.languageAbbr ?: return@map),
                    sub.subtitlingUrl ?: return@map
                )
            )
        }

        return true
    }

    private suspend fun getTracker(title: String?, type: String?, year: Int?): Tracker {
        val res = app.get("https://api.consumet.org/meta/anilist/$title")
            .parsedSafe<AniSearch>()?.results?.find { media ->
                (media.title?.english.equals(title, true) || media.title?.romaji.equals(
                    title,
                    true
                )) || (media.type.equals(type, true) && media.releaseDate == year)
            }
        return Tracker(res?.malId, res?.aniId, res?.image, res?.cover)
    }
    data class Tracker(
        val malId: Int? = null,
        val aniId: String? = null,
        val image: String? = null,
        val cover: String? = null,
    )

    data class UrlData(
        val id: Any? = null,
        val category: Int? = null,
    )

    data class Subtitling(
        val languageAbbr: String? = null,
        val language: String? = null,
        val subtitlingUrl: String? = null,
    )

    data class Definition(
        val code: String? = null,
        val description: String? = null,
    )

    data class UrlEpisode(
        val id: String? = null,
        val category: Int? = null,
        val epId: Int? = null,
        val definitionList: List<Definition>? = arrayListOf(),
        val subtitlingList: List<Subtitling>? = arrayListOf(),
    )

    data class Title(
        @JsonProperty("romaji") val romaji: String? = null,
        @JsonProperty("english") val english: String? = null,
    )

    data class Results(
        @JsonProperty("id") val aniId: String? = null,
        @JsonProperty("malId") val malId: Int? = null,
        @JsonProperty("title") val title: Title? = null,
        @JsonProperty("releaseDate") val releaseDate: Int? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("image") val image: String? = null,
        @JsonProperty("cover") val cover: String? = null,
    )

    data class AniSearch(
        @JsonProperty("results") val results: java.util.ArrayList<Results>? = arrayListOf(),
    )

    data class QuickSearchData(
        @JsonProperty("searchResults") val searchResults: ArrayList<Media>? = arrayListOf(),
    )

    data class QuickSearchRes(
        @JsonProperty("data") val data: QuickSearchData? = null,
    )

    data class PreviewResponse(
        @JsonProperty("data") val data: PreviewVideos? = null,
    )

    data class PreviewVideos(
        @JsonProperty("mediaUrl") val mediaUrl: String? = null,
        @JsonProperty("currentDefinition") val currentDefinition: String? = null,
    )

    data class SubtitlingList(
        @JsonProperty("languageAbbr") val languageAbbr: String? = null,
        @JsonProperty("language") val language: String? = null,
        @JsonProperty("subtitlingUrl") val subtitlingUrl: String? = null,
    )

    data class DefinitionList(
        @JsonProperty("code") val code: String? = null,
        @JsonProperty("description") val description: String? = null,
    )

    data class EpisodeVo(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("seriesNo") val seriesNo: Int? = null,
        @JsonProperty("definitionList") val definitionList: ArrayList<DefinitionList>? = arrayListOf(),
        @JsonProperty("subtitlingList") val subtitlingList: ArrayList<SubtitlingList>? = arrayListOf(),
    )

    data class Region(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
    )

    data class StarList(
        @JsonProperty("image") val image: String? = null,
        @JsonProperty("localName") val localName: String? = null,
    )

    data class MediaDetail(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("introduction") val introduction: String? = null,
        @JsonProperty("year") val year: Int? = null,
        @JsonProperty("category") val category: String? = null,
        @JsonProperty("coverVerticalUrl") val coverVerticalUrl: String? = null,
        @JsonProperty("coverHorizontalUrl") val coverHorizontalUrl: String? = null,
        @JsonProperty("score") val score: String? = null,
        @JsonProperty("starList") val starList: ArrayList<StarList>? = arrayListOf(),
        @JsonProperty("areaList") val areaList: ArrayList<Region>? = arrayListOf(),
        @JsonProperty("episodeVo") val episodeVo: ArrayList<EpisodeVo>? = arrayListOf(),
        @JsonProperty("likeList") val likeList: ArrayList<Media>? = arrayListOf(),
        @JsonProperty("tagNameList") val tagNameList: ArrayList<String>? = arrayListOf(),
    )

    data class Load(
        @JsonProperty("data") val data: MediaDetail? = null,
    )

    data class Media(
        @JsonProperty("id") val id: Any? = null,
        @JsonProperty("category") val category: Int? = null,
        @JsonProperty("domainType") val domainType: Int? = null,
        @JsonProperty("imageUrl") val imageUrl: String? = null,
        @JsonProperty("coverVerticalUrl") val coverVerticalUrl: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("jumpAddress") val jumpAddress: String? = null,
    )

    data class RecommendItems(
        @JsonProperty("homeSectionName") val homeSectionName: String? = null,
        @JsonProperty("homeSectionType") val homeSectionType: String? = null,
        @JsonProperty("recommendContentVOList") val media: ArrayList<Media>? = arrayListOf(),
    )

    data class Data(
        @JsonProperty("recommendItems") val recommendItems: ArrayList<RecommendItems>? = arrayListOf(),
    )

    data class Home(
        @JsonProperty("data") val data: Data? = null,
    )
    data class SearchPageItem(
        @JsonProperty("image") val image: String,
        @JsonProperty("introduction") val introduction: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("contentId") val contentId: Number,
        @JsonProperty("domainType") val domainType: Int,
        @JsonProperty("releaseTime") val releaseTime: String,
        @JsonProperty("sort") val sort: String,
    )
    data class HomeResponse(
        @JsonProperty("data") val data: DataHomeResponse,
        @JsonProperty("code") val code: String
    )

    data class DataHomeResponse(
        @JsonProperty("recommendItems") val recommendItems: List<RecommendItems>, // use for a movie when homepage
        @JsonProperty("page") val page: Int,
        @JsonProperty("name") val name: String,
        @JsonProperty("searchResults") val searchResults: List<SearchResultItem>, // use for a movie when search
        @JsonProperty("content") val content: List<SearchPageItem>, // use for a movie in page
    )

    data class ItemCategory(
        @JsonProperty("params") val params: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("screeningType") val screeningType: String,
    )

    data class ScreenItem(
        @JsonProperty("id") val id: Int,
        @JsonProperty("name") val name: String,
        @JsonProperty("items") val items: List<ItemCategory>,
    )

    data class DataCategory(
        @JsonProperty("screeningItems") val screeningItems: List<ScreenItem>, // use for a movie when homepage
        @JsonProperty("id") val id: Int,
        @JsonProperty("name") val name: String,
        @JsonProperty("params") val params: String,
    )
    data class HomeCategoryResponse(
        @JsonProperty("data") val data: List<DataCategory>,
        @JsonProperty("code") val code: String
    )
    private fun HomeCategoryResponse.toListTvShowPage(nameApi: String): List<Page> {
        val result = arrayListOf<Page>()
        data.map { dataCategory ->
            if (dataCategory.id == 2) {     // id = 2 is TvShow
                val listCategory =
                    dataCategory.screeningItems.find { it.id == 5 } // id = 5 is genres

                val list = listCategory?.items?.filter {
                    it.params.isNotBlank()
                }
                list?.map { itemCategory ->
                    Page(
                        name = itemCategory.name,
                        url = "TV,SETI,MINISERIES,VARIETY,TALK,COMIC,DOCUMENTARY*${itemCategory.params}*",
                        isSelected = false,
                        nameApi = nameApi
                    )
                }?.let { result.addAll(it) }
            }
        }
        return result
    }
    data class SearchResultItem(
        @JsonProperty("coverVerticalUrl") val coverVerticalUrl: String,
        @JsonProperty("coverHorizontalUrl") val coverHorizontalUrl: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("id") val id: Number,
        @JsonProperty("domainType") val domainType: Int,
        @JsonProperty("releaseTime") val releaseTime: String,
        @JsonProperty("sort") val sort: String,
        @JsonProperty("duration") val duration: String,
    )
    data class LokLokSearchResponse(
        @JsonProperty("data") val data: DataHomeResponse,
        @JsonProperty("code") val code: String
    )

    fun SearchPageItem.toMovieSearchResponse(nameApi: String) :  SearchResponse {
//        return MovieSearchResponse(
//            name = name,
//            url = "$contentId&$domainType",
//            apiName = nameApi,
//            type = TvType.Movie,
//            posterUrl = fixUrlImageLokLok(image),
//            year = null,
//            id = contentId.toInt()
//        )
        return newMovieSearchResponse(
            name,
            UrlData( contentId.toInt(),  domainType).toJson(),
            TvType.Movie,
        ) {
            this.posterUrl = (fixUrlImageLokLok(image))?.let {
                "$mainImageUrl/?url=${encode(it)}&w=175&h=246&fit=cover&output=webp"
            }
        }
    }
    fun SearchResultItem.toMovieSearchResponse(nameApi: String):  SearchResponse {
        return newMovieSearchResponse(
            name,
            UrlData( id.toInt(),  domainType).toJson(),
            TvType.Movie,
        ) {
            this.posterUrl = (fixUrlImageLokLok(coverVerticalUrl))?.let {
                "$mainImageUrl/?url=${encode(it)}&w=175&h=246&fit=cover&output=webp"
            }
        }

    }
    private fun fixUrlImageLokLok(
        url: String?,
        isCoverHorizontal: Boolean = false
    ): String {
        return "$url${if (isCoverHorizontal) "?imageMogr2/gravity/Center/thumbnail/750x/crop/750x421/interlace/1/background/Z3JheQ==/ignore-error/1" else "?imageView2/1/w/380/h/532/format/webp/interlace/1/ignore-error/1/q/90!/format/webp"}"
    }
    fun LokLokSearchResponse.toListSearchResponse(): List<SearchResponse> {
        if (!data.searchResults.isNullOrEmpty()) {
            return data.searchResults.map {
                it.toMovieSearchResponse(name)
            }
        }
        return emptyList()
    }
    override suspend fun loadPage(param: String): PageResponse? {
        Log.d("Blue", "loadPage = " + param)
        var nextUrl: String? = null
        val list = if (param.contains('&')) {
            // case 1: see more
            val id = param.split("&").first()
            var page = if (param.split("&").size > 1) param.split("&").last().toInt() else 0
            val params = mapOf(
                "id" to id,
                "page" to page.toString(),
                "size" to "18",
            )
            val urlRequest = "${apiUrl}/album/detail"
            val response = app.get(urlRequest,params = params, headers = createHeaders(params)).text
            val homeResponse = Gson().fromJson<HomeResponse>(response, HomeResponse::class.java)
            val list = homeResponse.data.content.map {
                it.toMovieSearchResponse(name)
            }
            if (list.isNotEmpty()) {
                page++
            }
            nextUrl = "$id&${page}"
            list
        } else {
            //case 2: filter
            val keyFilter = param.split("*").first()
            val valueFilter = param.split("*")[1]
            var page = param.split("*").last()
            val url = "https://web-api.netpop.app/cms/web/pc/search/search"
            val jsonStringData =
                "{\"size\":18,\"sort\":\"${page}\",\"params\":\"${keyFilter}\",\"area\":\"\",\"category\":\"${valueFilter}\",\"year\":\"\",\"subtitles\":\"\",\"order\":\"up\"}"

            val body = mapOf(
                "area" to "",
                "category" to valueFilter,
                "order" to "up",
                "params" to keyFilter,
                "size" to "18",
                "sort" to page,
                "subtitles" to "",
                "year" to "",
            )
            val response =  app.post(
                url,
                requestBody = body.toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull()),
                headers = createHeaders(body)
            ).text
            val searchResponse =
                Gson().fromJson<LokLokSearchResponse>(response, LokLokSearchResponse::class.java)
            var listResult = searchResponse.toListSearchResponse()
            if (!listResult.isNullOrEmpty()) {
                nextUrl = "$keyFilter*$valueFilter*${searchResponse.data.searchResults.last().sort}"
            }
            listResult
        }
        return PageResponse(list, nextUrl)
    }
    fun HomeCategoryResponse.toListMoviesPage(nameApi: String): List<Page> {
        val result = arrayListOf<Page>()
        data.map { dataCategory ->
            if (dataCategory.id == 1) {  // id = 1 is Movie

                val listCategory =
                    dataCategory.screeningItems.find { it.id == 2 }  // id = 2 is genres

                val list = listCategory?.items?.filter {
                    it.params.isNotBlank()
                }
                list?.map { itemCategory ->
                    Page(
                        name = itemCategory.name,
                        url = "MOVIE,TVSPECIAL*${itemCategory.params}*",
                        isSelected = false,
                        nameApi = nameApi
                    )
                }?.let { result.addAll(it) }
            }
        }
        return result
    }
}

