package app.minepanel.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import app.minepanel.data.db.entity.Milestone

@Dao
interface MilestoneDao {

    @Insert
    suspend fun insert(milestone: Milestone): Long

    @Query(
        "SELECT * FROM milestones " +
            "ORDER BY COALESCE(anchorEpochMs, createdAt) DESC, updatedAt DESC"
    )
    suspend fun all(): List<Milestone>

    @Query("SELECT COUNT(*) FROM milestones WHERE title = :title")
    suspend fun countTitle(title: String): Int

    @Query("DELETE FROM milestones WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM milestones")
    suspend fun clear()
}
