package org.hdhmc.bilihdpager.core

import android.os.Bundle

internal interface RestorablePatch {
    fun restore()
}

internal class HdMultiPageFix(private val logger: HookLogger) {
    private val fullscreenCallbackSelectionApplied = ThreadLocal<Boolean>()

    fun redirectGlobalIndex(director: Any, item: Any?): Boolean {
        return try {
            logger.debug {
                "redirectGlobalIndex invoked: " +
                        "director=${Diagnostics.describeObject(director)} item=${Diagnostics.describeObject(item)}"
            }
            val video = director.getObjectFieldOrNull("c")
                ?: return ignored("redirectGlobalIndex ignored: director.c is missing")
            val videoList = director.getObjectFieldOrNull("a")
                ?: return ignored("redirectGlobalIndex ignored: director.a is missing")
            val requestedIndex = item?.callMethodOrNullAs<Int>("getIndex")
                ?: return ignored("redirectGlobalIndex ignored: item.getIndex() is unavailable")
            val currentCount = getItemCount(videoList, video)
                ?: return ignored("redirectGlobalIndex ignored: item count getter is unavailable")
            logger.debug {
                "redirectGlobalIndex state: requestedIndex=$requestedIndex " +
                        "currentVideoPageCount=$currentCount videoList=${Diagnostics.describeObject(videoList)}"
            }
            if (requestedIndex < currentCount) {
                return ignored {
                    "redirectGlobalIndex ignored: requestedIndex=$requestedIndex is inside current video pages"
                }
            }

            val target = findGlobalIndex(videoList, requestedIndex)
                ?: return ignored { "redirectGlobalIndex ignored: no target video for globalIndex=$requestedIndex" }
            if (target.video === video && target.localIndex == requestedIndex) {
                return ignored("redirectGlobalIndex ignored: target is unchanged")
            }
            val redirected = director.callMethodIfExists("Z", target.videoIndex, target.localIndex)
            if (redirected) {
                logger.debug {
                    "redirectGlobalIndex applied: globalIndex=$requestedIndex " +
                            "videoIndex=${target.videoIndex} localIndex=${target.localIndex}"
                }
            } else {
                logger.error(
                    "redirectGlobalIndex failed: method Z(videoIndex=${target.videoIndex}, " +
                            "localIndex=${target.localIndex}) returned false"
                )
            }
            redirected
        } catch (error: Throwable) {
            logger.error("Failed to redirect HD global page index", error)
            false
        }
    }

    fun playNestedPageAsNormalSource(
        callback: Any,
        normalSourceClass: Class<*>?,
    ): Boolean {
        return try {
            logger.debug {
                "playNestedPageAsNormalSource invoked: " +
                        "callback=${Diagnostics.describeObject(callback)} " +
                        "normalSource=${Diagnostics.describeClass(normalSourceClass)}"
            }
            val index = callback.getIntFieldOrNull("b")
                ?: return ignored("playNestedPageAsNormalSource ignored: callback.b is missing")
            val detail = callback.getObjectFieldOrNull("c")
                ?: return ignored("playNestedPageAsNormalSource ignored: callback.c is missing")
            if (normalSourceClass == null) {
                return ignored("playNestedPageAsNormalSource ignored: normal source class is missing")
            }

            val pageCount = (detail.getObjectFieldOrNull("mPageList") as? List<*>)?.size
                ?: return ignored("playNestedPageAsNormalSource ignored: detail.mPageList is missing")
            val ugcSeason = detail.getObjectFieldOrNull("ugcSeason")
                ?: return ignored("playNestedPageAsNormalSource ignored: detail.ugcSeason is missing")
            val episodeCount = ugcSeason.getIntFieldOrNull("episodeCount") ?: 0
            logger.debug {
                "playNestedPageAsNormalSource state: index=$index pageCount=$pageCount " +
                        "episodeCount=$episodeCount detail=${Diagnostics.describeObject(detail)}"
            }
            if (pageCount <= 1 || pageCount <= episodeCount || index !in 0 until pageCount) {
                return ignored {
                    "playNestedPageAsNormalSource ignored: not a nested HD page case " +
                            "(index=$index pageCount=$pageCount episodeCount=$episodeCount)"
                }
            }

            val player = callback.getObjectFieldOrNull("a")
                ?: return ignored("playNestedPageAsNormalSource ignored: callback.a is missing")
            val playerFragment = player.getObjectFieldOrNull("j")
                ?: return ignored("playNestedPageAsNormalSource ignored: player.j is missing")
            val source = normalSourceClass.newNoArgInstanceOrNull()
                ?: return failed("playNestedPageAsNormalSource failed: failed to create normal source")
            val bundle = player.callMethodOrNull("B1", null) as? Bundle ?: Bundle()
            if (!initializeNormalSource(source, detail, bundle, "playNestedPageAsNormalSource")) {
                return failed("playNestedPageAsNormalSource failed: normal source init returned false")
            }

            val callbackRemoved = player.getObjectFieldOrNull("f")?.callMethodIfExists("J", callback) == true
            logger.debug { "playNestedPageAsNormalSource callback removal: removed=$callbackRemoved" }
            val target = findGlobalIndex(source, index)
                ?: return failed("playNestedPageAsNormalSource failed: no target video for index=$index")
            player.setObjectField("x", index)
            if (!switchPlayerDataSource(playerFragment, source)) {
                return failed("playNestedPageAsNormalSource failed: playerFragment source switch returned false")
            }
            val refreshed = playerFragment.callMethodIfExists("xc")
            logger.debug { "playNestedPageAsNormalSource refresh: refreshed=$refreshed" }
            if (!selectPlayerPage(playerFragment, target.videoIndex, target.localIndex)) {
                return failed(
                    "playNestedPageAsNormalSource failed: playerFragment page select " +
                            "videoIndex=${target.videoIndex} localIndex=${target.localIndex} returned false"
                )
            }
            logger.debug {
                "playNestedPageAsNormalSource applied: index=$index " +
                        "videoIndex=${target.videoIndex} localIndex=${target.localIndex} pageCount=$pageCount"
            }
            true
        } catch (error: Throwable) {
            logger.error("Failed to play HD nested page as normal source", error)
            false
        }
    }

