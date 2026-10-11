package com.frizovdanya.pixpet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.min

private const val GRID_W = 18
private const val GRID_H = 13

enum class PetSpecies(val key: String, val title: String) {
    CLAWD("clawd", "Clawd"),
    CAT("cat", "Кошка"),
    DOG("dog", "Собака");

    companion object {
        fun fromKey(key: String?): PetSpecies = entries.firstOrNull { it.key == key } ?: CLAWD
    }
}

internal object PetPalettes {
    val names = listOf("Классический", "Мятный", "Лавандовый", "Небесный", "Розовый", "Графит")
    private val body = intArrayOf(0xFFD97757.toInt(), 0xFF4FBF98.toInt(), 0xFF9B8AEE.toInt(), 0xFF4FA8E8.toInt(), 0xFFEE85AE.toInt(), 0xFF5E6670.toInt())
    private val eyes = intArrayOf(0xFF241916.toInt(), 0xFF12241D.toInt(), 0xFF1B1631.toInt(), 0xFF0E1C29.toInt(), 0xFF2A1220.toInt(), 0xFFF2F2F2.toInt())
    private val shade = intArrayOf(0xFFB75A42.toInt(), 0xFF329879.toInt(), 0xFF7865CF.toInt(), 0xFF347EB5.toInt(), 0xFFCF5D8D.toInt(), 0xFF3B4149.toInt())
    private val light = intArrayOf(0xFFFFB18E.toInt(), 0xFF9BE0C3.toInt(), 0xFFD0C8FF.toInt(), 0xFFB8E3FF.toInt(), 0xFFFFC5D8.toInt(), 0xFF9AA3AC.toInt())

    fun body(index: Int) = body[index.coerceIn(body.indices)]
    fun eyes(index: Int) = eyes[index.coerceIn(eyes.indices)]
    fun shade(index: Int) = shade[index.coerceIn(shade.indices)]
    fun light(index: Int) = light[index.coerceIn(light.indices)]
    fun blush(index: Int): Int = blend(body(index), 0xFFFF4F79.toInt(), 0.55f)

    private fun blend(a: Int, b: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        val r = (((a shr 16) and 255) + (((b shr 16) and 255) - ((a shr 16) and 255)) * t).toInt()
        val g = (((a shr 8) and 255) + (((b shr 8) and 255) - ((a shr 8) and 255)) * t).toInt()
        val blue = ((a and 255) + ((b and 255) - (a and 255)) * t).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or blue
    }
}

internal enum class PetPose { IDLE, WALK, PRESS, DRAG, FLY, PET, SLEEP, HANG, PEEK }
internal enum class PetExpression { OPEN, BLINK, HAPPY, SURPRISED, DIZZY, ANGRY, SLEEP }
internal enum class ParticleKind { HEART, SPARK, ZZZ, NOTE }

internal data class PetFrame(
    val pose: PetPose = PetPose.IDLE,
    val expression: PetExpression = PetExpression.OPEN,
    val look: Int = 0,
    val waveSide: Int = 0,
    val blush: Boolean = false,
    val mouthOpen: Boolean = false,
    val squash: Float = 0f,
    val rotation: Float = 0f,
    val hopOffset: Float = 0f,
    val phase: Float = 0f,
)

internal data class PetParticle(
    val kind: ParticleKind,
    val x: Float,
    val y: Float,
    val driftX: Float,
    val bornAtMs: Long,
    val durationMs: Long,
)

/** Pixel-art view shared by the live overlay and the settings preview. */
class PetArtView(context: Context) : View(context) {
    internal var species: PetSpecies = PetSpecies.CLAWD
        set(value) { field = value; invalidate() }
    internal var paletteIndex: Int = 0
        set(value) { field = value.coerceIn(0, PetPalettes.names.lastIndex); invalidate() }
    internal var frame: PetFrame = PetFrame()
        set(value) { field = value; invalidate() }
    internal var particles: List<PetParticle> = emptyList()
        set(value) { field = value; invalidate() }
    internal var frameTimeMs: Long = 0L
        set(value) { field = value; invalidate() }

