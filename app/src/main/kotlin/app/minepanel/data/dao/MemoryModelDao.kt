package app.minepanel.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.minepanel.data.db.entity.AttributeScore
import androidx.room.Upsert
import app.minepanel.data.db.entity.Badge
import app.minepanel.data.db.entity.ConfirmedMemory
import app.minepanel.data.db.entity.DraftMemory
import app.minepanel.data.db.entity.ManualFragment
import app.minepanel.data.db.entity.ModelRunLog
import app.minepanel.data.db.entity.PluginEvent

@Dao
interface AttributeScoreDao {

    @Upsert
    suspend fun upsert(score: AttributeScore)

    @Query("SELECT * FROM attribute_scores WHERE attributeKey = :key ORDER BY periodStart DESC LIMIT 1")
    suspend fun latest(key: String): AttributeScore?

    @Query("SELECT * FROM attribute_scores ORDER BY periodStart DESC")
    suspend fun all(): List<AttributeScore>

    @Query("SELECT COUNT(*) FROM attribute_scores WHERE attributeKey = :key AND periodStart = :periodStart AND periodEnd = :periodEnd")
    suspend fun countSame(key: String, periodStart: Long, periodEnd: Long): Int

    @Query("DELETE FROM attribute_scores")
    suspend fun clear()
}

@Dao
interface DraftMemoryDao {

    @Insert
    suspend fun insert(draft: DraftMemory): Long

    @Query(
        "UPDATE draft_memories SET status = :status, userEditedContent = :edited, reviewedAt = :reviewedAt " +
            "WHERE id = :id"
    )
    suspend fun review(id: Long, status: String, edited: String?, reviewedAt: Long)

    @Query("SELECT * FROM draft_memories WHERE status = :status ORDER BY createdAt DESC")
    suspend fun byStatus(status: String): List<DraftMemory>

    @Query("SELECT * FROM draft_memories WHERE draftType = :type AND status IN (:statuses) ORDER BY createdAt DESC")
    suspend fun byTypeAndStatuses(type: String, statuses: List<String>): List<DraftMemory>

    @Query("SELECT * FROM draft_memories WHERE id = :id")
    suspend fun byId(id: Long): DraftMemory?

    @Query("SELECT COUNT(*) FROM draft_memories WHERE draftType = :type AND status = :status AND contentJson = :label AND createdAt = :createdAt")
    suspend fun countTag(label: String, createdAt: Long, type: String, status: String): Int

    @Query("SELECT COUNT(*) FROM draft_memories WHERE draftType = :type AND status = :status AND contentJson = :content")
    suspend fun countConfirmedContent(content: String, type: String, status: String): Int

    @Query("DELETE FROM draft_memories")
    suspend fun clear()
}

@Dao
interface ConfirmedMemoryDao {

    @Insert
    suspend fun insert(memory: ConfirmedMemory): Long

    @Query("SELECT * FROM confirmed_memories WHERE periodStart BETWEEN :from AND :to ORDER BY periodStart DESC")
    suspend fun between(from: Long, to: Long): List<ConfirmedMemory>

    @Query("SELECT * FROM confirmed_memories ORDER BY periodStart DESC")
    suspend fun all(): List<ConfirmedMemory>

    @Query("DELETE FROM confirmed_memories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM confirmed_memories WHERE content = :content")
    suspend fun countContent(content: String): Int

    @Query("SELECT COUNT(*) FROM confirmed_memories WHERE content = :content AND periodStart = :periodStart AND periodEnd = :periodEnd")
    suspend fun countSame(content: String, periodStart: Long, periodEnd: Long): Int

    @Query("DELETE FROM confirmed_memories")
    suspend fun clear()
}

@Dao
interface BadgeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(badge: Badge)

    @Query("SELECT * FROM badges ORDER BY unlockedAt DESC")
    suspend fun all(): List<Badge>

    @Query("SELECT COUNT(*) FROM badges WHERE badgeKey = :key")
    suspend fun countByKey(key: String): Int

    @Query("DELETE FROM badges")
    suspend fun clear()
}

@Dao
interface ModelRunLogDao {

    @Insert
    suspend fun insert(log: ModelRunLog): Long

    @Query("SELECT * FROM model_run_logs ORDER BY startedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ModelRunLog>

    @Query("DELETE FROM model_run_logs")
    suspend fun clear()
}

@Dao
interface PluginEventDao {

    @Insert
    suspend fun insert(event: PluginEvent): Long

    @Query("SELECT * FROM plugin_events WHERE pluginType = :type ORDER BY timestamp DESC")
    suspend fun byType(type: String): List<PluginEvent>

    @Query("DELETE FROM plugin_events")
    suspend fun clear()
}

@Dao
interface ManualFragmentDao {

    @Insert
    suspend fun insert(fragment: ManualFragment): Long

    @Query("SELECT * FROM manual_fragments ORDER BY createdAt DESC")
    suspend fun all(): List<ManualFragment>

    @Query("SELECT * FROM manual_fragments WHERE createdAt BETWEEN :from AND :to ORDER BY createdAt DESC")
    suspend fun between(from: Long, to: Long): List<ManualFragment>

    @Query("SELECT COUNT(*) FROM manual_fragments WHERE content = :content")
    suspend fun countContent(content: String): Int

    @Query("DELETE FROM manual_fragments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM manual_fragments")
    suspend fun clear()
}
