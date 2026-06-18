package com.example.birdfinder.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.birdfinder.data.db.DetectionEntity
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders a "wrapped"-style summary card (photo + name + stats) to a PNG so a detection
 * can be shared as a single attractive image alongside its audio clip.
 */
object ShareCardRenderer {

    private const val W = 1080
    private const val H = 1800
    private const val MARGIN = 76f
    private val timeFmt: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm").withZone(ZoneId.systemDefault())

    suspend fun render(
        context: Context,
        row: DetectionEntity,
        imageUrl: String?,
    ): File = withContext(Dispatchers.IO) {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawBackground(canvas)

        var y = 72f
        y = drawLine(canvas, "BIRD FINDER", brandLabel(), MARGIN, y) + 18f

        // Photo
        val photoTop = y
        val photoBottom = photoTop + 720f
        val photoRect = RectF(MARGIN, photoTop, W - MARGIN, photoBottom)
        val photo = imageUrl?.let { downloadBitmap(it) }
        drawPhoto(canvas, photoRect, photo)
        y = photoBottom + 44f

        // Title + scientific
        y = drawLine(canvas, row.speciesCommon, titlePaint(row.speciesCommon), MARGIN, y) + 6f
        y = drawLine(canvas, row.speciesScientific, subtitlePaint(), MARGIN, y) + 22f

        // Confidence pill
        y = drawPill(canvas, MARGIN, y, "%.0f%% confidence".format(row.confidence * 100f)) + 34f

        // Stats
        y = drawStat(canvas, MARGIN, y, "WHEN", timeFmt.format(Instant.ofEpochMilli(row.timestampUtc)))
        val coords = if (row.latitude != null && row.longitude != null)
            "%.4f, %.4f".format(row.latitude, row.longitude) else "Location not recorded"
        y = drawStat(canvas, MARGIN, y, "WHERE", coords)
        val weather = buildString {
            append(row.weatherCondition ?: "—")
            row.weatherTempC?.let { append(" · %.0f°C".format(it)) }
        }
        drawStat(canvas, MARGIN, y, "WEATHER", weather)

        drawFooter(canvas, "Identified on-device · ${row.modelName} ${row.modelVersion}")

        photo?.recycle()

        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        val file = File(dir, "card-${row.id}-${row.timestampUtc}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        file
    }

    /** Draw a single text line whose top sits at [top]; return the next free top y. */
    private fun drawLine(canvas: Canvas, text: String, paint: Paint, x: Float, top: Float): Float {
        val fm = paint.fontMetrics
        val baseline = top - fm.top
        canvas.drawText(text, x, baseline, paint)
        return baseline + fm.bottom
    }

    private fun drawBackground(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, W.toFloat(), H.toFloat(),
            intArrayOf(Color.parseColor("#10283F"), Color.parseColor("#1C466E"), Color.parseColor("#2E6BA8")),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)
    }

    private fun drawPhoto(canvas: Canvas, rect: RectF, photo: Bitmap?) {
        val radius = 48f
        if (photo != null) {
            canvas.save()
            val path = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
            canvas.clipPath(path)
            val scale = max(rect.width() / photo.width, rect.height() / photo.height)
            val dx = rect.left + (rect.width() - photo.width * scale) / 2f
            val dy = rect.top + (rect.height() - photo.height * scale) / 2f
            val m = Matrix().apply { setScale(scale, scale); postTranslate(dx, dy) }
            canvas.drawBitmap(photo, m, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
        } else {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#22FFFFFF") }
            canvas.drawRoundRect(rect, radius, radius, p)
            val icon = textPaint(Color.parseColor("#88FFFFFF"), 120f, bold = false)
            icon.textAlign = Paint.Align.CENTER
            canvas.drawText("🪶", rect.centerX(), rect.centerY() + 40f, icon)
        }
    }

    /** @return next free top y below the pill. */
    private fun drawPill(canvas: Canvas, x: Float, top: Float, label: String): Float {
        val tp = textPaint(Color.parseColor("#FFFFFF"), 42f, bold = true)
        val fm = tp.fontMetrics
        val padH = 34f
        val padV = 20f
        val textH = fm.bottom - fm.top
        val rect = RectF(x, top, x + tp.measureText(label) + padH * 2, top + textH + padV * 2)
        canvas.drawRoundRect(rect, 26f, 26f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3F86D6") })
        canvas.drawText(label, x + padH, top + padV - fm.top, tp)
        return rect.bottom
    }

    /** @return next free top y below the stat. */
    private fun drawStat(canvas: Canvas, x: Float, top: Float, label: String, value: String): Float {
        val lp = textPaint(Color.parseColor("#9FC6EE"), 32f, bold = true).apply { letterSpacing = 0.12f }
        var y = drawLine(canvas, label, lp, x, top) + 6f
        val vp = textPaint(Color.WHITE, 50f, bold = false)
        autoSize(vp, value, W - x * 2, 50f, 32f)
        y = drawLine(canvas, value, vp, x, y) + 26f
        return y
    }

    private fun drawFooter(canvas: Canvas, text: String) {
        val p = textPaint(Color.parseColor("#88FFFFFF"), 30f, bold = false)
        canvas.drawText(text, MARGIN, H - 60f, p)
    }

    private fun brandLabel() = textPaint(Color.parseColor("#8FC0F0"), 46f, bold = true).apply {
        letterSpacing = 0.18f
    }

    private fun titlePaint(text: String) = textPaint(Color.WHITE, 84f, bold = true).also {
        autoSize(it, text, W - MARGIN * 2, 84f, 46f)
    }

    private fun subtitlePaint() = textPaint(Color.parseColor("#CCFFFFFF"), 46f, bold = false).apply {
        textSkewX = -0.18f // pseudo-italic
    }

    private fun textPaint(color: Int, size: Float, bold: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun autoSize(p: Paint, text: String, maxWidth: Float, start: Float, min: Float) {
        var size = start
        p.textSize = size
        while (p.measureText(text) > maxWidth && size > min) {
            size -= 2f
            p.textSize = size
        }
    }

    private fun downloadBitmap(url: String): Bitmap? = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 12000
            setRequestProperty("User-Agent", "BirdFinder/0.1 (personal hobby app)")
        }
        conn.inputStream.use { BitmapFactory.decodeStream(it) }
    } catch (_: Throwable) {
        null
    }
}