    fun patchNestedDownloadSections(detail: Any?): RestorablePatch? {
        return try {
            logger.debug { "patchNestedDownloadSections invoked: detail=${Diagnostics.describeObject(detail)}" }
            if (detail == null) {
                return ignoredPatch("patchNestedDownloadSections ignored: detail is null")
            }
            val pages = detail.getObjectFieldOrNull("mPageList") as? List<*>
                ?: return ignoredPatch("patchNestedDownloadSections ignored: detail.mPageList is missing")
            val ugcSeason = detail.getObjectFieldOrNull("ugcSeason")
                ?: return ignoredPatch("patchNestedDownloadSections ignored: detail.ugcSeason is missing")
            val episodeCount = ugcSeason.getIntFieldOrNull("episodeCount") ?: 0
            val oldSectionsObject = ugcSeason.getObjectFieldOrNull("sections")
            val oldSections = oldSectionsObject as? List<*>
            val pageCount = pages.size
            logger.debug {
                "patchNestedDownloadSections state: pageCount=$pageCount episodeCount=$episodeCount " +
                        "sectionCount=${oldSections?.size ?: -1}"
            }
            if (pageCount <= 1 || pageCount <= episodeCount) {
                return ignoredPatch {
                    "patchNestedDownloadSections ignored: not a nested HD page case " +
                            "(pageCount=$pageCount episodeCount=$episodeCount)"
                }
            }

            val loader = detail.javaClass.classLoader
            val sectionClass = oldSections.firstNonNullClass()
                ?: loader?.findClassOrNull("${Constants.VIDEO_DETAIL_CLASS}\$Section")
                ?: return ignoredPatch("patchNestedDownloadSections ignored: Section class is missing")
            val episodeClass = oldSections.firstEpisodeClass()
                ?: loader?.findClassOrNull("${Constants.VIDEO_DETAIL_CLASS}\$Episode")
                ?: return ignoredPatch("patchNestedDownloadSections ignored: Episode class is missing")
            val replacementSections = buildDownloadSections(detail, pages, sectionClass, episodeClass)
                ?: return ignoredPatch("patchNestedDownloadSections ignored: failed to synthesize sections")
            val oldEpisodeCount = ugcSeason.getIntFieldOrNull("episodeCount")
            ugcSeason.setObjectField("sections", replacementSections)
            if (oldEpisodeCount != null) {
                ugcSeason.setIntField("episodeCount", pages.size)
            }
            logger.debug {
                "patchNestedDownloadSections applied: sectionCount=${replacementSections.size} " +
                        "pageCount=${pages.size}"
            }
            object : RestorablePatch {
                override fun restore() {
                    runCatching {
                        ugcSeason.setObjectField("sections", oldSectionsObject)
                        if (oldEpisodeCount != null) {
                            ugcSeason.setIntField("episodeCount", oldEpisodeCount)
                        }
                    }.onFailure { error ->
                        logger.error("Failed to restore HD nested download sections", error)
                    }
                    logger.debug { "patchNestedDownloadSections restored" }
                }
            }
        } catch (error: Throwable) {
            logger.error("Failed to patch HD nested download sections", error)
            null
        }
    }

