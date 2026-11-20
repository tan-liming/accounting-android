package com.duo.ledger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duo.ledger.util.centsToYuan
import kotlin.math.cos
import kotlin.math.sin

/** 小于这个角度的扇区放不下文字，交给下方图例兜底。 */
private const val MIN_LABEL_ANGLE = 18f
/** 大于这个角度才放得下「分类名 + 金额/占比」两行。 */
private const val TWO_LINE_ANGLE = 30f

/**
 * 扇区必须彼此可分，整组都取主题橙会糊成一片。
 * 固定一组橙打头的年轻化撞色；白天/深色底都能看清。
 */
private fun piePalette(): List<Color> = listOf(
    Color(0xFFFF6B35),
    Color(0xFFFF9F1C),
    Color(0xFFF4C95D),
    Color(0xFF4CAF93),
    Color(0xFF5B8DEF),
    Color(0xFFB07BE0),
    Color(0xFFB4B2A9)
)

private fun linear(c: Float): Float =
    if (c <= 0.04045f) c / 12.92f
    else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()

/**
 * 按 WCAG 相对亮度选字色，而不是简单加权亮度。
 * 这套撞色板整体偏亮，白字在橙/蓝/紫上只有 3:1 对比度，深字普遍能到 6:1 以上。
 * L = 0.179 是「深字对比度 == 白字对比度」的分界点。
 */
private fun labelColor(fill: Color): Color {
    val l = 0.2126f * linear(fill.red) +
        0.7152f * linear(fill.green) +
        0.0722f * linear(fill.blue)
    return if (l > 0.179f) Color(0xFF201A17) else Color.White
}

/** 中文分类名可能很长，按扇区可用宽度截断，避免文字压到相邻扇区。 */
private fun fitLabel(
    label: String,
    measurer: TextMeasurer,
    style: TextStyle,
    maxWidth: Float
): String {
    if (measurer.measure(label, style).size.width <= maxWidth) return label
    for (end in label.length - 1 downTo 1) {
        val candidate = label.take(end) + "…"
        if (measurer.measure(candidate, style).size.width <= maxWidth) return candidate
    }
    return label.take(1)
}

/** 金额绝不截断成「¥50…」这种看不懂的片段，放不下就整段降级。 */
private fun fitAmount(
    candidates: List<String>,
    measurer: TextMeasurer,
    style: TextStyle,
    maxWidth: Float
): String = candidates.firstOrNull {
    measurer.measure(it, style).size.width <= maxWidth
} ?: candidates.last()

@Composable
fun DonutChartCard(title: String, data: PieData, modifier: Modifier = Modifier) {
    val palette = piePalette()
    val textMeasurer = rememberTextMeasurer()
    // 用数值当 key：上层每次重组都会重新 preparePieData，若拿 List 当 key 会不断重置选中态。
    var selectedIndex by remember(data.totalCents, data.excludedCount) {
        mutableStateOf<Int?>(null)
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)

            if (data.slices.isEmpty()) {
                Text(
                    "本期无可展示的数据（或全部为负净额）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .align(Alignment.CenterHorizontally)
                ) {
                    Canvas(modifier = Modifier.size(240.dp)) {
                        val stroke = size.minDimension * 0.22f
                        val radius = (size.minDimension - stroke) / 2f
                        val topLeft = Offset(center.x - radius, center.y - radius)
                        val arcSize = Size(radius * 2f, radius * 2f)
                        val total = data.totalCents.toFloat()

                        var start = -90f
                        data.slices.forEachIndexed { index, slice ->
                            val fill = palette[index % palette.size]
                            val dimmed = selectedIndex != null && selectedIndex != index
                            val sweep = slice.cents / total * 360f
                            val mid = start + sweep / 2f

                            drawArc(
                                color = fill.copy(alpha = if (dimmed) 0.35f else 1f),
                                startAngle = start,
                                sweepAngle = (sweep - 1.5f).coerceAtLeast(0.2f),
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = stroke)
                            )

                            if (sweep >= MIN_LABEL_ANGLE) {
                                val angle = Math.toRadians(mid.toDouble())
                                val x = center.x + radius * cos(angle).toFloat()
                                val y = center.y + radius * sin(angle).toFloat()
                                // 弧长近似用弦长估算，留一点余量防止贴到扇区边缘。
                                val half = Math.toRadians((sweep / 2f).toDouble())
                                val maxWidth = 2f * radius * sin(half).toFloat() * 0.82f
                                val style = TextStyle(
                                    fontSize = 10.sp,
                                    color = labelColor(fill).copy(
                                        alpha = if (dimmed) 0.45f else 1f
                                    )
                                )
                                val money = "¥${slice.cents.centsToYuan()}"
                                val percent = "${slice.percent}%"
                                val candidates = if (sweep >= TWO_LINE_ANGLE) {
                                    listOf("$money · $percent", money, percent)
                                } else {
                                    listOf(money, percent)
                                }
                                val valueText = fitAmount(candidates, textMeasurer, style, maxWidth)
                                val valueLayout = textMeasurer.measure(valueText, style)

                                if (sweep >= TWO_LINE_ANGLE) {
                                    val labelLayout = textMeasurer.measure(
                                        fitLabel(slice.label, textMeasurer, style, maxWidth),
                                        style
                                    )
                                    val blockHeight =
                                        labelLayout.size.height + valueLayout.size.height
                                    val top = y - blockHeight / 2f
                                    drawText(
                                        textLayoutResult = labelLayout,
                                        topLeft = Offset(x - labelLayout.size.width / 2f, top)
                                    )
                                    drawText(
                                        textLayoutResult = valueLayout,
                                        topLeft = Offset(
                                            x - valueLayout.size.width / 2f,
                                            top + labelLayout.size.height
                                        )
                                    )
                                } else {
                                    drawText(
                                        textLayoutResult = valueLayout,
                                        topLeft = Offset(
                                            x - valueLayout.size.width / 2f,
                                            y - valueLayout.size.height / 2f
                                        )
                                    )
                                }
                            }
                            start += sweep
                        }
                    }
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val focus = selectedIndex?.let { data.slices.getOrNull(it) }
                        Text(
                            focus?.let { "¥ ${it.cents.centsToYuan()}" }
                                ?: "¥ ${data.totalCents.centsToYuan()}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            focus?.label ?: "合计（入图）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                data.slices.forEachIndexed { index, slice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedIndex = if (selectedIndex == index) null else index
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    palette[index % palette.size],
                                    RoundedCornerShape(3.dp)
                                )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            slice.label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text("${slice.percent}%", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (data.excludedCount > 0) {
                    Text(
                        "${data.excludedCount} 个零/负净额分类未入图，饼图合计可能小于页面合计。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Text(
                    "占比按最大余数法分配，合计恰好 100%。金额直接标在扇区上；太窄的扇区只显示占比，点下方分类可在中心查看金额。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
