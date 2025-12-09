package com.glassous.aime.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.glassous.aime.data.dao.ModelConfigDao
import com.glassous.aime.data.model.Model
import com.glassous.aime.data.model.ModelGroup

@Database(
    entities = [ChatMessage::class, Conversation::class, ModelGroup::class, Model::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun modelConfigDao(): ModelConfigDao

    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // conversations: add soft delete columns
                db.execSQL("ALTER TABLE conversations ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE conversations ADD COLUMN deletedAt INTEGER")
                // chat_messages: add soft delete columns
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN deletedAt INTEGER")
                // model_groups: add soft delete columns
                db.execSQL("ALTER TABLE model_groups ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE model_groups ADD COLUMN deletedAt INTEGER")
                // models: add soft delete columns
                db.execSQL("ALTER TABLE models ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE models ADD COLUMN deletedAt INTEGER")
            }
        }

        fun getDatabase(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat_database"
                ).addMigrations(MIGRATION_3_4)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
