package xyz.kurdcloud.englishmaster.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Upgrading the app must keep downloaded questions and unsent answers (schema 1 -> 2). */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val schemaDir = File("schemas/xyz.kurdcloud.englishmaster.core.database.EnglishMasterDatabase")

    @Test
    fun version1DatabaseIsMigratedWithItsData() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = context.getDatabasePath("migration-test.db").apply { parentFile?.mkdirs(); delete() }

        // Build the version-1 database exactly as the exported schema describes it.
        val schema = Json.parseToJsonElement(File(schemaDir, "1.json").readText()).jsonObject["database"]!!.jsonObject
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            schema["entities"]!!.jsonArray.forEach { e ->
                val table = e.jsonObject["tableName"]!!.jsonPrimitive.content
                db.execSQL(e.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                e.jsonObject["indices"]?.jsonArray?.forEach { i ->
                    db.execSQL(i.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            schema["setupQueries"]!!.jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            db.execSQL(
                "INSERT INTO questions (id, cefr_level, skill, prompt, options, version, review_status, server_updated_at, downloaded_at) " +
                    "VALUES ('q1', 'A1', 'grammar', 'He ___ happy.', '[\"is\",\"are\"]', 1, 'approved', 't', 1)",
            )
            db.execSQL(
                "INSERT INTO responses (response_id, user_id, attempt_id, question_id, cefr_level, chosen_option, answered_at, sync_state, sync_attempts) " +
                    "VALUES ('r1', 'u1', 'a1', 'q1', 'A1', 'A', 5, 'PENDING', 0)",
            )
            db.version = 1
        }

        val db = Room.databaseBuilder(context, EnglishMasterDatabase::class.java, file.name).allowMainThreadQueries().build()
        try {
            val q = db.questions().byId("q1")!!
            assertEquals(listOf("is", "are"), q.options)
            assertNull(q.mediaPath)
            assertEquals("PENDING", db.responses().byId("r1")!!.syncState)
            db.content().replaceLevel(
                "A1",
                listOf(q.copy(passage = "Ali is happy.", mediaType = "image", mediaPath = "samples/x.png", mediaLocal = "/tmp/x.png")),
                LevelPackEntity("A1", 1, 2),
            )
            assertEquals("samples/x.png", db.questions().byId("q1")!!.mediaPath)
        } finally {
            db.close()
            file.delete()
        }
    }
}
