package cc.hafa.reader.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.BottomAppBarScrollBehavior
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.hafa.reader.R
import cc.hafa.reader.core.ArticleFilters
import cc.hafa.reader.core.Entry
import cc.hafa.reader.core.ViewSettings
import cc.hafa.reader.core.decodeHtmlEntities
import cc.hafa.reader.core.sectionOff
import cc.hafa.reader.core.showsLeadImage
import cc.hafa.reader.core.topicOff
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ARTICLE_WIDTH = 680.dp
private val ARTICLE_MARGIN = 20.dp
// room between articles while one slides over the next
private val PAGE_GAP = 16.dp

private fun isWebLink(link: String): Boolean = link.startsWith("https://")

/** Opens the share sheet for [entry]'s link; false when nothing on the device can share it. */
private fun share(context: Context, entry: Entry): Boolean {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, entry.title)
        putExtra(Intent.EXTRA_TEXT, entry.url)
    }
    return try {
        context.startActivity(Intent.createChooser(send, null))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/** An article's date and time as the web shows it, such as "Oct 12, 2026, 08:06", with the locale's month names. */
fun articleDate(published: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofPattern("MMM d, yyyy, HH:mm", locale).format(Instant.ofEpochMilli(published).atZone(zone))

/**
 * The open article: its details, actions and body. Swiping sideways slides to
 * the next or previous article in the list's order, which then counts as opened;
 * pulling well past the top or end, starting there, or sideways past the
 * first or last article, closes it: the article follows the pull less and
 * less the further it goes, and an arrow in the gap turns over, with a tick
 * each time the pull crosses the point where letting go would close it. Closing goes
 * through [onClose] with which way a drag sent the article (a unit step), or
 * zero for Back; while [leaving], it is on its way out and closes nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleScreen(snapshot: Snapshot, viewModel: ReaderViewModel, leaving: Boolean, onClose: (Offset) -> Unit) {
    val (state, view) = snapshot
    val close by rememberUpdatedState(onClose)
    val entry = view.current
    val colors = ReaderTheme.colors
    // The screen can open before the list knows its article, so only an article that was shown and then went (removed by sync) leaves it.
    var shown by remember { mutableStateOf(false) }
    if (entry != null) {
        shown = true
    }
    LaunchedEffect(entry == null && shown && !leaving) {
        if (entry == null && shown && !leaving) {
            close(Offset.Zero)
        }
    }
    val context = LocalContext.current
    LaunchedEffect(state.aheadPictures) {
        val loader = SingletonImageLoader.get(context)
        val requests = state.aheadPictures.map { address -> loader.enqueue(ImageRequest.Builder(context).data(address).build()) }
        try {
            awaitCancellation()
        } finally {
            requests.forEach { request -> request.dispose() }
        }
    }
    val scrollBehavior = BottomAppBarDefaults.exitAlwaysScrollBehavior()
    val pull = rememberEdgeDrag(Orientation.Vertical, fromEdgeOnly = true) { towards -> close(towards) }
    val sideways = rememberEdgeDrag(Orientation.Horizontal, fromEdgeOnly = false) { towards -> close(towards) }
    val filters = remember(entry, view.filters) { entry?.let(view.filters::of) }
    var filtersOpen by remember { mutableStateOf(false) }
    // Another article closes the sheet the last one had open.
    LaunchedEffect(entry?.id) { filtersOpen = false }
    // Outside the bar's own handling, so a pull past the end does not count as scrolling that hides the bar.
    Box(Modifier.fillMaxSize().nestedScroll(pull)) {
        ReaderScaffold(
            bottomBar = {
                ReaderBottomBar(scrollBehavior) {
                    BackButton { close(Offset.Zero) }
                    Spacer(Modifier.weight(1f))
                    if (entry != null) {
                        if (filters != null && !filters.isEmpty) {
                            FilterButton(off = filters.anyOff(view.viewSettings), open = filtersOpen) { filtersOpen = true }
                        }
                        ArticleBarActions(entry, state.shownRead(entry), viewModel)
                    }
                }
            },
            scrollBehavior = scrollBehavior,
        ) { padding ->
            if (entry != null) {
                val rows = view.visible
                val index = rows.indexOfFirst { row -> row.id == entry.id }
                val pageModifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding())
                    .graphicsLayer { translationY = pull.offset }
                if (index == -1) {
                    // An article the list no longer holds, such as one a filter just hid, stays open on its own.
                    ArticlePage(entry, snapshot, viewModel, padding, scrollBehavior, pageModifier)
                } else {
                    ArticlePager(rows, index, snapshot, viewModel, padding, scrollBehavior, sideways, pageModifier)
                }
                val release = stringResource(R.string.release_to_close)
                PullIndicator(pull, padding, release)
                PullIndicator(sideways, padding, release)
            }
        }
    }
    if (filtersOpen && filters != null && !filters.isEmpty) {
        FilterSheet(filters, view.viewSettings, viewModel) { filtersOpen = false }
    }
}

/** The bottom bar's filter button, with a dot while any of the article's chips is off; tinted while its sheet is [open]. */
@Composable
private fun FilterButton(off: Boolean, open: Boolean, onClick: () -> Unit) {
    val colors = ReaderTheme.colors
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (open) colors.accentSoft else Color.Transparent,
            contentColor = if (open) colors.accent else colors.muted,
        ),
    ) {
        WithDueDot(off) {
            Icon(
                Icons.Outlined.FilterList,
                contentDescription = stringResource(if (off) R.string.filters_some_off else R.string.filters),
            )
        }
    }
}

