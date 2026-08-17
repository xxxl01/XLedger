package com.xdl.xledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.xdl.xledger.ui.navigation.XLedgerNavGraph
import com.xdl.xledger.ui.theme.XLedgerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XLedgerTheme {
                XLedgerNavGraph()
            }
        }
    }
}