    fun useNormalDownloadProviderForNestedPages(
        controller: Any,
        detail: Any?,
        cid: Long,
        normalCore: Any?,
        normalProviderClass: Class<*>?,
    ): Boolean {
        return try {
            logger.debug {
                "useNormalDownloadProviderForNestedPages invoked: " +
                        "controller=${Diagnostics.describeObject(controller)} " +
                        "detail=${Diagnostics.describeObject(detail)} cid=$cid " +
                        "normalCore=${Diagnostics.describeObject(normalCore)} " +
                        "normalProvider=${Diagnostics.describeClass(normalProviderClass)}"
            }
            if (detail == null) {
                return ignored("useNormalDownloadProviderForNestedPages ignored: detail is null")
            }
            val pages = detail.getObjectFieldOrNull("mPageList") as? List<*>
                ?: return ignored("useNormalDownloadProviderForNestedPages ignored: detail.mPageList is missing")
            val ugcSeason = detail.getObjectFieldOrNull("ugcSeason")
                ?: return ignored("useNormalDownloadProviderForNestedPages ignored: detail.ugcSeason is missing")
            val episodeCount = ugcSeason.getIntFieldOrNull("episodeCount") ?: 0
            logger.debug {
                "useNormalDownloadProviderForNestedPages state: pageCount=${pages.size} " +
                        "episodeCount=$episodeCount " +
                        "detail=${Diagnostics.describeObject(detail)}"
            }
            if (pages.size <= 1 || pages.size <= episodeCount) {
                return ignored {
                    "useNormalDownloadProviderForNestedPages ignored: not a nested HD page case " +
                            "(pageCount=${pages.size} episodeCount=$episodeCount)"
                }
            }
            if (normalCore == null || normalProviderClass == null) {
                return ignored("useNormalDownloadProviderForNestedPages ignored: normal provider inputs are missing")
            }
            val activity = controller.getObjectFieldOrNull("a")
                ?: return ignored("useNormalDownloadProviderForNestedPages ignored: controller.a activity is missing")
            val normalProvider = normalProviderClass.newInstanceOrNull(activity, normalCore)
                ?: return failed("useNormalDownloadProviderForNestedPages failed: normal provider constructor unavailable")
            val patch = patchNestedDownloadSections(detail)
            val dialog = try {
                normalProvider.callMethodOrNull("c", detail, cid)
                    ?: normalProvider.callMethodOrNull("b", detail, cid)
            } finally {
                patch?.restore()
            } ?: return ignored("useNormalDownloadProviderForNestedPages ignored: normal provider returned no dialog")

            controller.setObjectField("c", normalProvider)
            controller.setObjectField("b", dialog)
            logger.debug {
                "useNormalDownloadProviderForNestedPages applied: provider=${Diagnostics.describeObject(normalProvider)} " +
                        "dialog=${Diagnostics.describeObject(dialog)} pageCount=${pages.size}"
            }
            true
        } catch (error: Throwable) {
            logger.error("Failed to use normal download provider for HD nested pages", error)
            false
        }
    }

