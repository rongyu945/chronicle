package com.rongyu.shixuji.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rongyu.shixuji.AppContainer
import com.rongyu.shixuji.theme.*

/** 模型看板 ViewModel：读 ApiModelDao（数据在设置里录入） */
class ModelBoardViewModel : ViewModel() {
    private val dao = AppContainer.database.apiModelDao()
    val models = dao.observeAll()
}

/**
 * 模型看板（按《时序集-模型看板细节.md》实现）
 * - 卡片含：模型名称/额度/重置日期/重置周期
 * - 额度详情 ⓘ：点击弹 名称/BaseURL/APIKey/开放平台（API Key 默认脱敏，需手动点"显示"）
 * - 同模型多 Key 合并成组，默认展开
 */
data class ApiKeyInfo(val name: String, val baseUrl: String, val key: String, val platform: String)
data class ApiModelGroup(
    val modelName: String,
    val quota: String,
    val resetDate: String,
    val resetCycle: String,
    val keys: List<ApiKeyInfo>,
    val color: Color
)

/** API Key 脱敏：保留头尾少量字符，中间打点 */
fun maskKey(k: String): String {
    val t = k.trim()
    return when {
        t.isEmpty() -> "（未填写）"
        t.length <= 8 -> "••••"
        else -> t.take(4) + "••••••••" + t.takeLast(4)
    }
}

@Composable
fun ModelBoardScreen(vm: ModelBoardViewModel = viewModel()) {
    val models by vm.models.collectAsState(initial = emptyList())
    var detailModel by remember { mutableStateOf<ApiModelGroup?>(null) }

    // 标题折叠：和待办/账目一样，往上滑标题从 28sp 缩到 17sp
    val listState = rememberLazyListState()
    val collapsed by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 36 }
    }
    val collapseT by animateFloatAsState(if (collapsed) 1f else 0f, tween(200), label = "navCollapseM")

    // 按 modelName 分组成组（同模型多 Key 合并一组）
    val groups: List<ApiModelGroup> = remember(models) {
        models.groupBy { it.modelName }.map { (name, list) ->
            ApiModelGroup(
                modelName = name,
                quota = list.first().quota,
                resetDate = list.first().resetDate,
                resetCycle = list.first().resetCycle,
                keys = list.map { ApiKeyInfo(it.keyName, it.baseUrl, it.apiKey, it.platform) },
                color = Color(list.first().color.toInt())
            )
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 标题条：往上滑时字号缩小、边距收紧（背景透明，和待办/账目一致）
        Text("模型看板",
            fontSize = (28f - 11f * collapseT).sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(
                start = 20.dp,
                top = if (collapseT > 0.5f) 8.dp else 20.dp,
                bottom = if (collapseT > 0.5f) 6.dp else 12.dp
            ))
        Spacer(Modifier.height(if (collapseT > 0.5f) 0.dp else 8.dp))

        // 标题之下的内容要用 weight 吃剩余高度：fillMaxSize 会把列表顶到屏幕外
        if (groups.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无模型", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("请前往 设置 → 大模型列表 添加", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp))
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 140.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(groups, key = { it.modelName }) { g ->
                    ModelGroupCard(g, { detailModel = it })
                }
            }
        }
    }

    detailModel?.let { m ->
        ModelDetailDialog(m) { detailModel = null }
    }
}

@Composable
private fun ModelGroupCard(
    g: ApiModelGroup,
    onInfoClick: (ApiModelGroup) -> Unit
) {
    // 多 Key 组：默认展开
    var expanded by remember { mutableStateOf(true) }
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)) {
        Column {
            // 头部：名称 + 额度 + ⓘ
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(g.color, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center) {
                    Text(g.modelName.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(g.modelName, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("${g.quota} · ${g.resetCycle} · ${g.resetDate}重置",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // ⓘ 详情按钮
                Box(Modifier.size(32.dp).clickable { onInfoClick(g) }
                    .semantics { contentDescription = "额度详情" }, contentAlignment = Alignment.Center) {
                    Text("ⓘ", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // 多 Key 展开/收起
                if (g.keys.size > 1) {
                    Box(Modifier.size(32.dp).clickable { expanded = !expanded }
                        .semantics { contentDescription = if (expanded) "收起" else "展开" },
                        contentAlignment = Alignment.Center) {
                        Text(if (expanded) "⌃" else "⌄", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            // 展开区：Key 列表
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                g.keys.forEachIndexed { i, k ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(g.color, CircleShape))
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(k.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            // 列表里只显示脱敏后的 Key，避免截屏/旁人一眼看全
                            Text("${k.baseUrl} · ${maskKey(k.key)}", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(k.platform, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelDetailDialog(m: ApiModelGroup, onDismiss: () -> Unit) {
    var revealed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = { Text("${m.modelName} · 额度详情", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailRow("模型名称", m.modelName)
                DetailRow("额度", "${m.quota} · ${m.resetCycle}")
                DetailRow("重置日期", m.resetDate.ifBlank { "—" })
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                // 逐条列出全部 Key（原来只显示第一个）
                m.keys.forEachIndexed { i, k ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Key ${i + 1} · ${k.name}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        DetailRow("Base URL", k.baseUrl.ifBlank { "—" })
                        DetailRow("API Key", if (revealed) k.key else maskKey(k.key))
                        DetailRow("开放平台", k.platform.ifBlank { "—" })
                    }
                    if (i != m.keys.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { revealed = !revealed }) { Text(if (revealed) "隐藏 Key" else "显示 Key") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.32f))
        Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.68f))
    }
}
