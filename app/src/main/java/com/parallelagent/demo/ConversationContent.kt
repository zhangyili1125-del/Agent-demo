package com.parallelagent.demo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
internal fun ConversationContent(
    state: DemoState,
    scenario: DemoScenario,
    modifier: Modifier = Modifier,
    onFillExample: () -> Unit,
    onConfirm: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
) {
    if (state.stage == DemoStage.IDLE) {
        WelcomeContent(
            scenario = scenario,
            modifier = modifier,
            onFillExample = onFillExample,
        )
        return
    }

    val listState = rememberLazyListState()
    LaunchedEffect(state.stage, state.completedSteps) {
        delay(80)
        val lastIndex = listState.layoutInfo.totalItemsCount - 1
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "user") {
            UserMessage(state.request)
        }

        if (state.hasReached(DemoStage.UNDERSTANDING)) {
            item(key = "understanding") {
                UnderstandingCard(state = state, scenario = scenario)
            }
        }

        if (state.hasReached(DemoStage.READY_TO_ACT)) {
            item(key = "capability") {
                AgentCard(title = "准备调用的能力") {
                    Text(
                        text = scenario.capabilityTitle,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = scenario.capabilityDescription,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (state.hasReached(DemoStage.WAITING_CONFIRMATION)) {
            item(key = "confirmation") {
                ConfirmationCard(
                    state = state,
                    question = scenario.confirmationQuestion,
                    onConfirm = onConfirm,
                    onStop = onStop,
                )
            }
        }

        if (state.hasReached(DemoStage.RUNNING)) {
            item(key = "running") {
                ExecutionCard(state = state, steps = scenario.executionSteps)
            }
        }

        if (state.hasReached(DemoStage.RESULT)) {
            item(key = "result") {
                ResultCard(scenario = scenario, onReset = onReset)
            }
        }

        if (state.stage == DemoStage.STOPPED) {
            item(key = "stopped") {
                StoppedCard(onReset = onReset)
            }
        }
    }
}

@Composable
private fun WelcomeContent(
    scenario: DemoScenario,
    modifier: Modifier,
    onFillExample: () -> Unit,
) {
    Box(modifier = modifier.padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.size(64.dp),
                color = BrandBlue,
                shape = RoundedCornerShape(20.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "P",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = scenario.welcomeTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = scenario.welcomeMessage,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(18.dp))
            TextButton(onClick = onFillExample) {
                Text("填入示例任务")
            }
        }
    }
}

@Composable
private fun UserMessage(request: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.86f),
            color = BrandBlue,
            shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
        ) {
            Text(
                text = request,
                modifier = Modifier.padding(16.dp),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun UnderstandingCard(
    state: DemoState,
    scenario: DemoScenario,
) {
    AgentCard(title = "Agent 对任务的理解") {
        when {
            state.stage == DemoStage.UNDERSTANDING -> Text("正在分析任务目标、边界和需要确认的操作…")
            state.stage == DemoStage.STOPPED && !state.hasReached(DemoStage.READY_TO_ACT) -> {
                Text("任务在理解阶段被停止，未生成最终理解。")
            }
            else -> Text(scenario.intentSummary)
        }
    }
}

@Composable
private fun ConfirmationCard(
    state: DemoState,
    question: String,
    onConfirm: () -> Unit,
    onStop: () -> Unit,
) {
    AgentCard(title = "需要你确认") {
        Text(question)
        Spacer(modifier = Modifier.height(12.dp))
        when {
            state.stage == DemoStage.WAITING_CONFIRMATION -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onConfirm) {
                        Text("确认并继续")
                    }
                    OutlinedButton(onClick = onStop) {
                        Text("停止任务")
                    }
                }
            }
            state.hasReached(DemoStage.RUNNING) -> StatusLine("已获得用户确认", Color(0xFF22A06B))
            else -> StatusLine("未确认，任务已停止", Color(0xFFCF4B3E))
        }
    }
}

@Composable
private fun ExecutionCard(
    state: DemoState,
    steps: List<String>,
) {
    val totalSteps = steps.size.coerceAtLeast(1)
    AgentCard(title = "执行进度") {
        LinearProgressIndicator(
            progress = { state.completedSteps.toFloat() / totalSteps },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        steps.forEachIndexed { index, step ->
            val isCompleted = index < state.completedSteps
            val isCurrent = state.stage == DemoStage.RUNNING && index == state.completedSteps
            StatusLine(
                text = when {
                    isCompleted -> "已完成：$step"
                    isCurrent -> "正在执行：$step"
                    else -> "待执行：$step"
                },
                color = when {
                    isCompleted -> Color(0xFF22A06B)
                    isCurrent -> BrandBlue
                    else -> Color(0xFF8993A4)
                },
            )
            if (index != steps.lastIndex) Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ResultCard(
    scenario: DemoScenario,
    onReset: () -> Unit,
) {
    AgentCard(title = scenario.resultTitle, accent = Color(0xFF22A06B)) {
        Text(scenario.resultSummary)
        Spacer(modifier = Modifier.height(12.dp))
        StatusLine("未下单、未支付、未改变任何真实 App 数据", Color(0xFF8A4B00))
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onReset) {
            Text("开始新任务")
        }
    }
}

@Composable
private fun StoppedCard(onReset: () -> Unit) {
    AgentCard(title = "任务已停止", accent = Color(0xFFCF4B3E)) {
        Text("已取消后续演示步骤，不会继续更新执行进度。")
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onReset) {
            Text("返回首页")
        }
    }
}

@Composable
private fun AgentCard(
    title: String,
    accent: Color = BrandBlue,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE3E7EF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(accent, CircleShape),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                )
                DemoModeBadge()
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun StatusLine(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, CircleShape),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
