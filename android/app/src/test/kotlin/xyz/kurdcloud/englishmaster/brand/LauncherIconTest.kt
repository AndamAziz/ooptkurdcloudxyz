package xyz.kurdcloud.englishmaster.brand

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Draws the adaptive launcher icon layers the way a launcher does and checks the logo is where it
 * should be. Set LAUNCHER_ICON_PREVIEW=/path.png to save the result.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherIconTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val size = 432 // 108dp at 4x

    private fun layer(id: Int, into: Canvas) {
        ContextCompat.getDrawable(context, id)!!.apply { setBounds(0, 0, size, size) }.draw(into)
    }

    @Test
    fun logoSitsInsideTheSafeZone() {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        layer(xyz.kurdcloud.englishmaster.core.designsystem.R.drawable.ic_launcher_background, canvas)
        layer(xyz.kurdcloud.englishmaster.core.designsystem.R.drawable.ic_launcher_foreground, canvas)
        System.getenv("LAUNCHER_ICON_PREVIEW")?.let { path -> File(path).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }

        val s = size / 108f
        val corner = bitmap.getPixel((4 * s).toInt(), (4 * s).toInt())
        assertTrue("indigo background", Color.blue(corner) > 150 && Color.red(corner) < 90)
        val sun = bitmap.getPixel((54 * s).toInt(), (50.5 * s).toInt())
        assertTrue("golden sun", Color.red(sun) > 230 && Color.green(sun) > 170 && Color.blue(sun) < 150)
        val page = bitmap.getPixel((40 * s).toInt(), (60 * s).toInt())
        assertTrue("white page", Color.red(page) > 240 && Color.green(page) > 240 && Color.blue(page) > 240)

        // Nothing but background outside the 66dp circle a launcher may keep.
        val bg = ContextCompat.getDrawable(context, xyz.kurdcloud.englishmaster.core.designsystem.R.drawable.ic_launcher_background)!!
        val plain = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bg.setBounds(0, 0, size, size); bg.draw(Canvas(it)) }
        var outside = 0
        for (y in 0 until size step 2) for (x in 0 until size step 2) {
            val dx = x / s - 54
            val dy = y / s - 54
            if (dx * dx + dy * dy > 33f * 33f && bitmap.getPixel(x, y) != plain.getPixel(x, y)) outside++
        }
        assertTrue("artwork outside the safe zone ($outside px)", outside < 40)
    }

    @Test
    fun monochromeLayerDraws() {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        layer(xyz.kurdcloud.englishmaster.core.designsystem.R.drawable.ic_launcher_monochrome, Canvas(bitmap))
        val s = size / 108f
        assertTrue(Color.alpha(bitmap.getPixel((54 * s).toInt(), (50.5 * s).toInt())) > 200)
        assertTrue(Color.alpha(bitmap.getPixel((4 * s).toInt(), (4 * s).toInt())) == 0)
    }
}
