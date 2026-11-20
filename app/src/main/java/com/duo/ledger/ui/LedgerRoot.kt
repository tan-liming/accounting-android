package com.duo.ledger.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.duo.ledger.R
import com.duo.ledger.data.BackupData
import com.duo.ledger.data.Period
import com.duo.ledger.data.PeriodRange
import com.duo.ledger.data.info
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ExportScope(val label: String, val code: String, val period: Period?) {
    DAY("当前日", "day", Period.DAY),
    WEEK("当前周", "week", Period.WEEK),
    MONTH("当前月", "month", Period.MONTH),
    YEAR("当前年", "year", Period.YEAR),
    ALL("全量", "all", null)
}

private data class PendingExport(
    val range: PeriodRange?,
    val memberId: Long?,
    val label: String
)

private fun formattedTime(millis: Long): String =
    if (millis <= 0L) "未知时间" else LocalDateTime.ofInstant(
        Instant.ofEpochMilli(millis), ZoneId.systemDefault()
    ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

private fun backupFileName(): String =
    "duo_ledger_backup_" + LocalDateTime.now()
        .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".json"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerRoot(vm: LedgerViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var exportMenuOpen by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<PendingExport?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var backupData by remember { mutableStateOf<BackupData?>(null) }
    var restoring by remember { mutableStateOf(false) }
    var reviveDeleted by remember { mutableStateOf(false) }

    // 系统文件选择器：不需要 FileProvider，也不需要存储权限。
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        val pending = pendingExport
        pendingExport = null
        if (uri == null || pending == null) return@rememberLauncherForActivityResult
        scope.launch {
            val csv = vm.exportCsv(pending.range, pending.memberId)
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(csv.toByteArray(Charsets.UTF_8))
            }
            snackbarHostState.showFor3Seconds("已导出 ${pending.label}")
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        scope.launch {
            val json = vm.exportBackup()
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(json.toByteArray(Charsets.UTF_8))
            }
            snackbarHostState.showFor3Seconds("已生成完整备份")
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        val text = try {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        } catch (e: Exception) {
            null
        }
        if (text == null) {
            scope.launch { snackbarHostState.showFor3Seconds("读取备份文件失败") }
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val inspected = vm.inspectBackup(text)
            val data = inspected.getOrNull()
            if (data != null) {
                backupData = data
            } else {
                snackbarHostState.showFor3Seconds(
                    inspected.exceptionOrNull()?.message ?: "备份文件无效"
                )
            }
        }
    }

    fun startExport(exportScope: ExportScope) {
        val range = exportScope.period?.let { vm.rangeFor(it) }
        // 全量导出无视成员筛选；周期导出跟随当前成员筛选与选中日期。
        val memberId = if (range == null) null else vm.memberFilter
        pendingExport = PendingExport(range, memberId, exportScope.label)
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, "duo_ledger_${exportScope.code}_${LocalDate.now()}.csv")
        }
        exportLauncher.launch(intent)
    }

    fun startBackup() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, backupFileName())
        }
        backupLauncher.launch(intent)
    }

    fun startRestore() {
        // 部分文件管理器不认 application/json，放开类型由用户自己挑。
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        restoreLauncher.launch(intent)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                    Box {
                        TextButton(onClick = { exportMenuOpen = true }) { Text("导出 / 备份") }
                        DropdownMenu(
                            expanded = exportMenuOpen,
                            onDismissRequest = { exportMenuOpen = false }
                        ) {
                            ExportScope.entries.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.label) },
                                    onClick = { exportMenuOpen = false; startExport(s) }
                                )
                            }
                            Text(
                                "以上为 CSV 流水；周期导出跟随当前日期与成员筛选。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            DropdownMenuItem(
                                text = { Text("完整备份（JSON）") },
                                onClick = { exportMenuOpen = false; startBackup() }
                            )
                            DropdownMenuItem(
                                text = { Text("从备份恢复…") },
                                onClick = { exportMenuOpen = false; startRestore() }
                            )
                            Text(
                                "备份含全部账目（含已删除）、成员、分类与设置；API Key 不写入备份。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = vm.tab == tab,
                        onClick = { vm.selectTab(tab) },
                        icon = { Icon(tabIcon(tab), contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        when (vm.tab) {
            Tab.ADD -> AddRecordScreen(vm, snackbarHostState, scope, Modifier.padding(padding))
            Tab.RECORDS -> RecordsScreen(vm, snackbarHostState, scope, Modifier.padding(padding))
            Tab.STATS -> StatsScreen(vm, Modifier.padding(padding))
        }
    }

    if (showSettings) {
        SettingsDialog(vm = vm, onDismiss = { showSettings = false })
    }

    backupData?.let { data ->
        val info = data.info()
        AlertDialog(
            onDismissRequest = {
                if (!restoring) {
                    backupData = null
                    reviveDeleted = false
                }
            },
            title = { Text("确认恢复备份") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("导出时间：${formattedTime(info.exportedAtMillis)}")
                    Text("成员 ${info.memberCount} 位 · 分类 ${info.categoryCount} 个")
                    Text(
                        "流水 ${info.recordCount} 笔（有效 ${info.activeRecordCount} 笔，" +
                            "已删除 ${info.deletedRecordCount} 笔）"
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "恢复会覆盖当前全部账目、成员与分类，且不可撤销。建议先导出一份当前数据。",
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "API Key 不在备份里，恢复后保持当前已填的值。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = reviveDeleted, onCheckedChange = { reviveDeleted = it })
                        Text(
                            "完整回滚：把备份里所有已删流水与已停用成员恢复（含此前手动删的单笔流水）",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !restoring,
                    onClick = {
                        restoring = true
                        scope.launch {
                            val result = vm.restoreBackup(data, reviveDeleted)
                            restoring = false
                            backupData = null
                            reviveDeleted = false
                            snackbarHostState.showFor3Seconds(
                                result.fold(
                                    onSuccess = { "已恢复 $it 笔流水" },
                                    onFailure = { "恢复失败：${it.message}" }
                                )
                            )
                        }
                    }
                ) { Text(if (restoring) "恢复中…" else "确认覆盖恢复") }
            },
            dismissButton = {
                TextButton(
                    enabled = !restoring,
                    onClick = { backupData = null; reviveDeleted = false }
                ) { Text("取消") }
            }
        )
    }
}

private fun tabIcon(tab: Tab): ImageVector = when (tab) {
    Tab.ADD -> Icons.Default.Add
    Tab.RECORDS -> Icons.AutoMirrored.Filled.List
    Tab.STATS -> Icons.Default.DateRange
}
