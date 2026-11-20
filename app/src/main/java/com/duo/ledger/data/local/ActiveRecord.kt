package com.duo.ledger.data.local

import androidx.room.DatabaseView

/**
 * 统一过滤软删除记录。所有查询都走这个视图，避免某个 DAO 漏写 WHERE deletedAt IS NULL。
 */
@DatabaseView(
    viewName = "active_records",
    value = "SELECT id, type, amountCents, day, memberId, categoryId, purpose, fromParty, toParty, createdAt, updatedAt " +
            "FROM records WHERE deletedAt IS NULL"
)
data class ActiveRecord(
    val id: Long,
    val type: EntryType,
    val amountCents: Long,
    val day: Long,
    val memberId: Long,
    val categoryId: Long?,
    val purpose: String,
    val fromParty: String?,
    val toParty: String?,
    val createdAt: Long,
    val updatedAt: Long
)
