package com.duo.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.duo.ledger.data.local.ActiveRecord
import com.duo.ledger.data.local.EntryType
import com.duo.ledger.util.centsToYuan
import java.time.LocalDate

private fun money(cents: Long): String =
    if (cents < 0) "-¥ ${(-cents).centsToYuan()}" else "¥ ${cents.centsToYuan()}"

private fun dayLabel(day: Long): String {
    val d = LocalDate.ofEpochDay(day)
    return "${d.monthValue}/${d.dayOfMonth}"
}

@Composable
fun StatsScreen(vm: LedgerViewModel, modifier: Modifier = Modifier) {
    val range by vm.range.collectAsState()
    val summary by vm.summary.collectAsState()
    val members by vm.members.collectAsState()
    val statRecords by vm.statRecords.collectAsState()
    var chartKind by remember { mutableStateOf(EntryType.EXPENSE) }
    // 一次只展开一行，再点同一行收起；避免四个区块同时展开把页面撑得过长。
    var expandedKey by remember { mutableStateOf<String?>(null) }

    fun toggle(key: String) {
        expandedKey = if (expandedKey == key) null else key
    }

    /** COALESCE(r.categoryId, 0) 里的 0 代表「未分类」，要匹配 categoryId 为 null 的流水。 */
    fun matchesCategory(recordCategoryId: Long?, groupId: Long): Boolean =
        if (groupId == 0L) recordCategoryId == null else recordCategoryId == groupId

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PeriodBar(
            period = vm.period,
            label = range.label,
            anchor = vm.anchor,
            onPeriodChange = vm::selectPeriod,
            onShift = vm::shiftPeriod,
            onPickDate = vm::jumpToDate
        )

        MemberChipsRow(
            members = members,
            selectedId = vm.memberFilter,
            onSelect = vm::filterByMember,
            showAll = true
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${range.label} 结余", style = MaterialTheme.typography.labelMedium)
                Text(
                    money(summary.balanceCents),
                    style = MaterialTheme.typography.headlineLarge
                )
                Text(
                    "收入 ${money(summary.incomeTotalCents)}　支出 ${money(summary.expenseTotalCents)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "对外转出 ${money(summary.transferOutTotalCents)}（${summary.transferOutCount} 笔，单列，已从结余扣除）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    "内部划转 ${summary.transferCount} 笔 ${money(summary.transferTotalCents)}，仅留痕，不进汇总与结余",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = chartKind == EntryType.EXPENSE,
                onClick = { chartKind = EntryType.EXPENSE },
                label = { Text("支出构成") }
            )
            FilterChip(
                selected = chartKind == EntryType.INCOME,
                onClick = { chartKind = EntryType.INCOME },
                label = { Text("收入构成") }
            )
        }

        val kindLabel = if (chartKind == EntryType.EXPENSE) "支出" else "收入"
        val categoryRows = if (chartKind == EntryType.EXPENSE) {
            summary.expenseByCategory.map { it.name to it.totalCents }
        } else {
            summary.incomeByCategory.map { it.name to it.totalCents }
        }
        val memberRows = if (chartKind == EntryType.EXPENSE) {
            summary.byMember.map { it.name to it.totalCents }
        } else {
            summary.incomeByMember.map { it.name to it.totalCents }
        }

        DonutChartCard(
            title = "${kindLabel}按分类",
            data = preparePieData(categoryRows)
        )
        DonutChartCard(
            title = "${kindLabel}按成员",
            data = preparePieData(memberRows)
        )
        Text(
            "饼图只统计${kindLabel}分类/成员构成；对外转出、内部划转与结余不入图。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        Text(
            "点任一汇总行可展开本期具体流水，再点一次收起。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        Text("按成员（支出）", style = MaterialTheme.typography.titleMedium)
        if (summary.byMember.isEmpty()) {
            Text("暂无成员", style = MaterialTheme.typography.bodySmall)
        }
        summary.byMember.forEach { item ->
            val key = "member-expense-${item.memberId}"
            ExpandableBarRow(
                label = item.name,
                cents = item.totalCents,
                totalCents = summary.expenseTotalCents,
                expanded = expandedKey == key,
                details = statRecords.filter {
                    it.type == EntryType.EXPENSE && it.memberId == item.memberId
                },
                onToggle = { toggle(key) }
            )
        }

        Text("按成员（收入）", style = MaterialTheme.typography.titleMedium)
        if (summary.incomeByMember.isEmpty()) {
            Text("暂无成员", style = MaterialTheme.typography.bodySmall)
        }
        summary.incomeByMember.forEach { item ->
            val key = "member-income-${item.memberId}"
            ExpandableBarRow(
                label = item.name,
                cents = item.totalCents,
                totalCents = summary.incomeTotalCents,
                expanded = expandedKey == key,
                details = statRecords.filter {
                    it.type == EntryType.INCOME && it.memberId == item.memberId
                },
                onToggle = { toggle(key) }
            )
        }

        Text("按支出分类", style = MaterialTheme.typography.titleMedium)
        if (summary.expenseByCategory.isEmpty()) {
            Text("本期暂无支出", style = MaterialTheme.typography.bodySmall)
        }
        summary.expenseByCategory.forEach { item ->
            val key = "category-expense-${item.categoryId}"
            ExpandableBarRow(
                label = item.name,
                cents = item.totalCents,
                totalCents = summary.expenseTotalCents,
                expanded = expandedKey == key,
                details = statRecords.filter {
                    it.type == EntryType.EXPENSE && matchesCategory(it.categoryId, item.categoryId)
                },
                onToggle = { toggle(key) }
            )
        }

        Text("按收入分类", style = MaterialTheme.typography.titleMedium)
        if (summary.incomeByCategory.isEmpty()) {
            Text("本期暂无收入", style = MaterialTheme.typography.bodySmall)
        }
        summary.incomeByCategory.forEach { item ->
            val key = "category-income-${item.categoryId}"
            ExpandableBarRow(
                label = item.name,
                cents = item.totalCents,
                totalCents = summary.incomeTotalCents,
                expanded = expandedKey == key,
                details = statRecords.filter {
                    it.type == EntryType.INCOME && matchesCategory(it.categoryId, item.categoryId)
                },
                onToggle = { toggle(key) }
            )
        }

        Text(
            "结余 = 收入 − 支出 − 对外转出；内部划转的钱没离开「我们」，不参与。结余是流量差，不是账户余额。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            "「按成员」是记账人 / 付款人口径，不是最终承担人（本应用不做分摊）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun ExpandableBarRow(
    label: String,
    cents: Long,
    totalCents: Long,
    expanded: Boolean,
    details: List<ActiveRecord>,
    onToggle: () -> Unit
) {
    val ratio = if (totalCents > 0) {
        (cents.toFloat() / totalCents.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(money(cents), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(8.dp))
            Text(
                if (expanded) "收起" else "明细",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        if (expanded) {
            DetailList(details)
        }
    }
}

@Composable
private fun DetailList(rows: List<ActiveRecord>) {
    val sorted = rows.sortedWith(
        compareByDescending<ActiveRecord> { it.day }.thenByDescending { it.createdAt }
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            "共 ${sorted.size} 笔 · 合计 ${money(sorted.sumOf { it.amountCents })}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        if (sorted.isEmpty()) {
            Text("本期无明细", style = MaterialTheme.typography.bodySmall)
        } else {
            // 固定最大高度 + 自身滚动：年视图下单个分类可能几百笔，不能把整页撑爆。
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
            ) {
                items(sorted, key = { it.id }) { r -> DetailRow(r) }
            }
        }
    }
}

@Composable
private fun DetailRow(r: ActiveRecord) {
    val color = when {
        r.amountCents < 0 -> MaterialTheme.colorScheme.error
        r.type == EntryType.INCOME -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            dayLabel(r.day),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(44.dp)
        )
        Text(
            r.purpose.ifEmpty { "（无用途）" },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(money(r.amountCents), style = MaterialTheme.typography.bodySmall, color = color)
    }
}
