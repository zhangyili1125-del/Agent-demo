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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val Green = Color(0xFF22A06B)
private val Red = Color(0xFFCF4B3E)

@Composable
internal fun ConversationContent(
    state: TaskState,
    modifier: Modifier = Modifier,
    onFillExample: () -> Unit,
    onConfirm: (String) -> Unit,
    onStop: () -> Unit,
    onNewConversation: () -> Unit,
) {
    if (state.stage == TaskStage.IDLE) {
        Box(modifier = modifier.padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier.size(72.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    "今天想让手机帮你做什么？",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "用一句话描述目标，我会先整理步骤，需要时再请你确认。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(16.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        "目前支持 · 创建笔记",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(Modifier.height(20.dp))
                OutlinedButton(onClick = onFillExample) { Text("试试：记下周末待办") }
                Spacer(Modifier.height(12.dp))
                Text(
                    "开始前，你可以查看并确认完整计划。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { UserMessage(state.lastUserMessage.ifBlank { state.request }) }

        when (state.stage) {
            TaskStage.UNDERSTANDING -> item {
                AgentCard("正在整理你的需求") {
                    Text("我会把目标和内容整理成一份清晰的计划。")
                }
            }
            TaskStage.CLARIFYING -> item {
                AgentCard("还需要一项信息") {
                    Text(state.message)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "请直接在下方补充，我会接着刚才的内容继续处理。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TaskStage.WAITING_CONFIRMATION, TaskStage.RUNNING, TaskStage.RESULT -> {
                val task = state.task
                if (task != null) item { TaskSpecCard(task) }
                if (state.stage == TaskStage.WAITING_CONFIRMATION && task != null) {
                    item { ConfirmationCard(task, onConfirm, onStop) }
                }
                if (state.stage == TaskStage.RUNNING) item { RunningCard() }
                if (state.stage == TaskStage.RESULT && task != null) item { ResultCard(task) }
            }
            TaskStage.FAILED -> item {
                AgentCard("这次没有完成", Red) {
                    Text(state.message)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "你可以返回首页后重试。如果任务已经开始，请先在 Standard Notes 中确认结果。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onNewConversation) { Text("新建对话") }
                }
            }
            TaskStage.STOPPED -> item {
                AgentCard("已取消", Red) {
                    Text("这项任务没有开始执行。")
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onNewConversation) { Text("新建对话") }
                }
            }
            TaskStage.IDLE -> Unit
        }
    }
}

@Composable
private fun TaskSpecCard(task: TaskSpec) {
    AgentCard("我准备这样做") {
        DetailLine("你的目标", task.userGoal)
        DetailLine("使用应用", task.targetApp)
        DetailLine("笔记标题", task.noteTitle)
        DetailLine("笔记内容", task.noteBody)
        Text("计划步骤", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        val orderedSteps = task.steps.sortedBy(TaskStep::order)
        orderedSteps.forEachIndexed { index, step ->
            PlanStep(step.order, step.action.displayName())
            if (index < orderedSteps.lastIndex) Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(14.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                "这次只会新建笔记，不会删除、分享或更改账号。",
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ConfirmationCard(
    task: TaskSpec,
    onConfirm: (String) -> Unit,
    onStop: () -> Unit,
) {
    AgentCard("确认后开始") {
        Text("将在 Standard Notes 新建这条笔记。请确认内容是否正确。")
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = { onConfirm(task.taskVersion) },
                modifier = Modifier.weight(1f),
            ) { Text("确认并开始") }
            OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) { Text("暂不执行") }
        }
    }
}

@Composable
private fun RunningCard() {
    AgentCard("正在处理") {
        Text("我正在独立工作空间中创建笔记，你可以继续使用这台手机。")
        Spacer(Modifier.height(14.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Text(
            "完成后会在这里显示结果。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ResultCard(task: TaskSpec) {
    AgentCard("笔记已创建", Green) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Green,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("《${task.noteTitle}》已保存到 Standard Notes。")
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "标题和内容已经核对。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun StepAction.displayName(): String = when (this) {
    StepAction.LAUNCH_APP -> "打开 Standard Notes"
    StepAction.CREATE_NOTE -> "创建笔记并填写内容"
    StepAction.VERIFY_NOTE -> "检查标题和内容"
}

@Composable
private fun PlanStep(order: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    order.toString(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
    Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun UserMessage(request: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
        ) {
            Text(request, modifier = Modifier.padding(16.dp), color = Color.White, style = MaterialTheme.typography.bodyLarge)
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(accent, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
