package io.nekohasekai.sagernet.ui.compose.components

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * A tiny playable "dino runner" shown when none of the user's servers work.
 * Tap the play area to jump; dodge the cactuses. Pure Compose Canvas, no assets.
 */
@Composable
fun DinoGameDialog(onDismiss: () -> Unit) {
    ExpressiveDialog(onDismissRequest = onDismiss) {
        Text(
            text = "so sad...",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            text = "None of your servers are reachable. Here, have a game.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        DinoGame()

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    }
}

private const val GROUND_Y = 0.78f      // fraction of height where the ground is
private const val DINO_X = 0.16f        // fraction of width for the dino
private const val GRAVITY = 2600f       // px/s^2 (scaled by height later)
private const val JUMP_V = -900f        // initial jump velocity

@Composable
fun DinoGame(modifier: Modifier = Modifier) {
    val surface = MaterialTheme.colorScheme.surfaceContainerHighest
    val ground = MaterialTheme.colorScheme.outline
    val dinoColor = MaterialTheme.colorScheme.primary
    val cactusColor = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface

    var running by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }

    // Dino vertical state. dinoY is the offset above ground in px (0 = on ground,
    // negative = in the air); velocity in px/s.
    var dinoY by remember { mutableFloatStateOf(0f) }
    var dinoV by remember { mutableFloatStateOf(0f) }
    var onGround by remember { mutableStateOf(true) }
    var wantJump by remember { mutableStateOf(false) }

    // Obstacles: list of x fractions (0..1+), moving left
    var obstacles by remember { mutableStateOf(listOf<Float>()) }
    var speed by remember { mutableFloatStateOf(0.45f) }   // fraction of width per second
    var spawnTimer by remember { mutableFloatStateOf(0f) }

    // Canvas dimensions captured for the physics loop.
    var w by remember { mutableFloatStateOf(1f) }
    var h by remember { mutableFloatStateOf(1f) }

    fun reset() {
        score = 0; dinoY = 0f; dinoV = 0f; onGround = true; wantJump = false
        obstacles = listOf(); speed = 0.45f; spawnTimer = 0f
        gameOver = false; running = true
    }

    fun jump() {
        when {
            gameOver || !running -> reset()
            onGround -> wantJump = true
        }
    }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = 0L
        while (running) {
            withInfiniteAnimationFrameNanos { now ->
                if (last == 0L) { last = now; return@withInfiniteAnimationFrameNanos }
                val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                val gy = h * GROUND_Y
                val scale = h / 400f
                val g = GRAVITY * scale

                if (wantJump && onGround) {
                    dinoV = JUMP_V * scale
                    onGround = false
                    wantJump = false
                }
                if (!onGround) {
                    dinoV += g * dt
                    dinoY += dinoV * dt
                    if (dinoY >= 0f) { dinoY = 0f; dinoV = 0f; onGround = true }
                }

                // move + spawn obstacles
                val move = speed * dt
                obstacles = obstacles.map { it - move }.filter { it > -0.15f }
                spawnTimer -= dt
                if (spawnTimer <= 0f) {
                    obstacles = obstacles + (1.05f + Random.nextFloat() * 0.2f)
                    spawnTimer = 0.9f + Random.nextFloat() * 0.8f
                    score += 1
                    speed = (speed + 0.02f).coerceAtMost(1.1f)
                }

                // collision: dino box vs cactus box
                val dinoLeft = w * DINO_X
                val dinoW = h * 0.11f
                val dinoH = h * 0.14f
                val dinoTop = gy - dinoH + dinoY
                val dinoBottom = gy + dinoY
                for (ox in obstacles) {
                    val cx = w * ox
                    val cw = h * 0.07f
                    val ch = h * 0.15f
                    val cTop = gy - ch
                    val overlapX = cx < dinoLeft + dinoW && cx + cw > dinoLeft
                    val overlapY = dinoBottom > cTop
                    if (overlapX && overlapY) {
                        running = false; gameOver = true
                    }
                }
            }
        }
    }

    Column(modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(surface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { jump() },
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                w = size.width; h = size.height
                drawGame(
                    size = size,
                    dinoY = dinoY,
                    obstacles = obstacles,
                    ground = ground,
                    dino = dinoColor,
                    cactus = cactusColor,
                )
            }
            // Overlays
            if (!running && !gameOver) {
                CenterHint("Tap to play", onSurface)
            }
            if (gameOver) {
                CenterHint("Game over — tap to retry", onSurface)
            }
            Text(
                text = "Score: $score",
                style = MaterialTheme.typography.labelLarge,
                color = onSurface,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.CenterHint(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = color,
        modifier = Modifier.align(Alignment.Center),
    )
}

private fun DrawScope.drawGame(
    size: Size,
    dinoY: Float,
    obstacles: List<Float>,
    ground: Color,
    dino: Color,
    cactus: Color,
) {
    val w = size.width
    val h = size.height
    val gy = h * GROUND_Y

    // ground line
    drawLine(
        color = ground,
        start = Offset(0f, gy),
        end = Offset(w, gy),
        strokeWidth = h * 0.012f,
    )

    // dino — a single rounded block in the theme's primary colour.
    val dinoW = h * 0.11f
    val dinoH = h * 0.14f
    val dx = w * DINO_X
    val dTop = gy - dinoH + dinoY
    drawRoundRect(
        color = dino,
        topLeft = Offset(dx, dTop),
        size = Size(dinoW, dinoH),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(dinoW * 0.3f),
    )

    // cactuses — single rounded blocks (top corners only).
    for (ox in obstacles) {
        val cx = w * ox
        val cw = h * 0.07f
        val ch = h * 0.15f
        val r = cw * 0.35f
        val path = androidx.compose.ui.graphics.Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = cx,
                    top = gy - ch,
                    right = cx + cw,
                    bottom = gy,
                    topLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(r),
                    topRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(r),
                    bottomLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(0f),
                    bottomRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(0f),
                ),
            )
        }
        drawPath(path, color = cactus)
    }
}