    fun shouldUseNormalPageSelector(detail: Any?): Boolean {
        return try {
            logger.debug { "shouldUseNormalPageSelector invoked: detail=${Diagnostics.describeObject(detail)}" }
            if (detail == null) {
                return ignored("shouldUseNormalPageSelector ignored: detail is null")
            }
            val pages = detail.getObjectFieldOrNull("mPageList") as? List<*>
                ?: return ignored("shouldUseNormalPageSelector ignored: detail.mPageList is missing")
            val ugcSeason = detail.getObjectFieldOrNull("ugcSeason")
                ?: return ignored("shouldUseNormalPageSelector ignored: detail.ugcSeason is missing")
            val episodeCount = ugcSeason.getIntFieldOrNull("episodeCount") ?: 0
            val sections = ugcSeason.getObjectFieldOrNull("sections") as? List<*>
            val sectionEpisodeCount = sections.sumEpisodeCount()
            val seasonPageCount = maxOf(episodeCount, sectionEpisodeCount)
            logger.debug {
                "shouldUseNormalPageSelector state: pageCount=${pages.size} " +
                        "episodeCount=$episodeCount sectionEpisodeCount=$sectionEpisodeCount"
            }
            if (pages.size <= 1 || pages.size <= seasonPageCount) {
                return ignored {
                    "shouldUseNormalPageSelector ignored: not a nested HD page selector case " +
                            "(pageCount=${pages.size} seasonPageCount=$seasonPageCount)"
                }
            }
            logger.debug {
                "shouldUseNormalPageSelector applied: pageCount=${pages.size} " +
                        "seasonPageCount=$seasonPageCount"
            }
            true
        } catch (error: Throwable) {
            logger.error("Failed to detect HD nested page selector case", error)
            false
        }
    }

    fun shouldUseNormalVideoSource(detail: Any?): Boolean {
        val shouldUseNormal = shouldUseNormalPageSelector(detail)
        if (shouldUseNormal) {
            logger.debug { "shouldUseNormalVideoSource applied" }
        }
        return shouldUseNormal
    }

    fun replaceWrapperWithNormalSource(
        wrapper: Any,
        detail: Any?,
        bundle: Bundle?,
        normalSourceClass: Class<*>?,
    ): Boolean {
        return try {
            logger.debug {
                "replaceWrapperWithNormalSource invoked: wrapper=${Diagnostics.describeObject(wrapper)} " +
                        "detail=${Diagnostics.describeObject(detail)} " +
                        "normalSource=${Diagnostics.describeClass(normalSourceClass)}"
            }
            if (detail == null) {
                return ignored("replaceWrapperWithNormalSource ignored: detail is null")
            }
            if (!shouldUseNormalPageSelector(detail)) return false
            if (normalSourceClass == null) {
                return ignored("replaceWrapperWithNormalSource ignored: normal source class is missing")
            }
            val source = normalSourceClass.newNoArgInstanceOrNull()
                ?: return failed("replaceWrapperWithNormalSource failed: failed to create normal source")
            if (!initializeNormalSource(source, detail, bundle ?: Bundle(), "replaceWrapperWithNormalSource")) {
                return failed("replaceWrapperWithNormalSource failed: normal source init returned false")
            }
            wrapper.setObjectField("c", source)
            logger.debug {
                "replaceWrapperWithNormalSource applied: wrapper=${Diagnostics.describeObject(wrapper)} " +
                        "source=${Diagnostics.describeObject(source)}"
            }
            true
        } catch (error: Throwable) {
            logger.error("Failed to replace HD wrapper source", error)
            false
        }
    }

    fun normalPageSelectorItems(
        provider: Any?,
        playerService: Any?,
        normalSourceClass: Class<*>?,
        selectorItemClass: Class<*>?,
    ): List<Any>? {
        return try {
            logger.debug {
                "normalPageSelectorItems invoked: provider=${Diagnostics.describeObject(provider)} " +
                        "playerService=${Diagnostics.describeObject(playerService)} " +
                        "normalSource=${Diagnostics.describeClass(normalSourceClass)}"
            }
            val detail = provider?.callMethodOrNull("d")
                ?: return null.also { logger.debug { "normalPageSelectorItems ignored: provider.d() is null" } }
            val state = buildNormalSelectorState(detail, playerService, normalSourceClass, selectorItemClass)
                ?: return null
            logger.debug {
                "normalPageSelectorItems applied: itemCount=${state.items.size} currentIndex=${state.currentIndex}"
            }
            state.items
        } catch (error: Throwable) {
            logger.error("Failed to build HD normal page selector items", error)
            null
        }
    }

    fun normalPageSelectorIndex(
        provider: Any?,
        playerService: Any?,
        normalSourceClass: Class<*>?,
        selectorItemClass: Class<*>?,
    ): Int? {
        return try {
            val detail = provider?.callMethodOrNull("d") ?: return null
            val state = buildNormalSelectorState(detail, playerService, normalSourceClass, selectorItemClass)
                ?: return null
            logger.debug {
                "normalPageSelectorIndex applied: currentIndex=${state.currentIndex} itemCount=${state.items.size}"
            }
            state.currentIndex
        } catch (error: Throwable) {
            logger.error("Failed to build HD normal page selector index", error)
            null
        }
    }

