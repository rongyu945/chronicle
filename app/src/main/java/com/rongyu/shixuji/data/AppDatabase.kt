package com.rongyu.shixuji.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

// ===== 待办（普通） =====
@Entity(tableName = "todos", indices = [Index("dueDate")])
data class Todo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val detail: String = "",
    val done: Boolean = false,
    val dueDate: Long = 0,       // epoch millis, 0 = 无期限
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val priority: Int = 0,   // 0=无 1=低 2=中 3=高
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0   // 手动拖动排序（越小越靠前；0=未手动安排）
)

@Dao
interface TodoDao {
    @Insert suspend fun insert(todo: Todo): Long
    @Query("DELETE FROM todos WHERE id = :id") suspend fun delete(id: Long)
    @Query("UPDATE todos SET done = :done WHERE id = :id") suspend fun setDone(id: Long, done: Boolean)
    @Query("UPDATE todos SET title = :title, detail = :detail, dueDate = :dueDate WHERE id = :id")
    suspend fun update(id: Long, title: String, detail: String, dueDate: Long)
    @Query("UPDATE todos SET priority = :priority WHERE id = :id") suspend fun setPriority(id: Long, priority: Int)
    @Query("UPDATE todos SET sortOrder = :order WHERE id = :id") suspend fun setSortOrder(id: Long, order: Int)
    /** 排序：手动顺序优先，其次未完成、优先级、截止日期 */
    @Query("SELECT * FROM todos ORDER BY sortOrder ASC, done ASC, priority DESC, dueDate ASC")
    fun observeAll(): Flow<List<Todo>>
    @Query("SELECT * FROM todos") suspend fun getAll(): List<Todo>
    @Query("DELETE FROM todos") suspend fun clear()
}

// ===== 账目 =====
@Entity(tableName = "accounts", indices = [Index("date")])
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amountCents: Long,       // 整数"分"，知识库记账共识
    val type: Int,               // 0=支出 1=收入
    val tag: String,
    val date: Long = System.currentTimeMillis()
)

@Dao
interface AccountDao {
    @Insert suspend fun insert(account: Account): Long
    @Query("DELETE FROM accounts WHERE id = :id") suspend fun delete(id: Long)
    @Query("UPDATE accounts SET title = :title, amountCents = :cents, type = :type, tag = :tag, date = :date WHERE id = :id")
    suspend fun update(id: Long, title: String, cents: Long, type: Int, tag: String, date: Long)
    @Query("SELECT * FROM accounts ORDER BY date DESC") fun observeAll(): Flow<List<Account>>
    /** 只取一个日期区间的账目（下推到 SQL，避免整库拉进内存） */
    @Query("SELECT * FROM accounts WHERE date >= :start AND date <= :end ORDER BY date DESC")
    fun observeBetween(start: Long, end: Long): Flow<List<Account>>
    /** 删除标签时把用到它的账目标签清空，避免留下"孤儿标签" */
    @Query("UPDATE accounts SET tag = '' WHERE tag = :name") suspend fun clearTag(name: String)
    @Query("SELECT COUNT(*) FROM accounts WHERE tag = :name") suspend fun countByTag(name: String): Int
    @Query("SELECT * FROM accounts") suspend fun getAll(): List<Account>
    @Query("DELETE FROM accounts") suspend fun clear()
}

// ===== 循环待办（每周几固定出现） =====
@Entity(tableName = "loop_todos")
data class LoopTodo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val detail: String = "",
    val dayOfWeek: Int,          // 1..7 (周一=1 ... 周日=7), 按需求每周几循环
    val doneThisWeek: Boolean = false
)

@Dao
interface LoopTodoDao {
    @Insert suspend fun insert(t: LoopTodo): Long
    @Query("DELETE FROM loop_todos WHERE id = :id") suspend fun delete(id: Long)
    @Query("UPDATE loop_todos SET doneThisWeek = :done WHERE id = :id") suspend fun setDone(id: Long, done: Boolean)
    /** 进入新的一周时统一清零（否则"每周循环"永远只生效一次） */
    @Query("UPDATE loop_todos SET doneThisWeek = 0") suspend fun resetAllDone()
    @Query("SELECT * FROM loop_todos ORDER BY dayOfWeek ASC") fun observeAll(): Flow<List<LoopTodo>>
    @Query("SELECT * FROM loop_todos") suspend fun getAll(): List<LoopTodo>
    @Query("DELETE FROM loop_todos") suspend fun clear()
}

