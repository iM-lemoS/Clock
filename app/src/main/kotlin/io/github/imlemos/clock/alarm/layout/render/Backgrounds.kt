package io.github.imlemos.clock.alarm.layout.render

import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.imlemos.clock.alarm.layout.Background
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
fun LayoutBackground(background: Background, modifier: Modifier = Modifier, animate: Boolean = true) {
    Box(modifier.fillMaxSize().background(Color.Black)) {
        when (background) {
            is Background.Preset -> PresetBackground(background, animate)
            is Background.Image -> {
                ImageBackground(background.path)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = background.dim)))
            }
            is Background.Video -> {
                VideoBackground(background.path, playing = animate)
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
        PresetKind.GRADIENT -> Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(colorA, colorB))))
        PresetKind.WAVES -> WaveDots(colorA, colorB, phase)
        PresetKind.MATRIX -> MatrixDots(colorA, colorB, phase)
    }
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

@Composable
private fun ImageBackground(path: String) {
    val widthPx = LocalWindowInfo.current.containerSize.width
    val image by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { decodeSampled(path, widthPx) }
    }
    image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
}

/** Decodes at roughly the screen's width so large photos do not exhaust memory. */
private fun decodeSampled(path: String, targetWidth: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()

/** A muted, looping video scaled to fill the screen (centre crop). No extra library: MediaPlayer. */
@Composable
private fun VideoBackground(path: String, playing: Boolean) {
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
                            player.setOnVideoSizeChangedListener { _, videoWidth, videoHeight ->
                                setTransform(centerCrop(width, height, videoWidth, videoHeight))
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
        modifier = Modifier.fillMaxSize(),
    )
}

/** TextureView stretches the video to the view; this matrix crops it to keep its proportions. */
private fun centerCrop(viewWidth: Int, viewHeight: Int, videoWidth: Int, videoHeight: Int): Matrix {
    val matrix = Matrix()
    if (videoWidth == 0 || videoHeight == 0) return matrix
    val viewRatio = viewWidth / viewHeight.toFloat()
    val videoRatio = videoWidth / videoHeight.toFloat()
    val (scaleX, scaleY) = if (videoRatio > viewRatio) videoRatio / viewRatio to 1f else 1f to viewRatio / videoRatio
    matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
    return matrix
}
