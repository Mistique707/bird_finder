package com.example.birdfinder.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.birdfinder.data.db.DetectionEntity
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.ArrayList

/**
 * Helpers that turn app-private files into share intents via FileProvider so the
 * Android share sheet can hand them off to Drive / Mail / Files etc.
 */
object ShareUtil {

    private val FILE_TS: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

    private fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /**
     * Share a rendered card image + the audio clip together (with a text caption) as a
     * single multi-attachment share — a "wrapped"-style detection postcard.
     */
    fun shareDetectionCard(
        context: Context,
        cardImage: File,
        clipRelativePath: String,
    ) {
        // Audio FIRST so single-attachment targets keep the recording (the part that matters);
        // the card image carries all the text info visually.
        val uris = ArrayList<Uri>()
        val clip = File(context.filesDir, clipRelativePath)
        if (clip.isFile) uris += uriFor(context, clip)
        if (cardImage.isFile) uris += uriFor(context, cardImage)
        if (uris.isEmpty()) return

        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_SUBJECT, "Bird Finder detection")
            // NOTE: deliberately NO EXTRA_TEXT — when present, many targets switch to a
            // text-share path and silently drop the file attachments. The info is in the card.
            //
            // CRITICAL for SEND_MULTIPLE: FLAG_GRANT_READ_URI_PERMISSION only grants URIs
            // reachable via getData()/getClipData(), NOT EXTRA_STREAM. Without ClipData the
            // receiver can't read the audio, so it silently drops it.
            clipData = ClipData.newUri(context.contentResolver, "Bird Finder", uris[0]).apply {
                for (i in 1 until uris.size) addItem(ClipData.Item(uris[i]))
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, "Share detection").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun shareClip(context: Context, clipRelativePath: String, speciesCommon: String) {
        val full = File(context.filesDir, clipRelativePath)
        if (!full.isFile) return
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            full,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Bird Finder clip — $speciesCommon")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, "Share clip").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /**
     * Write [rows] as CSV under filesDir/exports/, then fire the share sheet.
     * Returns the file written, or null if no rows.
     */
    fun exportCsvAndShare(context: Context, rows: List<DetectionEntity>): File? {
        if (rows.isEmpty()) return null
        val exports = File(context.filesDir, "exports").apply { mkdirs() }
        val file = File(exports, "bird_finder-${FILE_TS.format(Instant.now())}.csv")
        file.bufferedWriter().use { w ->
            w.append("id,timestamp_utc,species_common,species_scientific,confidence,")
                .append("latitude,longitude,model_name,model_version,clip_path,")
                .append("weather_temp_c,weather_condition\n")
            rows.forEach { r ->
                w.append(r.id.toString()).append(',')
                w.append(Instant.ofEpochMilli(r.timestampUtc).toString()).append(',')
                w.append(csvEscape(r.speciesCommon)).append(',')
                w.append(csvEscape(r.speciesScientific)).append(',')
                w.append(r.confidence.toString()).append(',')
                w.append(r.latitude?.toString().orEmpty()).append(',')
                w.append(r.longitude?.toString().orEmpty()).append(',')
                w.append(csvEscape(r.modelName)).append(',')
                w.append(csvEscape(r.modelVersion)).append(',')
                w.append(csvEscape(r.clipPath)).append(',')
                w.append(r.weatherTempC?.toString().orEmpty()).append(',')
                w.append(csvEscape(r.weatherCondition ?: "")).append('\n')
            }
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Bird Finder export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, "Share export").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return file
    }

    private fun csvEscape(s: String): String {
        if (s.isEmpty()) return ""
        val needsQuoting = s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuoting) return s
        return "\"" + s.replace("\"", "\"\"") + "\""
    }
}
