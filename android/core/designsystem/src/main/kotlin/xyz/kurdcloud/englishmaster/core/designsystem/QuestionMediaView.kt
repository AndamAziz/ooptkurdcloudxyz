package xyz.kurdcloud.englishmaster.core.designsystem

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.kurdcloud.englishmaster.core.model.MediaType
import xyz.kurdcloud.englishmaster.core.model.QuestionMedia
import java.net.HttpURLConnection
import java.net.URL

/** Reading text shown above a question (English content: the caller keeps it left-to-right). */
@Composable
fun PassageCard(passage: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().testTag("question_passage"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Text(
            passage,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()).padding(16.dp),
        )
    }
}

/**
 * Picture or short video attached to a question. Pictures come from the copy saved with the level
 * pack when available, otherwise from the network; videos are streamed with the system player.
 */
@Composable
fun QuestionMediaView(media: QuestionMedia, unavailableText: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        val shape = RoundedCornerShape(16.dp)
        val frame = Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
        when (media.type) {
            MediaType.IMAGE -> QuestionImage(media, unavailableText, frame)
            MediaType.VIDEO -> QuestionVideo(media, frame)
        }
        media.caption?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp))
        }
    }
}

private sealed interface ImageState {
    data object Loading : ImageState
    data class Ready(val bitmap: ImageBitmap) : ImageState
    data object Failed : ImageState
}

@Composable
private fun QuestionImage(media: QuestionMedia, unavailableText: String, modifier: Modifier) {
    var state by remember(media.url, media.localPath) { mutableStateOf<ImageState>(ImageState.Loading) }
    LaunchedEffect(media.url, media.localPath) {
        state = withContext(Dispatchers.IO) {
            runCatching {
                val local = media.localPath?.let { BitmapFactory.decodeFile(it) }
                local ?: (URL(media.url).openConnection() as HttpURLConnection).run {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    try { inputStream.use { BitmapFactory.decodeStream(it) } } finally { disconnect() }
                }
            }.getOrNull()?.let { ImageState.Ready(it.asImageBitmap()) } ?: ImageState.Failed
        }
    }
    Box(modifier.testTag("question_image"), contentAlignment = Alignment.Center) {
        when (val s = state) {
            ImageState.Loading -> CircularProgressIndicator()
            is ImageState.Ready -> Image(
                s.bitmap,
                contentDescription = media.caption,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(),
            )
            ImageState.Failed -> Text(unavailableText, modifier = Modifier.padding(16.dp))
        }
    }
}

@Composable
private fun QuestionVideo(media: QuestionMedia, modifier: Modifier) {
    AndroidView(
        modifier = modifier.testTag("question_video").semantics { contentDescription = media.caption ?: "video" },
        factory = { context ->
            VideoView(context).apply {
                val controller = MediaController(context)
                controller.setAnchorView(this)
                setMediaController(controller)
                setVideoURI(Uri.parse(media.url))
                // Short clips play once on their own; the controls (tap the video) replay them.
                setOnPreparedListener { start() }
            }
        },
        onRelease = { it.stopPlayback() },
    )
}
