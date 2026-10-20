package xyz.kurdcloud.englishmaster.feature.exam

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.core.graphics.withTranslation
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.kurdcloud.englishmaster.core.model.Certificate
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** QR modules for [text], [size] x [size] pixels with a 1-module quiet zone. Pure Java (zxing). */
fun qrMatrix(text: String, size: Int): BitMatrix =
    QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
    )

/**
 * Renders a certificate as a one-page A4 landscape PDF in the app cache and returns a
 * content:// Uri that viewer and share apps can read.
 */
class CertificateExporter(private val context: Context, private val verificationUrl: (Certificate) -> String) {

    suspend fun export(certificate: Certificate): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "certificates").apply { mkdirs() }
        val file = File(dir, "EnglishMaster-${certificate.level.label}-${certificate.code}.pdf")
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, 1).create())
            draw(page.canvas, certificate)
            document.finishPage(page)
            file.outputStream().use { document.writeTo(it) }
        } finally {
            document.close()
        }
        FileProvider.getUriForFile(context, "${context.packageName}.certificates", file)
    }

    /** Draws the certificate page; also used by tests to render a preview bitmap. */
    internal fun draw(canvas: Canvas, c: Certificate) {
        val navy = Color.rgb(0x1B, 0x2A, 0x5E)
        val gold = Color.rgb(0xC9, 0xA2, 0x27)
        val grey = Color.rgb(0x55, 0x5B, 0x6B)

        canvas.drawColor(Color.WHITE)
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        frame.color = navy; frame.strokeWidth = 6f
        canvas.drawRect(RectF(18f, 18f, WIDTH - 18f, HEIGHT - 18f), frame)
        frame.color = gold; frame.strokeWidth = 1.5f
        canvas.drawRect(RectF(30f, 30f, WIDTH - 30f, HEIGHT - 30f), frame)

        // Level seal, top left.
        val seal = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = navy }
        canvas.drawCircle(105f, 105f, 44f, seal)
        seal.color = gold; seal.style = Paint.Style.STROKE; seal.strokeWidth = 3f
        canvas.drawCircle(105f, 105f, 38f, seal)
        text(canvas, c.level.label, 24f, Color.WHITE, bold = true, x = 61f, width = 88f, y = 90f)

        text(canvas, "ENGLISHMASTER AI", 13f, gold, bold = true, y = 62f, letterSpacing = 0.25f)
        text(canvas, "Certificate of Achievement", 34f, navy, bold = true, y = 80f, serif = true)
        text(canvas, "بڕوانامەی سەرکەوتن", 18f, navy, y = 132f)
        text(canvas, "This certifies that  ·  ئەمە دەیسەلمێنێت کە", 13f, grey, y = 178f)
        text(canvas, c.holderName, 30f, Color.BLACK, bold = true, y = 202f, serif = true)
        val line = Paint().apply { color = gold; strokeWidth = 1.2f }
        canvas.drawLine(WIDTH / 2f - 190f, 250f, WIDTH / 2f + 190f, 250f, line)
        text(canvas, "has passed the EnglishMaster ${c.level.label} level final exam", 15f, navy, y = 266f)
        text(canvas, "بە سەرکەوتوویی تاقیکردنەوەی کۆتایی ئاستی ${c.level.label} ـی تێپەڕاندووە", 14f, navy, y = 292f)

        val percent = if (c.total == 0) 0 else c.score * 100 / c.total
        val date = DateFormat.getDateInstance(DateFormat.LONG, Locale.UK).format(Date(c.issuedAtMillis))
        val details = listOf(
            "Exam score:  ${c.score} / ${c.total}  ($percent%)",
            "Date of issue:  $date",
            "Certificate ID:  ${c.code}",
            "Verify:  scan the QR code or open the link below",
        )
        details.forEachIndexed { i, d -> text(canvas, d, 12.5f, grey, x = 70f, width = 520f, y = 360f + i * 24f, align = Layout.Alignment.ALIGN_NORMAL) }
        text(canvas, verificationUrl(c), 8.5f, grey, x = 70f, width = 560f, y = 458f, align = Layout.Alignment.ALIGN_NORMAL)

        // QR code, bottom right, linking to the public verification page.
        val qrSize = 128
        val qr = qrBitmap(verificationUrl(c), qrSize)
        canvas.drawBitmap(qr, WIDTH - 70f - qrSize, 340f, null)
        qr.recycle()
        text(canvas, "Scan to verify", 9f, grey, x = WIDTH - 70f - qrSize, width = qrSize.toFloat(), y = 472f)

        text(
            canvas,
            "A level achievement certificate of EnglishMaster AI, aligned to CEFR level descriptors. It is not an official CEFR certification.",
            8.5f, grey, y = HEIGHT - 62f,
        )
    }

    private fun qrBitmap(text: String, size: Int): Bitmap {
        val m = qrMatrix(text, size)
        val bmp = createBitmap(m.width, m.height)
        for (y in 0 until m.height) for (x in 0 until m.width) bmp[x, y] = if (m[x, y]) Color.BLACK else Color.WHITE
        return bmp
    }

    /** Draws wrapped text; StaticLayout handles shaping and right-to-left Sorani correctly. */
    private fun text(
        canvas: Canvas,
        value: String,
        size: Float,
        color: Int,
        y: Float,
        x: Float = 40f,
        width: Float = WIDTH - 80f,
        bold: Boolean = false,
        serif: Boolean = false,
        letterSpacing: Float = 0f,
        align: Layout.Alignment = Layout.Alignment.ALIGN_CENTER,
    ) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            this.letterSpacing = letterSpacing
            typeface = Typeface.create(if (serif) Typeface.SERIF else Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, width.toInt()).setAlignment(align).build()
        canvas.withTranslation(x, y) { layout.draw(this) }
    }

    internal companion object {
        // A4 landscape in PostScript points.
        const val WIDTH = 842
        const val HEIGHT = 595
    }
}