/**
 * The open article's filters in a sheet over the dimmed article: its sections, then one row of topics per group.
 * Dragging it down, tapping outside it or Back closes it through [onDismiss].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(filters: ArticleFilters, settings: ViewSettings, viewModel: ReaderViewModel, onDismiss: () -> Unit) {
    val colors = ReaderTheme.colors
    val model = viewModel.model
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.panel,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
            Row(
                Modifier.padding(bottom = 8.dp).semantics { heading() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.FilterList, contentDescription = null, tint = colors.muted)
                Text(stringResource(R.string.filters), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FilterRows(
                    sections = filters.sections.map { section ->
                        FilterChipItem(section.place, section.label, null, sectionOff(settings.offTopics, section.place)) {
                            model.toggleSection(section.place, fromArticle = true)
                        }
                    },
                    topics = filters.topics.map { row ->
                        row.map { topic ->
                            FilterChipItem(topic.trim().lowercase(), topic, null, topicOff(settings, topic)) {
                                model.toggleTopic(topic, fromArticle = true)
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * The list's articles side by side, on the one at [index]; settling on another
 * opens it. Dragging on past the first or last article is [sideways], which
 * moves it and far enough closes it, as stepping past either end does. The bottom bar comes back whenever the page changes, so an article
 * too short to scroll never leaves it hidden.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticlePager(
    rows: List<Entry>,
    index: Int,
    snapshot: Snapshot,
    viewModel: ReaderViewModel,
    padding: PaddingValues,
    scrollBehavior: BottomAppBarScrollBehavior,
    sideways: EdgeDrag,
    modifier: Modifier,
) {
    val pagerState = rememberPagerState(initialPage = index) { rows.size }
    val latestRows by rememberUpdatedState(rows)
    LaunchedEffect(pagerState) {
        var previous = pagerState.settledPage
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val settled = latestRows.getOrNull(page)
            if (settled != null && settled.id != viewModel.model.state.value.currentId) {
                viewModel.openEntry(settled.id, heading = if (page < previous) -1 else 1)
            }
            previous = page
        }
    }
    LaunchedEffect(pagerState, scrollBehavior) {
        snapshotFlow { pagerState.currentPage }.drop(1).collectLatest { scrollBehavior.showBar() }
    }
    // The list can change under the open article (sync, a refresh); keep showing that article.
    LaunchedEffect(index) {
        if (!pagerState.isScrollInProgress && pagerState.currentPage != index) {
            pagerState.scrollToPage(index)
        }
    }
    HorizontalPager(
        state = pagerState,
        modifier = modifier
            .nestedScroll(sideways)
            .graphicsLayer { translationX = sideways.offset },
        pageSpacing = PAGE_GAP,
        key = { page -> rows[page].id },
    ) { page ->
        // Fading as it slides, so the article coming in reads as taking the place of the one going out.
        val pageModifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = pageAlpha(pagerState.getOffsetDistanceInPages(page)) }
        ArticlePage(rows[page], snapshot, viewModel, padding, scrollBehavior.takeIf { page == pagerState.currentPage }, pageModifier)
    }
}

/**
 * One article's details, actions and body; the body shows once it is the open
 * article and has loaded. On the page in view, [scrollBehavior] is the bottom
 * bar's, kept shown while the article is at its top or end.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticlePage(
    entry: Entry,
    snapshot: Snapshot,
    viewModel: ReaderViewModel,
    padding: PaddingValues,
    scrollBehavior: BottomAppBarScrollBehavior?,
    modifier: Modifier,
) {
    val (state, view) = snapshot
    val listState = rememberLazyListState()
    // A neighbor's body is ready before it is opened, so swiping onto it shows it at once.
    val shownBody = if (state.bodyId == entry.id) state.body else state.aheadBodies[entry.id]
    if (scrollBehavior != null) {
        // At the top or the end, including an article too short to scroll, the bar slides back and stays shown.
        LaunchedEffect(listState, scrollBehavior) {
            snapshotFlow { !(listState.canScrollForward && listState.canScrollBackward) }.collectLatest { atEdge ->
                if (atEdge) {
                    // Again whenever the bar's own settling after a fling moves it off.
                    snapshotFlow { scrollBehavior.state.heightOffset != 0f }.collect { hidden ->
                        if (hidden) {
                            scrollBehavior.showBar()
                        }
                    }
                }
            }
        }
    }
    val colors = ReaderTheme.colors
    // Null until the body is split, so the lead image does not appear and then jump away.
    val split by produceState<Pair<List<HtmlBlock>, Boolean>?>(null, shownBody, entry.imageUrl) {
        value = if (shownBody != null) {
            withContext(Dispatchers.Default) {
                htmlBlocks(shownBody) to showsLeadImage(entry.imageUrl, shownBody)
            }
        } else {
            null
        }
    }
    val blocks = split?.first ?: emptyList()
    val showImage = split?.second == true
    val layoutDirection = LocalLayoutDirection.current
    BoxWithConstraints(modifier) {
        // Wide screens keep lines to a readable length, centered, with the whole width still scrolling.
        val side = maxOf(ARTICLE_MARGIN, (maxWidth - ARTICLE_WIDTH) / 2)
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection) + side,
                end = padding.calculateEndPadding(layoutDirection) + side,
                top = 24.dp,
                bottom = scrollEndPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "head") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val title = remember(entry.title, entry.url, colors) {
                        if (entry.url.isEmpty()) {
                            AnnotatedString(entry.title)
                        } else {
                            // A link annotation, so screen readers announce the title as a link.
                            buildAnnotatedString {
                                withLink(LinkAnnotation.Url(entry.url, TextLinkStyles(SpanStyle(color = colors.text)))) {
                                    append(entry.title)
                                }
                            }
                        }
                    }
                    Text(
                        title,
                        fontFamily = ReaderTheme.readingFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 30.sp,
                        lineHeight = 36.sp,
                        style = LocalTextStyle.current.copy(lineBreak = LineBreak.Heading),
                    )
                    val dateText = if (entry.dated) articleDate(entry.published) else ""
                    val feedTitle = state.feeds.firstOrNull { feed -> feed.id == entry.feedId }?.title ?: ""
                    val meta = listOf(feedTitle, entry.author, dateText).filter { part -> part.isNotEmpty() }.joinToString(" · ")
                    Text(
                        meta,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp, lineHeight = 16.sp,
                        color = colors.muted,
                    )
                }
            }
            if (showImage) {
                item(key = "image") {
                    NaturalSizeImage(
                        model = decodeHtmlEntities(entry.imageUrl),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 384.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                }
            }
            items(blocks) { block -> BodyBlock(block) }
        }
    }
}

/** Star, read, share and open the original, for the bottom bar; [read] is the read mark the article shows. */
@Composable
private fun RowScope.ArticleBarActions(entry: Entry, read: Boolean, viewModel: ReaderViewModel) {
    val colors = ReaderTheme.colors
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    IconAction(
        if (entry.starred) Icons.Filled.Star else Icons.Outlined.StarOutline,
        stringResource(R.string.star),
        viewModel.model::toggleStar,
        tint = if (entry.starred) colors.star else colors.muted,
        pressed = entry.starred,
    )
    IconAction(
        if (read) Icons.AutoMirrored.Outlined.Undo else Icons.Outlined.Check,
        stringResource(if (read) R.string.mark_unread else R.string.mark_read),
        viewModel.model::toggleRead,
    )
    if (entry.url.isNotEmpty()) {
        IconAction(Icons.Outlined.Share, stringResource(R.string.share), {
            if (!share(context, entry)) {
                viewModel.notify(Notice.Text(R.string.share_failed))
            }
        })
        IconAction(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.open_original), { uriHandler.openUri(entry.url) })
    }
}

