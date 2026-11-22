package app.minepanel.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.minepanel.data.db.entity.DailyAggregate
import app.minepanel.data.db.entity.RawUsageEvent

@Dao
interface RawUsageEventDao {

    @Insert
    suspend fun insert(event: RawUsageEvent): Long

    @Insert
    suspend fun insertAll(events: List<RawUsageEvent>)

    @Query("SELECT COUNT(*) FROM raw_usage_events")
    suspend fun count(): Long

    @Query("SELECT * FROM raw_usage_events WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp")
    suspend fun range(from: Long, to: Long): List<RawUsageEvent>

    @Query("SELECT * FROM raw_usage_events ORDER BY timestamp")
    suspend fun all(): List<RawUsageEvent>

    @Query("SELECT COUNT(*) FROM raw_usage_events WHERE timestamp = :timestamp AND eventType = :eventType AND category = :category AND durationMs = :durationMs AND sessionId IS :sessionId")
    suspend fun countSame(timestamp: Long, eventType: String, category: String, durationMs: Long, sessionId: String?): Int

    @Query("DELETE FROM raw_usage_events")
    suspend fun clear()
}

@Dao
interface DailyAggregateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DailyAggregate)

    @Query("SELECT * FROM daily_aggregates WHERE date = :date")
    suspend fun byDate(date: String): List<DailyAggregate>

    @Query("SELECT * FROM daily_aggregates WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun between(from: String, to: String): List<DailyAggregate>

    @Query("DELETE FROM daily_aggregates WHERE date BETWEEN :fromDate AND :toDate")
    suspend fun deleteBetween(fromDate: String, toDate: String)

    @Query("DELETE FROM daily_aggregates")
    suspend fun clear()
}