    fun prepareFullscreenSeasonSelector(panel: Any, normalSourceType: Any?): Boolean {
        return try {
            logger.debug {
                "prepareFullscreenSeasonSelector invoked: panel=${Diagnostics.describeObject(panel)} " +
                        "normalSourceType=${Diagnostics.describeObject(normalSourceType)}"
            }
            val provider = panel.getObjectFieldOrNull("p")
                ?: return ignored("prepareFullscreenSeasonSelector ignored: panel.p is missing")
            val detail = provider.callMethodOrNull("d")
                ?: return ignored("prepareFullscreenSeasonSelector ignored: provider.d() is null")
            if (!shouldUseNormalPageSelector(detail)) {
                return false
            }
            if (normalSourceType == null) {
                return ignored("prepareFullscreenSeasonSelector ignored: TypeNormal is missing")
            }
            panel.setObjectField("q", normalSourceType)
            logger.debug { "prepareFullscreenSeasonSelector applied" }
            true
        } catch (error: Throwable) {
            logger.error("Failed to prepare HD fullscreen selector", error)
            false
        }
    }

    fun patchFullscreenSeasonSelector(
        panel: Any,
        normalSourceClass: Class<*>?,
        selectorItemClass: Class<*>?,
        normalSourceType: Any?,
    ): Boolean {
        return try {
            logger.debug {
                "patchFullscreenSeasonSelector invoked: panel=${Diagnostics.describeObject(panel)} " +
                        "normalSource=${Diagnostics.describeClass(normalSourceClass)}"
            }
            val provider = panel.getObjectFieldOrNull("p")
                ?: return ignored("patchFullscreenSeasonSelector ignored: panel.p is missing")
            val playerService = panel.getObjectFieldOrNull("m")
            val detail = provider.callMethodOrNull("d")
                ?: return ignored("patchFullscreenSeasonSelector ignored: provider.d() is null")
            val state = buildNormalSelectorState(detail, playerService, normalSourceClass, selectorItemClass)
                ?: return false

            if (normalSourceType != null) {
                panel.setObjectField("q", normalSourceType)
            }
            val adapter = panel.getObjectFieldOrNull("l")
                ?: return ignored("patchFullscreenSeasonSelector ignored: panel.l adapter is missing")
            if (normalSourceType != null) {
                adapter.setObjectField("f", normalSourceType)
            }
            if (playerService != null && switchPlayerDataSource(playerService, state.source)) {
                logger.debug {
                    "patchFullscreenSeasonSelector source sync applied: " +
                            "playerService=${Diagnostics.describeObject(playerService)} " +
                            "source=${Diagnostics.describeObject(state.source)}"
                }
            }
            adapter.callMethodIfExists("setItems", state.items)
            adapter.callMethodIfExists("n0", state.currentIndex)
            adapter.callMethodIfExists("notifyDataSetChanged")
            panel.getObjectFieldOrNull("e")
                ?.callMethodIfExists("setText", detail.getStringFieldOrEmpty("mTitle").ifBlank { "选集" })
            panel.getObjectFieldOrNull("g")
                ?.callMethodIfExists("setText", "${state.currentIndex + 1}/${state.items.size}")
            logger.debug {
                "patchFullscreenSeasonSelector applied: itemCount=${state.items.size} " +
                        "currentIndex=${state.currentIndex} adapter=${Diagnostics.describeObject(adapter)}"
            }
            true
        } catch (error: Throwable) {
            logger.error("Failed to patch HD fullscreen selector", error)
            false
        }
    }

