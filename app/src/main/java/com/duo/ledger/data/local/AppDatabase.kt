package com.duo.ledger.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MemberEntity::class, CategoryEntity::class, RecordEntity::class],
    views = [ActiveRecord::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memberDao(): MemberDao
    abstract fun categoryDao(): CategoryDao
    abstract fun recordDao(): RecordDao

    companion object {
        const val DB_NAME = "duo_ledger.db"

        private val EXPENSE_CATEGORIES = listOf(
            "餐饮", "买菜", "交通", "日用", "居住", "娱乐", "医疗",
            "人情", "服饰", "教育", "通讯", "旅行", "其他"
        )
        private val INCOME_CATEGORIES = listOf(
            "工资", "奖金", "红包", "兼职", "理财收益", "其他收入"
        )

        /**
         * v1 -> v2：分类表加 kind 列，并把内置收入分类补进去。
         * 老库只走 Migration 不走 onCreate，所以两份 seed 都要写；
         * 流水表一条不动，已有账目不会丢。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN kind TEXT NOT NULL DEFAULT 'EXPENSE'")
                INCOME_CATEGORIES.forEachIndexed { index, name ->
                    db.execSQL(
                        "INSERT INTO categories (name, sortOrder, builtIn, archived, kind) " +
                                "VALUES (?, ?, 1, 0, 'INCOME')",
                        arrayOf<Any>(name, 100 + index)
                    )
                }
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_1_2)
                .addCallback(SeedCallback())
                .build()

        /** 首次建库（v2 新装）时写入两名成员和两套内置分类。 */
        private class SeedCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("INSERT INTO members (id, name, sortOrder, archived) VALUES (1, '我', 0, 0)")
                db.execSQL("INSERT INTO members (id, name, sortOrder, archived) VALUES (2, '对象', 1, 0)")

                EXPENSE_CATEGORIES.forEachIndexed { index, name ->
                    db.execSQL(
                        "INSERT INTO categories (name, sortOrder, builtIn, archived, kind) " +
                                "VALUES (?, ?, 1, 0, 'EXPENSE')",
                        arrayOf<Any>(name, index)
                    )
                }
                INCOME_CATEGORIES.forEachIndexed { index, name ->
                    db.execSQL(
                        "INSERT INTO categories (name, sortOrder, builtIn, archived, kind) " +
                                "VALUES (?, ?, 1, 0, 'INCOME')",
                        arrayOf<Any>(name, 100 + index)
                    )
                }
            }
        }
    }
}
