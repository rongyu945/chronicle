package com.rongyu.shixuji.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rongyu.shixuji.AppContainer
import com.rongyu.shixuji.BuildConfig
import com.rongyu.shixuji.DEFAULT_TAB_ORDER
import com.rongyu.shixuji.FONT_SCALES
import com.rongyu.shixuji.data.AccTagEntity
import com.rongyu.shixuji.data.Account
import com.rongyu.shixuji.data.AppDatabase
import com.rongyu.shixuji.data.AppPref
import com.rongyu.shixuji.data.LoopTodo
import com.rongyu.shixuji.data.QuickEntry
import com.rongyu.shixuji.data.Todo
import com.rongyu.shixuji.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * 标签可选色（24 色，按色系分成 6 行）：红系 / 橙黄系 / 绿系 / 青蓝系 / 紫粉系 / 中性系。
 *
 * 用户 2026-10-14：「原来 12 色太单调，颜色再丰富一点、选择更多一点」→ 12 → 24，
 * 每行 4 色、行首标色系，一眼能挑到想要的那一档。
 * 注意：颜色是**按值存进标签记录**的（AccTag.color），所以换色板不会改动已有标签的颜色；
 * 旧 12 色全部保留在新色板里（老标签进编辑器仍然能高亮出自己那一格）。
 */
private val TAG_COLOR_GROUPS: List<Pair<String, List<Long>>> = listOf(
    "红系" to listOf(0xFFFF3B30, 0xFFFF5F3C, 0xFFFF2D55, 0xFFD0021B),
    "橙黄系" to listOf(0xFFFF9500, 0xFFFF9F0A, 0xFFFFD60A, 0xFFC9A227),
    "绿系" to listOf(0xFF34C759, 0xFF8BC34A, 0xFF6B8E23, 0xFF0A5C3C),
    "青蓝系" to listOf(0xFF30B0C7, 0xFF32ADE6, 0xFF1E9EF0, 0xFF5A6CF0),
    "紫粉系" to listOf(0xFFAF52DE, 0xFF7C4DFF, 0xFFF06292, 0xFFD98880),
    "中性系" to listOf(0xFF8E8E93, 0xFF2C2C2E, 0xFF8B6A4B, 0xFFD8C3A5)
)
private val TAG_COLORS: List<Long> = TAG_COLOR_GROUPS.flatMap { it.second }

