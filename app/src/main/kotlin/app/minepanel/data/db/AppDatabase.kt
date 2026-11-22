package app.minepanel.data.db

import android.content.Context
import androidx.room.Database
import java.io.File
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.minepanel.data.dao.AttributeScoreDao
import app.minepanel.data.dao.BadgeDao
import app.minepanel.data.dao.ConfirmedMemoryDao
import app.minepanel.data.dao.DailyAggregateDao
import app.minepanel.data.dao.DraftMemoryDao
import app.minepanel.data.dao.ManualFragmentDao
import app.minepanel.data.dao.MilestoneDao
import app.minepanel.data.dao.ModelRunLogDao
import app.minepanel.data.dao.PluginEventDao
import app.minepanel.data.dao.RawUsageEventDao
import app.minepanel.data.dao.ChatMessageDao
import app.minepanel.data.dao.ChatMemoryDao
import app.minepanel.data.dao.ChatMemoryDraftDao
import app.minepanel.data.dao.ChatSessionDao
import app.minepanel.data.db.entity.AttributeScore
import app.minepanel.data.db.entity.Badge
import app.minepanel.data.db.entity.ConfirmedMemory
import app.minepanel.data.db.entity.DailyAggregate
import app.minepanel.data.db.entity.DraftMemory
import app.minepanel.data.db.entity.ManualFragment
import app.minepanel.data.db.entity.Milestone
import app.minepanel.data.db.entity.ModelRunLog
import app.minepanel.data.db.entity.PluginEvent
import app.minepanel.data.db.entity.RawUsageEvent
import app.minepanel.data.db.entity.ChatMessage
import app.minepanel.data.db.entity.ChatMemory
import app.minepanel.data.db.entity.ChatMemoryDraft
import app.minepanel.data.db.entity.ChatSession
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        RawUsageEvent::class,
        DailyAggregate::class,
        AttributeScore::class,
        DraftMemory::class,
        ConfirmedMemory::class,
        Badge::class,
        ModelRunLog::class,
        PluginEvent::class,
        ManualFragment::class,
        Milestone::class,
        ChatMessage::class,
        ChatMemoryDraft::class,
        ChatMemory::class,
        ChatSession::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rawUsageEventDao(): RawUsageEventDao
    abstract fun dailyAggregateDao(): DailyAggregateDao
    abstract fun attributeScoreDao(): AttributeScoreDao
    abstract fun draftMemoryDao(): DraftMemoryDao
    abstract fun confirmedMemoryDao(): ConfirmedMemoryDao
    abstract fun badgeDao(): BadgeDao
    abstract fun modelRunLogDao(): ModelRunLogDao
    abstract fun pluginEventDao(): PluginEventDao
    abstract fun manualFragmentDao(): ManualFragmentDao
    abstract fun milestoneDao(): MilestoneDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun chatMemoryDraftDao(): ChatMemoryDraftDao
    abstract fun chatMemoryDao(): ChatMemoryDao
    abstract fun chatSessionDao(): ChatSessionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** 1 → 2：新增本人手写片段表；不改动既有表结构与数据。 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS manual_fragments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        content TEXT NOT NULL,
                        mood TEXT,
                        category TEXT,
                        anchorEpochMs INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_manual_fragments_createdAt ON manual_fragments(createdAt)")
            }
        }

        /** 2 → 3：新增本人自定义里程碑表；不改动既有表结构与数据。 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS milestones (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        label TEXT,
                        note TEXT,
                        anchorEpochMs INTEGER,
                        linkedFragmentId INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_milestones_anchorEpochMs ON milestones(anchorEpochMs)")
            }
        }

        /** 3 → 4：正式记忆增来源列（人工/自动确认）；既有行默认记为人工。 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE confirmed_memories ADD COLUMN source TEXT NOT NULL DEFAULT 'manual'"
                )
            }
        }

        /** 4 → 5：新增独立聊天消息表；不改动既有表结构与数据。 */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS chat_messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_createdAt ON chat_messages(createdAt)")
            }
        }

        /** 5 → 6：新增聊天记忆候选表与已采纳要点表；不改动既有表结构与数据。 */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS chat_memory_drafts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        content TEXT NOT NULL,
                        status TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        reviewedAt INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_memory_drafts_status ON chat_memory_drafts(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_memory_drafts_createdAt ON chat_memory_drafts(createdAt)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS chat_memories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        content TEXT NOT NULL,
                        source TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        originDraftId INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_memories_createdAt ON chat_memories(createdAt)")
            }
        }

        /** 6 → 7：新增聊天会话表；消息补归属会话列，既有消息归入默认会话。 */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS chat_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        lastMessageAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "INSERT INTO chat_sessions (title, createdAt, lastMessageAt) VALUES ('默认会话', 0, 0)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_sessions_lastMessageAt ON chat_sessions(lastMessageAt)")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN sessionId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_sessionId ON chat_messages(sessionId)")
            }
        }

        fun get(context: Context, passphrase: ByteArray): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: build(context, passphrase).also { instance = it }
            }
        }

        private fun build(context: Context, passphrase: ByteArray): AppDatabase {
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "minepanel.db"
            )
                .openHelperFactory(factory)
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_6, MIGRATION_6_7
                )
                .build()
        }

        /** 测试专用：进程内非加密内存库，不读写磁盘、不走 Keystore/SQLCipher。生产路径不调用。 */
        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, AppDatabase::class.java)
                .build()

        /** 关闭连接并物理删除数据库文件（含 WAL/SHM），供“删除全部数据”使用。 */
        @Synchronized
        fun closeAndReset(context: Context) {
            instance?.close()
            instance = null
            val dbFile = context.applicationContext.getDatabasePath("minepanel.db")
            listOf(
                dbFile,
                File(dbFile.parentFile, "${dbFile.name}-wal"),
                File(dbFile.parentFile, "${dbFile.name}-shm")
            ).forEach { if (it.exists()) it.delete() }
        }
    }
}