// ===== 记账标签（可增删改） =====
@Entity(tableName = "acc_tags")
data class AccTagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Long,             // RGB int
    val icon: String = ""
)

@Dao
interface AccTagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(t: AccTagEntity): Long
    @Query("DELETE FROM acc_tags WHERE id = :id") suspend fun delete(id: Long)
    @Query("UPDATE acc_tags SET name = :name, color = :color WHERE id = :id") suspend fun update(id: Long, name: String, color: Long)
    @Query("SELECT * FROM acc_tags ORDER BY id ASC") fun observeAll(): Flow<List<AccTagEntity>>
    @Query("SELECT * FROM acc_tags") suspend fun getAll(): List<AccTagEntity>
    @Query("DELETE FROM acc_tags") suspend fun clear()
}

// ===== AI 模型 / Key（设置里录入，看板读） =====
@Entity(tableName = "api_models")
data class ApiModelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val modelName: String,
    val quota: String,
    val resetDate: String,
    val resetCycle: String,
    val baseUrl: String,
    val apiKey: String,
    val keyName: String,
    val platform: String,
    val color: Long
)

@Dao
interface ApiModelDao {
    @Insert suspend fun insert(m: ApiModelEntity): Long
    @Query("DELETE FROM api_models WHERE id = :id") suspend fun delete(id: Long)
    @Query("UPDATE api_models SET modelName=:name, quota=:quota, resetDate=:rd, resetCycle=:rc, baseUrl=:bu, apiKey=:ak, keyName=:kn, platform=:pf, color=:color WHERE id=:id")
    suspend fun update(id: Long, name: String, quota: String, rd: String, rc: String, bu: String, ak: String, kn: String, pf: String, color: Long)
    @Query("SELECT * FROM api_models ORDER BY id ASC") fun observeAll(): Flow<List<ApiModelEntity>>
    @Query("SELECT * FROM api_models") suspend fun getAll(): List<ApiModelEntity>
    @Query("DELETE FROM api_models") suspend fun clear()
}

// ===== 常用记账（记账页顶部的一键快捷） =====
@Entity(tableName = "quick_entries")
data class QuickEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amountCents: Long,
    val type: Int = 0,           // 0=支出 1=收入
    val tag: String = "",
    val sortOrder: Int = 0
)

@Dao
interface QuickEntryDao {
    @Insert suspend fun insert(e: QuickEntry): Long
    @Query("DELETE FROM quick_entries WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM quick_entries ORDER BY sortOrder ASC, id ASC") fun observeAll(): Flow<List<QuickEntry>>
    @Query("SELECT * FROM quick_entries") suspend fun getAll(): List<QuickEntry>
    @Query("DELETE FROM quick_entries") suspend fun clear()
}

// ===== 应用偏好（底栏顺序、宋体开关、外观模式、循环周戳、月预算、字号） =====
@Entity(tableName = "app_prefs")
data class AppPref(
    @PrimaryKey val key: String,
    val value: String
)

@Dao
interface AppPrefDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(pref: AppPref)
    @Query("SELECT value FROM app_prefs WHERE `key` = :key") suspend fun get(key: String): String?
    @Query("SELECT * FROM app_prefs") fun observeAll(): Flow<List<AppPref>>
}

@Database(
    entities = [Todo::class, Account::class, LoopTodo::class, AccTagEntity::class, ApiModelEntity::class, AppPref::class, QuickEntry::class],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
    abstract fun accountDao(): AccountDao
    abstract fun loopTodoDao(): LoopTodoDao
    abstract fun accTagDao(): AccTagDao
    abstract fun apiModelDao(): ApiModelDao
    abstract fun appPrefDao(): AppPrefDao
    abstract fun quickEntryDao(): QuickEntryDao
}