/** 设置（按《时序集-设置详情页细节.md》：待办 / 账单 / 界面显示 / 数据 / 关于作者） */
@Composable
fun SettingsScreen(
    isSongti: Boolean,
    isDark: Boolean?,
    tabOrder: List<String>,
    fontScale: Float,
    onToggleSongti: (Boolean) -> Unit,
    onApplyOrder: (List<String>) -> Unit,
    onToggleDark: (Boolean?) -> Unit,
    onSetFontScale: (Float) -> Unit
) {
    // 二级页导航：null=主菜单
    var page by remember { mutableStateOf<String?>(null) }

    // 返回键：在二级页时先回主菜单，而非退出App
    BackHandler(enabled = page != null) { page = null }

    // 标题折叠：和待办/账目一样，往上滑标题从 28sp 缩到 17sp
    val scrollState = rememberScrollState()
    val collapsed by remember {
        derivedStateOf { scrollState.value > 36 }
    }
    val collapseT by animateFloatAsState(if (collapsed) 1f else 0f, tween(200), label = "navCollapseS")

    // 每次进入本页都回到最上面（和待办/账目一致）；
    // 不重置就会保留上次滚到的位置，切过来看到的是设置列表末尾。
    LaunchedEffect(Unit) { scrollState.scrollTo(0) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 标题条：往上滑字号缩小、边距收紧（背景透明）
        Text("设置",
            fontSize = (28f - 11f * collapseT).sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(
                start = 20.dp,
                top = if (collapseT > 0.5f) 8.dp else 20.dp,
                bottom = if (collapseT > 0.5f) 6.dp else 8.dp
            ))

        if (page != null) {
            SettingSubPage(page!!, onBack = { page = null }, isSongti, onToggleSongti, tabOrder, onApplyOrder)
            return@Column
        }

        // 底部留白要够：底栏是浮起来的胶囊，留 16dp 会让最后一张卡（关于）被挡住
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {

            SectionCard("待办") {
                NavRow("循环待办", "每周几循环") { page = "loop" }
                NavRow("导出未完成待办", "复制文本") { page = "export" }
            }
            SectionCard("账单") {
                NavRow("标签管理", "自由添加/修改") { page = "tags" }
                NavRow("月度预算", "超支会提示") { page = "budget" }
                NavRow("常用记账", "一键记一笔") { page = "quick" }
                NavRow("账单总览", "按月查看") { page = "bills" }
                NavRow("支出明细", "标签占比饼图") { page = "pie" }
            }
            SectionCard("界面显示设置") {
                SwitchRow("使用宋体", isSongti, onToggleSongti)
                Text("外观模式", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 2.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("跟随系统" to null, "浅色" to false, "深色" to true).forEach { (label, mode) ->
                        FilterChip(
                            selected = isDark == mode,
                            onClick = { onToggleDark(mode) },
                            label = { Text(label, fontSize = 13.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Text("字号", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FONT_SCALES.forEach { (label, scale) ->
                        FilterChip(
                            selected = kotlin.math.abs(fontScale - scale) < 0.01f,
                            onClick = { onSetFontScale(scale) },
                            label = { Text(label, fontSize = 13.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                NavRow("底栏排序", tabOrder.joinToString(" · ")) { page = "order" }
            }
            SectionCard("数据") {
                NavRow("备份 / 恢复", "导出或导入全部数据") { page = "backup" }
            }
            SectionCard("关于作者") {
                NavRow("作者", "戎昱") {}
                NavRow("版本", BuildConfig.VERSION_NAME) {}
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)) {
        Text(title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun NavRow(label: String, value: String, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("›", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp))
    }
}

/** 二级页分发 */
@Composable
private fun ColumnScope.SettingSubPage(
    page: String,
    onBack: () -> Unit,
    isSongti: Boolean,
    onToggleSongti: (Boolean) -> Unit,
    tabOrder: List<String>,
    onApplyOrder: (List<String>) -> Unit
) {
    // 二级页：标题行固定在外、内容滚动（标题同样往上滑缩小：20sp → 14sp）
    // key = page：换一个二级页要回到顶部，否则会把上一个页面的滚动位置带过来
    val subScroll = rememberScrollState()
    LaunchedEffect(page) { subScroll.scrollTo(0) }
    val subCollapsed by remember { derivedStateOf { subScroll.value > 24 } }
    val subCollapseT by animateFloatAsState(if (subCollapsed) 1f else 0f, tween(200), label = "navCollapseSub")

    Column(Modifier.weight(1f).fillMaxWidth()) {
        // 标题行（滚动容器外，固定）
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(
                start = 8.dp, end = 16.dp,
                top = if (subCollapseT > 0.5f) 6.dp else 4.dp,
                bottom = if (subCollapseT > 0.5f) 4.dp else 8.dp
            )) {
            Text("‹", fontSize = if (subCollapseT > 0.5f) 20.sp else 26.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onBack() }.padding(end = 8.dp))
            Text(when (page) {
                "loop" -> "循环待办"; "export" -> "导出未完成待办"
                "tags" -> "标签管理"; "bills" -> "账单总览"
                "pie" -> "支出明细"
                "order" -> "底栏排序"
                "budget" -> "月度预算"; "quick" -> "常用记账"
                "backup" -> "备份 / 恢复"
                else -> "设置"
            }, fontSize = (20f - 6f * subCollapseT).sp, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(subScroll)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 150.dp)
        ) {
        when (page) {
            "loop" -> LoopTodoPage()
            "export" -> ExportUndonePage()
            "tags" -> TagManagePage()
            "bills" -> BillsOverviewPage()
            "pie" -> ExpensePiePage()
            "order" -> TabOrderPage(tabOrder, onApplyOrder, onBack)
            "budget" -> BudgetPage()
            "quick" -> QuickEntryPage()
            "backup" -> BackupPage()
        }
        }
    }
}

// ===== 设置相关数据 ViewModel =====
class SettingsViewModel : ViewModel() {
    private val todoDao = AppContainer.database.todoDao()
    private val loopDao = AppContainer.database.loopTodoDao()
    private val tagDao = AppContainer.database.accTagDao()
    private val accountDao = AppContainer.database.accountDao()
    private val prefDao = AppContainer.database.appPrefDao()
    private val quickDao = AppContainer.database.quickEntryDao()

    val todos = todoDao.observeAll()
    val loops = loopDao.observeAll()
    val tags = tagDao.observeAll()
    val accounts = accountDao.observeAll()
    val quickEntries = quickDao.observeAll()

    // ===== 月度预算 =====
    suspend fun budgetCents(): Long = prefDao.get("budget_month")?.toLongOrNull() ?: 0L
    fun setBudget(cents: Long) = viewModelScope.launch { prefDao.put(AppPref("budget_month", cents.toString())) }

    // ===== 常用记账 =====
    fun addQuick(e: QuickEntry) = viewModelScope.launch { quickDao.insert(e) }
    fun deleteQuick(id: Long) = viewModelScope.launch { quickDao.delete(id) }

    fun addLoop(title: String, dow: Int) = viewModelScope.launch { loopDao.insert(LoopTodo(title = title, dayOfWeek = dow)) }
    fun toggleLoop(id: Long, done: Boolean) = viewModelScope.launch { loopDao.setDone(id, done) }
    fun deleteLoop(id: Long) = viewModelScope.launch { loopDao.delete(id) }

    /** 新建标签：同名就直接改那一条的颜色，别插出两条一模一样的（列表里分不清） */
    fun addTag(name: String, color: Long) = viewModelScope.launch(Dispatchers.IO) {
        val same = tagDao.getAll().firstOrNull { it.name == name }
        if (same != null) tagDao.update(same.id, name, color)
        else tagDao.upsert(AccTagEntity(name = name, color = color))
    }

    /**
     * 改标签。**改名必须同时改账目里的 tag 字符串** —— 账目是按名字关联的，
     * 只改 acc_tags.name 会让老账目全变孤儿（灰点、饼图掉「其他」）。
     * 两件事放同一个事务里，避免改一半。
     */
    fun updateTag(tag: AccTagEntity, newName: String, color: Long) = viewModelScope.launch(Dispatchers.IO) {
        AppContainer.database.withTransaction {
            if (newName != tag.name) accountDao.renameTag(tag.name, newName)
            tagDao.update(tag.id, newName, color)
        }
    }

    suspend fun countByTag(name: String): Int = accountDao.countByTag(name)

    /** 删标签：同时把用到它的账目标签清空，不留孤儿标签 */
    fun deleteTag(tag: AccTagEntity) = viewModelScope.launch(Dispatchers.IO) {
        accountDao.clearTag(tag.name)
        tagDao.delete(tag.id)
    }


    // ===== 备份 / 恢复 =====
    /**
     * 备份文件格式版本。
     * 改动任何一张表的字段/含义（或 buildBackupJson 的输出结构）时都要 +1，
     * 这样将来恢复旧文件才能知道"这份文件是什么年代导的、缺哪些字段"。
     * 1 = 初版（漏 priority/sortOrder/doneAt）；2 = 补齐 todos 三项 + 记 dbVersion/appVersion。
     */
    companion object { const val BACKUP_SCHEMA = 2 }

    suspend fun buildBackupJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("app", "时序集")
        root.put("schema", BACKUP_SCHEMA)
        root.put("exportedAt", System.currentTimeMillis())
        // 数据库版本与 App 版本：恢复时用来判断"这份文件来自哪一版、字段够不够"
        root.put("dbVersion", AppDatabase.DB_VERSION)
        root.put("appVersion", BuildConfig.VERSION_NAME)

        val todosArr = JSONArray()
        todoDao.getAll().forEach { t ->
            todosArr.put(JSONObject().apply {
                put("id", t.id); put("title", t.title); put("detail", t.detail)
                put("done", t.done); put("dueDate", t.dueDate); put("createdAt", t.createdAt)
                // 这三个漏掉会静默丢数据：恢复后所有优先级变「无」、拖动顺序被打乱、
                // 已完成分区的先后也丢了（早期版本确实漏了，这里补上）
                put("priority", t.priority); put("sortOrder", t.sortOrder); put("doneAt", t.doneAt)
            })
        }
        root.put("todos", todosArr)

        val accArr = JSONArray()
        accountDao.getAll().forEach { a ->
            accArr.put(JSONObject().apply {
                put("id", a.id); put("title", a.title); put("amountCents", a.amountCents)
                put("type", a.type); put("tag", a.tag); put("date", a.date)
            })
        }
        root.put("accounts", accArr)

        val loopArr = JSONArray()
        loopDao.getAll().forEach { l ->
            loopArr.put(JSONObject().apply {
                put("id", l.id); put("title", l.title); put("detail", l.detail)
                put("dayOfWeek", l.dayOfWeek); put("doneThisWeek", l.doneThisWeek)
            })
        }
        root.put("loopTodos", loopArr)

        val tagArr = JSONArray()
        tagDao.getAll().forEach { t ->
            tagArr.put(JSONObject().apply {
                put("id", t.id); put("name", t.name); put("color", t.color); put("icon", t.icon)
            })
        }
        root.put("accTags", tagArr)

        val quickArr = JSONArray()
        quickDao.getAll().forEach { q ->
            quickArr.put(JSONObject().apply {
                put("id", q.id); put("title", q.title); put("amountCents", q.amountCents)
                put("type", q.type); put("tag", q.tag); put("sortOrder", q.sortOrder)
            })
        }
        root.put("quickEntries", quickArr)

        prefDao.get("budget_month")?.let { root.put("pref_budget", it) }
        prefDao.get("font_scale")?.let { root.put("pref_font_scale", it) }
        prefDao.get("songti")?.let { root.put("pref_songti", it) }
        prefDao.get("dark")?.let { root.put("pref_dark", it) }
        prefDao.get("tab_order")?.let { root.put("pref_tab_order", it) }

        root.toString(2)
    }

    /** 返回恢复的记录条数；-1 表示文件格式不对 */
    suspend fun restoreFromJson(json: String): Int = withContext(Dispatchers.IO) {
        val root = try { JSONObject(json) } catch (_: Exception) { return@withContext -1 }
        if (root.optJSONArray("todos") == null && root.optJSONArray("accounts") == null) return@withContext -1

        // 旧版文件（schema 1）没有 priority/sortOrder/doneAt，取默认 0 即可；
        // 将来 schema 再升，这里按来源版本分支处理，别一律当新版读。
        val srcSchema = root.optInt("schema", 1)
        if (srcSchema > BACKUP_SCHEMA) return@withContext -2   // 来自更新的版本，字段可能读不懂

        var n = 0

        todoDao.clear()
        root.optJSONArray("todos")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                todoDao.insert(Todo(id = o.optLong("id"), title = o.optString("title"),
                    detail = o.optString("detail"), done = o.optBoolean("done"),
                    dueDate = o.optLong("dueDate"), createdAt = o.optLong("createdAt"),
                    // 旧备份文件里没有这三项 → optInt/optLong 取默认 0，行为与从前一致
                    priority = o.optInt("priority", 0),
                    sortOrder = o.optInt("sortOrder", 0),
                    doneAt = o.optLong("doneAt", 0L)))
                n++
            }
        }
        accountDao.clear()
        root.optJSONArray("accounts")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                accountDao.insert(Account(id = o.optLong("id"), title = o.optString("title"),
                    amountCents = o.optLong("amountCents"), type = o.optInt("type"),
                    tag = o.optString("tag"), date = o.optLong("date")))
                n++
            }
        }
        loopDao.clear()
        root.optJSONArray("loopTodos")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                loopDao.insert(LoopTodo(id = o.optLong("id"), title = o.optString("title"),
                    detail = o.optString("detail"), dayOfWeek = o.optInt("dayOfWeek"),
                    doneThisWeek = o.optBoolean("doneThisWeek")))
                n++
            }
        }
        tagDao.clear()
        root.optJSONArray("accTags")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                tagDao.upsert(AccTagEntity(id = o.optLong("id"), name = o.optString("name"),
                    color = o.optLong("color"), icon = o.optString("icon")))
                n++
            }
        }
        quickDao.clear()
        root.optJSONArray("quickEntries")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                quickDao.insert(QuickEntry(id = o.optLong("id"), title = o.optString("title"),
                    amountCents = o.optLong("amountCents"), type = o.optInt("type"),
                    tag = o.optString("tag"), sortOrder = o.optInt("sortOrder")))
                n++
            }
        }
        root.optString("pref_budget").takeIf { it.isNotBlank() }?.let { prefDao.put(AppPref("budget_month", it)) }
        root.optString("pref_font_scale").takeIf { it.isNotBlank() }?.let { prefDao.put(AppPref("font_scale", it)) }
        root.optString("pref_songti").takeIf { it.isNotBlank() }?.let { prefDao.put(AppPref("songti", it)) }
        root.optString("pref_dark").takeIf { it.isNotBlank() }?.let { prefDao.put(AppPref("dark", it)) }
        root.optString("pref_tab_order").takeIf { it.isNotBlank() }?.let { prefDao.put(AppPref("tab_order", it)) }
        n
    }
}