    private val pixelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    init {
        isClickable = true
        isFocusable = false
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        PetRenderer.draw(canvas, pixelPaint, width.toFloat(), height.toFloat(), species, paletteIndex, frame, frameTimeMs, particles)
    }

}

internal object PetRenderer {
    private val starts = intArrayOf(5, 4, 3, 3, 3, 3, 3, 4, 5)
    private val LEG_XS = intArrayOf(5, 8, 11, 14)

    fun draw(
        canvas: Canvas,
        paint: Paint,
        width: Float,
        height: Float,
        species: PetSpecies,
        palette: Int,
        frame: PetFrame,
        nowMs: Long,
        particles: List<PetParticle>,
    ) {
        if (width <= 0f || height <= 0f) return
        val unit = min(width / GRID_W, height / (GRID_H + 2f))
        if (unit <= 0f) return
        val topPad = unit * 2f
        val centerX = width / 2f
        val centerY = topPad + unit * 6.5f
        val sx = when {
            frame.squash > 0f -> 1.08f
            frame.squash < 0f -> 0.92f
            else -> 1f
        }
        val sy = when {
            frame.squash > 0f -> 0.90f
            frame.squash < 0f -> 1.08f
            else -> 1f
        }

        canvas.save()
        canvas.rotate(frame.rotation, centerX, centerY + frame.hopOffset)
        canvas.translate(0f, frame.hopOffset)
        canvas.scale(sx, sy, centerX, centerY)

        val body = PetPalettes.body(palette)
        val eye = PetPalettes.eyes(palette)
        val shade = PetPalettes.shade(palette)
        val light = PetPalettes.light(palette)
        val blush = PetPalettes.blush(palette)

        // Tail and ears are drawn first so the body overlaps their roots cleanly.
        when (species) {
            PetSpecies.CAT -> {
                px(canvas, paint, 14, 8, 1, 1, shade, unit, topPad)
                px(canvas, paint, 15, 7, 1, 1, body, unit, topPad)
                px(canvas, paint, 16, 6, 1, 2, shade, unit, topPad)
                px(canvas, paint, 15, 5, 1, 1, body, unit, topPad)
                px(canvas, paint, 4, 2, 1, 2, shade, unit, topPad)
                px(canvas, paint, 5, 1, 2, 2, body, unit, topPad)
                px(canvas, paint, 6, 0, 1, 1, shade, unit, topPad)
                px(canvas, paint, 12, 1, 2, 2, body, unit, topPad)
                px(canvas, paint, 14, 2, 1, 2, shade, unit, topPad)
                px(canvas, paint, 6, 2, 1, 1, 0xFFFFB1B8.toInt(), unit, topPad)
                px(canvas, paint, 13, 2, 1, 1, 0xFFFFB1B8.toInt(), unit, topPad)
            }
            PetSpecies.DOG -> {
                px(canvas, paint, 14, 6, 2, 1, shade, unit, topPad)
                px(canvas, paint, 15, 5, 2, 1, body, unit, topPad)
                px(canvas, paint, 16, 6, 1, 2, shade, unit, topPad)
                px(canvas, paint, 2, 3, 2, 3, shade, unit, topPad)
                px(canvas, paint, 3, 6, 2, 2, shade, unit, topPad)
                px(canvas, paint, 14, 3, 2, 3, shade, unit, topPad)
                px(canvas, paint, 13, 6, 2, 2, shade, unit, topPad)
            }
            PetSpecies.CLAWD -> Unit
        }

        // Pixel silhouette, with narrower top and feet.
        for (row in starts.indices) {
            val y = row + 2
            var x0 = starts[row]
            var x1 = GRID_W - x0
            if (frame.pose == PetPose.PET && row > 5) {
                x0 -= 1
                x1 += 1
            }
            px(canvas, paint, x0, y, x1 - x0, 1, body, unit, topPad)
        }

        // Little highlight gives the blob fur volume without smoothing the pixels.
        px(canvas, paint, 4, 4, 2, 1, light, unit, topPad)
        if (species == PetSpecies.CAT) {
            px(canvas, paint, 7, 3, 1, 1, light, unit, topPad)
        }

        val walking = frame.pose == PetPose.WALK || frame.pose == PetPose.PEEK || frame.pose == PetPose.DRAG
        val step = if ((frame.phase * 7f).toInt() and 1 == 0) 0 else 1
        for (i in LEG_XS.indices) {
            val legHeight = when {
                frame.pose == PetPose.SLEEP -> 1
                walking && ((i + step) and 1 == 1) -> 1
                else -> 2
            }
            px(canvas, paint, LEG_XS[i], 11, 1, legHeight, body, unit, topPad)
        }

        val armUp = frame.pose == PetPose.DRAG || frame.pose == PetPose.FLY || frame.pose == PetPose.HANG
        px(canvas, paint, 1, 7, 2, 2, body, unit, topPad)
        px(canvas, paint, 15, 7, 2, 2, body, unit, topPad)
        if (armUp || frame.waveSide < 0) {
            px(canvas, paint, 1, 5, 1, 3, body, unit, topPad)
            px(canvas, paint, 2, 5, 1, 1, body, unit, topPad)
        }
        if (armUp || frame.waveSide > 0) {
            px(canvas, paint, 16, 5, 1, 3, body, unit, topPad)
            px(canvas, paint, 15, 5, 1, 1, body, unit, topPad)
        }
        if (frame.pose == PetPose.SLEEP) {
            px(canvas, paint, 2, 7, 2, 1, body, unit, topPad)
            px(canvas, paint, 14, 7, 2, 1, body, unit, topPad)
        }

        val eyesY = if (species == PetSpecies.DOG) 5 else 5
        val leftEyeX = 6 + frame.look.coerceIn(-1, 1)
        val rightEyeX = 11 + frame.look.coerceIn(-1, 1)
        drawEye(canvas, paint, leftEyeX, eyesY, eye, frame.expression, unit, topPad, true)
        drawEye(canvas, paint, rightEyeX, eyesY, eye, frame.expression, unit, topPad, false)

        if (frame.blush || frame.pose == PetPose.PET) {
            px(canvas, paint, 4, 7, 2, 1, blush, unit, topPad)
            px(canvas, paint, 12, 7, 2, 1, blush, unit, topPad)
        }

        if (species == PetSpecies.DOG) {
            px(canvas, paint, 8, 7, 2, 1, 0xFFFFDAB7.toInt(), unit, topPad)
            px(canvas, paint, 9, 7, 1, 1, eye, unit, topPad)
            if (frame.mouthOpen) px(canvas, paint, 8, 8, 2, 1, 0xFFEA7890.toInt(), unit, topPad)
        } else {
            if (frame.mouthOpen) {
                px(canvas, paint, 8, 7, 2, 2, eye, unit, topPad)
            } else {
                px(canvas, paint, 8, 7, 2, 1, eye, unit, topPad)
            }
            if (species == PetSpecies.CAT) {
                px(canvas, paint, 8, 6, 1, 1, 0xFFFFB1B8.toInt(), unit, topPad)
                px(canvas, paint, 7, 7, 1, 1, 0xFFFFB1B8.toInt(), unit, topPad)
            }
        }

        // Floating pixel effects stay inside the pet window, so the rest of the screen remains touch-through.
        for (particle in particles) {
            val elapsed = (nowMs - particle.bornAtMs).coerceAtLeast(0L)
            val progress = (elapsed.toFloat() / particle.durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
            val alpha = (255f * (1f - progress)).toInt()
            val cell = unit * 0.72f
            val x = particle.x + particle.driftX * progress
            val y = particle.y - unit * (2.5f * progress)
            drawParticle(canvas, paint, particle.kind, x, y, cell, alpha)
        }
        canvas.restore()
    }

    private fun drawEye(canvas: Canvas, paint: Paint, x: Int, y: Int, color: Int, expression: PetExpression, unit: Float, top: Float, left: Boolean) {
        when (expression) {
            PetExpression.BLINK, PetExpression.SLEEP -> px(canvas, paint, x - 1, y + 1, 3, 1, color, unit, top)
            PetExpression.HAPPY -> {
                px(canvas, paint, x - 1, y + 1, 1, 1, color, unit, top)
                px(canvas, paint, x, y, 1, 1, color, unit, top)
                px(canvas, paint, x + 1, y + 1, 1, 1, color, unit, top)
            }
            PetExpression.SURPRISED -> px(canvas, paint, x, y, 1, 3, color, unit, top)
            PetExpression.DIZZY -> {
                px(canvas, paint, x - 1, y, 1, 1, color, unit, top)
                px(canvas, paint, x + 1, y, 1, 1, color, unit, top)
                px(canvas, paint, x, y + 1, 1, 1, color, unit, top)
                px(canvas, paint, x - 1, y + 2, 1, 1, color, unit, top)
                px(canvas, paint, x + 1, y + 2, 1, 1, color, unit, top)
            }
            PetExpression.ANGRY -> {
                px(canvas, paint, x, y, 1, 2, color, unit, top)
                px(canvas, paint, if (left) x + 1 else x - 1, y - 1, 1, 1, color, unit, top)
            }
            PetExpression.OPEN -> px(canvas, paint, x, y, 1, 2, color, unit, top)
        }
    }

    private fun drawParticle(canvas: Canvas, paint: Paint, kind: ParticleKind, x: Float, y: Float, cell: Float, alpha: Int) {
        val rows = when (kind) {
            ParticleKind.HEART -> HEART
            ParticleKind.SPARK -> SPARK
            ParticleKind.ZZZ -> ZZZ
            ParticleKind.NOTE -> NOTE
        }
        val color = when (kind) {
            ParticleKind.HEART -> 0xFFFF5C7A.toInt()
            ParticleKind.SPARK -> 0xFFFFC94D.toInt()
            ParticleKind.ZZZ -> 0xFF8FA3BF.toInt()
            ParticleKind.NOTE -> 0xFF6FB8FF.toInt()
        }
        paint.color = color
        paint.alpha = alpha
        for (row in rows.indices) {
            for (col in rows[row].indices) {
                if (rows[row][col] == 'X') {
                    canvas.drawRect(x + col * cell, y + row * cell, x + (col + 1) * cell, y + (row + 1) * cell, paint)
                }
            }
        }
        paint.alpha = 255
    }

    private fun px(canvas: Canvas, paint: Paint, x: Int, y: Int, w: Int, h: Int, color: Int, unit: Float, top: Float) {
        paint.color = color
        paint.alpha = 255
        canvas.drawRect(x * unit, top + y * unit, (x + w) * unit, top + (y + h) * unit, paint)
    }

    private val HEART = arrayOf(".XX.XX.", "XXXXXXX", "XXXXXXX", ".XXXXX.", "..XXX..", "...X...")
    private val SPARK = arrayOf("..X..", "..X..", "XX.XX", "..X..", "..X..")
    private val ZZZ = arrayOf("XXXXX", "...X.", "..X..", ".X...", "XXXXX")
    private val NOTE = arrayOf("..XXXXX", "..X...X", "..X...X", "..X...X", "XXX.XXX", "XXX.XXX")
}

/** Clickable poop uses the same chunky pixel grid as the character. */
class PoopArtView(context: Context, private val onClean: () -> Unit) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    companion object {
        private val POOP_PATTERN = arrayOf(
            "....X....", "...XXo...", "..XXXXX..", ".XXXXXXo.",
            ".XwkXwkX.", "XXXXXXXXo", "XXXXXXXXX",
        )
    }

    init {
        isClickable = true
        setOnClickListener { onClean() }
        contentDescription = "Убрать кучку"
        scaleX = 0.1f
        scaleY = 0.1f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pattern = POOP_PATTERN
        val cell = min(width / 9f, height / 7f)
        val ox = (width - cell * 9f) / 2f
        val oy = (height - cell * 7f) / 2f
        for (row in pattern.indices) {
            for (col in pattern[row].indices) {
                val color = when (pattern[row][col]) {
                    'X' -> 0xFF8A5A33.toInt()
                    'o' -> 0xFFB27D4E.toInt()
                    'w' -> 0xFFFFFFFF.toInt()
                    'k' -> 0xFF1A1A1A.toInt()
                    else -> 0
                }
                if (color != 0) {
                    paint.color = color
                    canvas.drawRect(ox + col * cell, oy + row * cell, ox + (col + 1) * cell, oy + (row + 1) * cell, paint)
                }
            }
        }
    }
}
