package com.rongyu.shixuji

import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppContainer.init(applicationContext)

        // 启动时先把界面设置同步读出来（只读、几毫秒）：
        // ① 用它定窗口底色；② 交给 MainScreen 当首帧的初值。
        // 这一步是为了让首帧就是"用户设的样子"，从而消掉深色冷启动时先闪一下浅色的问题。
        val seeded = readUiPrefs()
        applyWindowBackground(seeded)

        enableEdgeToEdge()
        // 主题统一在 MainScreen 里按用户设置（宋体 / 明暗）应用，这里不再多包一层
        setContent { MainScreen(initialPrefs = seeded) }
    }

    /**
     * 冷启动第一帧的窗口底色，按用户**自己设的**明暗来定。
     *
     * 为什么需要它：`values` / `values-night` 里的启动主题只能跟随**系统**夜间模式，
     * 而 App 的外观是「跟随系统 / 常亮 / 常暗」三选一。用户把系统留在浅色、
     * App 选「常暗」时，冷启动仍会先按浅色主题画一帧 → 深色界面之前闪一片白
     * （反过来的组合则闪一下黑）。这里在 Compose 起来之前就把窗口底色刷对。
     *
     * @param seeded 同步读到的界面设置；null / 无 dark 键 = 跟随系统，交给启动主题处理
     */
    private fun applyWindowBackground(seeded: Map<String, String>?) {
        val dark = when (seeded?.get("dark")) {
            "1" -> true                       // 常暗
            "0" -> false                      // 常亮
            else -> null                      // "2" / 没设过 = 跟随系统
        } ?: return
        val color = if (dark) Color.parseColor("#0E0F12") else Color.parseColor("#F2F2F7")
        window.setBackgroundDrawable(ColorDrawable(color))
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        // 状态栏图标跟着底色的明暗走，否则深色底上还是深色图标（看不见）
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !dark
    }

    /**
     * 同步（阻塞）读界面设置。返回 null 表示读不到（首次安装、库被锁等），
     * 调用方按「跟随系统 + 让 Room 慢慢读」处理。
     *
     * 只读打开、不碰 Room 实例，所以不会和后面的初始化抢；理清这个值只要一次
     * 单表查询（同一批 insert 过的行在同一页里）。
     */
    private fun readUiPrefs(): Map<String, String>? = try {
        val f = findDbFile() ?: return null
        SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery(
                "SELECT \"key\", value FROM app_prefs WHERE \"key\" IN ('dark','songti','font_scale','tab_order')",
                null
            ).use { c ->
                buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
            }
        }
    } catch (_: Throwable) {
        null
    }

    /** 找实际的数据库文件（Room 用的名字带 .db，别猜）；带上 -wal 里的最新数据由 sqlite 自己处理 */
    private fun findDbFile(): File? {
        val dir = getDatabasePath("x").parentFile ?: return null
        return dir.listFiles()?.firstOrNull { it.name == DB_NAME && it.isFile }
            ?: dir.listFiles()?.firstOrNull { it.name.endsWith(".db") && it.isFile }
    }

    private companion object {
        const val DB_NAME = "shixuji.db"
    }
}