// ===== 循环待办 =====
@Composable
private fun LoopTodoPage(vm: SettingsViewModel = viewModel()) {
    val loops by vm.loops.collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<LoopTodo?>(null) }

    Column {
        LabelText("每周固定出现的待办，勾选状态进入新的一周会自动重置", 13, MaterialTheme.colorScheme.onSurfaceVariant)
        loops.forEach { t ->
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = t.doneThisWeek, onCheckedChange = { vm.toggleLoop(t.id, it) })
                    Column(Modifier.weight(1f)) {
                        Text(t.title, fontSize = 15.sp)
                        Text("每周${DateUtils.WEEK_CN[t.dayOfWeek - 1]}", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("删", color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                        modifier = Modifier.clickable { deleteTarget = t }.padding(8.dp))
                }
            }
        }
        Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("添加循环待办") }
    }

    if (showAdd) {
        var title by remember { mutableStateOf("") }
        var dow by remember { mutableIntStateOf(1) }
        AlertDialog(
            shape = GlassDialogShape,
            containerColor = glassDialogContainer(),
            modifier = glassDialogModifier(),
            onDismissRequest = { showAdd = false }, title = { Text("添加循环待办") },
            text = {
                Column {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("内容") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("循环于：", fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    DateUtils.WEEK_CN.forEachIndexed { i, n ->
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { dow = i + 1 }) {
                            RadioButton(selected = dow == i + 1, onClick = { dow = i + 1 })
                            Text(n, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { if (title.isNotBlank()) { vm.addLoop(title, dow); showAdd = false } }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } })
    }

    deleteTarget?.let { t ->
        AlertDialog(
            shape = GlassDialogShape,
            containerColor = glassDialogContainer(),
            modifier = glassDialogModifier(),
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除这条循环待办？") },
            text = { Text("「${t.title}」") },
            confirmButton = {
                TextButton(onClick = { vm.deleteLoop(t.id); deleteTarget = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } })
    }
}

// ===== 底栏排序（长按拖动 或 ↑↓ 点击） =====
@Composable
private fun TabOrderPage(current: List<String>, onApplyOrder: (List<String>) -> Unit, onBack: () -> Unit) {
    val initial = remember(current) {
        val m = current.filter { it in DEFAULT_TAB_ORDER }
        if (m.size == DEFAULT_TAB_ORDER.size) m else DEFAULT_TAB_ORDER
    }
    var order by remember(initial) { mutableStateOf(initial) }
    var draggingLabel by remember { mutableStateOf<String?>(null) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    val itemHeight = 62.dp

    Column {
        LabelText("长按卡片拖动，或点 ↑ ↓ 调整顺序；第一个为默认打开的页面", 13, MaterialTheme.colorScheme.onSurfaceVariant)
        order.forEach { label ->
            key(label) {
                val isDragging = draggingLabel == label
                Card(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        .graphicsLayer { translationY = if (isDragging) dragDy else 0f }
                        .zIndex(if (isDragging) 1f else 0f)
                        .pointerInput(order) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { draggingLabel = label; dragDy = 0f },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragDy += amount.y
                                    val th = itemHeight.toPx()
                                    val cur = order.indexOf(label)
                                    if (cur < 0) return@detectDragGesturesAfterLongPress
                                    if (dragDy > th / 2 && cur < order.size - 1) {
                                        order = order.toMutableList().also { it.add(cur + 1, it.removeAt(cur)) }
                                        dragDy -= th
                                    } else if (dragDy < -th / 2 && cur > 0) {
                                        order = order.toMutableList().also { it.add(cur - 1, it.removeAt(cur)) }
                                        dragDy += th
                                    }
                                },
                                onDragEnd = { draggingLabel = null; dragDy = 0f },
                                onDragCancel = { draggingLabel = null; dragDy = 0f }
                            )
                        },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("≡", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
                        if (order.first() == label) {
                            Text("默认", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Text("↑", fontSize = 18.sp, modifier = Modifier.clickable {
                            val i = order.indexOf(label)
                            if (i > 0) order = order.toMutableList().also { it.add(i - 1, it.removeAt(i)) }
                        }.padding(6.dp))
                        Text("↓", fontSize = 18.sp, modifier = Modifier.clickable {
                            val i = order.indexOf(label)
                            if (i in 0 until order.size - 1) order = order.toMutableList().also { it.add(i + 1, it.removeAt(i)) }
                        }.padding(6.dp))
                    }
                }
            }
        }
        Button(onClick = { onApplyOrder(order); onBack() }, modifier = Modifier.fillMaxWidth()) { Text("保存") }
    }
}

@Composable
private fun LabelText(s: String, sp: Int, c: Color) {
    Text(s, fontSize = sp.sp, color = c, modifier = Modifier.padding(bottom = 8.dp))
}

// ===== 导出未完成待办 =====
@Composable
private fun ExportUndonePage(vm: SettingsViewModel = viewModel()) {
    val todos by vm.todos.collectAsState(initial = emptyList())
    val ctx = LocalContext.current
    val undone = remember(todos) { todos.filter { !it.done }.sortedBy { it.dueDate } }
    var exported by remember { mutableStateOf("") }

    Column {
        LabelText("导出所有未完成的待办为可复制的文本", 13, MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = {
            exported = if (undone.isEmpty()) "暂无未完成待办"
            else buildString {
                append("未完成待办（共 ${undone.size} 项）：\n")
                undone.forEachIndexed { i, t ->
                    append("${i + 1}. ${t.title}")
                    if (t.detail.isNotBlank()) append("（${t.detail}）")
                    if (t.dueDate != 0L) append(" · ${DateUtils.formatDateCn(t.dueDate)}")
                    append("\n")
                }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("生成") }
        if (exported.isNotBlank()) {
            Card(Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)) {
                Text(exported, modifier = Modifier.padding(14.dp), fontSize = 13.sp)
            }
            Button(onClick = {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("待办", exported))
                toast(ctx, "已复制到剪贴板")
            }, modifier = Modifier.fillMaxWidth()) { Text("复制") }
        }
    }
}

// ===== 标签管理 =====
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TagManagePage(vm: SettingsViewModel = viewModel()) {
    val tags by vm.tags.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<AccTagEntity?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<AccTagEntity?>(null) }
    var usageCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(deleteTarget) {
        usageCount = deleteTarget?.let { vm.countByTag(it.name) } ?: 0
    }

    Column {
        LabelText("管理记账使用的颜色标签", 13, MaterialTheme.colorScheme.onSurfaceVariant)
        tags.forEach { t ->
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).background(Color(t.color.toInt()).copy(alpha = .8f), RoundedCornerShape(8.dp)))
                    Text(t.name, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
                    Text("改", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp,
                        modifier = Modifier.clickable { editing = t }.padding(8.dp))
                    Text("删", color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                        modifier = Modifier.clickable { deleteTarget = t }.padding(8.dp))
                }
            }
        }
        Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("添加标签") }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var color by remember { mutableStateOf(TAG_COLORS.first().toLong()) }
        TagEditorDialog("添加标签", name, color,
            onName = { name = it }, onColor = { color = it },
            onConfirm = { vm.addTag(name.trim(), color); showAdd = false },
            onDismiss = { showAdd = false })
    }

    editing?.let { tag ->
        var name by remember(tag.id) { mutableStateOf(tag.name) }
        var color by remember(tag.id) { mutableStateOf(tag.color) }
        TagEditorDialog("修改标签", name, color,
            onName = { name = it }, onColor = { color = it },
            onConfirm = { vm.updateTag(tag, name.trim(), color); editing = null },
            onDismiss = { editing = null })
    }

    deleteTarget?.let { t ->
        AlertDialog(
            shape = GlassDialogShape,
            containerColor = glassDialogContainer(),
            modifier = glassDialogModifier(),
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除标签「${t.name}」？") },
            text = {
                Text(if (usageCount > 0)
                    "有 $usageCount 笔账目正在使用这个标签，删除后这些账目会变成无标签（记录本身保留）。"
                else "当前没有账目使用这个标签。")
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteTag(t); deleteTarget = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagEditorDialog(
    title: String, name: String, color: Long,
    onName: (String) -> Unit, onColor: (Long) -> Unit,
    onConfirm: () -> Unit, onDismiss: () -> Unit
) {
    AlertDialog(
            shape = GlassDialogShape,
            containerColor = glassDialogContainer(),
            modifier = glassDialogModifier(),
            onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = onName, label = { Text("标签名") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("颜色：", fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                TagColorPicker(color) { onColor(it) }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onConfirm() }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

/** 标签颜色选择器：24 个色块 8×3 平铺，**不写色系名** —— 直接看颜色挑（用户 2026-10-14：不要色系名称） */
@Composable
private fun TagColorPicker(selected: Long, onPick: (Long) -> Unit) {
    // 老标签用的是旧色板的颜色（不在下面 24 色里）→ 顶上单独放一格它的原色 + 「当前」，
    // 否则打开修改会一个圈都不亮，像是颜色丢了。
    val legacy = TAG_COLORS.none { it == selected }
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (legacy) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Swatch(selected, Color(selected).luminance() > 0.45f, true, onPick)
                Text("当前", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TAG_COLORS.chunked(8).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { c -> Swatch(c, Color(c).luminance() > 0.45f, c == selected, onPick) }
            }
        }
    }
}

/** 单格色块：亮色底用黑描边、深色底用白描边（选中的才画） */
@Composable
private fun Swatch(color: Long, light: Boolean, on: Boolean, onPick: (Long) -> Unit) {
    val ring = if (light) Color.Black.copy(alpha = .75f) else Color.White.copy(alpha = .9f)
    Box(
        Modifier.size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(color))
            .then(if (on) Modifier.border(2.5.dp, ring, RoundedCornerShape(8.dp)) else Modifier)
            .clickable { onPick(color) }
    )
}

// ===== 支出明细（饼图：有标签按标签分，没标签的并成「其他」） =====
/** 一个扇区：名称（标签名或「其他」）、金额（分）、颜色 */
private class PieSlice(val name: String, val cents: Long, val color: Color)

@Composable
private fun ExpensePiePage(vm: SettingsViewModel = viewModel()) {
    val accounts by vm.accounts.collectAsState(initial = emptyList())
    val tags by vm.tags.collectAsState(initial = emptyList())
    val today = DateUtils.todayCal()
    var y by remember { mutableIntStateOf(today.get(Calendar.YEAR)) }
    var m0 by remember { mutableIntStateOf(today.get(Calendar.MONTH)) }

    // 只统计「支出」（type==0）；收入不进饼图
    val monthOut = remember(accounts, y, m0) {
        val s = DateUtils.monthStartMs(y, m0)
        val e = DateUtils.monthEndMs(y, m0)
        accounts.filter { it.type == 0 && it.date in s..e }
    }
    val total = monthOut.sumOf { it.amountCents }
    // 按标签分组：有标签 → 名字=标签名、颜色=标签色（标签被删过对不上色时退灰）；
    // 没标签 → 全部并成「其他」（灰点同账目页的无标签灰）。
    val slices = remember(monthOut, tags) {
        val tagColor = tags.associate { it.name to Color(it.color.toInt()) }
        monthOut.groupBy { it.tag.trim() }
            .map { (tag, list) ->
                if (tag.isEmpty()) PieSlice("其他", list.sumOf { it.amountCents }, Color(0xFFC7C7CC))
                else PieSlice(tag, list.sumOf { it.amountCents }, tagColor[tag] ?: Color(0xFF8E8E93))
            }
            .filter { it.cents > 0 }
            // 有标签的按金额从大到小，「其他」永远排最后（垫底参考，不跟标签抢位）
            .sortedWith(compareBy<PieSlice> { it.name == "其他" }.thenByDescending { it.cents })
    }

    Column {
        // 月份切换：和账单总览同款 ‹ X年X月 ›
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", fontSize = 22.sp, modifier = Modifier.clickable {
                val (ny, nm) = DateUtils.addMonths(y, m0, -1); y = ny; m0 = nm
            }.padding(end = 10.dp))
            Text("${y}年${m0 + 1}月", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text("›", fontSize = 22.sp, modifier = Modifier.clickable {
                val (ny, nm) = DateUtils.addMonths(y, m0, 1); y = ny; m0 = nm
            }.padding(start = 10.dp))
        }
        Spacer(Modifier.height(16.dp))

        if (total <= 0L) {
            Text("本月没有支出记录", fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), textAlign = TextAlign.Center)
        } else {
            Text("本月支出合计 ${formatCents(total)}", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            // 饼图：drawArc(useCenter=true) 从 12 点方向顺时针铺扇区。
            // 最后一片用「360 - 已用角度」收尾，避免浮点误差在接缝处漏出一条发丝缝。
            Canvas(Modifier.fillMaxWidth().height(240.dp)) {
                val r = size.minDimension / 2f
                val topLeft = Offset(center.x - r, center.y - r)
                var start = -90f
                slices.forEachIndexed { i, s ->
                    val sweep = if (i == slices.lastIndex) 360f - start
                        else s.cents.toFloat() / total.toFloat() * 360f
                    drawArc(color = s.color, startAngle = start, sweepAngle = sweep,
                        useCenter = true, topLeft = topLeft, size = Size(r * 2f, r * 2f))
                    start += sweep
                }
            }
            Spacer(Modifier.height(16.dp))
            // 图例：色点 + 名称 + 占比 + 金额（图例和饼图同一份 slices，顺序一致）
            slices.forEach { s ->
                val pct = s.cents.toDouble() / total.toDouble() * 100.0
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(s.color, CircleShape))
                    Text(s.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp).weight(1f))
                    Text(String.format("%.1f%%", pct), fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(52.dp), textAlign = TextAlign.End)
                    Text(formatCents(s.cents), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 10.dp).width(92.dp), textAlign = TextAlign.End)
                }
            }
        }
    }
}

