package com.livetv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.phimhd.AppController
import com.phimhd.IPTV
import com.phimhd.ListIPTV
import com.phimhd.ResponseIPTV

class LiveTVProvider : MainAPI() {
    override var name = IPTV_API_NAME
    override var mainUrl = "https://ophimx.app/api/phimhd/iptv"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val hasChromecastSupport: Boolean
        get() = true
    override val supportedTypes: Set<TvType>
        get() = setOf(
            TvType.Live,
        )
    companion object{
        const val IPTV_API_NAME = "Bóng Đá & Tivi Online"

    }
    private fun ListIPTV.toHomeResponse(): HomePageResponse {
        val group = this.items.groupBy { item -> item.group?.title }
        val list = arrayListOf<HomePageList>()
        group.entries.forEach {
            val listItem = it.value.filter{iptv ->  iptv.url != null && iptv.url!!.contains(".m3u8") }.map { iptv -> iptv.toLiveTvSearchResponse() }
            if(!listItem.isNullOrEmpty()){
                list.add(
                    HomePageList(
                        name = it.key ?: "",
                        list = listItem,
                        isHorizontalImages = true
//                    cardType = ParentItemAdapter.CardType.TV_CARD
                    )
                )
            }

        }
        list.sortBy { it -> it.name }
        return HomePageResponse(items = list)
    }
    private fun IPTV.toLiveTvSearchResponse() : LiveTvSearchResponse {
        val map = mapOf<String,String>()
//    this.http?.referrer?.let {
//        map.plus(Pair("referrer",it))
//    }
        this.http?.userAgent?.let {
            map.plus(Pair("User-Agent",it))
        }
        val liveLoadResponse = LiveStreamLoadResponse(
            name = name ?: "",
            url = this.url ?: "",
            apiName = IPTV_API_NAME,
            dataUrl = this.url ?: "",
            posterUrl =  this.tvg?.logo,
            type = TvType.Live
        )
        return LiveTvSearchResponse(
            name= this.name ?: "",
            url = liveLoadResponse.toJson(),
            apiName =  IPTV_API_NAME ,
            TvType.Live,
            this.tvg?.logo ,
            0,
            headers = map
        )
    }
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        try {
            if(AppController.instance?.homePageLiveTv != null) {
                return AppController.instance?.homePageLiveTv
            }
            val listIPTV = app.get(mainUrl).parsedSafe<ResponseIPTV>()

//            val listIPTV = Gson().fromJson<ResponseIPTV>(response, ResponseIPTV::class.java)
            val array = arrayListOf<IPTV>()
            listIPTV?.bongda?.items?.let {
                array.addAll(it)
            }
            listIPTV?.iptv?.items?.let{
                array.addAll(it)
            }
            val list= ListIPTV(array)
            AppController.instance?.homePageLiveTv = list.toHomeResponse()
            return AppController.instance?.homePageLiveTv

        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    override suspend fun load(url: String): LoadResponse? {
        return parseJson<LiveStreamLoadResponse>(url)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback.invoke(ExtractorLink(source = data,name= IPTV_API_NAME, url = data, referer = "", isM3u8 = true,quality = getQualityFromName("720P")))
        return true
    }
}