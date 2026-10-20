package cc.hafa.reader.ui

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cc.hafa.reader.core.PictureChoice
import coil3.compose.AsyncImage

/**
 * A picture from the web shown no wider than a browser would show it, so a
 * small picture is not stretched to fill [modifier]'s width: [declaredWidth]
 * dp when the image declares a width (0 when not), else its pixel width over
 * [density], as a browser counts CSS pixels.
 */
@Composable
fun NaturalSizeImage(
    model: String,
    contentDescription: String?,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
    declaredWidth: Double = 0.0,
    density: Double = 1.0,
) {
    val picture = PictureChoice(model, declaredWidth, density)
    var maxWidth by remember(picture) {
        mutableStateOf(if (declaredWidth > 0) picture.displayWidth(0.0).dp else Dp.Unspecified)
    }
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = contentScale,
        onSuccess = { state ->
            val width = state.painter.intrinsicSize.width
            // Coil decodes no smaller than the space it is given, so a picture
            // narrower than the screen reads here at its own pixel width.
            if (width.isFinite() && width > 0) {
                maxWidth = picture.displayWidth(width.toDouble()).dp
            }
        },
        modifier = Modifier.widthIn(max = maxWidth).then(modifier),
    )
}
