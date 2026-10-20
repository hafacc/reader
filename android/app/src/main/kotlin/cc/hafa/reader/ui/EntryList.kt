package cc.hafa.reader.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.hafa.reader.R
import cc.hafa.reader.core.Entry
import cc.hafa.reader.core.ReaderState
import cc.hafa.reader.core.Selection
import cc.hafa.reader.core.ViewTitle
import cc.hafa.reader.core.articleTopics
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Name of a view in the reader's words. */
@Composable
fun viewTitleText(title: ViewTitle): String =
    when (title) {
        ViewTitle.AllItems -> stringResource(R.string.all_items)
        ViewTitle.Starred -> stringResource(R.string.starred)
        is ViewTitle.Named -> title.name
    }

/**
 * The article list: its bottom bar, the cards, and pulling down from the top
 * or up past the end to check the view's feeds, with a haptic tick each time
 * the pull crosses the point where letting go checks them; the pull past the
 * end works as pulling an article past its end does, and screen readers have
 * a Refresh action instead. As it shows, it scrolls the card of [reveal], the
 * id of the article just left, into full view when it is not already.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryList(snapshot: Snapshot, viewModel: ReaderViewModel, listState: LazyListState, onMenu: () -> Unit, reveal: String = "") {
    val (state, view) = snapshot
    val colors = ReaderTheme.colors
    val model = viewModel.model
    val feedTitles = remember(state.feeds) { state.feeds.associate { feed -> feed.id to feed.title } }
    val shownFeed = (state.selection as? Selection.FeedView)?.let { chosen -> state.feeds.firstOrNull { feed -> feed.id == chosen.id } }
    val latestView by rememberUpdatedState(view)
    val problem = shownFeed?.error ?: ""
    val bannerRows = if (problem.isNotEmpty()) 1 else 0
    LaunchedEffect(reveal, listState) {
        val row = latestView.visible.indexOfFirst { entry -> entry.id == reveal }
        if (row != -1) {
            val info = snapshotFlow { listState.layoutInfo }.first { layout -> layout.totalItemsCount > 0 }
            val whole = info.visibleItemsInfo.filter { item ->
                item.offset >= info.viewportStartOffset && item.offset + item.size <= info.viewportEndOffset - info.afterContentPadding
            }
            val target = revealIndex(row + bannerRows, whole.firstOrNull()?.index ?: 0, whole.lastOrNull()?.index ?: -1)
            if (target != null) {
                listState.scrollToItem(target)
            }
        }
    }
    val refreshing = viewModel.refreshing(state)
    val latestRefreshing by rememberUpdatedState(refreshing)
    // Whether the refresh under way came from a pull past the end, so its indicator turns there rather than at the top.
    var refreshFromEnd by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) {
        if (!refreshing) {
            refreshFromEnd = false
        }
    }
    val endPull = rememberEdgeDrag(Orientation.Vertical, fromEdgeOnly = true, pastEndOnly = true, staysOnRelease = false) {
        viewModel.refreshView()
        refreshFromEnd = true
    }
    // Not while feeds are checked, when the indicator is already turning at the end or the top.
    SideEffect { endPull.enabled = !refreshing }
    val refreshLabel = stringResource(R.string.refresh)
    val pullState = rememberPullToRefreshState()
    // Whether a finger is on the list, so the indicator moving on its own (a refresh starting or ending) does not tick.
    var touching by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(pullState, haptics) {
        snapshotFlow { pullState.distanceFraction >= 1f }.drop(1).collect {
            if (touching && !latestRefreshing) {
                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            }
        }
    }
    // Outside the bar's own handling, so a pull past the end does not count as scrolling that hides the bar.
    Box(Modifier.fillMaxSize().nestedScroll(endPull)) {
        ReaderScaffold(
            bottomBar = { scrollBehavior ->
                ReaderBottomBar(scrollBehavior) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        ViewButton(viewTitleText(view.viewTitle), state, onMenu)
                    }
                    IconAction(
                        if (state.unreadOnly) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        stringResource(R.string.unread_only),
                        { model.setUnreadOnly(!state.unreadOnly) },
                        tint = if (state.unreadOnly) colors.accent else colors.muted,
                        pressed = state.unreadOnly,
                    )
                    IconAction(Icons.Outlined.Tune, stringResource(R.string.settings), viewModel::toggleSettings)
                    IconAction(Icons.Outlined.DoneAll, stringResource(R.string.mark_all_read), model::markAllRead, enabled = view.canMarkAllRead)
                }
            },
        ) { padding ->
            val layoutDirection = LocalLayoutDirection.current
            val topRefreshing = refreshing && !refreshFromEnd
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding())
                    // Pulling does nothing while feeds are checked; a refresh from the end leaves the top indicator hidden.
                    .pullToRefresh(topRefreshing, pullState, enabled = !refreshing, onRefresh = viewModel::refreshView)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            touching = true
                            try {
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                } while (event.changes.any { change -> change.pressed })
                            } finally {
                                touching = false
                            }
                        }
                    },
            ) {
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationY = endPull.offset }
                        .semantics {
                            customActions = listOf(
                                CustomAccessibilityAction(refreshLabel) {
                                    viewModel.refreshView()
                                    true
                                },
                            )
                        },
                    state = listState,
                    contentPadding = PaddingValues(
                        start = padding.calculateStartPadding(layoutDirection),
                        end = padding.calculateEndPadding(layoutDirection),
                        bottom = scrollEndPadding(),
                    ),
                ) {
                    if (bannerRows > 0) {
                        item(key = "problem") { FeedProblemBanner(problemText(problem)) }
                    }
                    items(view.visible, key = { entry -> entry.id }) { entry ->
                        EntryCard(
                            entry = entry,
                            read = state.shownRead(entry),
                            source = feedTitles[entry.feedId] ?: "",
                            topics = remember(entry.categories, view.topicOrder) { articleTopics(entry.categories, view.topicOrder) },
                            active = entry.id == state.currentId,
                            onOpen = { viewModel.openEntry(entry.id) },
                            onToggleRead = { model.toggleReadFor(entry.id) },
                            onStar = { model.toggleStarFor(entry.id) },
                        )
                    }
                    if (view.visible.isEmpty()) {
                        item(key = "empty") {
                            Column(
                                Modifier
                                    .fillParentMaxSize()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(stringResource(R.string.no_entries), fontSize = 14.sp, lineHeight = 20.sp, color = colors.muted)
                            }
                        }
                    }
                }
                PullToRefreshDefaults.Indicator(pullState, topRefreshing, Modifier.align(Alignment.TopCenter))
            }
            PullIndicator(endPull, padding, stringResource(R.string.release_to_refresh), spinning = refreshing && refreshFromEnd)
        }
    }
}

/**
 * The list bar's button that opens the feed list: the logo, which carries the
 * due dot and broadcasts while feeds are checked, the view's [title], cut short
 * with an ellipsis when long, and a small chevron; the web's in `entry-list.svelte`.
 */
