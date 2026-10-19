package xyz.kurdcloud.englishmaster.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Database(
    entities = [QuestionEntity::class, LevelPackEntity::class, ResponseEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class EnglishMasterDatabase : RoomDatabase() {
    abstract fun questions(): QuestionDao
    abstract fun levelPacks(): LevelPackDao
    abstract fun responses(): ResponseDao
    abstract fun content(): ContentDao

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