    fun selectNormalPageFromFullscreenSelector(
        provider: Any?,
        playerService: Any?,
        index: Int,
        normalSourceClass: Class<*>?,
    ): Boolean {
        return try {
            logger.debug {
                "selectNormalPageFromFullscreenSelector invoked: " +
                        "provider=${Diagnostics.describeObject(provider)} " +
                        "playerService=${Diagnostics.describeObject(playerService)} index=$index " +
                        "normalSource=${Diagnostics.describeClass(normalSourceClass)}"
            }
            val detail = provider?.callMethodOrNull("d")
                ?: return ignored("selectNormalPageFromFullscreenSelector ignored: provider.d() is null")
            if (!shouldUseNormalPageSelector(detail)) return false
            if (playerService == null) {
                return ignored("selectNormalPageFromFullscreenSelector ignored: player service is missing")
            }
            if (normalSourceClass == null) {
                return ignored("selectNormalPageFromFullscreenSelector ignored: normal source class is missing")
            }
            val pages = detail.getObjectFieldOrNull("mPageList") as? List<*>
                ?: return ignored("selectNormalPageFromFullscreenSelector ignored: detail.mPageList is missing")
            if (index !in pages.indices) {
                return ignored {
                    "selectNormalPageFromFullscreenSelector ignored: index=$index out of range " +
                            "pageCount=${pages.size}"
                }
            }

            val source = normalSourceClass.newNoArgInstanceOrNull()
                ?: return failed("selectNormalPageFromFullscreenSelector failed: failed to create normal source")
            if (!initializeNormalSource(source, detail, Bundle(), "selectNormalPageFromFullscreenSelector")) {
                return failed("selectNormalPageFromFullscreenSelector failed: normal source init returned false")
            }
            val target = findGlobalIndex(source, index)
                ?: return failed("selectNormalPageFromFullscreenSelector failed: no target video for index=$index")
            if (!switchPlayerDataSource(playerService, source)) {
                return failed("selectNormalPageFromFullscreenSelector failed: playerService source switch returned false")
            }
            if (!selectPlayerPage(playerService, target.videoIndex, target.localIndex)) {
                return failed(
                    "selectNormalPageFromFullscreenSelector failed: playerService page select " +
                            "videoIndex=${target.videoIndex} localIndex=${target.localIndex} returned false"
                )
            }
            logger.debug {
                "selectNormalPageFromFullscreenSelector applied: index=$index " +
                        "videoIndex=${target.videoIndex} localIndex=${target.localIndex} pageCount=${pages.size}"
            }
            true
        } catch (error: Throwable) {
            logger.error("Failed to select HD normal page from fullscreen selector", error)
            false
        }
    }

    fun selectNormalPageFromFullscreenProvider(
        provider: Any?,
        playerService: Any?,
        index: Int,
        normalSourceClass: Class<*>?,
    ): Boolean {
        if (fullscreenCallbackSelectionApplied.get() == true) {
            fullscreenCallbackSelectionApplied.remove()
            logger.debug {
                "selectNormalPageFromFullscreenProvider skipped: already handled by fullscreen callback"
            }
            return true
        }
        return selectNormalPageFromFullscreenSelector(
            provider = provider,
            playerService = playerService,
            index = index,
            normalSourceClass = normalSourceClass,
        )
    }

    fun selectNormalPageFromFullscreenCallback(
        callback: Any?,
        index: Int,
        normalSourceClass: Class<*>?,
    ): Boolean {
        return try {
            logger.debug {
                "selectNormalPageFromFullscreenCallback invoked: " +
                        "callback=${Diagnostics.describeObject(callback)} index=$index"
            }
            val panel = callback?.getObjectFieldOrNull("a")
                ?: return ignored("selectNormalPageFromFullscreenCallback ignored: callback.a panel is missing")
            val provider = panel.getObjectFieldOrNull("p")
            val playerService = panel.getObjectFieldOrNull("m")
            val selected = selectNormalPageFromFullscreenSelector(
                provider = provider,
                playerService = playerService,
                index = index,
                normalSourceClass = normalSourceClass,
            )
            if (selected) {
                fullscreenCallbackSelectionApplied.set(true)
            }
            selected
        } catch (error: Throwable) {
            logger.error("Failed to select HD normal page from fullscreen callback", error)
            false
        }
    }

    fun clearFullscreenCallbackSelectionMarker() {
        fullscreenCallbackSelectionApplied.remove()
    }

