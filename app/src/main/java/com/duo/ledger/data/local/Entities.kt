package com.duo.ledger.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "members")
data class MemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    val archived: Boolean = false
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    val builtIn: Boolean = false,
    val archived: Boolean = false,
    /** 支出分类 / 收入分类分开；v2 新增列，老数据一律落 EXPENSE。 */
    @ColumnInfo(defaultValue = "'EXPENSE'") val kind: EntryType = EntryType.EXPENSE
)

@Entity(
    tableName = "records",
    foreignKeys = [
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["memberId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["day"]),
        Index(value = ["type", "day"]),
        Index(value = ["memberId"]),
        Index(value = ["categoryId"])
    ]
)
data class RecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: EntryType,
    /** 金额，单位：分。绝不使用浮点存储金额。 */
    val amountCents: Long,
    /** LocalDate.toEpochDay()，日历日不带时区，设备改时区也不会串天。 */
    val day: Long,
    /** 记账人 / 付款人，不是最终承担人（本应用不做分摊）。 */
    val memberId: Long,
    /** 仅支出使用；转账为 null。 */
    val categoryId: Long?,
    val purpose: String,
    /** 转账：转出方，成员名或账户标签（轻量文本，不建账户实体）。 */
    val fromParty: String? = null,
    /** 转账：转入方。 */
    val toParty: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    /** 软删除标记；财务流水不做物理删除，便于撤销。 */
    val deletedAt: Long? = null
)
