package com.xdl.xledger.ui.stats

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xdl.xledger.ui.common.SubPageScaffold
import com.xdl.xledger.ui.theme.xledgerColors
import com.xdl.xledger.util.DateTimeFormat

/** 大额支出页：某月单笔金额最大的 20 笔支出，从大到小，点击进入编辑页。 */
@Composable
fun TopExpensesScreen(
    year: Int,
    month: Int,
    onBack: () -> Unit,
    onOpenEdit: (Int) -> Unit,
    viewModel: TopExpensesViewModel = viewModel(
        factory = TopExpensesViewModel.factory(year, month),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // 从编辑页改了或删了记录后返回，静默刷新列表
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.load(showLoading = false)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SubPageScaffold(
        title = "大额支出",
        subtitle = "${DateTimeFormat.formatMonthLabel(year, month)} · 前 ${uiState.items.size} 笔 " +
            "¥%.2f".format(uiState.total),
        onBack = onBack,
    ) { innerPadding ->
        RankedTransactionsBody(
            uiState = uiState,
            amountColor = MaterialTheme.xledgerColors.expense,
            emptyText = "本月没有支出记录",
            innerPadding = innerPadding,
            onOpenEdit = onOpenEdit,
        )
    }
}
