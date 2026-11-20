package com.duo.ledger.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.duo.ledger.data.Period
import java.time.LocalDate

@Composable
fun PeriodBar(
    period: Period,
    label: String,
    anchor: LocalDate,
    onPeriodChange: (Period) -> Unit,
    onShift: (Int) -> Unit,
    onPickDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 日历预填当前 anchor，翻到历史后打开也停在那一带，方便就近微调。
    fun openDatePicker() {
        DatePickerDialog(
            context,
            { _, y, m, d -> onPickDate(LocalDate.of(y, m + 1, d)) },
            anchor.year,
            anchor.monthValue - 1,
            anchor.dayOfMonth
        ).show()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onShift(-1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一周期")
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable { openDatePicker() }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "选择日期",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = { onShift(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一周期")
            }
            TextButton(onClick = { onPickDate(LocalDate.now()) }) {
                Text("今天", style = MaterialTheme.typography.labelMedium)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Period.entries.forEach { p ->
                FilterChip(
                    selected = period == p,
                    onClick = { onPeriodChange(p) },
                    label = { Text(p.label) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
