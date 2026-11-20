package com.duo.ledger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.duo.ledger.data.local.CategoryEntity
import com.duo.ledger.data.local.EntryType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 分类选择 + 管理的内容，不含 Dialog 外壳。
 * 记一笔页用 CategoryPickerDialog 包一层；编辑流水页在同一个 Dialog 内切换显示，避免嵌套弹窗。
 * 用 LazyColumn 而不是 DropdownMenu：分类多时 Popup 会显示不全且滚动容易失效。
 */
@Composable
fun CategoryPickerContent(
    kind: EntryType,
    categories: List<CategoryEntity>,
    selectedId: Long?,
    vm: LedgerViewModel,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    onSelect: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = if (kind == EntryType.INCOME) "收入" else "支出"
    val active = categories.filter { !it.archived && it.kind == kind }
    val deleted = categories.filter { it.archived && it.kind == kind }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var editName by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("选择${label}分类", style = MaterialTheme.typography.titleMedium)
        Text(
            "点名称选中，右侧可改名 / 删除。删除只从列表移除，已记账目的分类归属保留；3 秒内可撤销。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        // 固定最大高度 + 自身滚动，分类再多也能划到最下面。
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(active, key = { "active-${it.id}" }) { c ->
                if (editingId == c.id) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            vm.renameCategory(c.id, editName)
                            editingId = null
                        }) { Icon(Icons.Default.Check, contentDescription = "保存改名") }
                        IconButton(onClick = { editingId = null }) {
                            Icon(Icons.Default.Close, contentDescription = "取消")
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelect(c.id) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                c.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (c.id == selectedId) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            if (c.id == selectedId) {
                                Text(
                                    "当前",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(onClick = { editingId = c.id; editName = c.name }) {
                            Icon(Icons.Default.Edit, contentDescription = "改名")
                        }
                        IconButton(onClick = {
                            vm.deleteCategory(c.id)
                            scope.launch {
                                snackbarHostState.showFor3Seconds(
                                    message = "已删除分类「${c.name}」",
                                    actionLabel = "撤销"
                                ) { vm.restoreCategory(c.id) }
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除分类")
                        }
                    }
                }
            }

            if (deleted.isNotEmpty()) {
                item {
                    Text(
                        "已删除分类（${deleted.size}，可恢复）",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
                items(deleted, key = { "deleted-${it.id}" }) { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            c.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        TextButton(onClick = { vm.restoreCategory(c.id) }) { Text("恢复") }
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("新建${label}分类") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val name = newName
                scope.launch {
                    val id = vm.addCategory(name, kind)
                    if (id != null) {
                        newName = ""
                        onSelect(id)
                    } else {
                        snackbarHostState.showFor3Seconds("分类名不能为空")
                    }
                }
            }) { Text("添加") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onClose) { Text("完成") }
        }
    }
}

@Composable
fun CategoryPickerDialog(
    kind: EntryType,
    selectedId: Long?,
    vm: LedgerViewModel,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val categories by vm.allCategories.collectAsState()
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            CategoryPickerContent(
                kind = kind,
                categories = categories,
                selectedId = selectedId,
                vm = vm,
                snackbarHostState = snackbarHostState,
                scope = scope,
                onSelect = onSelect,
                onClose = onDismiss
            )
        }
    }
}
