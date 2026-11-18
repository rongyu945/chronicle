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
    // （原 setDone 已删：它只改 done、不清 doneAt，勾上后 doneAt 还是 0，
    //   dayOpen 排除它、dayDone 又要求 doneAt 落在当天 → 条目会从界面彻底消失。
    //   勾选统一走下面的 setDoneFull。）
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
    // 2026-11-07 用户：「待办界面怎么都不按照重要性排序的呀？」
    // 根因：原来 `sortOrder ASC` 是第一键，而新建待办拿的是 `minSortOrder() - 1`（排最上），
    // 于是列表顺序 = 创建顺序倒过来，`priority DESC` 永远排不上用场 —— 高优先级的反而垫底。
    // 现在 `priority DESC` 提到第一键：高 → 中 → 低 → 无。
    // 同档之内仍按 `sortOrder ASC`，所以手动拖拽和「新建排本档最上」都还在，
    // 只是拖不过优先级那一道坎（想挪到别的档就改优先级，这也是它该有的语义）。
    // ★ 已完成那一级必须是 DESC：doneAt 越大 = 越晚完成，用户要的是「最近完成的排最前」。
    //   旧写法 ASC 正好相反（最早完成在最前），只是被 TodoScreen 里再 `sortedByDescending { doneAt }`
    //   盖住了；任何直接用 observeAll() 顺序渲染的地方都会看到反的结果。
    @Query("SELECT * FROM todos ORDER BY priority DESC, sortOrder ASC, CASE WHEN done = 1 THEN doneAt ELSE 0 END DESC, dueDate ASC")
    fun observeAll(): Flow<List<Todo>>
    @Query("SELECT * FROM todos") suspend fun getAll(): List<Todo>
    @Query("DELETE FROM todos") suspend fun clear()

    // ── 批量整理专用（5.7.1）：一条 SQL 处理一批，不要 forEach 循环开事务 ──
    @Query("DELETE FROM todos WHERE id IN (:ids)") suspend fun deleteAll(ids: List<Long>)
    @Query("UPDATE todos SET dueDate = :dueDate WHERE id IN (:ids)")
    suspend fun setDueAll(ids: List<Long>, dueDate: Long)
    @Query("UPDATE todos SET priority = :priority WHERE id IN (:ids)")
    suspend fun setPriorityAll(ids: List<Long>, priority: Int)
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
    /**
     * 某个时间点之后各标签的记账次数（记账弹窗里「标签按最近 30 天最常用排序」用）。
     * 用 tag 聚合比先 getAll() 再在 Kotlin 里 groupingBy 省一次整表搬运。
     */
    @Query("SELECT tag AS tag, COUNT(*) AS cnt FROM accounts WHERE tag != '' AND date >= :since GROUP BY tag")
    suspend fun countByTagSince(since: Long): List<TagCount>
    @Query("SELECT * FROM accounts") suspend fun getAll(): List<Account>
    @Query("DELETE FROM accounts") suspend fun clear()
}

// ===== 循环待办（按间隔天数循环出现） =====
/**
 * 循环待办：5.5.0 起不再按「每周几」，改成**两个日期推间隔**。
 *
 *  - [startDate]   提醒开始日期（当天 00:00）。用来定间隔的下限：早于这天的日子不算。
 *  - [nextDate]    下一次提醒日期（当天 00:00）。序列就是「以它为锚，每 [intervalDays] 天一次」。
 *  - [intervalDays] 间隔天数，保存时由 `(nextDate - startDate)` 推出来并固化 ——
 *                  固化是必须的：日子往后走时会把 nextDate 往前推一个或多个间隔，
 *                  若每次再拿两个日期反算，间隔会翻倍（第 2 次提醒后变成 2N 天）。
 *  - [doneDates]   勾掉过的提醒日（epochDay 逗号串，只留最近若干条）。
 *                  按「哪一天」记录而不是一个全局布尔：间隔可以是 3 天也可以是 20 天，
 *                  没有「这一周」这种概念，布尔标记既会串天也无法回溯。
 */
@Entity(tableName = "loop_todos")
data class LoopTodo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val detail: String = "",
    val startDate: Long = 0L,
    val nextDate: Long = 0L,
    val intervalDays: Int = 7,
    val doneDates: String = "",
    /**
     * 手动排序用（v10 新增）。0 起、越小越前。
     *
     * 为什么循环待办也要这个字段：它是列表里的一节，附近三节（当天 / 过往未完成 / 无期限）
     * 都带左边把手、能长按拖动换位，只有这一节拖不动 —— 用户 2026-11-09 直接问
     * 「为什么不能像其他待办一样左边有个把手」。没有这列就只能按 nextDate 派生顺序，拖了也没处存。
     */
    val sortOrder: Int = 0,
)

