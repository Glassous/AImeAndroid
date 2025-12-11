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
    version = 7,
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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN modelDisplayName TEXT")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add uuid column with default empty string to satisfy NOT NULL constraint
                db.execSQL("ALTER TABLE conversations ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                
                // Populate uuid for existing records with random UUIDs
                val cursor = db.query("SELECT id FROM conversations")
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val uuid = java.util.UUID.randomUUID().toString()
                    db.execSQL("UPDATE conversations SET uuid = '$uuid' WHERE id = $id")
                }
                cursor.close()
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add uuid column with default empty string to satisfy NOT NULL constraint
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                
                // Populate uuid for existing records with random UUIDs
                val cursor = db.query("SELECT id FROM chat_messages")
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val uuid = java.util.UUID.randomUUID().toString()
                    db.execSQL("UPDATE chat_messages SET uuid = '$uuid' WHERE id = $id")
                }
                cursor.close()
            }
        }

        fun getDatabase(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat_database"
                ).addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
