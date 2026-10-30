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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Green = Color(0xFF22A06B)
private val Red = Color(0xFFCF4B3E)

@Composable
internal fun ConversationContent(
    state: TaskState,
    modifier: Modifier = Modifier,
    onFillExample: () -> Unit,
    onConfirm: (String) -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
) {
    if (state.stage == TaskStage.IDLE) {
        Box(modifier = modifier.padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(modifier = Modifier.size(64.dp), color = BrandBlue, shape = RoundedCornerShape(20.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("P", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("描述你的手机任务", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(
                    "大模型会理解自然语言并生成分步计划。当前已开放的真实能力是创建 Standard Notes 笔记。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(18.dp))
                TextButton(onClick = onFillExample) { Text("填入示例任务") }
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
                AgentCard("大模型正在理解") { Text("正在识别目标、补全参数并规划可核验的执行步骤…") }
            }
            TaskStage.CLARIFYING -> item {
                AgentCard("还需要一项信息") {
                    Text(state.message)
                    Spacer(Modifier.height(12.dp))
                    Text("请直接在下方输入框补充，我会结合上一条请求继续理解。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TaskStage.WAITING_CONFIRMATION, TaskStage.RUNNING, TaskStage.RESULT -> {
                val task = state.task
                if (task != null) {
                    item { TaskSpecCard(task) }
                    item {
                        ConfirmationCard(
                            state = state,
                            task = task,
                            onConfirm = onConfirm,
                            onStop = onStop,
                        )
                    }
                }
                if (state.stage == TaskStage.RUNNING && task != null) item { RunningCard(task) }
                if (state.stage == TaskStage.RESULT) item { ResultCard(state, onReset) }
            }
            TaskStage.FAILED -> item {
                AgentCard("任务没有完成", Red) {
                    Text(state.message)
                    Spacer(Modifier.height(10.dp))
                    Text("没有把失败显示成成功。请检查电脑模型服务、API key、USB 连接和虚拟手机。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onReset) { Text("返回首页") }
                }
            }
            TaskStage.STOPPED -> item {
                AgentCard("任务已停止", Red) {
                    Text("任务没有发送给虚拟手机，也没有创建笔记。")
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onReset) { Text("返回首页") }
                }
            }
            TaskStage.IDLE -> Unit
        }
    }
}

@Composable
private fun TaskSpecCard(task: TaskSpec) {
    AgentCard("大模型生成的任务计划") {
        DetailLine("用户目标", task.userGoal)
        DetailLine("目标 App", task.targetApp)
        DetailLine("识别出的操作", task.intent.displayName())
        DetailLine("标题", task.noteTitle)
        DetailLine("正文", task.noteBody)
        Text("执行步骤", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
        task.steps.sortedBy(TaskStep::order).forEach { step ->
            StatusLine("${step.order}. ${step.action.displayName()}", BrandBlue)
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(4.dp))
        DetailLine("安全限制", "不删除、不分享、不修改账号")
    }
}

@Composable
private fun ConfirmationCard(
    state: TaskState,
    task: TaskSpec,
    onConfirm: (String) -> Unit,
    onStop: () -> Unit,
) {
    AgentCard("需要你确认") {
        Text("将在 Standard Notes 测试账号中新建一条真实笔记。是否按上面的内容创建？")
        Spacer(Modifier.height(12.dp))
        when (state.stage) {
            TaskStage.WAITING_CONFIRMATION -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onConfirm(task.taskVersion) }) { Text("确认并执行") }
                OutlinedButton(onClick = onStop) { Text("停止任务") }
            }
            TaskStage.RUNNING, TaskStage.RESULT -> StatusLine("已确认这一个版本的任务", Green)
            else -> Unit
        }
    }
}

@Composable
private fun RunningCard(task: TaskSpec) {
    AgentCard("执行进度") {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        StatusLine("独立虚拟手机已收到任务", Green)
        task.steps.sortedBy(TaskStep::order).forEach { step ->
            Spacer(Modifier.height(8.dp))
            StatusLine("正在执行：${step.action.displayName()}", BrandBlue)
        }
        Spacer(Modifier.height(12.dp))
        Text("任务发送后不提供中途停止，避免界面停在不确定状态。", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ResultCard(state: TaskState, onReset: () -> Unit) {
    AgentCard("真实任务已完成", Green) {
        Text(state.message)
        Spacer(Modifier.height(10.dp))
        StatusLine("执行端已重新读取标题和正文并确认一致", Green)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onReset) { Text("开始新任务") }
    }
}

private fun TaskIntent.displayName(): String = when (this) {
    TaskIntent.CREATE_NOTE -> "创建笔记"
}

private fun StepAction.displayName(): String = when (this) {
    StepAction.LAUNCH_APP -> "打开目标 App"
    StepAction.CREATE_NOTE -> "创建笔记并填写内容"
    StepAction.VERIFY_NOTE -> "重新读取并核对结果"
}

@Composable
private fun DetailLine(label: String, value: String) {
    Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
    Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun UserMessage(request: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.86f),
            color = BrandBlue,
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE3E7EF)),
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

@Composable
private fun StatusLine(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}
