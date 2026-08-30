package com.resona.music.ui.player

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full-bleed backdrop for Now Playing: the artwork itself, blurred and
 * scrimmed toward [AlbumArtPalette.background], so the player/queue/lyrics
 * tabs all sit on the same album-derived backdrop.
 *
 *
 * The blur is applied at decode time via [BlurTransformation] (cached as a
 * plain bitmap) rather than [Modifier.blur], which re-renders a full-screen
 * blurred layer every frame of the backdrop's animations.
 */
@Composable
fun AlbumArtBackdrop(
    artworkUrl: String?,
    palette: AlbumArtPalette,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val blurRadiusPx = with(LocalDensity.current) { 72.dp.toPx() }

    Box(modifier = modifier.fillMaxSize()) {
        if (!artworkUrl.isNullOrBlank()) {
            val request = remember(artworkUrl, blurRadiusPx) {
                ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .transformations(BlurTransformation(radius = blurRadiusPx))
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.55f)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            palette.background.copy(alpha = 0.85f),
                            MaterialTheme.colorScheme.background,
                        )
                    )
                )
        )
    }
}

/**
 * One-shot Gaussian blur applied at decode time (off the UI thread, cached
 * by Coil alongside the bitmap). Blurs into a half-size canvas that the
 * caller upscales to fill, keeping the blur cheap. Uses [RenderEffect] on
 * API 31+, [BlurMaskFilter] below -- Compose's runtime blur is slow
 * software rendering there.
 */
private class BlurTransformation(
    private val radius: Float,
) : Transformation {

    override val cacheKey: String = "resona-blur:$radius"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap = withContext(Dispatchers.IO) {
        // Blur on a half-size canvas; the caller upscales to fill.
        val output = Bitmap.createBitmap(
            input.width / SAMPLE,
            input.height / SAMPLE,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(output)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Paint's RenderEffect hook isn't public; apply it via RenderNode.
            val renderNode = RenderNode("resona-blur")
            renderNode.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
            val record = renderNode.beginRecording(output.width, output.height)
            record.drawBitmap(input, null, Rect(0, 0, output.width, output.height), Paint())
            renderNode.endRecording()
            canvas.drawRenderNode(renderNode)
            renderNode.discardDisplayList()
        } else {
            @Suppress("DEPRECATION")
            val paint = Paint().apply {
                maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawBitmap(input, null, Rect(0, 0, output.width, output.height), paint)
        }
        output
    }

    private companion object {
        const val SAMPLE = 2
    }
}
