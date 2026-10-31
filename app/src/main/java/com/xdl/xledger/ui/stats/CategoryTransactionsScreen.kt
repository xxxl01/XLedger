package com.xdl.xledger.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xdl.xledger.data.db.entity.TransactionEntity
import com.xdl.xledger.ui.common.SubPageScaffold
import com.xdl.xledger.ui.theme.xledgerColors
import com.xdl.xledger.util.DateTimeFormat
import com.xdl.xledger.util.MoneyFormat

/** 分类明细页：某月某分类的每条记录，按金额从大到小排序，点击进入编辑页。 */
@Composable
fun CategoryTransactionsScreen(
    year: Int,
    month: Int,
    transactionType: Int,
    categoryId: Int?,
    includeChildren: Boolean,
    onBack: () -> Unit,
    onOpenEdit: (Int) -> Unit,
    viewModel: CategoryTransactionsViewModel = viewModel(
        factory = CategoryTransactionsViewModel.factory(
            year, month, transactionType, categoryId, includeChildren,
        ),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.xledgerColors
    val lifecycleOwner = LocalLifecycleOwner.current

    // 从编辑页改了或删了记录后返回，静默刷新列表
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.load(showLoading = false)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val typeLabel = if (transactionType == TransactionEntity.TYPE_INCOME) "收入" else "支出"
    val amountColor = if (transactionType == TransactionEntity.TYPE_INCOME) colors.income else colors.expense

    SubPageScaffold(
        title = uiState.title.ifEmpty { "分类明细" },
        subtitle = "${DateTimeFormat.formatMonthLabel(year, month)} · $typeLabel " +
            "¥%.2f · 共 ${uiState.items.size} 条".format(uiState.total),
        onBack = onBack,
    ) { innerPadding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = colors.accent) }

            uiState.errorMessage != null -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = uiState.errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.expense,
                )
            }

            uiState.items.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "这个分类本月没有记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                items(uiState.items, key = { it.transaction.transactionId }) { item ->
                    val transaction = item.transaction
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.card, RoundedCornerShape(8.dp))
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(8.dp))
                            .clickable { onOpenEdit(transaction.transactionId) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = transaction.description ?: "未命名记录",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${DateTimeFormat.formatMonthDay(transaction.occurredAt)} " +
                                    "${DateTimeFormat.formatTime(transaction.occurredAt)} · ${item.categoryLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                        Text(
                            text = MoneyFormat.formatSigned(transaction.amount, transaction.transactionType),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = amountColor,
                        )
                    }
                }
            }
        }
    }
}
