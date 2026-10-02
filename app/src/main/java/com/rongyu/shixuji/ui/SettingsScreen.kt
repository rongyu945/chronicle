package com.rongyu.shixuji.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import com.rongyu.shixuji.data.ApiModelEntity
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

/** 标签可选色（12 色）：红/橙/黄/绿/青/蓝/紫/紫红/玫红/棕/灰/墨绿 */
private val TAG_COLORS = listOf(
    0xFFFF5F3C, 0xFFFF9F0A, 0xFFFFD60A, 0xFF34C759, 0xFF30B0C7, 0xFF1E9EF0,
    0xFFAF52DE, 0xFF5A6CF0, 0xFFFF2D55, 0xFF8B6A4B, 0xFF8E8E93, 0xFF0A5C3C
)

/** 设置（按《时序集-设置详情页细节.md》：待办/账单/大模型列表/界面显示/关于作者） */
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
            }
            SectionCard("大模型列表") {
                NavRow("模型管理", "模型 / Key") { page = "models" }
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
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp).padding(vertical = 10.dp),
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
    val subScroll = rememberScrollState()
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
                "models" -> "模型管理"; "order" -> "底栏排序"
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
            "models" -> ModelManagePage()
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
    private val modelDao = AppContainer.database.apiModelDao()
    private val accountDao = AppContainer.database.accountDao()
    private val prefDao = AppContainer.database.appPrefDao()
    private val quickDao = AppContainer.database.quickEntryDao()

    val todos = todoDao.observeAll()
    val loops = loopDao.observeAll()
    val tags = tagDao.observeAll()
    val models = modelDao.observeAll()
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

    fun addTag(name: String, color: Long) = viewModelScope.launch { tagDao.upsert(AccTagEntity(name = name, color = color)) }
    fun updateTag(id: Long, name: String, color: Long) = viewModelScope.launch { tagDao.update(id, name, color) }

    suspend fun countByTag(name: String): Int = accountDao.countByTag(name)

    /** 删标签：同时把用到它的账目标签清空，不留孤儿标签 */
    fun deleteTag(tag: AccTagEntity) = viewModelScope.launch(Dispatchers.IO) {
        accountDao.clearTag(tag.name)
        tagDao.delete(tag.id)
    }

    fun addModel(m: ApiModelEntity) = viewModelScope.launch { modelDao.insert(m) }
    fun updateModel(m: ApiModelEntity) = viewModelScope.launch {
        modelDao.update(m.id, m.modelName, m.quota, m.resetDate, m.resetCycle, m.baseUrl, m.apiKey, m.keyName, m.platform, m.color)
    }
    fun deleteModel(id: Long) = viewModelScope.launch { modelDao.delete(id) }

    // ===== 备份 / 恢复 =====
    suspend fun buildBackupJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("app", "时序集")
        root.put("schema", 1)
        root.put("exportedAt", System.currentTimeMillis())

        val todosArr = JSONArray()
        todoDao.getAll().forEach { t ->
            todosArr.put(JSONObject().apply {
                put("id", t.id); put("title", t.title); put("detail", t.detail)
                put("done", t.done); put("dueDate", t.dueDate); put("createdAt", t.createdAt)
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

        val modelArr = JSONArray()
        modelDao.getAll().forEach { m ->
            modelArr.put(JSONObject().apply {
                put("id", m.id); put("modelName", m.modelName); put("quota", m.quota)
                put("resetDate", m.resetDate); put("resetCycle", m.resetCycle); put("baseUrl", m.baseUrl)
                put("apiKey", m.apiKey); put("keyName", m.keyName); put("platform", m.platform); put("color", m.color)
            })
        }
        root.put("apiModels", modelArr)

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
        var n = 0

        todoDao.clear()
        root.optJSONArray("todos")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                todoDao.insert(Todo(id = o.optLong("id"), title = o.optString("title"),
                    detail = o.optString("detail"), done = o.optBoolean("done"),
                    dueDate = o.optLong("dueDate"), createdAt = o.optLong("createdAt")))
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
        modelDao.clear()
        root.optJSONArray("apiModels")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                modelDao.insert(ApiModelEntity(id = o.optLong("id"), modelName = o.optString("modelName"),
                    quota = o.optString("quota"), resetDate = o.optString("resetDate"),
                    resetCycle = o.optString("resetCycle"), baseUrl = o.optString("baseUrl"),
                    apiKey = o.optString("apiKey"), keyName = o.optString("keyName"),
                    platform = o.optString("platform"), color = o.optLong("color", 0xFF0A84FF)))
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
        if (m.size == 4) m else DEFAULT_TAB_ORDER
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
            onConfirm = { vm.updateTag(tag.id, name.trim(), color); editing = null },
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.Start),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TAG_COLORS.forEach { c ->
                        val cl = c.toLong()
                        Box(Modifier.size(28.dp).clickable { onColor(cl) }
                            .background(Color(c).copy(alpha = if (color == cl) 1f else .4f), RoundedCornerShape(8.dp)))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onConfirm() }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

// ===== 模型管理 =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelManagePage(vm: SettingsViewModel = viewModel()) {
    val models by vm.models.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<ApiModelEntity?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showAddKey by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ApiModelEntity?>(null) }

    Column {
        LabelText("添加 / 管理 AI 模型与 API Key，看板将读取此处", 13, MaterialTheme.colorScheme.onSurfaceVariant)
        models.forEach { m ->
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp).background(Color(m.color.toInt()).copy(alpha = .8f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center) {
                        Text(m.modelName.take(1), color = Color.White, fontSize = 14.sp)
                    }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(m.modelName, fontSize = 15.sp)
                        Text("${m.keyName} · ${m.baseUrl}", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("改", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp,
                        modifier = Modifier.clickable { editing = m }.padding(8.dp))
                    Text("删", color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                        modifier = Modifier.clickable { deleteTarget = m }.padding(8.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showAdd = true }, modifier = Modifier.weight(1f)) { Text("添加模型") }
            OutlinedButton(onClick = { showAddKey = true }, enabled = models.isNotEmpty(),
                modifier = Modifier.weight(1f)) { Text("添加 Key") }
        }
    }

    if (showAdd) {
        ModelEditDialog(editing = null, onConfirm = { vm.addModel(it); showAdd = false }, onDismiss = { showAdd = false })
    }

    editing?.let { m ->
        ModelEditDialog(editing = m, onConfirm = { vm.updateModel(it); editing = null }, onDismiss = { editing = null })
    }

    deleteTarget?.let { m ->
        AlertDialog(
            shape = GlassDialogShape,
            containerColor = glassDialogContainer(),
            modifier = glassDialogModifier(),
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除这条模型记录？") },
            text = { Text("${m.modelName} · ${m.keyName}") },
            confirmButton = {
                TextButton(onClick = { vm.deleteModel(m.id); deleteTarget = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } })
    }

    // 添加 Key：给已有模型加一条新 Key（只需选模型 + Key 名称 + Key 值）
    if (showAddKey) {
        val uniqueModels = remember(models) { models.map { it.modelName }.distinct() }
        var pickModel by remember { mutableStateOf(uniqueModels.firstOrNull() ?: "") }
        var pickOpen by remember { mutableStateOf(false) }
        var newKey by remember { mutableStateOf("") }
        var newKeyName by remember { mutableStateOf("") }
        AlertDialog(
            shape = GlassDialogShape,
            containerColor = glassDialogContainer(),
            modifier = glassDialogModifier(),
            onDismissRequest = { showAddKey = false }, title = { Text("添加 Key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("所选模型的 URL / 额度 / 平台将自动复用", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ExposedDropdownMenuBox(expanded = pickOpen, onExpandedChange = { pickOpen = !pickOpen }) {
                        OutlinedTextField(value = pickModel, onValueChange = {}, readOnly = true,
                            label = { Text("选择模型") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = pickOpen) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
                        ExposedDropdownMenu(expanded = pickOpen, onDismissRequest = { pickOpen = false }) {
                            uniqueModels.forEach { nm ->
                                DropdownMenuItem(text = { Text(nm) }, onClick = { pickModel = nm; pickOpen = false })
                            }
                        }
                    }
                    OutlinedTextField(value = newKeyName, onValueChange = { newKeyName = it },
                        label = { Text("Key 名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = newKey, onValueChange = { newKey = it },
                        label = { Text("API Key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pickModel.isNotBlank() && newKey.isNotBlank()) {
                        models.firstOrNull { it.modelName == pickModel }?.let { base ->
                            vm.addModel(ApiModelEntity(
                                modelName = base.modelName, quota = base.quota, resetDate = base.resetDate,
                                resetCycle = base.resetCycle, baseUrl = base.baseUrl, apiKey = newKey,
                                keyName = newKeyName.ifBlank { base.keyName }, platform = base.platform, color = base.color))
                            showAddKey = false
                        }
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showAddKey = false }) { Text("取消") } })
    }
}

/** 添加 / 修改模型共用弹窗（editing == null 表示新增） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelEditDialog(
    editing: ApiModelEntity?,
    onConfirm: (ApiModelEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val key = editing?.id
    var name by remember(key) { mutableStateOf(editing?.modelName ?: "") }
    var quota by remember(key) { mutableStateOf(editing?.quota ?: "") }
    var rdate by remember(key) { mutableStateOf(editing?.resetDate ?: "") }
    var rcyc by remember(key) { mutableStateOf(editing?.resetCycle ?: "") }
    var url by remember(key) { mutableStateOf(editing?.baseUrl ?: "") }
    var keyName by remember(key) { mutableStateOf(editing?.keyName ?: "") }
    var apiKey by remember(key) { mutableStateOf(editing?.apiKey ?: "") }
    var plat by remember(key) { mutableStateOf(editing?.platform ?: "") }

    AlertDialog(
            shape = GlassDialogShape,
            containerColor = glassDialogContainer(),
            modifier = glassDialogModifier(),
            onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "添加模型" else "修改模型") },
        text = {
            Column(
                Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("模型名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = quota, onValueChange = { quota = it }, label = { Text("额度") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = rdate, onValueChange = { rdate = it }, label = { Text("重置日期") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = rcyc, onValueChange = { rcyc = it }, label = { Text("重置周期") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = keyName, onValueChange = { keyName = it }, label = { Text("Key 名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("API Key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = plat, onValueChange = { plat = it }, label = { Text("开放平台链接") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    onConfirm(ApiModelEntity(
                        id = editing?.id ?: 0, modelName = name.trim(), quota = quota, resetDate = rdate,
                        resetCycle = rcyc, baseUrl = url, apiKey = apiKey,
                        keyName = keyName.ifBlank { "default" }, platform = plat,
                        color = editing?.color ?: 0xFF0A84FF))
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
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
                    Text(DateUtils.formatTime(acc.date), fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(accountTitle(acc), fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
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
    var pendingBackup by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val text = pendingBackup
        if (uri != null && text != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { out -> out.write(text.toByteArray(Charsets.UTF_8)) }
                toast(ctx, "备份已保存")
            } catch (e: Exception) {
                toast(ctx, "备份失败：${e.message}")
            }
        }
        pendingBackup = null
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
                if (text.isBlank()) {
                    toast(ctx, "读取文件失败")
                } else {
                    val n = vm.restoreFromJson(text)
                    toast(ctx, if (n >= 0) "已恢复 $n 条记录" else "文件格式不正确")
                }
                busy = false
            }
        }
    }

    Column {
        LabelText("把待办 / 账目 / 标签 / 模型 / 设置导出为一个 JSON 文件，换机或重装后可以完整恢复。",
            13, MaterialTheme.colorScheme.onSurfaceVariant)

        Button(onClick = {
            if (busy) return@Button
            busy = true
            scope.launch {
                val json = vm.buildBackupJson()
                busy = false
                pendingBackup = json
                val stamp = DateUtils.calAt(System.currentTimeMillis()).let { c ->
                    "${c.get(Calendar.YEAR)}${c.get(Calendar.MONTH) + 1}${c.get(Calendar.DAY_OF_MONTH)}"
                }
                createLauncher.launch("时序集备份_$stamp.json")
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(if (busy) "处理中…" else "导出备份文件")
        }

        OutlinedButton(onClick = { confirmRestore = true }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("从备份文件恢复")
        }

        LabelText("注意：恢复会先清空当前全部记录，再写入备份中的内容，请确认文件来源。",
            12, MaterialTheme.colorScheme.error)
    }

    if (confirmRestore) {
        AlertDialog(
            shape = GlassDialogShape,
            containerColor = glassDialogContainer(),
            modifier = glassDialogModifier(),
            onDismissRequest = { confirmRestore = false },
            title = { Text("确认恢复？") },
            text = { Text("当前所有待办、账目、标签、模型记录都会被备份文件中的内容替换。此操作不可撤销，建议先导出一份当前备份。") },
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
