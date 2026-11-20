package com.duo.ledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.duo.ledger.ui.LedgerRoot
import com.duo.ledger.ui.LedgerViewModel
import com.duo.ledger.ui.theme.DuoLedgerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DuoLedgerTheme {
                val vm: LedgerViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            LedgerViewModel(application) as T
                    }
                )
                LedgerRoot(vm)
            }
        }
    }
}
