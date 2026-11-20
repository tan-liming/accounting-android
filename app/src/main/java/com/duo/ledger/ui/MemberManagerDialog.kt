package com.duo.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.duo.ledger.data.local.MemberImpact
import com.duo.ledger.util.centsToYuan
import kotlinx.coroutines.launch

private data class PendingDelete(val id: Long, val name: String, val impact: MemberImpact)

@Composable
fun MemberManagerDialog(
    vm: LedgerViewModel,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit
) {
    val members by vm.members.collectAsState()
    val allMembers by vm.allMembers.collectAsState()
    val deletedMembers = allMembers.filter { it.archived }
    val scope = rememberCoroutineScope()
    var editingId by remember { mutableStateOf<Long?>(null) }
    var editName by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }

    // 确认页和列表页共用一个 Dialog，避免嵌套 Dialog 的层级/焦点问题。
    Dialog(onDismissRequest = { if (pendingDelete != null) pendingDelete = null else onDismiss() }) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            if (pendingDelete == null) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("成员管理", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "改名只改显示名，历史流水仍按成员 ID 归属。删除成员会一并移除其名下全部流水（软删除，可撤销、可从 JSON 备份回滚），历史统计与导出会随之变化。至少保留一个成员。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(members, key = { "active-${it.id}" }) { m ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (editingId == m.id) {
                                    OutlinedTextField(
                                        value = editName,
                                        onValueChange = { editName = it },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = {
                                        vm.renameMember(m.id, editName)
                                        editingId = null
                                    }) {
                                        Icon(Icons.Default.Check, contentDescription = "保存改名")
                                    }
                                    IconButton(onClick = { editingId = null }) {
                                        Icon(Icons.Default.Close, contentDescription = "取消")
                                    }
                                } else {
                                    Text(m.name, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { editingId = m.id; editName = m.name }) {
                                        Icon(Icons.Default.Edit, contentDescription = "改名")
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                pendingDelete = PendingDelete(
                                                    m.id, m.name, vm.memberImpact(m.id)
                                                )
                                            }
                                        },
                                        enabled = members.size > 1
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "删除成员")
                                    }
                                }
                            }
                        }
                        // 撤销窗口过后、以及旧版遗留的停用成员，都从这里找回。
                        if (deletedMembers.isNotEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier.padding(top = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        "已删除成员（${deletedMembers.size}）",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        "可点「恢复」找回；恢复后若想删干净，再走一次带确认的删除即可。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            items(deletedMembers, key = { "deleted-${it.id}" }) { m ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(m.name, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { vm.restoreMember(m.id) }) { Text("恢复") }
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("新成员名") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            vm.addMember(newName)
                            newName = ""
                        }) { Text("添加") }
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("完成") }
                }
            } else {
                val target = pendingDelete!!
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("删除成员「${target.name}」？", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "将同时移除其名下 ${target.impact.recordCount} 笔流水：支出 ¥ ${target.impact.expenseCents.centsToYuan()}、收入 ¥ ${target.impact.incomeCents.centsToYuan()}。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "历史统计、结余与 CSV 导出都会随之变化；这是改账，且撤销窗口过后只能靠备份找回。建议先导出一份完整备份。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "软删除，不物理清除：确认后 3 秒内可点提示条上的「撤销」恢复；也可在恢复 JSON 备份时勾选「完整回滚」找回。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = { pendingDelete = null }) { Text("取消") }
                        Button(onClick = {
                            val deleting = target
                            pendingDelete = null
                            scope.launch {
                                val ok = vm.deleteMember(deleting.id, deleting.name)
                                if (ok) {
                                    snackbarHostState.showFor3Seconds(
                                        message = "已删除「${deleting.name}」及其 ${deleting.impact.recordCount} 笔流水",
                                        actionLabel = "撤销",
                                        onAction = { vm.undoDeleteMember() }
                                    )
                                    // 3 秒窗口过后，撤销入口随提示一起消失。
                                    vm.clearUndoMember()
                                } else {
                                    snackbarHostState.showFor3Seconds("至少要保留一个成员")
                                }
                            }
                        }) { Text("确认删除") }
                    }
                }
            }
        }
    }
}
