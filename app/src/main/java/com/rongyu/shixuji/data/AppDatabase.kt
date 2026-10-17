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
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,  // 手动拖动排序（越小越靠前；0=未手动安排）
    /** 完成时刻（勾选瞬间写入、取消勾选清零）：已完成分区按它倒序，最近完成的排最前 */
    @ColumnInfo(defaultValue = "0") val doneAt: Long = 0L
)

@Dao
interface TodoDao {
    @Insert suspend fun insert(todo: Todo): Long
    /**
     * 撤回专用插入：同 id 覆盖。
     *
     * 为什么不能用上面那个 insert —— 撤回「修改」时那条**还在库里**，
     * 用 INSERT OR ABORT 会撞主键抛 SQLiteConstraintException（撤回一下就把应用搞崩）。
     * 撤回的语义就是「让这条回到从前的样子」，覆盖是对的。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(todo: Todo): Long
    @Query("DELETE FROM todos WHERE id = :id") suspend fun delete(id: Long)
    @Query("UPDATE todos SET done = :done WHERE id = :id") suspend fun setDone(id: Long, done: Boolean)
    @Query("UPDATE todos SET title = :title, detail = :detail, dueDate = :dueDate WHERE id = :id")
    suspend fun update(id: Long, title: String, detail: String, dueDate: Long)
    @Query("UPDATE todos SET priority = :priority WHERE id = :id") suspend fun setPriority(id: Long, priority: Int)
    @Query("UPDATE todos SET sortOrder = :order WHERE id = :id") suspend fun setSortOrder(id: Long, order: Int)
    @Query("SELECT * FROM todos WHERE id = :id") suspend fun getById(id: Long): Todo?
    /**
     * 全表最小的手动顺序值：新增待办时取它再减 1，让新条目**恒排在最上面**
     * （用户 2026-10-14：新建的就放最上面）。数据库顺序才是列表顺序的来源，
     * 只靠内存里的 openOrder 在重建 App 后会掉回原位。
     */
    @Query("SELECT COALESCE(MIN(sortOrder), 0) FROM todos") suspend fun minSortOrder(): Int
    /**
     * 勾选 / 取消勾选：同时记下完成时刻（已完成分区按它倒序）。
     * 排序里 [done] 只作为**最后**一级、且在 done == 1 那些之间才起作用
     * （CASE 表达式），不会改变「未完成之间」和「已完成之间」的相对顺序。
     */
    @Query("UPDATE todos SET done = :done, doneAt = :at WHERE id = :id")
    suspend fun setDoneFull(id: Long, done: Boolean, at: Long)
    /**
     * 排序：手动顺序优先，其次优先级、截止日期；勾掉的沉底（同一分区内相对顺序不变）。
     *
     * 说明：用 CASE 而不是直接按 done 排 —— 直接按 done 排会在「勾选那一刻」重排整个列表，
     * 未完成条目之间会被优先级/日期重新洗一遍（用户看到的「条目乱跳」）。
     * 现在只有 done 那一级参与比较，勾掉的一条落到已完成区，其余相对顺序原样保留。
     */
    @Query("SELECT * FROM todos ORDER BY sortOrder ASC, CASE WHEN done = 1 THEN doneAt ELSE 0 END ASC, priority DESC, dueDate ASC")
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
    /** 撤回专用插入：同 id 覆盖（同 TodoDao.upsert，撤回「修改」时那条还在库里） */
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(account: Account): Long
    @Query("DELETE FROM accounts WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM accounts WHERE id = :id") suspend fun getById(id: Long): Account?
    @Query("UPDATE accounts SET title = :title, amountCents = :cents, type = :type, tag = :tag, date = :date WHERE id = :id")
    suspend fun update(id: Long, title: String, cents: Long, type: Int, tag: String, date: Long)
    @Query("SELECT * FROM accounts ORDER BY date DESC") fun observeAll(): Flow<List<Account>>
    /** 只取一个日期区间的账目（下推到 SQL，避免整库拉进内存） */
    @Query("SELECT * FROM accounts WHERE date >= :start AND date <= :end ORDER BY date DESC")
    fun observeBetween(start: Long, end: Long): Flow<List<Account>>
    /** 修改标签时同步改名：账目是用**标签名字符串**关联的（account.tag），
     *  只改 acc_tags.name 会让老账目全部变成孤儿标签（灰点、饼图掉进「其他」）。 */
    @Query("UPDATE accounts SET tag = :newName WHERE tag = :oldName") suspend fun renameTag(oldName: String, newName: String)
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

    /**
     * 恢复备份时把「界面设置」整组抹掉重写用（见 SettingsViewModel.restoreFromJson）。
     * 只传给它的都是界面设置键，不含 loop_week 这类内部状态 —— 内部状态不该被备份覆盖。
     */
    @Query("DELETE FROM app_prefs WHERE `key` IN (:keys)") suspend fun removeAll(keys: List<String>)
}

@Database(
    entities = [Todo::class, Account::class, LoopTodo::class, AccTagEntity::class, AppPref::class, QuickEntry::class],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        /**
         * 备份文件里「界面设置」对应的偏好键名。恢复时这一组是**整组替换**：
         * 先删掉这五个键，再写入文件里带的那些。
         *
         * 为什么不是「只覆盖文件里带的键」：文件里没有 font_scale，可能有两种意思 ——
         * 「导出时没设过字号」或「这版 App 还没有字号功能」。前者应当恢复成默认字号，
         * 后者不该动当前值。既然分不清，就按「备份 = 那一刻的完整状态」来理解：
         * 这一组一律以备份为准，备份里没有 = 回到默认。这条组里不含 loop_week
         * 这类内部状态（它不是"设置"，被覆盖会让循环待办提前/延后一周清零）。
         */
        val PREF_KEYS = listOf("budget_month", "font_scale", "songti", "dark", "tab_order")

        /**
         * 当前数据库版本。**改 @Database(version = ...) 时必须同步改这里** —— 备份文件靠它
         * 记录"这份数据来自哪一版"，恢复时才知道字段够不够。之所以另存一个常量而不是读
         * Room 运行时值，是因为导出备份的地方（SettingsViewModel）不该为了一个数字去碰数据库实例。
         */
        const val DB_VERSION = 6
    }

    abstract fun todoDao(): TodoDao
    abstract fun accountDao(): AccountDao
    abstract fun loopTodoDao(): LoopTodoDao
    abstract fun accTagDao(): AccTagDao
    abstract fun appPrefDao(): AppPrefDao
    abstract fun quickEntryDao(): QuickEntryDao
}