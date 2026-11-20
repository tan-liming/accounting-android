package com.duo.ledger.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.duo.ledger.data.local.ActiveRecord
import com.duo.ledger.data.local.EntryType
import com.duo.ledger.util.centsToYuan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun EditRecordDialog(
    record: ActiveRecord,
    vm: LedgerViewModel,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    onDismiss: () -> Unit
) {
    val members by vm.members.collectAsState()
    val categories by vm.categories.collectAsState()

    var type by remember(record.id) { mutableStateOf(record.type) }
    var amountText by remember(record.id) { mutableStateOf(record.amountCents.centsToYuan()) }
    var purpose by remember(record.id) { mutableStateOf(record.purpose) }
    var date by remember(record.id) { mutableStateOf(LocalDate.ofEpochDay(record.day)) }
    var memberId by remember(record.id) { mutableStateOf(record.memberId) }
    var categoryId by remember(record.id) { mutableStateOf(record.categoryId) }
    var fromParty by remember(record.id) { mutableStateOf(record.fromParty.orEmpty()) }
    var toParty by remember(record.id) { mutableStateOf(record.toParty.orEmpty()) }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val visibleCategories = remember(categories, type) {
        when (type) {
            EntryType.EXPENSE -> categories.filter { it.kind == EntryType.EXPENSE }
            EntryType.INCOME -> categories.filter { it.kind == EntryType.INCOME }
            else -> emptyList()
        }
    }
    // 改了类型后原分类可能属于另一套，自动落到当前类型的第一个分类。
    LaunchedEffect(type, visibleCategories) {
        if (categoryId != null && visibleCategories.none { it.id == categoryId }) {
            categoryId = visibleCategories.firstOrNull()?.id
        } else if (categoryId == null && visibleCategories.isNotEmpty()) {
            categoryId = visibleCategories.firstOrNull()?.id
        }
    }

    val context = LocalContext.current
    val needsCategory = type == EntryType.EXPENSE || type == EntryType.INCOME
    val categoryName = visibleCategories.firstOrNull { it.id == categoryId }?.name ?: "选择分类"

    fun save() {
        val chosenCategory = if (needsCategory) categoryId else null
        val from: String? = when (type) {
            EntryType.INCOME -> fromParty.trim().ifEmpty { null }
            EntryType.TRANSFER_OUT -> members.firstOrNull { it.id == memberId }?.name
            EntryType.TRANSFER -> fromParty.trim().ifEmpty { null }
            else -> null
        }
        val to: String? = when (type) {
            EntryType.TRANSFER_OUT, EntryType.TRANSFER -> toParty.trim().ifEmpty { null }
            else -> null
        }
        val ok = vm.saveEdit(
            id = record.id,
            type = type,
            amountText = amountText,
            day = date.toEpochDay(),
            memberId = memberId,
            categoryId = chosenCategory,
            purpose = purpose.trim().ifEmpty { "未填用途" },
            fromParty = from,
            toParty = to
        )
        if (!ok) {
            errorText = "请输入有效金额（最多两位小数，不能为 0；负数仅用于支出红冲）"
            return
        }
        scope.launch { snackbarHostState.showFor3Seconds("已修改") }
        onDismiss()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("编辑流水", style = MaterialTheme.typography.titleMedium)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == EntryType.EXPENSE,
                        onClick = { type = EntryType.EXPENSE },
                        label = { Text("支出") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = type == EntryType.INCOME,
                        onClick = { type = EntryType.INCOME },
                        label = { Text("收入") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == EntryType.TRANSFER_OUT,
                        onClick = { type = EntryType.TRANSFER_OUT },
                        label = { Text("对外转出") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = type == EntryType.TRANSFER,
                        onClick = { type = EntryType.TRANSFER },
                        label = { Text("内部划转") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("金额（元）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = purpose,
                    onValueChange = { purpose = it },
                    label = { Text("用途") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                            date.year,
                            date.monthValue - 1,
                            date.dayOfMonth
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("日期：$date") }

                Text("记在谁名下", style = MaterialTheme.typography.labelMedium)
                MemberChipsRow(
                    members = members,
                    selectedId = memberId,
                    onSelect = { memberId = it ?: record.memberId },
                    modifier = Modifier.fillMaxWidth()
                )

                if (needsCategory) {
                    Box {
                        OutlinedButton(
                            onClick = { categoryMenuOpen = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(categoryName) }
                        DropdownMenu(
                            expanded = categoryMenuOpen,
                            onDismissRequest = { categoryMenuOpen = false }
                        ) {
                            visibleCategories.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c.name) },
                                    onClick = { categoryId = c.id; categoryMenuOpen = false }
                                )
                            }
                        }
                    }
                }

                when (type) {
                    EntryType.INCOME -> OutlinedTextField(
                        value = fromParty,
                        onValueChange = { fromParty = it },
                        label = { Text("来源（选填）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    EntryType.TRANSFER_OUT -> OutlinedTextField(
                        value = toParty,
                        onValueChange = { toParty = it },
                        label = { Text("转给谁") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    EntryType.TRANSFER -> {
                        OutlinedTextField(
                            value = fromParty,
                            onValueChange = { fromParty = it },
                            label = { Text("转出方") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = toParty,
                            onValueChange = { toParty = it },
                            label = { Text("转入方") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    else -> Unit
                }

                errorText?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { save() }) { Text("保存") }
                }
            }
        }
    }
}