    private fun buildDownloadSections(
        detail: Any,
        pages: List<*>,
        sectionClass: Class<*>,
        episodeClass: Class<*>,
    ): List<Any>? {
        val episodes = ArrayList<Any>(pages.size)
        val aid = detail.getLongFieldOrNull("mAvid") ?: 0L
        val bvid = detail.getStringFieldOrEmpty("mBvid")
        val cover = detail.getStringFieldOrEmpty("mCover")
        pages.forEachIndexed { index, page ->
            if (page == null) return@forEachIndexed
            val episode = episodeClass.newNoArgInstanceOrNull() ?: return null
            val cid = page.getLongFieldOrNull("mCid") ?: 0L
            val pageIndex = page.getIntFieldOrNull("mPage") ?: index + 1
            val title = page.getStringFieldOrEmpty("mTitle").ifBlank { "P$pageIndex" }
            episode.setLongField("aid", aid)
            episode.setObjectField("bvid", bvid)
            episode.setLongField("cid", cid)
            episode.setObjectField("coverUrl", cover)
            episode.setObjectField("dimension", page.getObjectFieldOrNull("mDimension"))
            episode.setLongField("id", if (cid != 0L) cid else pageIndex.toLong())
            episode.setIntField("pageIndex", pageIndex)
            episode.setObjectField("srcFrom", page.getStringFieldOrEmpty("mFrom"))
            episode.setObjectField("title", title)
            episodes += episode
        }
        if (episodes.isEmpty()) return null

        val section = sectionClass.newNoArgInstanceOrNull() ?: return null
        section.setObjectField("episodes", episodes)
        section.setLongField("id", 0L)
        section.setObjectField("title", detail.getStringFieldOrEmpty("mTitle"))
        runCatching { section.setLongField("type", 0L) }
        return listOf(section)
    }

    private fun buildNormalSelectorState(
        detail: Any,
        playerService: Any?,
        normalSourceClass: Class<*>?,
        selectorItemClass: Class<*>?,
    ): NormalSelectorState? {
        if (!shouldUseNormalPageSelector(detail)) return null
        if (normalSourceClass == null) {
            logger.debug { "buildNormalSelectorState ignored: normal source class is missing" }
            return null
        }
        val source = normalSourceClass.newNoArgInstanceOrNull()
            ?: return null.also { logger.debug { "buildNormalSelectorState ignored: failed to create normal source" } }
        if (!initializeNormalSource(source, detail, Bundle(), "buildNormalSelectorState")) {
            logger.debug { "buildNormalSelectorState ignored: normal source init returned false" }
            return null
        }
        val videoCount = getVideoCount(source)
            ?: return null.also {
                logger.debug { "buildNormalSelectorState ignored: source video count getter is unavailable" }
            }
        val items = ArrayList<Any>()
        for (videoIndex in 0 until videoCount) {
            val video = getVideo(source, videoIndex) ?: continue
            val count = getItemCount(source, video) ?: 0
            logger.debug {
                "buildNormalSelectorState video: videoIndex=$videoIndex itemCount=$count"
            }
            for (localIndex in 0 until count) {
                val item = getSelectorItem(source, video, localIndex) ?: continue
                if (selectorItemClass == null || selectorItemClass.isInstance(item)) {
                    items += item
                } else {
                    logger.debug {
                        "buildNormalSelectorState skipped non-selector item: ${Diagnostics.describeObject(item)}"
                    }
                }
            }
        }
        if (items.isEmpty()) {
            logger.debug { "buildNormalSelectorState ignored: no selector items built" }
            return null
        }
        val playerIndex = playerService?.callMethodOrNull("z0")
            ?.callMethodOrNullAs<Int>("a")
            ?: playerService?.callMethodOrNull("p0")
                ?.callMethodOrNullAs<Int>("a")
        val currentIndex = (playerIndex ?: 0).coerceIn(0, items.lastIndex)
        logger.debug {
            "buildNormalSelectorState built: itemCount=${items.size} playerIndex=$playerIndex " +
                    "currentIndex=$currentIndex"
        }
        return NormalSelectorState(source, items, currentIndex)
    }

    private fun findGlobalIndex(videoList: Any, index: Int): TargetIndex? {
        val videoCount = getVideoCount(videoList)
            ?: return null.also { logger.debug { "findGlobalIndex failed: video count getter is unavailable" } }
        logger.debug { "findGlobalIndex state: globalIndex=$index videoCount=$videoCount" }
        var offset = index
        for (videoIndex in 0 until videoCount) {
            val video = getVideo(videoList, videoIndex) ?: continue
            val count = getItemCount(videoList, video) ?: 0
            logger.debug { "findGlobalIndex item: videoIndex=$videoIndex pageCount=$count offset=$offset" }
            if (offset < count) return TargetIndex(video, videoIndex, offset)
            offset -= count
        }
        return null
    }