// ===== 账单总览 =====
@Composable
private fun BillsOverviewPage(vm: SettingsViewModel = viewModel()) {
    val accounts by vm.accounts.collectAsState(initial = emptyList())
    val ctx = LocalContext.current
    val today = DateUtils.todayCal()
    var y by remember { mutableIntStateOf(today.get(Calendar.YEAR)) }
    var m0 by remember { mutableIntStateOf(today.get(Calendar.MONTH)) }
    var fmt by remember { mutableStateOf("csv") }
    var range by remember { mutableStateOf("month") }   // month / year / all / custom
    var cy1 by remember { mutableIntStateOf(today.get(Calendar.YEAR)) }
    var cm1 by remember { mutableIntStateOf(0) }
    var cy2 by remember { mutableIntStateOf(today.get(Calendar.YEAR)) }
    var cm2 by remember { mutableIntStateOf(today.get(Calendar.MONTH)) }

    val monthAcc = remember(accounts, y, m0) {
        val s = DateUtils.month(y, m0).timeInMillis
        val e = DateUtils.endOfDayMs(DateUtils.month(y, m0).apply { set(Calendar.DAY_OF_MONTH, DateUtils.daysInMonth(y, m0)) }.timeInMillis)
        accounts.filter { it.date in s..e }.sortedByDescending { it.date }
    }

    // 导出区间
    val (exStart, exEnd) = when (range) {
        "year" -> {
            val s = DateUtils.todayCal().apply { set(y, 0, 1) }.timeInMillis
            val e = DateUtils.endOfDayMs(DateUtils.todayCal().apply { set(y, 11, 31) }.timeInMillis)
            s to e
        }
        "all" -> 0L to Long.MAX_VALUE
        "custom" -> {
            val s = DateUtils.month(cy1, cm1).timeInMillis
            val e = DateUtils.endOfDayMs(DateUtils.month(cy2, cm2).apply { set(Calendar.DAY_OF_MONTH, DateUtils.daysInMonth(cy2, cm2)) }.timeInMillis)
            s to e
        }
        else -> {
            val s = DateUtils.month(y, m0).timeInMillis
            val e = DateUtils.endOfDayMs(DateUtils.month(y, m0).apply { set(Calendar.DAY_OF_MONTH, DateUtils.daysInMonth(y, m0)) }.timeInMillis)
            s to e
        }
    }
    val exportAcc = remember(accounts, range, exStart, exEnd) {
        accounts.filter { it.date in exStart..exEnd }.sortedByDescending { it.date }
    }
    val exportName = when (range) {
        "year" -> "时序集账单_${y}年"
        "all" -> "时序集账单_全部"
        "custom" -> "时序集账单_${cy1}年${cm1 + 1}月-${cy2}年${cm2 + 1}月"
        else -> "时序集账单_${y}年${m0 + 1}月"
    }

    var pendingText by remember { mutableStateOf<String?>(null) }
    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(if (fmt == "csv") "text/csv" else "text/plain")
    ) { uri ->
        val text = pendingText
        if (uri != null && text != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { out -> out.write(text.toByteArray(Charsets.UTF_8)) }
                toast(ctx, "已导出 ${exportAcc.size} 条记录")
            } catch (e: Exception) {
                toast(ctx, "导出失败：${e.message}")
            }
        }
        pendingText = null
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", fontSize = 22.sp, modifier = Modifier.clickable {
                val (ny, nm) = DateUtils.addMonths(y, m0, -1); y = ny; m0 = nm
            }.padding(end = 10.dp))
            Text("${y}年${m0 + 1}月", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text("›", fontSize = 22.sp, modifier = Modifier.clickable {
                val (ny, nm) = DateUtils.addMonths(y, m0, 1); y = ny; m0 = nm
            }.padding(start = 10.dp))
        }
        val total = monthAcc.sumOf { if (it.type == 1) it.amountCents else -it.amountCents }
        LabelText("当月合计：${formatCents(total)}", 14, MaterialTheme.colorScheme.primary)
        // 按天分组（最近的在最上面）
        val byDay = remember(monthAcc) {
            monthAcc.groupBy { DateUtils.calAt(it.date).get(Calendar.DAY_OF_MONTH) }
                .toSortedMap(compareByDescending { it })
        }
        byDay.forEach { (day, list) ->
            Text("${m0 + 1}月${day}日", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            list.forEach { acc ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    // 条目界面不显示时间（用户要求）；具体时刻仍能在账目详情弹窗和导出文件里看到
                    Text(accountTitle(acc), fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(formatCents(if (acc.type == 1) acc.amountCents else -acc.amountCents),
                        fontSize = 14.sp, color = if (acc.type == 1) incomeGreen else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        if (byDay.isEmpty()) LabelText("本月暂无账目", 13, MaterialTheme.colorScheme.onSurfaceVariant)

        Text("导出范围：", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("month" to "本月", "year" to "今年", "all" to "全部", "custom" to "自定义").forEach { (k, label) ->
                FilterChip(selected = range == k, onClick = { range = k }, label = { Text(label, fontSize = 13.sp) })
            }
        }
        if (range == "custom") {
            MonthStepper("从", cy1, cm1, { cy1 = it }, { cm1 = it })
            MonthStepper("到", cy2, cm2, { cy2 = it }, { cm2 = it })
        }
        Text("导出格式：", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("csv" to "CSV", "txt" to "文本").forEach { (k, label) ->
                FilterChip(selected = fmt == k, onClick = { fmt = k }, label = { Text(label, fontSize = 13.sp) })
            }
        }
        Button(onClick = {
            if (exportAcc.isEmpty()) { toast(ctx, "该范围内没有账目"); return@Button }
            pendingText = buildAccountExport(exportAcc, fmt)
            createLauncher.launch("$exportName.${if (fmt == "csv") "csv" else "txt"}")
        }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Text(if (exportAcc.isEmpty()) "导出文件（0 条）" else "导出文件（${exportAcc.size} 条）")
        }
    }
}

@Composable
private fun MonthStepper(
    label: String, y: Int, m0: Int, onYear: (Int) -> Unit, onMonth: (Int) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
        Text("‹", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable {
            val (ny, nm) = DateUtils.addMonths(y, m0, -1); onYear(ny); onMonth(nm)
        }.padding(horizontal = 6.dp))
        Text("${y}年${m0 + 1}月", fontSize = 14.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Text("›", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable {
            val (ny, nm) = DateUtils.addMonths(y, m0, 1); onYear(ny); onMonth(nm)
        }.padding(horizontal = 6.dp))
    }
}

/** CSV 单元格转义：含逗号/引号/换行时用双引号包裹并转义内部引号 */
private fun csvCell(v: String): String {
    val s = v.replace("\"", "\"\"")
    return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"$s\"" else s
}

/** 生成账单导出文本（CSV 或文本）。CSV 带 BOM，Excel 打开中文不乱码 */
private fun buildAccountExport(acc: List<Account>, fmt: String): String {
    val sb = StringBuilder()
    if (fmt == "csv") {
        sb.append('\uFEFF')
        sb.append("日期,时间,标题,标签,金额\n")
        acc.forEach { a ->
            val c = DateUtils.calAt(a.date)
            sb.append(csvCell("${c.get(Calendar.YEAR)}-${c.get(Calendar.MONTH) + 1}-${c.get(Calendar.DAY_OF_MONTH)}")).append(",")
                .append(csvCell(DateUtils.formatTime(a.date))).append(",")
                .append(csvCell(accountTitle(a))).append(",")
                .append(csvCell(a.tag)).append(",")
                .append(csvCell((if (a.type == 1) "+" else "-") + centsToYuanString(a.amountCents))).append("\n")
        }
    } else {
        acc.forEach { a ->
            val sign = if (a.type == 1) "+" else "-"
            sb.append("${DateUtils.formatDateCn(a.date)}  ${DateUtils.formatTime(a.date)}  ")
                .append("[${a.tag}]  ${accountTitle(a)}  $sign${centsToYuanString(a.amountCents)} 元\n")
        }
    }
    return sb.toString()
}

// ===== 备份 / 恢复 =====
@Composable
private fun BackupPage(vm: SettingsViewModel = viewModel()) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    /** 一次性把文本交给下一个启动器：onClick 里赋值，launcher 回调里取走并清空 */
    var textForPicker by remember { mutableStateOf<String?>(null) }
    var suggestedName by remember { mutableStateOf("时序集备份.json") }
    /** true = 这次导出只是「某个动作的副产物」（如恢复前自动备份），成功与否说法不同 */
    var isAutoBackup by remember { mutableStateOf(false) }
    /** 点「从备份文件恢复」后先做自动备份，备份流程收尾时再弹确认框 */
    var wantRestore by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }

    fun stampName(): String {
        val c = DateUtils.calAt(System.currentTimeMillis())
        val p = { v: Int -> v.toString().padStart(2, '0') }
        return "时序集备份_${c.get(Calendar.YEAR)}${p(c.get(Calendar.MONTH) + 1)}${p(c.get(Calendar.DAY_OF_MONTH))}" +
            "_${p(c.get(Calendar.HOUR_OF_DAY))}${p(c.get(Calendar.MINUTE))}${p(c.get(Calendar.SECOND))}.json"
    }

    /** 把 json 存到外部（用户自己挑位置），用于「导出备份」按钮 */
    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val text = textForPicker
        if (uri != null && text != null) {
            val ok = try {
                ctx.contentResolver.openOutputStream(uri)?.use { out -> out.write(text.toByteArray(Charsets.UTF_8)) }
                true
            } catch (e: Exception) {
                toast(ctx, "备份失败：${e.message}"); false
            }
            if (ok) toast(ctx, "备份已保存")
        }
        textForPicker = null
        isAutoBackup = false
        // 自动备份这一环结束 → 该弹恢复确认框了
        if (wantRestore) { wantRestore = false; confirmRestore = true }
    }

    /**
     * 「分享备份」：先把 json 写进自己的 cacheDir/backup_share/，再用 FileProvider 生成
     * content:// uri 交给系统分享面板（微信 / 邮件 / 网盘 / 蓝牙都能收）。
     * 比「导出备份文件」更省事：不用在文件选择器里找目录。
     */
    fun shareBackup(json: String) {
        try {
            val dir = java.io.File(ctx.cacheDir, "backup_share").apply { mkdirs() }
            // 清掉上一次的，免得 cache 里越堆越多
            dir.listFiles()?.forEach { it.delete() }
            val f = java.io.File(dir, stampName())
            f.writeText(json, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(ctx, "com.rongyu.shixuji.fileprovider", f)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "时序集备份")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(send, "分享备份文件"))
        } catch (e: Exception) {
            toast(ctx, "分享失败：${e.message}")
        }
    }

    /**
     * 恢复前的自动备份：先写进自己的 cacheDir，再用 FileProvider 交给系统「另存为」。
     * 不能用 CreateDocument 直接拿 uri 写——没有权限；也不能静默写外部存储。
     * 结果就是：点一次恢复，会先让你选个地方存当前数据，存完才问「确认恢复？」。
     */
    val autoBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val text = textForPicker
        if (uri == null || text == null) {
            // 用户取消了自动备份 → 不往下走，避免他还没备份就被覆盖
            textForPicker = null; wantRestore = false; isAutoBackup = false
            toast(ctx, "已取消，未做任何修改")
            return@rememberLauncherForActivityResult
        }
        val ok = try {
            ctx.contentResolver.openOutputStream(uri)?.use { out -> out.write(text.toByteArray(Charsets.UTF_8)) }
            true
        } catch (e: Exception) {
            toast(ctx, "自动备份失败：${e.message}"); false
        }
        textForPicker = null
        isAutoBackup = false
        if (ok) { toast(ctx, "已自动备份当前数据"); confirmRestore = true }
        else wantRestore = false
    }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    try {
                        ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    } catch (_: Exception) { "" }
                }
                when {
                    text.isBlank() -> toast(ctx, "读取文件失败")
                    else -> when (val n = vm.restoreFromJson(text)) {
                        -2 -> toast(ctx, "这个备份来自更新的版本，请先升级 App")
                        -1 -> toast(ctx, "文件格式不正确")
                        else -> toast(ctx, "已恢复 $n 条记录")
                    }
                }
                busy = false
            }
        }
    }

    Column {
        LabelText("把待办 / 账目 / 标签 / 设置导出为一个 JSON 文件，换机或重装后可以完整恢复。",
            13, MaterialTheme.colorScheme.onSurfaceVariant)

        Button(onClick = {
            if (busy) return@Button
            busy = true
            scope.launch {
                textForPicker = vm.buildBackupJson()
                busy = false
                isAutoBackup = false
                suggestedName = stampName()
                createLauncher.launch(suggestedName)
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(if (busy) "处理中…" else "导出备份文件")
        }

        OutlinedButton(onClick = {
            if (busy) return@OutlinedButton
            busy = true
            scope.launch {
                shareBackup(vm.buildBackupJson())
                busy = false
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("分享备份文件")
        }

        OutlinedButton(onClick = {
            // 先备份、再恢复：这一串走完才弹「确认恢复？」
            if (busy) return@OutlinedButton
            busy = true
            scope.launch {
                textForPicker = vm.buildBackupJson()
                busy = false
                isAutoBackup = true
                wantRestore = true
                suggestedName = "恢复前自动备份_" + stampName().removePrefix("时序集备份_")
                autoBackupLauncher.launch(suggestedName)
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(if (busy) "处理中…" else "从备份文件恢复")
        }

        LabelText("恢复前会先让你把当前数据存一份（强烈建议存），确认后再选择要恢复的备份文件。",
            12, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (confirmRestore) {
        AlertDialog(
            shape = GlassDialogShape,
            containerColor = glassDialogContainer(),
            modifier = glassDialogModifier(),
            onDismissRequest = { confirmRestore = false },
            title = { Text("确认恢复？") },
            text = { Text("当前所有待办、账目、标签都会被备份文件中的内容替换。此操作不可撤销，你刚才已经存了一份当前数据的备份。") },
            confirmButton = {
                TextButton(onClick = { confirmRestore = false; openLauncher.launch(arrayOf("application/json", "*/*")) }) { Text("选择文件") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("取消") } })
    }
}

private fun toast(ctx: Context, msg: String) {
    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}

// ===== 月度预算 =====
@Composable
private fun BudgetPage(vm: SettingsViewModel = viewModel()) {
    var text by remember { mutableStateOf("") }
    var savedCents by remember { mutableLongStateOf(-1L) }

    LaunchedEffect(Unit) {
        val cur = vm.budgetCents()
        savedCents = cur
        text = if (cur > 0) centsToYuanString(cur) else ""
    }

    Column {
        LabelText("设一个每月支出额度，记账页顶部的汇总卡会显示进度条，超出后变黄并标出超支金额。留空表示不设预算。",
            13, MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            label = { Text("每月预算（元）") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        )
        Button(onClick = {
            val cents = if (text.isBlank()) 0L else parseYuanToCents(text)
            if (cents != null) {
                vm.setBudget(cents)
                savedCents = cents
            }
        }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("保存") }
        if (savedCents > 0) {
            LabelText("当前预算：${formatCents(savedCents)}", 14, MaterialTheme.colorScheme.primary)
        } else if (savedCents == 0L) {
            LabelText("当前未设置预算", 13, MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ===== 常用记账 =====
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun QuickEntryPage(vm: SettingsViewModel = viewModel()) {
    val list by vm.quickEntries.collectAsState(initial = emptyList())
    val tags by vm.tags.collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }

    Column {
        LabelText("把最常记的几笔存成快捷按钮（最多 6 个），记账页顶部点一下就记当天一笔。",
            13, MaterialTheme.colorScheme.onSurfaceVariant)
        list.forEach { q ->
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = glassBorder(), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(q.title, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    if (q.tag.isNotBlank()) {
                        Text(q.tag, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 10.dp))
                    }
                    Text(formatCents(q.amountCents), fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 84.dp))
                    Text("删", color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                        modifier = Modifier.clickable { vm.deleteQuick(q.id) }.padding(8.dp))
                }
            }
        }
        Button(onClick = { showAdd = true }, enabled = list.size < 6,
            modifier = Modifier.fillMaxWidth()) { Text(if (list.size >= 6) "最多 6 个" else "添加常用记账") }
    }

    if (showAdd) {
        var title by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        var isIncome by remember { mutableStateOf(false) }
        var tag by remember { mutableStateOf("") }
        AlertDialog(
            shape = GlassDialogShape,
            containerColor = glassDialogContainer(),
            modifier = glassDialogModifier(),
            onDismissRequest = { showAdd = false },
            title = { Text("添加常用记账") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it },
                        label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = amount, onValueChange = { amount = it },
                        label = { Text("金额") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !isIncome, onClick = { isIncome = false }, label = { Text("支出") })
                        FilterChip(selected = isIncome, onClick = { isIncome = true }, label = { Text("收入") })
                    }
                    if (tags.isNotEmpty()) {
                        Text("标签（可选）", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            tags.forEach { t ->
                                FilterChip(
                                    selected = tag == t.name,
                                    onClick = { tag = if (tag == t.name) "" else t.name },
                                    label = { Text(t.name) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cents = parseYuanToCents(amount)
                    if (title.isNotBlank() && cents != null) {
                        vm.addQuick(QuickEntry(title = title.trim(), amountCents = cents,
                            type = if (isIncome) 1 else 0, tag = tag, sortOrder = list.size))
                        showAdd = false
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } }
        )
    }
}
