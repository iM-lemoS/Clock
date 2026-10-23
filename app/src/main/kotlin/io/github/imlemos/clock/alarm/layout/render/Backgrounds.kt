// The platform ExifInterface is enough from minSdk 31 (see decodeSampled).
@file:Suppress("ExifInterface")

package io.github.imlemos.clock.alarm.layout.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.ExifInterface
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.imlemos.clock.alarm.layout.Background
import io.github.imlemos.clock.alarm.layout.GradientDirection
import io.github.imlemos.clock.alarm.layout.Frame
import io.github.imlemos.clock.alarm.layout.LayoutGeometry
import io.github.imlemos.clock.alarm.layout.Size2
import io.github.imlemos.clock.alarm.layout.PresetKind
import io.github.imlemos.clock.core.designsystem.component.DotGrid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/** The alarm screen background: a preset drawn in code, a photo or a silent looping video. */
@Composable
fun LayoutBackground(
    background: Background,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    onMediaAspect: (Float) -> Unit = {},
) {
    var aspect by remember(background.mediaPath()) { mutableFloatStateOf(0f) }
    val reportAspect: (Float) -> Unit = { aspect = it; onMediaAspect(it) }
    Box(modifier.fillMaxSize().background(Color.Black)) {
        when (background) {
            is Background.Preset -> PresetBackground(background, animate)
            is Background.Image -> {
                CoverBox(aspect, background.frame) { frameModifier -> ImageBackground(background.path, frameModifier, reportAspect) }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = background.dim)))
            }
            is Background.Video -> {
                CoverBox(aspect, background.frame) { frameModifier ->
                    VideoBackground(background.path, playing = animate, modifier = frameModifier, onAspect = reportAspect)
                }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = background.dim)))
            }
        }
    }
}

/** Default colours of each preset: (foreground, background). */
fun presetColors(kind: PresetKind): Pair<Color, Color> = when (kind) {
    PresetKind.DOTS -> Color(0x40FFFFFF) to Color.Black
    PresetKind.WAVES -> Color(0x99FFFFFF) to Color.Black
    PresetKind.MATRIX -> Color(0xFFE5302B) to Color.Black
    PresetKind.SOLID -> Color(0xFF101010) to Color.Black
    PresetKind.GRADIENT -> Color(0xFF1B2A4A) to Color.Black
}

@Composable
private fun PresetBackground(preset: Background.Preset, animate: Boolean) {
    val (defaultA, defaultB) = presetColors(preset.kind)
    val colorA = preset.colorA?.let(::Color) ?: defaultA
    val colorB = preset.colorB?.let(::Color) ?: defaultB
    val transition = rememberInfiniteTransition(label = "background")
    val animated by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8_000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    // Read while drawing so animation only redraws. Thumbnails stay still.
    val phase = { if (animate) animated else 0.25f }
    when (preset.kind) {
        PresetKind.DOTS -> Box(Modifier.fillMaxSize().background(colorB)) {
            DotGrid(Modifier.fillMaxSize(), fadeStart = 0f, color = colorA)
        }
        PresetKind.SOLID -> Box(Modifier.fillMaxSize().background(colorA))
        PresetKind.GRADIENT -> Box(Modifier.fillMaxSize().background(gradient(preset.direction, colorA, colorB)))
        PresetKind.WAVES -> WaveDots(colorA, colorB, phase)
        PresetKind.MATRIX -> MatrixDots(colorA, colorB, phase)
    }
}

/** From [from] to [to] in [direction]; diagonals run corner to corner. */
private fun gradient(direction: GradientDirection, from: Color, to: Color): Brush = when (direction) {
    GradientDirection.DOWN -> Brush.verticalGradient(listOf(from, to))
    GradientDirection.ACROSS -> Brush.horizontalGradient(listOf(from, to))
    GradientDirection.DIAGONAL_DOWN -> Brush.linearGradient(listOf(from, to), start = Offset.Zero, end = Offset.Infinite)
    GradientDirection.DIAGONAL_UP -> Brush.linearGradient(
        listOf(from, to),
        start = Offset(0f, Float.POSITIVE_INFINITY),
        end = Offset(Float.POSITIVE_INFINITY, 0f),
    )
}

