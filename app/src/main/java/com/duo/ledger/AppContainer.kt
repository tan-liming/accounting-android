package com.duo.ledger

import android.content.Context
import com.duo.ledger.data.LedgerPreferences
import com.duo.ledger.data.LedgerRepository
import com.duo.ledger.data.local.AppDatabase

/** 极简依赖容器：单机小应用没必要引入 DI 框架。 */
object AppContainer {
    @Volatile
    private var instance: LedgerRepository? = null

    fun repository(context: Context): LedgerRepository =
        instance ?: synchronized(this) {
            instance ?: LedgerRepository(
                AppDatabase.build(context.applicationContext),
                LedgerPreferences(context.applicationContext)
            ).also { instance = it }
        }
}
