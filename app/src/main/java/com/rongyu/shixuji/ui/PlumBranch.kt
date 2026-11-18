package com.rongyu.shixuji.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.rongyu.shixuji.R
import com.rongyu.shixuji.theme.isDarkTheme

/**
 * 顶部右上角的三种花枝装饰（2026-11-07）。
 *
 * 素材全部来自用户提供的渲染图（file_00000000e06c81f79851548e0f8956fb.png）：
 * 图上本来就有三条枝 —— 上红桃、中白兰、下粉樱，**都是从右向左上方长**（右端枝梢低、
 * 左端枝头高），所以一张都不翻转，直接按原样右对齐放进来就是用户要的走向。
 *
 * 抠图：
 *  · 红桃 / 粉樱 —— rembg + u2net 直接抠，枝干细但完整；
 *  · 白兰 —— u2net 认不出（白花瓣和背景那层浅绿雾亮度几乎一样，只能捞出一朵花），
 *    换 isnet-general-use 才整条扣下来；花瓣被背景带的灰用提亮 10% 找回。
 *  · 三条都做了"清雾"：背景是模糊渐变，模型会把枝梢外侧的半透明雾判成前景，
 *    贴在浅纸上是一圈灰影 —— alpha < 0.22 归零，再 0.22→1 重新拉伸。
 *
 * **零调色**：颜色、亮度、饱和一个像素都不改 —— 只动 alpha（把背景抠掉、
 * 把花瓣外那圈半透明的灰描边砍掉）。深色版和浅色版是同一张图，
 * 不再做"压暗降饱和"，用户要的就是原图那副画。
 *
 * 之前走过弯路：把整丛兰草叶一起抠 → 叶子在绿雾里 alpha 边缘一大片半透明过渡，
 * 贴浅纸上发虚；后来又试图"提亮 14% + 推白 + 锐化"去救 → 花瓣被推成死白，丢了层次，
 * 反而比原图难看。正解就是别碰颜色，只把 alpha 的过渡带收干净。
 *
 * 宽度百分比各不相同：素材宽高比差得多（红桃 5.3:1 很扁、粉樱 2.5:1、白兰 2.9:1），
 * 统一百分比会让红桃细成一条线。数值都调到"末端刚好停在周条上沿"。
 *
 * 【别把图顶出容器上沿】三张素材都是按 alpha 外接框紧裁过的 —— 图的第一行就是花瓣/花苞的
 * 尖端。所以任何向上位移（负 offset）都会把最上面那朵花顶出父容器上沿、被父容器裁掉，
 * 表现就是「花瓣被水平切平」（8.6.3 用户报的待办页、设置页）。位移一律用
 * `padding(top = …)` 排在 `fillMaxWidth` 之前，容器自己长高，图整张落在里面。
 */
enum class BranchFlower(
    val lightRes: Int,
    val darkRes: Int,
    val widthFraction: Float,
    val topInsetDp: Float,
) {
    /**
     * 待办页：粉樱。
     * 枝条右端（枝梢）落在图片底部，而「‹7日›」正好在同一条水平线上，
     * 宽度 52% 时枝梢压住箭头和日期。
     * 2026-11-07 用户：「待办的花还可以下调」—— 允许枝梢落回标题区。
     * 2026-11-18：原来的 -4f（负 lift）会把贴图顶出容器上沿被父容器裁掉，
     * 最上面那朵花的花瓣被水平切平 → 归 0。**本字段必须 ≥ 0**。
     */
    TodoBlossom(R.drawable.plum_cherry_light, R.drawable.plum_cherry_dark, 0.52f, 0f),

    /** 账目页：梅花。素材极扁（5.3:1），宽度给到 0.62 才不至于细成线；本来就矮，只下移 12dp。 */
    AccountPlum(R.drawable.plum_peach_light, R.drawable.plum_peach_dark, 0.62f, 12f),

    /**
     * 设置页：白兰 + 兰草叶（整丛保留，叶子没被裁掉）。
     * 同样从 -4f 归 0：负值会把最上面那个花苞的顶部切平。
     */
    SettingsOrchid(R.drawable.plum_orchid_light, R.drawable.plum_orchid_dark, 0.62f, 0f),
}

@Composable
fun PlumBranch(
    flower: BranchFlower,
    modifier: Modifier = Modifier,
    opacity: Float = 0.92f,
) {
    val aspect = when (flower) {
        BranchFlower.TodoBlossom -> 957f / 363f
        BranchFlower.AccountPlum -> 1060f / 184f
        BranchFlower.SettingsOrchid -> 914f / 358f
    }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
        Image(
            painter = painterResource(if (isDarkTheme) flower.darkRes else flower.lightRes),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                // 【8.6.3 修「花最上面被裁切」】位移必须用 padding 排在 fillMaxWidth **之前**：
                // padding 让容器长高、图整张落在里面，一个像素都不越界；
                // 原来是 .offset(y = -4.dp)，图被顶出容器上沿 → 父容器把上沿裁掉，
                // 最上面那朵花的花瓣被水平切平（待办、设置两页都中招）。
                .padding(top = flower.topInsetDp.dp)
                .fillMaxWidth(flower.widthFraction)
                .aspectRatio(aspect)
                .alpha(opacity)
        )
    }
}
