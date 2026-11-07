package com.inspection.demo.phone

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.inspection.demo.protocol.InspectionSyncEntry

/**
 * The phone owns one active task at a time. Room gives the snapshot an atomic
 * durable boundary while keeping the domain model independent of Android.
 */
@Entity(tableName = "inspection_task_snapshot")
data class TaskSnapshotEntity(
    @androidx.room.PrimaryKey val slot: Int = ACTIVE_SLOT,
    val taskId: String,
    val stateJson: String,
    val updatedAt: Long,
) {
    private companion object { const val ACTIVE_SLOT = 1 }
}

@Dao
interface TaskSnapshotDao {
    @Query("SELECT * FROM inspection_task_snapshot WHERE slot = 1 LIMIT 1")
    suspend fun loadActive(): TaskSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveActive(snapshot: TaskSnapshotEntity)

    @Query("DELETE FROM inspection_task_snapshot WHERE slot = 1")
    suspend fun clearActive()
}

@Entity(tableName = "inspection_task_history")
data class TaskHistoryEntity(
    @androidx.room.PrimaryKey val taskId: String,
    val stateJson: String,
    val status: String,
    val startedAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val completedAtEpochMs: Long?,
)

@Dao
interface TaskHistoryDao {
    @Query("SELECT * FROM inspection_task_history ORDER BY updatedAtEpochMs DESC")
    suspend fun loadAll(): List<TaskHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: TaskHistoryEntity)
}

@Entity(tableName = "inspection_sync_queue")
data class SyncQueueEntity(
    @androidx.room.PrimaryKey val reportId: String,
    val taskId: String,
    val reportJson: String,
    val enqueuedAtEpochMs: Long,
    val state: String,
    val attempts: Int,
    val nextAttemptAtEpochMs: Long,
    val lastError: String?,
    val sendingAtEpochMs: Long,
) {
    fun toProtocol(): InspectionSyncEntry = InspectionSyncEntry(
        reportId, taskId, reportJson, enqueuedAtEpochMs, state, attempts,
        nextAttemptAtEpochMs, lastError, sendingAtEpochMs,
    )

    companion object {
        fun fromProtocol(entry: InspectionSyncEntry) = SyncQueueEntity(
            entry.reportId, entry.taskId, entry.reportJson, entry.enqueuedAtEpochMs,
            entry.state, entry.attempts, entry.nextAttemptAtEpochMs,
            entry.lastError, entry.sendingAtEpochMs,
        )
    }
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM inspection_sync_queue ORDER BY enqueuedAtEpochMs ASC")
    suspend fun loadAll(): List<SyncQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<SyncQueueEntity>)

    @Query("DELETE FROM inspection_sync_queue")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(entries: List<SyncQueueEntity>) {
        clearAll()
        if (entries.isNotEmpty()) insertAll(entries)
    }
}

@Database(entities = [TaskSnapshotEntity::class, TaskHistoryEntity::class, SyncQueueEntity::class], version = 3, exportSchema = false)
abstract class InspectionDatabase : RoomDatabase() {
    abstract fun taskSnapshotDao(): TaskSnapshotDao
    abstract fun taskHistoryDao(): TaskHistoryDao
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        @Volatile private var instance: InspectionDatabase? = null

        fun get(context: Context): InspectionDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    InspectionDatabase::class.java,
                    "inspection-phone.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS inspection_sync_queue (
                        reportId TEXT NOT NULL,
                        taskId TEXT NOT NULL,
                        reportJson TEXT NOT NULL,
                        enqueuedAtEpochMs INTEGER NOT NULL,
                        state TEXT NOT NULL,
                        attempts INTEGER NOT NULL,
                        nextAttemptAtEpochMs INTEGER NOT NULL,
                        lastError TEXT,
                        sendingAtEpochMs INTEGER NOT NULL,
                        PRIMARY KEY(reportId)
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS inspection_task_history (
                        taskId TEXT NOT NULL,
                        stateJson TEXT NOT NULL,
                        status TEXT NOT NULL,
                        startedAtEpochMs INTEGER NOT NULL,
                        updatedAtEpochMs INTEGER NOT NULL,
                        completedAtEpochMs INTEGER,
                        PRIMARY KEY(taskId)
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
