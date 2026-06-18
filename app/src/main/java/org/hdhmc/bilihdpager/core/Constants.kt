package org.hdhmc.bilihdpager.core

internal object Constants {
    const val TAG = "BiliHDPager"
    const val HD_PACKAGE_NAME = "tv.danmaku.bilibilihd"

    const val VIDEO_ITEM_CLASS = "tv.danmaku.biliplayerv2.service.g"
    const val VIDEO_CLASS = "tv.danmaku.biliplayerv2.service.Video"
    const val DIRECTOR_CLASS =
        "tv.danmaku.biliplayerimpl.videodirector.VideosPlayDirectorService"
    val NORMAL_SOURCE_CLASSES = listOf("sl2.c", "bs2.c")
    val SOURCE_WRAPPER_CLASSES = listOf("sl2.b", "bs2.b")
    val SOURCE_WRAPPER_INIT_METHODS = listOf("x2", "B2")
    const val VIDEO_DETAIL_HELPER_CLASS = "vr2.b"
    const val VIDEO_DETAIL_CLASS =
        "tv.danmaku.bili.videopage.data.view.model.BiliVideoDetail"
    const val DOWNLOAD_SEASON_CONTAINER_CLASS =
        "tv.danmaku.bili.ui.videodownload.download.h"
    const val DOWNLOAD_CALLBACK_CLASS =
        "tv.danmaku.bili.ui.videodownload.download.w\$h"
    const val DOWNLOAD_CLIENT_CLASS =
        "tv.danmaku.bili.ui.videodownload.download.m"
    val DOWNLOAD_CONTROLLER_CLASSES = listOf("ei2.c", "oo2.c")
    const val DOWNLOAD_NORMAL_CORE_CLASS =
        "tv.danmaku.bili.ui.videodownload.download.l"
    const val DOWNLOAD_SEASON_CORE_CLASS =
        "tv.danmaku.bili.ui.videodownload.download.j"
    const val DOWNLOAD_NORMAL_PROVIDER_CLASS =
        "tv.danmaku.bili.ui.videodownload.download.e"
    const val INTRODUCTION_DETAIL_FRAGMENT_CLASS =
        "tv.danmaku.bili.ui.video.videodetail.party.tab.introduction.IntroductionDetailFragment"
    val INTRODUCTION_PAGE_SELECTOR_METHODS = listOf("fu", "Ku", "Ir", "ns")
    val PLAYER_SERVICE_CLASSES = listOf(
        "tv.danmaku.biliplayerv2.service.m0",
        "tv.danmaku.biliplayerv2.service.n0",
    )
    val FULLSCREEN_SEASON_SELECTOR_CLASSES = listOf("yi2.g", "ip2.g")
    val FULLSCREEN_SELECTOR_CALLBACK_CLASSES = listOf("yi2.g\$d", "ip2.g\$d")
    val SELECTOR_DATA_PROVIDER_CLASSES = listOf("cm2.e", "ls2.e")
    val DIRECTOR_PLAY_ITEM_METHODS = listOf("R1", "e2")
    val FULLSCREEN_BIND_METHODS_BY_CLASS = mapOf(
        "yi2.g" to listOf("b0"),
        "ip2.g" to listOf("W"),
    )
    val FULLSCREEN_REFRESH_METHODS_BY_CLASS = mapOf(
        "yi2.g" to listOf("x0"),
        "ip2.g" to listOf("r0"),
    )
    const val SELECTOR_ITEM_CLASS = "tv.danmaku.bili.videopage.player.o"
    const val SOURCE_TYPE_CLASS = "tv.danmaku.bili.videopage.player.datasource.SourceType"
}