@Dao
interface LoopTodoDao {
    @Insert suspend fun insert(t: LoopTodo): Long

    /**
     * 撤回删除用：按原 id 插回。
     *
     * ★ 不能用上面的 [insert]：它是默认的 ABORT，一旦那条 id 已经被新记录占用
     *  （删掉最大 id 后再新建，SQLite 会复用这个号）就抛 SQLiteConstraintException，
     *   而且是在 viewModelScope 的协程里抛 —— 直接崩 App。待办/账目那边一直是 upsert，
     *   这两个 DAO 以前只在设置页用到、没人想到撤回，漏了。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(t: LoopTodo): Long

    @Query("UPDATE loop_todos SET title = :title, detail = :detail, startDate = :startDate, " +
        "nextDate = :nextDate, intervalDays = :intervalDays WHERE id = :id")
    suspend fun update(id: Long, title: String, detail: String,
                       startDate: Long, nextDate: Long, intervalDays: Int)
    @Query("UPDATE loop_todos SET doneDates = :doneDates WHERE id = :id")
    suspend fun setDoneDates(id: Long, doneDates: String)
    /** 日子往前走之后把「下一次提醒」推到最近的一次（加的是整倍数间隔，序列本身不变） */
    @Query("UPDATE loop_todos SET nextDate = :nextDate WHERE id = :id")
    suspend fun setNextDate(id: Long, nextDate: Long)
    @Query("DELETE FROM loop_todos WHERE id = :id") suspend fun delete(id: Long)
    // 排序：先按手动顺序，同序号（老数据全是 0）再按到期日 —— 老库升级上来时表现和以前一致
    @Query("SELECT * FROM loop_todos ORDER BY sortOrder ASC, nextDate ASC, id ASC")
    fun observeAll(): Flow<List<LoopTodo>>

    @Query("UPDATE loop_todos SET sortOrder = :order WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Int)

    /** 新增一条时排在最后：取当前最大值 + 1（空表给 0） */
    @Query("SELECT IFNULL(MAX(sortOrder), -1) FROM loop_todos") suspend fun maxSortOrder(): Int
    @Query("SELECT * FROM loop_todos WHERE id = :id") suspend fun getById(id: Long): LoopTodo?
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

    /** 撤回删除用（同 [LoopTodoDao.upsert]：按原 id 插回，id 被占用也不崩） */
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(e: QuickEntry): Long
    @Query("DELETE FROM quick_entries WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM quick_entries ORDER BY sortOrder ASC, id ASC") fun observeAll(): Flow<List<QuickEntry>>
    @Query("SELECT * FROM quick_entries") suspend fun getAll(): List<QuickEntry>

    /**
     * 标签改名 / 删除时要同步这里。
     *
     * 常用记账存的是**标签名字符串**（不是 acc_tags 的外键），所以标签管理里改名或删标签
     * 只要不同步这张表，快捷记账写出来的账目就会带一个已经不存在的标签名 ——
     * 账目页显示灰点、支出饼图里多出一档灰的「孤儿标签」，看着像标签没删掉。
     */
    @Query("UPDATE quick_entries SET tag = :newName WHERE tag = :oldName")
    suspend fun renameTag(oldName: String, newName: String)
    @Query("UPDATE quick_entries SET tag = '' WHERE tag = :name")
    suspend fun clearTag(name: String)

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

/** 标签使用次数（[AccountDao.countByTagSince] 的投影，列名必须与查询的别名一致） */
data class TagCount(val tag: String, val cnt: Int)

@Database(
    entities = [Todo::class, Account::class, LoopTodo::class, AccTagEntity::class, AppPref::class, QuickEntry::class],
    version = 10,
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
        /**
         * 恢复备份时**可能**被替换的偏好键（白名单见 [BackupCodec.PREF_MAP]）。
         *
         * 实际恢复只替换「文件里确实带了」的那几个（`prefDao.removeAll(data.prefs.keys)`）——
         * 整组删除会把老备份里不存在的项抹成默认。
         */
        val PREF_KEYS: List<String> = BackupCodec.PREF_KEYS

        /**
         * 当前数据库版本。**改 @Database(version = ...) 时必须同步改这里** —— 备份文件靠它
         * 记录"这份数据来自哪一版"，恢复时才知道字段够不够。之所以另存一个常量而不是读
         * Room 运行时值，是因为导出备份的地方（SettingsViewModel）不该为了一个数字去碰数据库实例。
         */
        const val DB_VERSION = 10
    }

    abstract fun todoDao(): TodoDao
    abstract fun accountDao(): AccountDao
    abstract fun loopTodoDao(): LoopTodoDao
    abstract fun accTagDao(): AccTagDao
    abstract fun appPrefDao(): AppPrefDao
    abstract fun quickEntryDao(): QuickEntryDao
}