package com.duo.ledger.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.duo.ledger.data.local.MemberEntity

/** 成员数量不固定，用横向滚动避免多人时 chips 被挤压。 */
@Composable
fun MemberChipsRow(
    members: List<MemberEntity>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    showAll: Boolean = false,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showAll) {
            FilterChip(
                selected = selectedId == null,
                onClick = { onSelect(null) },
                label = { Text("合计") }
            )
        }
        members.forEach { m ->
            FilterChip(
                selected = selectedId == m.id,
                onClick = { onSelect(m.id) },
                label = { Text(m.name) }
            )
        }
        trailing?.invoke()
    }
}