@Composable
private fun BodyBlock(block: HtmlBlock) {
    val colors = ReaderTheme.colors
    val uriHandler = LocalUriHandler.current
    when (block) {
        is HtmlBlock.Text -> {
            val links = TextLinkStyles(SpanStyle(color = colors.accent))
            val text = remember(block.html, links) { AnnotatedString.fromHtml(block.html, linkStyles = links) }
            val quoted = block.quoteDepth > 0 || block.kind == TextKind.Caption
            val style = bodyStyle(block, ReaderTheme.readingFamily)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
            ) {
                if (quoted) {
                    Box(
                        Modifier
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(colors.line),
                    )
                }
                Text(
                    if (block.prefix.isEmpty()) text else AnnotatedString(block.prefix) + text,
                    modifier = Modifier.padding(start = if (quoted) 12.dp else 0.dp),
                    style = style,
                    color = if (block.kind == TextKind.Body && block.quoteDepth == 0 || block.kind == TextKind.Heading) {
                        colors.text
                    } else {
                        colors.muted
                    },
                )
            }
        }
        is HtmlBlock.Table -> BodyTable(block)
        is HtmlBlock.Code -> Text(
            block.text,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(colors.side)
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            softWrap = false,
        )
        is HtmlBlock.Image -> NaturalSizeImage(
            model = block.source,
            contentDescription = block.alt.ifEmpty { null },
            contentScale = ContentScale.FillWidth,
            declaredWidth = block.declaredWidth,
            density = block.density,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .then(if (isWebLink(block.link)) Modifier.clickable { uriHandler.openUri(block.link) } else Modifier),
        )
        HtmlBlock.Rule -> HorizontalDivider(color = colors.line)
    }
}