@Composable
private fun ViewButton(title: String, state: ReaderState, onMenu: () -> Unit) {
    val colors = ReaderTheme.colors
    val suggested = refreshSuggested(state)
    val suggestion = stringResource(R.string.refresh_suggested)
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = stringResource(R.string.show_feeds), role = Role.Button, onClick = onMenu)
            .semantics {
                if (suggested) {
                    stateDescription = suggestion
                }
            }
            .padding(start = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WithDueDot(suggested) {
            Logo(Modifier.size(28.dp), loading = !state.loaded || state.refreshing || state.adding, labeled = false)
        }
        Text(
            title,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(start = 8.dp, end = 4.dp)
                .semantics { heading() },
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            Icons.Outlined.UnfoldMore,
            contentDescription = null,
            tint = colors.muted,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** Why the feed on show cannot be checked, at the top of its list. */
@Composable
private fun FeedProblemBanner(text: String) {
    val colors = ReaderTheme.colors
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(text, fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onErrorContainer)
        }
        HorizontalDivider(color = colors.line)
    }
}

/** When an article was posted: the time today, else the month and day. */
private fun age(published: Long): String {
    val zone = ZoneId.systemDefault()
    val moment = Instant.ofEpochMilli(published).atZone(zone)
    val locale = Locale.getDefault()
    val pattern = if (moment.toLocalDate() == LocalDate.now(zone)) {
        DateFormat.getBestDateTimePattern(locale, "HHmm")
    } else {
        DateFormat.getBestDateTimePattern(locale, "MMMd")
    }
    return DateTimeFormatter.ofPattern(pattern, locale).format(moment)
}

/** One article in the list; swiping it sideways marks it read or unread. */
@Composable
private fun EntryCard(
    entry: Entry,
    read: Boolean,
    source: String,
    topics: List<String>,
    active: Boolean,
    onOpen: () -> Unit,
    onToggleRead: () -> Unit,
    onStar: () -> Unit,
) {
    val colors = ReaderTheme.colors
    val readIcon = if (read) Icons.AutoMirrored.Outlined.Undo else Icons.Outlined.Check
    val hasImage = entry.imageUrl.isNotEmpty()
    val swipe = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    val latestToggleRead by rememberUpdatedState(onToggleRead)
    // SwipeToDismissBox calls this again whenever it is a new lambda while the card is still out; toggling recomposes
    // the card, so a fresh lambda on each pass would flip the mark back and forth until the card is back in place.
    val onDismiss: (SwipeToDismissBoxValue) -> Unit = remember(swipe, scope) {
        { _ ->
            latestToggleRead()
            scope.launch { swipe.reset() }
        }
    }
    Column {
        SwipeToDismissBox(
            state = swipe,
            onDismiss = onDismiss,
            backgroundContent = {
                Row(
                    Modifier
                        .fillMaxSize()
                        .background(colors.accentSoft)
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = if (swipe.dismissDirection == SwipeToDismissBoxValue.EndToStart) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(readIcon, null, tint = colors.accent)
                }
            },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(if (active) colors.accentSoft else colors.page)
                    .clickable(onClick = onOpen)
                    .padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 2.dp),
            ) {
                Row(
                    Modifier.padding(end = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (entry.dated) "$source · ${age(entry.published)}" else source,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp, lineHeight = 16.sp,
                            color = colors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            entry.title,
                            fontWeight = FontWeight.SemiBold,
                            color = if (read && !active) colors.read else colors.text,
                            lineHeight = 21.sp,
                        )
                        if (entry.excerpt.isNotEmpty()) {
                            Text(
                                entry.excerpt,
                                fontSize = 14.sp, lineHeight = 20.sp,
                                color = colors.muted,
                                maxLines = if (hasImage) 2 else 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (hasImage) {
                        AsyncImage(
                            model = entry.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .size(64.dp)
                                .clip(RoundedCornerShape(6.dp)),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        topics.joinToString(" · "),
                        modifier = Modifier.weight(1f),
                        fontSize = 12.sp, lineHeight = 16.sp,
                        color = colors.read,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconAction(
                        if (entry.starred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        stringResource(R.string.star),
                        onStar,
                        tint = if (entry.starred) colors.star else colors.muted,
                        iconSize = 20.dp,
                        pressed = entry.starred,
                    )
                    IconAction(
                        readIcon,
                        stringResource(if (read) R.string.mark_unread else R.string.mark_read),
                        onToggleRead,
                        iconSize = 20.dp,
                    )
                }
            }
        }
        HorizontalDivider(color = colors.line)
    }
}
