package com.duo.ledger.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
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
import com.duo.ledger.data.local.EntryType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun AddRecordScreen(
    vm: LedgerViewModel,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    val members by vm.members.collectAsState()
    val categories by vm.categories.collectAsState()
    val lastMemberId by vm.lastMemberId.collectAsState()
    val lastCategoryId by vm.lastCategoryId.collectAsState()
    val aiEnabled by vm.aiEnabled.collectAsState()
    var aiDraft by remember { mutableStateOf<com.duo.ledger.ai.AiDraft?>(null) }

    val expenseCategories = remember(categories) {
        categories.filter { it.kind == EntryType.EXPENSE }
    }
    val incomeCategories = remember(categories) {
        categories.filter { it.kind == EntryType.INCOME }
    }

    var type by remember { mutableStateOf(EntryType.EXPENSE) }
    var amountText by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var memberId by remember { mutableStateOf<Long?>(null) }
    var expenseCategoryId by remember { mutableStateOf<Long?>(null) }
    var incomeCategoryId by remember { mutableStateOf<Long?>(null) }
    var sourceText by remember { mutableStateOf("") }
    var fromParty by remember { mutableStateOf("") }
    var toParty by remember { mutableStateOf("") }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showMemberManager by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    // 默认沿用上次的成员；上次选中的成员若已归档则回落到第一个可用成员。
    LaunchedEffect(lastMemberId, members) {
        if (memberId == null) memberId = lastMemberId ?: members.firstOrNull()?.id
    }
    LaunchedEffect(members, memberId) {
        if (memberId != null && members.none { it.id == memberId }) {
            memberId = members.firstOrNull()?.id
        }
    }
    // 分类偏好可能指向另一种类型，先校验再采用。
    LaunchedEffect(lastCategoryId, expenseCategories) {
        if (expenseCategoryId == null) {
            val candidate = lastCategoryId
            expenseCategoryId = if (candidate != null && expenseCategories.any { it.id == candidate }) {
                candidate
            } else {
                expenseCategories.firstOrNull()?.id
            }
        }
    }
    LaunchedEffect(incomeCategories) {
        if (incomeCategoryId == null) incomeCategoryId = incomeCategories.firstOrNull()?.id
    }

    // AI 识别结果只写进表单 state，不自动保存；日期保持用户当前选择。
    LaunchedEffect(aiDraft) {
        val draft = aiDraft ?: return@LaunchedEffect
        type = draft.type
        amountText = draft.amountText
        purpose = draft.purpose
        date = draft.date
        if (draft.memberId != null) memberId = draft.memberId
        when (draft.type) {
            EntryType.EXPENSE -> if (draft.categoryId != null) expenseCategoryId = draft.categoryId
            EntryType.INCOME -> if (draft.categoryId != null) incomeCategoryId = draft.categoryId
            else -> Unit
        }
        when (draft.type) {
            EntryType.INCOME -> sourceText = draft.counterparty
            EntryType.TRANSFER_OUT -> toParty = draft.counterparty
            EntryType.TRANSFER -> toParty = draft.counterparty
            else -> Unit
        }
        errorText = null
        aiDraft = null
    }

    val context = LocalContext.current
    val firstMemberName = members.firstOrNull()?.name.orEmpty()
    val secondMemberName = members.getOrNull(1)?.name.orEmpty()
    val needsCategory = type == EntryType.EXPENSE || type == EntryType.INCOME
    val visibleCategories = if (type == EntryType.INCOME) incomeCategories else expenseCategories
    val currentCategoryId = if (type == EntryType.INCOME) incomeCategoryId else expenseCategoryId
    val categoryName = visibleCategories.firstOrNull { it.id == currentCategoryId }?.name ?: "选择分类"

    // 当前选中的分类被删除后自动回落，避免把新流水记到已删分类下。
    LaunchedEffect(visibleCategories, currentCategoryId) {
        if (currentCategoryId != null && visibleCategories.none { it.id == currentCategoryId }) {
            val fallback = visibleCategories.firstOrNull()?.id
            if (type == EntryType.INCOME) incomeCategoryId = fallback else expenseCategoryId = fallback
        }
    }

    fun save() {
        val mid = memberId
        if (mid == null) {
            errorText = "请先选择成员"
            return
        }
        val chosenCategory = if (needsCategory) currentCategoryId else null
        val currentMemberName = members.firstOrNull { it.id == mid }?.name.orEmpty()
        val from: String? = when (type) {
            EntryType.INCOME -> sourceText.trim().ifEmpty { null }
            EntryType.TRANSFER_OUT -> currentMemberName.ifEmpty { null }
            EntryType.TRANSFER -> fromParty.trim().ifEmpty { firstMemberName.ifEmpty { "转出方" } }
            else -> null
        }
        val to: String? = when (type) {
            EntryType.TRANSFER_OUT -> toParty.trim().ifEmpty { "外部" }
            EntryType.TRANSFER -> toParty.trim().ifEmpty { secondMemberName.ifEmpty { firstMemberName } }
            else -> null
        }
        val defaultPurpose = when (type) {
            EntryType.EXPENSE -> "未填用途"
            EntryType.INCOME -> "收入"
            EntryType.TRANSFER_OUT -> "对外转出"
            EntryType.TRANSFER -> "内部划转"
        }
        val ok = vm.save(
            type = type,
            amountText = amountText,
            day = date.toEpochDay(),
            memberId = mid,
            categoryId = chosenCategory,
            purpose = purpose.trim().ifEmpty { defaultPurpose },
            fromParty = from,
            toParty = to
        )
        if (!ok) {
            errorText = "请输入有效金额（最多两位小数，不能为 0；负数仅用于支出红冲）"
            return
        }
        errorText = null
        amountText = ""
        purpose = ""
        val message = when (type) {
            EntryType.EXPENSE -> "已记一笔支出"
            EntryType.INCOME -> "已记一笔收入"
            EntryType.TRANSFER_OUT -> "对外转出已记录（单列，不计入支出）"
            EntryType.TRANSFER -> "内部划转已留痕"
        }
        scope.launch { snackbarHostState.showFor3Seconds(message) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 扩展功能：开关关闭时整块不显示，原有手填流程不受任何影响。
        if (aiEnabled) {
            AiQuickEntry(
                vm = vm,
                snackbarHostState = snackbarHostState,
                scope = scope,
                onDraft = { aiDraft = it }
            )
        }
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
        ) {
            Text("日期：$date")
        }

        Text("记在谁名下", style = MaterialTheme.typography.labelMedium)
        MemberChipsRow(
            members = members,
            selectedId = memberId,
            onSelect = { memberId = it },
            modifier = Modifier.fillMaxWidth(),
            trailing = {
                TextButton(onClick = { showMemberManager = true }) { Text("管理成员") }
            }
        )

        if (needsCategory) {
            OutlinedButton(
                onClick = { showCategoryPicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("分类：$categoryName")
            }
        }

        when (type) {
            EntryType.INCOME -> OutlinedTextField(
                value = sourceText,
                onValueChange = { sourceText = it },
                label = { Text("来源（选填）") },
                placeholder = { Text("如：公司 / 朋友") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            EntryType.TRANSFER_OUT -> OutlinedTextField(
                value = toParty,
                onValueChange = { toParty = it },
                label = { Text("转给谁") },
                placeholder = { Text("如：朋友 / 房东") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            EntryType.TRANSFER -> {
                OutlinedTextField(
                    value = fromParty,
                    onValueChange = { fromParty = it },
                    label = { Text("转出方") },
                    placeholder = { Text(firstMemberName.ifEmpty { "如：银行卡 / 微信" }) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = toParty,
                    onValueChange = { toParty = it },
                    label = { Text("转入方") },
                    placeholder = { Text(secondMemberName.ifEmpty { "如：对象 / 微信" }) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            else -> Unit
        }

        val hint = when (type) {
            EntryType.INCOME -> "收入计入收入合计与结余。"
            EntryType.TRANSFER_OUT -> "对外转出单列展示，不进支出合计，但计入结余减项。"
            EntryType.TRANSFER -> "内部划转仅留痕，不进任何汇总，也不影响结余。"
            else -> null
        }
        hint?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        errorText?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) {
            Text("保存")
        }
    }

    if (showCategoryPicker && needsCategory) {
        CategoryPickerDialog(
            kind = type,
            selectedId = currentCategoryId,
            vm = vm,
            snackbarHostState = snackbarHostState,
            scope = scope,
            onSelect = { id ->
                if (type == EntryType.INCOME) incomeCategoryId = id else expenseCategoryId = id
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false }
        )
    }

    if (showMemberManager) {
        MemberManagerDialog(
            vm = vm,
            snackbarHostState = snackbarHostState,
            onDismiss = { showMemberManager = false }
        )
    }
}
