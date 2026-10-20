package xyz.kurdcloud.englishmaster.feature.exam

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import xyz.kurdcloud.englishmaster.core.model.Certificate
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import java.io.File

/**
 * PdfDocument itself needs the device's native library (not available under Robolectric), so this
 * renders the same page drawing onto a bitmap. Set CERTIFICATE_PREVIEW=/path.png to save it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CertificatePdfTest {
    @Test
    fun pageDrawsTextFrameAndQrCode() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val c = Certificate(CefrLevel.B1, "Sara Ahmed", 18, 20, "EM-ABCD-EFGH-JKLM", 1_792_500_000_000)
        val exporter = CertificateExporter(context) { "https://x.supabase.co/functions/v1/verify-certificate?code=${it.code}" }
        val bitmap = Bitmap.createBitmap(CertificateExporter.WIDTH * 2, CertificateExporter.HEIGHT * 2, Bitmap.Config.ARGB_8888)
        exporter.draw(Canvas(bitmap).apply { scale(2f, 2f) }, c)

        System.getenv("CERTIFICATE_PREVIEW")?.let { path -> File(path).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }

        // Navy frame at the edge, and a dark-and-light QR area at the bottom right.
        val frame = bitmap.getPixel(2 * 18, 2 * 300)
        assertTrue(Color.blue(frame) > Color.red(frame))
        var dark = 0
        var light = 0
        for (y in 2 * 345 until 2 * 460 step 4) for (x in 2 * (842 - 190) until 2 * (842 - 75) step 4) {
            if (Color.red(bitmap.getPixel(x, y)) < 60) dark++ else light++
        }
        assertTrue("QR code drawn (dark=$dark light=$light)", dark > 200 && light > 200)
    }
}
