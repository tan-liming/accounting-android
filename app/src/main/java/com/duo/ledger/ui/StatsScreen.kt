package com.duo.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.duo.ledger.data.local.EntryType
import com.duo.ledger.util.centsToYuan

private fun money(cents: Long): String =
    if (cents < 0) "-¥ ${(-cents).centsToYuan()}" else "¥ ${cents.centsToYuan()}"

@Composable
fun StatsScreen(vm: LedgerViewModel, modifier: Modifier = Modifier) {
    val range by vm.range.collectAsState()
    val summary by vm.summary.collectAsState()
    val members by vm.members.collectAsState()
    var chartKind by remember { mutableStateOf(EntryType.EXPENSE) }

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

        Text("按成员（支出）", style = MaterialTheme.typography.titleMedium)
        if (summary.byMember.isEmpty()) {
            Text("暂无成员", style = MaterialTheme.typography.bodySmall)
        }
        summary.byMember.forEach { item ->
            BarRow(item.name, item.totalCents, summary.expenseTotalCents)
        }

        Text("按成员（收入）", style = MaterialTheme.typography.titleMedium)
        if (summary.incomeByMember.isEmpty()) {
            Text("暂无成员", style = MaterialTheme.typography.bodySmall)
        }
        summary.incomeByMember.forEach { item ->
            BarRow(item.name, item.totalCents, summary.incomeTotalCents)
        }

        Text("按支出分类", style = MaterialTheme.typography.titleMedium)
        if (summary.expenseByCategory.isEmpty()) {
            Text("本期暂无支出", style = MaterialTheme.typography.bodySmall)
        }
        summary.expenseByCategory.forEach { item ->
            BarRow(item.name, item.totalCents, summary.expenseTotalCents)
        }

        Text("按收入分类", style = MaterialTheme.typography.titleMedium)
        if (summary.incomeByCategory.isEmpty()) {
            Text("本期暂无收入", style = MaterialTheme.typography.bodySmall)
        }
        summary.incomeByCategory.forEach { item ->
            BarRow(item.name, item.totalCents, summary.incomeTotalCents)
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
private fun BarRow(label: String, cents: Long, totalCents: Long) {
    val ratio = if (totalCents > 0) {
        (cents.toFloat() / totalCents.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(money(cents), style = MaterialTheme.typography.bodyMedium)
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
    }
}