/** Rows of dots following slow sine waves. */
@Composable
private fun WaveDots(color: Color, backgroundColor: Color, phase: () -> Float) {
    val step = with(LocalDensity.current) { 14.dp.toPx() }
    val radius = with(LocalDensity.current) { 1.6.dp.toPx() }
    Canvas(Modifier.fillMaxSize().background(backgroundColor)) {
        val waves = 7
        val t = phase()
        val columns = (size.width / step).toInt() + 1
        for (wave in 0 until waves) {
            val baseY = size.height * (0.55f + wave * 0.06f)
            val amplitude = step * (1.5f + wave * 0.4f)
            val alpha = 1f - wave / waves.toFloat() * 0.7f
            for (column in 0..columns) {
                val x = column * step
                val angle = (x / size.width) * 2f * PI.toFloat() * 1.5f + t * 2f * PI.toFloat() + wave * 0.6f
                drawCircle(color, radius, Offset(x, baseY + sin(angle) * amplitude), alpha = alpha * color.alpha)
            }
        }
    }
}

/** A dot matrix with bright bands falling down each column at its own pace. */
@Composable
private fun MatrixDots(color: Color, backgroundColor: Color, phase: () -> Float) {
    val step = with(LocalDensity.current) { 16.dp.toPx() }
    val radius = with(LocalDensity.current) { 1.8.dp.toPx() }
    Canvas(Modifier.fillMaxSize().background(backgroundColor)) {
        val columns = (size.width / step).toInt()
        val rows = (size.height / step).toInt()
        val originX = (size.width - columns * step) / 2f + step / 2f
        val t = phase()
        for (column in 0 until columns) {
            // A fixed pseudo-random speed and offset per column.
            val seed = ((column * 73 + 17) % 101) / 101f
            val head = ((t * (1f + seed) + seed) % 1f) * (rows + 12) - 6
            for (row in 0 until rows) {
                val distance = head - row
                val brightness = if (distance in 0f..10f) (1f - distance / 10f).pow(2) else 0f
                val alpha = 0.12f + 0.88f * brightness
                drawCircle(color, radius, Offset(originX + column * step, row * step + step / 2f), alpha = alpha)
            }
        }
    }
}

private fun Background.mediaPath(): String? = when (this) {
    is Background.Image -> path
    is Background.Video -> path
    is Background.Preset -> null
}

/**
 * Lays out a photo or video at the size that covers the screen with its own proportions (centre
 * crop, nothing stretched), then applies the user's zoom and position. The overflow stays drawn
 * so moving it never reveals an empty edge; the screen clips it.
 */
@Composable
private fun CoverBox(aspect: Float, frame: Frame, content: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        val area = Size2(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val sized = if (aspect > 0f) {
            Modifier.requiredSize(maxOf(maxWidth, maxHeight * aspect), maxOf(maxHeight, maxWidth / aspect))
        } else {
            Modifier.fillMaxSize()
        }
        val clamped = if (aspect > 0f) LayoutGeometry.clampFrame(frame, area, aspect) else Frame()
        content(
            sized.graphicsLayer {
                scaleX = clamped.zoom
                scaleY = clamped.zoom
                translationX = clamped.offsetX * area.width
                translationY = clamped.offsetY * area.height
            },
        )
    }
}

@Composable
private fun ImageBackground(path: String, modifier: Modifier, onAspect: (Float) -> Unit) {
    val widthPx = LocalWindowInfo.current.containerSize.width
    val image by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { decodeSampled(path, widthPx) }
    }
    image?.let {
        LaunchedEffect(it) { onAspect(it.width / it.height.toFloat()) }
        Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    }
}

/**
 * Decodes at roughly the screen's width so large photos do not exhaust memory.
 *
 * The platform ExifInterface is fine from minSdk 31; the AndroidX copy only backports fixes for
 * much older releases, so no extra dependency.
 */
private fun decodeSampled(path: String, targetWidth: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
    val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@runCatching null
    // Camera photos are often stored sideways with an EXIF orientation, which BitmapFactory ignores.
    val degrees = when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    val upright = if (degrees == 0f) {
        bitmap
    } else {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
    }
    upright.asImageBitmap()
}.getOrNull()

/** A muted, looping video scaled to fill the screen (centre crop). No extra library: MediaPlayer. */
@Composable
private fun VideoBackground(path: String, playing: Boolean, modifier: Modifier, onAspect: (Float) -> Unit) {
    val player = remember(path) { MediaPlayer() }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { context ->
            TextureView(context).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        if (!File(path).exists()) return
                        runCatching {
                            player.setDataSource(path)
                            player.setSurface(Surface(texture))
                            player.isLooping = true
                            player.setVolume(0f, 0f)
                            // The view is laid out at the video's own proportions (CoverBox).
                            player.setOnVideoSizeChangedListener { _, videoWidth, videoHeight ->
                                if (videoHeight > 0) onAspect(videoWidth / videoHeight.toFloat())
                            }
                            player.setOnPreparedListener { if (playing) it.start() else it.seekTo(0) }
                            player.prepareAsync()
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean = true
                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                }
            }
        },
        modifier = modifier,
    )
}

