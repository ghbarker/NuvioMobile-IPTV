package com.nuvio.app.features.livetv.ondemand

import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.model.ChannelNameEditor
import com.nuvio.app.features.livetv.model.LiveTvSettings
import com.nuvio.app.features.livetv.model.LiveUserState

/** Group presentation never changes the provider names used by queries and parental controls. */
internal object OnDemandGroups {
    /** Visible groups in this profile's order, retaining their raw names and stable ids. */
    fun visible(categories: List<VodCategory>, user: LiveUserState): List<VodCategory> {
        val order = user.vodGroupOrder.withIndex().associate { (i, uid) -> uid to i }
        return categories
            .filter { !user.hidesVodCategory(it.uid) }
            .withIndex()
            .sortedWith(compareBy<IndexedValue<VodCategory>>({ order[it.value.uid] ?: Int.MAX_VALUE }, { it.index }))
            .map { it.value }
    }

    fun name(category: VodCategory, user: LiveUserState, settings: LiveTvSettings): String =
        displayName(category.name, user.vodGroupNames[category.uid], settings.nameRemovals, settings.cleanVodGroupNames)

    /** Manual names win, including when they happen to equal the automatic result. */
    fun displayName(rawName: String, customName: String?, removals: String, enabled: Boolean): String =
        customName?.takeIf { it.isNotBlank() }
            ?: if (enabled) ChannelNameEditor.clean(rawName, ChannelNameEditor.terms(removals)).ifBlank { rawName }
            else rawName

    /** The sort for a group (or for "all" with [uid] null): yours for that group, else the default sort. */
    fun sortFor(uid: String?, user: LiveUserState, settings: LiveTvSettings): VodSort =
        uid?.let { VodSort.parse(user.vodSorts[it]) } ?: VodSort.parse(settings.vodDefaultSort) ?: VodSort.DEFAULT

    fun hide(uid: String) = LiveTvPreferences.updateVodHiddenCategories(show = emptySet(), hide = setOf(uid))
    fun show(uid: String) = LiveTvPreferences.updateVodHiddenCategories(show = setOf(uid), hide = emptySet())
}
