package com.nuvio.app.features.livetv.ui.sports

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.livetv.sports.SportsListingSource
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.livetv_sports_listing_both
import nuvio.composeapp.generated.resources.livetv_sports_listing_guide
import nuvio.composeapp.generated.resources.livetv_sports_listing_help
import nuvio.composeapp.generated.resources.livetv_sports_listing_schedule
import nuvio.composeapp.generated.resources.livetv_sports_listing_title
import org.jetbrains.compose.resources.stringResource

/** Listing choice only: the TV guide continues to supply channels for scheduled events. */
@Composable
internal fun SportsListingSourceChoices(
    selected: SportsListingSource,
    onSelect: (SportsListingSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    Column(modifier, verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6)) {
        Text(stringResource(Res.string.livetv_sports_listing_title), style = MaterialTheme.typography.labelLarge, color = tokens.colors.textPrimary)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
        ) {
            listOf(
                SportsListingSource.SCHEDULE to stringResource(Res.string.livetv_sports_listing_schedule),
                SportsListingSource.GUIDE to stringResource(Res.string.livetv_sports_listing_guide),
                SportsListingSource.BOTH to stringResource(Res.string.livetv_sports_listing_both),
            ).forEach { (source, label) ->
                Chip(label = label, selected = selected == source, onClick = { onSelect(source) })
            }
        }
        Text(stringResource(Res.string.livetv_sports_listing_help), style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted)
    }
}
