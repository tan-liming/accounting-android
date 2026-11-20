package com.duo.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.duo.ledger.ai.AiDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 独立扩展组件：不配 Key、关掉开关或识别失败，都不影响下面原有的手填表单。 */
@Composable
fun AiQuickEntry(
    vm: LedgerViewModel,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    onDraft: (AiDraft) -> Unit,
    modifier: Modifier = Modifier
) {
    // 中文输入法的拼音串靠 composition 标记未上屏部分；用 String 保存状态会在重组时
    // 丢掉 composition 和光标位置，与 IME 的 commitText 时序竞争就表现为「字符顺序乱」。
    // TextFieldValue 把文本、选区、组合区一起留住。
    var text by remember { mutableStateOf(TextFieldValue("")) }
    var loading by remember { mutableStateOf(false) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("一句话记账（AI）", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("如：昨天中午我花了 35 吃午饭") },
                singleLine = true,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val input = text.text.trim()
                        if (input.isEmpty()) return@Button
                        loading = true
                        scope.launch {
                            val result = vm.recognize(input)
                            loading = false
                            val draft = result.getOrNull()
                            if (draft != null) {
                                // 识别后保留原话，方便微调再识别；清空只由「清空」按钮负责。
                                onDraft(draft)
                                snackbarHostState.showFor3Seconds("已填入表单，请确认后保存")
                            } else {
                                snackbarHostState.showFor3Seconds(
                                    result.exceptionOrNull()?.message ?: "识别失败"
                                )
                            }
                        }
                    },
                    enabled = !loading && text.text.isNotBlank()
                ) {
                    Text(if (loading) "识别中…" else "识别")
                }
                TextButton(
                    onClick = { text = TextFieldValue("") },
                    enabled = !loading && text.text.isNotEmpty()
                ) {
                    Text("清空")
                }
            }
            Text(
                "只把这句话和成员/分类名单发送到 DeepSeek；结果仅填表（含识别出的日期），不会自动保存。Key 在右上角设置里填。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
