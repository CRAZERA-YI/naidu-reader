package com.lingoreader.app.ui.vocab

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingoreader.app.data.model.VocabWord
import com.lingoreader.app.data.sm2.StudyAnalytics

@Composable
fun VocabStatsSheetContent(
    analytics: StudyAnalytics,
    dailyGoal: Int,
    stubbornWords: List<VocabWord>,
    onChangeDailyGoal: (Int) -> Unit,
    onAttackStubborn: () -> Unit,
    onOpenWord: (Long) -> Unit
) {
    val totalWords = analytics.newCount + analytics.learningCount + analytics.familiarCount + analytics.masteredCount
    val goalProgress = (analytics.todayReviewed.toFloat() / dailyGoal.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("学习统计与记忆曲线", style = MaterialTheme.typography.titleLarge)

        // 1. Daily Goal + Streak Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.LocalFireDepartment,
                            contentDescription = null,
                            tint = Color(0xFFD9822B),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "已连续学习 ${analytics.streakDays} 天",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = "累计复习 ${analytics.totalReviewedAllTime} 次",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "今日目标：${analytics.todayReviewed} / $dailyGoal 词（新增 ${analytics.todayAdded} 词）",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            onClick = { onChangeDailyGoal((dailyGoal - 5).coerceAtLeast(5)) },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text("-5", modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Surface(
                            onClick = { onChangeDailyGoal((dailyGoal + 5).coerceAtMost(100)) },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text("+5", modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                LinearProgressIndicator(
                    progress = { goalProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                )
            }
        }

        // 2. Mastery Distribution Segmented Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("词汇掌握度分布（共 $totalWords 词）", style = MaterialTheme.typography.titleMedium)
                if (totalWords > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                    ) {
                        if (analytics.newCount > 0) {
                            Box(Modifier.weight(analytics.newCount.toFloat()).fillMaxHeight().background(Color(0xFFE0A458)))
                        }
                        if (analytics.learningCount > 0) {
                            Box(Modifier.weight(analytics.learningCount.toFloat()).fillMaxHeight().background(Color(0xFF6BA3BE)))
                        }
                        if (analytics.familiarCount > 0) {
                            Box(Modifier.weight(analytics.familiarCount.toFloat()).fillMaxHeight().background(Color(0xFF56987D)))
                        }
                        if (analytics.masteredCount > 0) {
                            Box(Modifier.weight(analytics.masteredCount.toFloat()).fillMaxHeight().background(Color(0xFF2E5E4E)))
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    LegendDot("新词", analytics.newCount, Color(0xFFE0A458))
                    LegendDot("初记", analytics.learningCount, Color(0xFF6BA3BE))
                    LegendDot("熟悉", analytics.familiarCount, Color(0xFF56987D))
                    LegendDot("已掌握", analytics.masteredCount, Color(0xFF2E5E4E))
                }
            }
        }

        // 3. 28-Day Study Activity Heatmap (4 x 7 grid)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("近 28 天学习打卡热力图", style = MaterialTheme.typography.titleMedium)
                val rows = analytics.heatmap28Days.chunked(7)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rows.forEach { week ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            week.forEach { day ->
                                val act = day.totalActivity
                                val tileColor = when {
                                    act == 0 -> MaterialTheme.colorScheme.surfaceVariant
                                    act < 5 -> Color(0xFFC4DFD3)
                                    act < 15 -> Color(0xFF7CB49E)
                                    act < 30 -> Color(0xFF42846E)
                                    else -> Color(0xFF234D3F)
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1.25f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(tileColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = day.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                                        color = if (act >= 15) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Next 7 Days SM-2 Review Load Forecast
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("未来 7 天待复习预测", style = MaterialTheme.typography.titleMedium)
                val maxDue = analytics.forecast7Days.maxOfOrNull { it.dueCount }?.coerceAtLeast(1) ?: 1
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(104.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    analytics.forecast7Days.forEachIndexed { idx, item ->
                        val ratio = (item.dueCount.toFloat() / maxDue.toFloat()).coerceIn(0f, 1f)
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Text(
                                text = item.dueCount.toString(),
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                                color = if (idx == 0 && item.dueCount > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.65f)
                                    .height((ratio * 60f).dp.coerceAtLeast(if (item.dueCount > 0) 8.dp else 3.dp))
                                    .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                    .background(
                                        when {
                                            item.dueCount == 0 -> MaterialTheme.colorScheme.surfaceVariant
                                            idx == 0 -> MaterialTheme.colorScheme.secondary
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                    )
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 5. Stubborn Words Hall of Shame + Attack CTA
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("高频顽固词榜 (${stubbornWords.size})", style = MaterialTheme.typography.titleMedium)
                    if (stubbornWords.isNotEmpty()) {
                        Button(
                            onClick = onAttackStubborn,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Rounded.School, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("专项突击", fontSize = 12.sp)
                        }
                    }
                }
                if (stubbornWords.isEmpty()) {
                    Text(
                        text = "太棒了！目前没有反复遗忘或高频未掌握的顽固词。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    stubbornWords.take(6).forEach { w ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenWord(w.id) }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(w.word, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                                Text(
                                    text = w.meaning.replace("\n", "；"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "遗忘${w.lapseCount} · 遇${w.encounterCount}次",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                                color = Color(0xFFB3261E)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(4.dp))
        Text("$label $count", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
