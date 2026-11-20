package com.duo.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch

@Composable
fun SettingsDialog(vm: LedgerViewModel, onDismiss: () -> Unit) {
    val savedKey by vm.aiApiKey.collectAsState()
    val savedModel by vm.aiModel.collectAsState()
    val savedBaseUrl by vm.aiBaseUrl.collectAsState()
    val savedEnabled by vm.aiEnabled.collectAsState()

    var key by remember { mutableStateOf(savedKey) }
    var model by remember { mutableStateOf(savedModel) }
    var baseUrl by remember { mutableStateOf(savedBaseUrl) }
    var enabled by remember { mutableStateOf(savedEnabled) }
    var keyVisible by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // DataStore 首次异步发值后同步到输入框，但用户正在输入时不会被覆盖。
    LaunchedEffect(savedKey) { key = savedKey }
    LaunchedEffect(savedModel) { model = savedModel }
    LaunchedEffect(savedBaseUrl) { baseUrl = savedBaseUrl }
    LaunchedEffect(savedEnabled) { enabled = savedEnabled }

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
                Text("设置", style = MaterialTheme.typography.titleMedium)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用 AI 一句话记账", modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("DeepSeek API Key") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (keyVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(onClick = { keyVisible = !keyVisible }) {
                            Text(if (keyVisible) "隐藏" else "显示")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("接口地址（Base URL）") },
                    singleLine = true,
                    supportingText = {
                        Text("默认 ${com.duo.ledger.ai.DEFAULT_AI_BASE_URL}；可填 /v1 兼容地址或代理/中转")
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("模型名") },
                    singleLine = true,
                    supportingText = { Text("默认 deepseek-flash；模型改名时直接在这里改即可") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "接口地址只补 /chat/completions 后缀，留空则回到官方默认。Key 只保存在本机应用私有目录，不写进代码、不上传。启用后，只有你主动点「识别」时才会把输入的那句话连同成员/分类名单发送到该地址；历史流水、金额、结余一律不发送。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
                    Button(
                        enabled = !saving,
                        onClick = {
                            saving = true
                            // 等 DataStore 写盘完成再关闭，立刻点识别也能读到新配置。
                            scope.launch {
                                try {
                                    vm.saveAiSettings(key, model, enabled, baseUrl)
                                    onDismiss()
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    ) { Text(if (saving) "保存中…" else "保存") }
                }
            }
        }
    }
}
