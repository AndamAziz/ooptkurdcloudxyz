package xyz.kurdcloud.englishmaster.core.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Database(
    entities = [QuestionEntity::class, LevelPackEntity::class, ResponseEntity::class],
    version = 4,
    exportSchema = true,
    // v2: passage and media columns on questions (all nullable, so Room migrates automatically).
    // v3: tags column on questions (defaults to an empty list until the level is downloaded again).
    // v4: Kurdish translation columns on questions and responses.
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
@TypeConverters(Converters::class)
abstract class EnglishMasterDatabase : RoomDatabase() {
    abstract fun questions(): QuestionDao
    abstract fun levelPacks(): LevelPackDao
    abstract fun responses(): ResponseDao
    abstract fun content(): ContentDao

    /** Removes every downloaded question and saved answer (after the account was deleted). */
    suspend fun clearAll() = withContext(Dispatchers.IO) { clearAllTables() }

    companion object {
        fun create(context: Context): EnglishMasterDatabase =
            Room.databaseBuilder(context, EnglishMasterDatabase::class.java, "englishmaster.db").build()

        fun inMemory(context: Context): EnglishMasterDatabase =
            Room.inMemoryDatabaseBuilder(context, EnglishMasterDatabase::class.java).allowMainThreadQueries().build()
    }
}

internal class Converters {
    private val serializer = ListSerializer(String.serializer())

    @TypeConverter
    fun fromList(value: List<String>): String = Json.encodeToString(serializer, value)

    @TypeConverter
    fun toList(value: String): List<String> = Json.decodeFromString(serializer, value)
}
