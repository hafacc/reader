package cc.hafa.reader.ui

import android.content.ClipData
import android.os.Build
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.hafa.reader.R
import cc.hafa.reader.core.PolicyRun
import cc.hafa.reader.core.SITE_URL
import cc.hafa.reader.sync.SyncConfig
import kotlinx.coroutines.launch

/**
 * How to reach support: the email address, which a tap writes to and a long press copies, feedback on GitHub, and
 * where to delete the account and read the privacy policy, as on the web page, with a link to it in the bottom bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactScreen(viewModel: ReaderViewModel) {
    val colors = ReaderTheme.colors
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val links = LocalUriHandler.current
    val open = rememberInfoLinks(viewModel)
    ReaderScaffold(
        bottomBar = { scrollBehavior ->
            BackBottomBar(stringResource(R.string.contact), scrollBehavior, viewModel::back) {
                WebPageButton(Detail.Contact)
            }
        },
    ) { padding ->
        Column(
            Modifier.scrollingScreen(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.contact_us), fontFamily = ReaderTheme.readingFamily, fontSize = 24.sp, lineHeight = 32.sp, color = colors.text)
            Text(stringResource(R.string.contact_body), fontSize = 16.sp, lineHeight = 24.sp, color = colors.text)
            // A tap writes to the address; a long press copies it, as a long press on a link does in a browser.
            Text(
                SyncConfig.SUPPORT_EMAIL,
                modifier = Modifier.combinedClickable(
                    onClickLabel = stringResource(R.string.contact_write),
                    onLongClickLabel = stringResource(R.string.contact_copy),
                    onClick = { links.openUri("mailto:${SyncConfig.SUPPORT_EMAIL}") },
                    onLongClick = {
                        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(SyncConfig.SUPPORT_EMAIL, SyncConfig.SUPPORT_EMAIL))) }
                        // Android 13 and later confirm a copy themselves.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            viewModel.notify(Notice.Text(R.string.contact_copied))
                        }
                    },
                ),
                color = colors.accent,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                textDecoration = TextDecoration.Underline,
            )
            val feedback = listOf(
                PolicyRun("${stringResource(R.string.contact_feedback)} "),
                PolicyRun(stringResource(R.string.contact_feedback_link), SyncConfig.FEEDBACK_URL),
                PolicyRun("."),
            )
            val elsewhere = listOf(
                PolicyRun("${stringResource(R.string.contact_delete)} "),
                PolicyRun(stringResource(R.string.contact_delete_link), "$SITE_URL/delete"),
                PolicyRun(". ${stringResource(R.string.contact_privacy)} "),
                PolicyRun(stringResource(R.string.contact_privacy_link), "$SITE_URL/privacy"),
                PolicyRun("."),
            )
            for (runs in listOf(feedback, elsewhere)) {
                Text(linked(runs, open), fontSize = 16.sp, lineHeight = 24.sp, color = colors.text)
            }
        }
    }
}
