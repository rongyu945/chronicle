package com.rongyu.shixuji

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rongyu.shixuji.data.AppDatabase

/** 应用级依赖容器 */
object AppContainer {
    lateinit var database: AppDatabase
        private set

    // v1 -> v2: 新增 loop_todos / acc_tags / api_models / app_prefs 四表
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS loop_todos (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, detail TEXT NOT NULL, dayOfWeek INTEGER NOT NULL, doneThisWeek INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS acc_tags (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, color INTEGER NOT NULL, icon TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS api_models (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, modelName TEXT NOT NULL, quota TEXT NOT NULL, resetDate TEXT NOT NULL, resetCycle TEXT NOT NULL, baseUrl TEXT NOT NULL, apiKey TEXT NOT NULL, keyName TEXT NOT NULL, platform TEXT NOT NULL, color INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS app_prefs (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
        }
    }

    // v2 -> v3: todos.dueDate / accounts.date 加索引
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_todos_dueDate ON todos (dueDate)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_accounts_date ON accounts (date)")
        }
    }

    // v3 -> v4: todos 加 priority/sortOrder；新增 quick_entries 表
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE todos ADD COLUMN priority INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE todos ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE TABLE IF NOT EXISTS quick_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, amountCents INTEGER NOT NULL, type INTEGER NOT NULL, tag TEXT NOT NULL, sortOrder INTEGER NOT NULL)")
        }
    }

    // v4 -> v5: todos 加 doneAt（完成时刻，已完成分区按它倒序排）
    // 回填：老库里已勾掉的条目用截止日/创建时刻兜底，只是决定它们在「已完成」区里的先后
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE todos ADD COLUMN doneAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE todos SET doneAt = CASE WHEN dueDate != 0 THEN dueDate ELSE createdAt END WHERE done = 1")
        }
    }

    // v5 -> v6: 删除「模型看板」整块功能（用户 2026-10-15 要求彻底删掉界面与设置里的联动）
    // 只丢这一张表；待办/账目/标签/偏好/常用记账一律不动。
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS api_models")
        }
    }

    fun init(context: Context) {
        if (::database.isInitialized) return
        // 刻意不保留 fallbackToDestructiveMigration：漏写迁移时宁可崩溃暴露，
        // 也不要静默删库重建（记账数据不可再生）。改动版本号前请先做好「备份/恢复」。
        database = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "shixuji.db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()
    }
}
