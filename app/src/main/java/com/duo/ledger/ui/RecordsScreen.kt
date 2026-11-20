package com.duo.ledger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.duo.ledger.data.local.ActiveRecord
import com.duo.ledger.data.local.EntryType
import com.duo.ledger.util.centsToYuan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate

private val TYPE_OPTIONS = listOf(
    null to "全部",
    EntryType.EXPENSE to "支出",
    EntryType.INCOME to "收入",
    EntryType.TRANSFER_OUT to "对外转出",
    EntryType.TRANSFER to "内部划转"
)

@Composable
fun RecordsScreen(
    vm: LedgerViewModel,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    val range by vm.range.collectAsState()
    val records by vm.records.collectAsState()
    val summary by vm.summary.collectAsState()
    val members by vm.members.collectAsState()
    val allMembers by vm.allMembers.collectAsState()
    val categories by vm.categories.collectAsState()

    var editing by remember { mutableStateOf<ActiveRecord?>(null) }

    // 含已删除成员：从旧备份恢复的历史流水也能显示名字，不会变成空白。
    val memberNames = allMembers.associate { it.id to it.name }
    val categoryNames = categories.associate { it.id to it.name }
    val grouped = records.groupBy { it.day }.toSortedMap(reverseOrder())

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TYPE_OPTIONS.forEach { (option, label) ->
                        FilterChip(
                            selected = vm.typeFilter == option,
                            onClick = { vm.filterByType(option) },
                            label = { Text(label) }
                        )
                    }
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("支出合计", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "¥ ${summary.expenseTotalCents.centsToYuan()}",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "收入 ¥${summary.incomeTotalCents.centsToYuan()}　结余 ¥${summary.balanceCents.centsToYuan()}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (summary.transferOutCount > 0) {
                            Text(
                                "对外转出 ${summary.transferOutCount} 笔 ¥${summary.transferOutTotalCents.centsToYuan()}（单列，不计入支出）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        if (summary.transferCount > 0) {
                            Text(
                                "内部划转 ${summary.transferCount} 笔 ¥${summary.transferTotalCents.centsToYuan()}（仅留痕）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
                Text(
                    "点击任意流水可编辑；右侧图标删除（3 秒内可撤销）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        grouped.forEach { (day, list) ->
            item(key = "header-$day") {
                Text(
                    LocalDate.ofEpochDay(day).toString(),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(list, key = { it.id }) { r ->
                RecordRow(
                    record = r,
                    memberName = memberNames[r.memberId].orEmpty(),
                    categoryName = r.categoryId?.let { categoryNames[it] },
                    onEdit = { editing = r },
                    onDelete = {
                        vm.delete(r.id)
                        scope.launch {
                            snackbarHostState.showFor3Seconds("已删除", actionLabel = "撤销") {
                                vm.undoDelete()
                            }
                        }
                    }
                )
            }
        }
    }

    editing?.let { record ->
        EditRecordDialog(
            record = record,
            vm = vm,
            snackbarHostState = snackbarHostState,
            scope = scope,
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun RecordRow(
    record: ActiveRecord,
    memberName: String,
    categoryName: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val typeLabel = when (record.type) {
        EntryType.EXPENSE -> "支出"
        EntryType.INCOME -> "收入"
        EntryType.TRANSFER_OUT -> "对外转出"
        EntryType.TRANSFER -> "内部划转"
    }
    val description = when (record.type) {
        EntryType.EXPENSE, EntryType.INCOME -> "${categoryName ?: "未分类"} · ${record.purpose}"
        EntryType.TRANSFER_OUT -> "转给 ${record.toParty.orEmpty()} · ${record.purpose}"
        EntryType.TRANSFER -> "${record.fromParty.orEmpty()} → ${record.toParty.orEmpty()} · ${record.purpose}"
    }
    val amountText = when (record.type) {
        EntryType.EXPENSE -> "¥ ${record.amountCents.centsToYuan()}"
        EntryType.INCOME -> "+¥ ${record.amountCents.centsToYuan()}"
        EntryType.TRANSFER_OUT -> "→¥ ${record.amountCents.centsToYuan()}"
        EntryType.TRANSFER -> "¥ ${record.amountCents.centsToYuan()}"
    }
    val amountColor = when (record.type) {
        EntryType.INCOME -> MaterialTheme.colorScheme.primary
        EntryType.TRANSFER -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.onSurface
    }
    val note = when (record.type) {
        EntryType.TRANSFER_OUT -> " · 不计入支出"
        EntryType.TRANSFER -> " · 不计入汇总"
        else -> ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Text(
                "$memberName · $typeLabel$note",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Text(amountText, style = MaterialTheme.typography.bodyLarge, color = amountColor)
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "删除")
        }
    }
}