    private fun isModernNormalSource(source: Any): Boolean {
        return source.javaClass.name.startsWith("bs2.")
    }

    private fun getVideoCount(source: Any): Int? {
        return if (isModernNormalSource(source)) {
            source.callMethodOrNullAs<Int>("E") ?: source.callMethodOrNullAs<Int>("h1")
        } else {
            source.callMethodOrNullAs<Int>("h1") ?: source.callMethodOrNullAs<Int>("E")
        }
    }

    private fun getVideo(source: Any, index: Int): Any? {
        return if (isModernNormalSource(source)) {
            source.callMethodOrNullAs<Any>("C", index) ?: source.callMethodOrNullAs<Any>("c1", index)
        } else {
            source.callMethodOrNullAs<Any>("c1", index) ?: source.callMethodOrNullAs<Any>("C", index)
        }
    }

    private fun getItemCount(source: Any, video: Any): Int? {
        return if (isModernNormalSource(source)) {
            source.callMethodOrNullAs<Int>("o2", video) ?: source.callMethodOrNullAs<Int>("A1", video)
        } else {
            source.callMethodOrNullAs<Int>("A1", video) ?: source.callMethodOrNullAs<Int>("o2", video)
        }
    }

    private fun getSelectorItem(source: Any, video: Any, index: Int): Any? {
        return if (isModernNormalSource(source)) {
            source.callMethodOrNull("n2", video, index) ?: source.callMethodOrNull("z1", video, index)
        } else {
            source.callMethodOrNull("z1", video, index) ?: source.callMethodOrNull("n2", video, index)
        }
    }

    private fun initializeNormalSource(
        source: Any,
        detail: Any,
        bundle: Bundle,
        operation: String,
    ): Boolean {
        val initialized = source.callMethodIfExists("x2", detail, bundle) ||
                source.callMethodIfExists("B2", detail, bundle) ||
                source.callFirstMethodBySignatureIfExists(
                    listOf(detail.javaClass, Bundle::class.java),
                    detail,
                    bundle,
                )
        logger.debug { "$operation normal source init: initialized=$initialized source=${Diagnostics.describeObject(source)}" }
        return initialized
    }

    private fun switchPlayerDataSource(player: Any, source: Any): Boolean {
        return player.callMethodIfExists("lk", source) ||
                player.callMethodIfExists("Co", source) ||
                player.callMethodIfExists("k7", source) ||
                player.callMethodIfExists("d5", source)
    }

    private fun selectPlayerPage(player: Any, videoIndex: Int, localIndex: Int): Boolean {
        return player.callMethodIfExists("n0", videoIndex, localIndex, true) ||
                player.callMethodIfExists("F0", videoIndex, localIndex, true) ||
                player.callMethodIfExists("z0", videoIndex, localIndex)
    }

    private fun ignored(message: String): Boolean {
        logger.debug { message }
        return false
    }

    private inline fun ignored(message: () -> String): Boolean {
        logger.debug(message)
        return false
    }

    private fun ignoredPatch(message: String): RestorablePatch? {
        logger.debug { message }
        return null
    }

    private inline fun ignoredPatch(message: () -> String): RestorablePatch? {
        logger.debug(message)
        return null
    }

    private fun failed(message: String): Boolean {
        logger.error(message)
        return false
    }

    private data class TargetIndex(
        val video: Any,
        val videoIndex: Int,
        val localIndex: Int,
    )

    private data class NormalSelectorState(
        val source: Any,
        val items: List<Any>,
        val currentIndex: Int,
    )
}

private fun List<*>?.firstNonNullClass(): Class<*>? =
    this?.firstNotNullOfOrNull { it?.javaClass }

private fun List<*>?.firstEpisodeClass(): Class<*>? =
    this?.asSequence()
        ?.flatMap { section ->
            (section?.getObjectFieldOrNull("episodes") as? List<*>)?.asSequence() ?: emptySequence()
        }
        ?.firstNotNullOfOrNull { it?.javaClass }

private fun List<*>?.sumEpisodeCount(): Int =
    this?.sumOf { section ->
        (section?.getObjectFieldOrNull("episodes") as? List<*>)?.size ?: 0
    } ?: 0

private fun Any.getStringFieldOrEmpty(name: String): String =
    getObjectFieldOrNull(name) as? String ?: ""