// Sizes and weights of Tailwind's prose styles, which the web article uses.
private val HEADING_STYLES = listOf(
    TextStyle(fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold),
    TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    TextStyle(fontSize = 20.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
)

private fun bodyStyle(block: HtmlBlock.Text, family: FontFamily): TextStyle {
    val base = TextStyle(fontFamily = family)
    return when (block.kind) {
        TextKind.Heading -> base.merge(HEADING_STYLES[(block.level - 1).coerceIn(0, HEADING_STYLES.size - 1)])
        TextKind.Caption -> base.copy(fontSize = 14.sp, lineHeight = 20.sp, fontStyle = FontStyle.Italic)
        TextKind.FigureCaption -> base.copy(fontSize = 14.sp, lineHeight = 20.sp)
        TextKind.Body -> base.copy(fontSize = 16.sp, lineHeight = 28.sp)
    }
}

/** A table drawn cell by cell, scrolling sideways when it is wider than the screen. */
@Composable
private fun BodyTable(table: HtmlBlock.Table) {
    val colors = ReaderTheme.colors
    val links = TextLinkStyles(SpanStyle(color = colors.accent))
    Column(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        for (row in table.rows) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                for (cell in row) {
                    val text = remember(cell.html, links) { AnnotatedString.fromHtml(cell.html, linkStyles = links) }
                    Text(
                        text,
                        modifier = Modifier
                            .widthIn(min = 64.dp, max = 280.dp)
                            .fillMaxHeight()
                            .border(0.5.dp, colors.line)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        fontFamily = ReaderTheme.readingFamily,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = if (cell.header) FontWeight.SemiBold else FontWeight.Normal,
                        color = colors.text,
                    )
                }
            }
        }
    }
}
