package cc.hafa.reader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.hafa.reader.R
import cc.hafa.reader.core.PolicyBlock
import cc.hafa.reader.core.PolicyRun
import cc.hafa.reader.core.SITE_URL
import cc.hafa.reader.core.parsePolicy

/** The privacy policy or the terms, from the same text as their web pages, with a link to the web page in the bottom bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolicyScreen(page: Detail, viewModel: ReaderViewModel) {
    val colors = ReaderTheme.colors
    val context = LocalContext.current
    val name = if (page == Detail.Privacy) "privacy" else "terms"
    val blocks = remember(name) { context.assets.open("$name.html").bufferedReader().use { reader -> parsePolicy(reader.readText()) } }
    val open = rememberInfoLinks(viewModel)
    ReaderScaffold(
        bottomBar = { scrollBehavior ->
            BackBottomBar(stringResource(if (page == Detail.Privacy) R.string.privacy_title else R.string.terms_title), scrollBehavior, viewModel::back) {
                WebPageButton(page)
            }
        },
    ) { padding ->
        Column(Modifier.scrollingScreen(padding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (block in blocks) {
                val text = linked(block.runs, open)
                when (block) {
                    is PolicyBlock.Heading -> Text(
                        text,
                        modifier = Modifier.padding(top = 12.dp).semantics { heading() },
                        fontFamily = ReaderTheme.readingFamily,
                        fontSize = 20.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text,
                    )
                    is PolicyBlock.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("•", fontSize = 16.sp, lineHeight = 24.sp, color = colors.muted)
                        Text(text, fontSize = 16.sp, lineHeight = 24.sp, color = colors.text)
                    }
                    is PolicyBlock.Paragraph -> if (block.updated) {
                        Text(text, fontSize = 14.sp, lineHeight = 20.sp, color = colors.muted)
                    } else {
                        Text(text, fontSize = 16.sp, lineHeight = 24.sp, color = colors.text)
                    }
                }
            }
        }
    }
}

/** The bottom bar's link from [page] in the app to the same page on the web. */
@Composable
fun WebPageButton(page: Detail) {
    val links = LocalUriHandler.current
    val path = when (page) {
        Detail.Privacy -> "privacy"
        Detail.Terms -> "terms"
        else -> "contact"
    }
    IconAction(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.open_on_web), { links.openUri("$SITE_URL/$path") })
}

/** Opens a link from one of the info pages: another of them in the app, anything else outside it. */
@Composable
fun rememberInfoLinks(viewModel: ReaderViewModel): (String) -> Unit {
    val links = LocalUriHandler.current
    return remember(viewModel, links) {
        { href ->
            when (href.removeSuffix("/")) {
                "$SITE_URL/privacy" -> viewModel.showInfo(Detail.Privacy)
                "$SITE_URL/terms" -> viewModel.showInfo(Detail.Terms)
                "$SITE_URL/contact" -> viewModel.showInfo(Detail.Contact)
                else -> links.openUri(href)
            }
        }
    }
}

/** [runs] as text, with their links in the accent color, opened by [open]. */
@Composable
fun linked(runs: List<PolicyRun>, open: (String) -> Unit): AnnotatedString {
    val style = TextLinkStyles(SpanStyle(color = ReaderTheme.colors.accent, textDecoration = TextDecoration.Underline))
    return buildAnnotatedString {
        for (run in runs) {
            if (run.href.isEmpty()) {
                append(run.text)
            } else {
                withLink(LinkAnnotation.Clickable(run.href, style) { open(run.href) }) { append(run.text) }
            }
        }
    }
}
