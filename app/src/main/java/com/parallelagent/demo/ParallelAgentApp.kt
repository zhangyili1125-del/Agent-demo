package com.parallelagent.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

internal val BrandBlue = Color(0xFF335CFF)
internal val Canvas = Color(0xFFF8F9FD)

private val TaskStateSaver = listSaver<TaskState, Any>(
    save = { state ->
        listOf(
            state.stage.name,
            state.request,
            state.lastUserMessage,
            state.message,
            state.completedSteps,
            state.task?.toJson()?.toString().orEmpty(),
        )
    },
    restore = { values ->
        val taskJson = values[5] as String
        TaskState(
            stage = TaskStage.valueOf(values[0] as String),
            request = values[1] as String,
            lastUserMessage = values[2] as String,
            message = values[3] as String,
            completedSteps = values[4] as Int,
            task = taskJson.takeIf(String::isNotEmpty)?.let {
                TaskSpec.fromJson(JSONObject(it))
            },
        )
    },
)

@Composable
fun ParallelAgentTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = BrandBlue,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE8ECFF),
            onPrimaryContainer = Color(0xFF142B8C),
            background = Canvas,
            surface = Color.White,
            surfaceVariant = Color(0xFFF0F2F8),
            outlineVariant = Color(0xFFDDE2EC),
        ),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParallelAgentApp() {
    val apiClient = remember { TaskApiClient() }
    val interpreter = remember(apiClient) { RemoteTaskInterpreter(apiClient) }
    var input by rememberSaveable { mutableStateOf("") }
    var state by rememberSaveable(stateSaver = TaskStateSaver) {
        mutableStateOf(TaskState())
    }

    fun dispatch(action: TaskAction) {
        state = reduceTaskState(state, action)
    }

    LaunchedEffect(state.stage, state.request) {
        if (state.stage == TaskStage.UNDERSTANDING) {
            dispatch(TaskAction.Interpreted(interpreter.interpret(state.request)))
        }
    }

    LaunchedEffect(state.stage, state.task?.taskVersion) {
        val task = state.task
        if (state.stage == TaskStage.RUNNING && task != null) {
            apiClient.execute(task).fold(
                onSuccess = { dispatch(TaskAction.ExecutionSucceeded(it)) },
                onFailure = {
                    dispatch(
                        TaskAction.ExecutionFailed(
                            it.message ?: "暂时无法连接任务服务，请稍后重试。",
                        ),
                    )
                },
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppHeader(stage = state.stage, onStop = { dispatch(TaskAction.Stop) })
        },
        bottomBar = {
            InputBar(
                input = input,
                inputLocked = state.stage == TaskStage.UNDERSTANDING ||
                    state.stage == TaskStage.WAITING_CONFIRMATION ||
                    state.stage == TaskStage.RUNNING,
                placeholderText = when (state.stage) {
                    TaskStage.UNDERSTANDING -> "正在整理你的需求…"
                    TaskStage.WAITING_CONFIRMATION -> "请先确认上方计划"
                    TaskStage.RUNNING -> "正在为你处理…"
                    else -> "说说你想完成什么"
                },
                onInputChange = { input = it },
                onSend = {
                    dispatch(TaskAction.Submit(input))
                    input = ""
                },
            )
        },
    ) { paddingValues ->
        ConversationContent(
            state = state,
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            onFillExample = {
                input = "帮我记一下，周末要整理房间，还要买些水果"
            },
            onConfirm = { version -> dispatch(TaskAction.Confirm(version)) },
            onStop = { dispatch(TaskAction.Stop) },
            onReset = { dispatch(TaskAction.Reset) },
        )
    }
}

@Composable
private fun AppHeader(stage: TaskStage, onStop: () -> Unit) {
    Surface(shadowElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Parallel Agent", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(7.dp).background(stage.statusColor(), CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stage.statusText(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
            }
            if (stage.canStop) {
                TextButton(onClick = onStop) { Text("取消") }
            }
        }
    }
}

@Composable
private fun InputBar(
    input: String,
    inputLocked: Boolean,
    placeholderText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(shadowElevation = 8.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                enabled = !inputLocked,
                modifier = Modifier.weight(1f),
                placeholder = { Text(placeholderText) },
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = onSend,
                enabled = !inputLocked && input.isNotBlank(),
                modifier = Modifier.size(56.dp),
                contentPadding = PaddingValues(0.dp),
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    modifier = Modifier.size(30.dp),
                )
            }
        }
    }
}

private fun TaskStage.statusText(): String = when (this) {
    TaskStage.IDLE -> "随时可以开始"
    TaskStage.UNDERSTANDING -> "正在整理你的需求"
    TaskStage.CLARIFYING -> "等待你补充"
    TaskStage.WAITING_CONFIRMATION -> "计划已准备好"
    TaskStage.RUNNING -> "正在为你处理"
    TaskStage.RESULT -> "任务已完成"
    TaskStage.FAILED -> "遇到了一点问题"
    TaskStage.STOPPED -> "任务已取消"
}

private fun TaskStage.statusColor(): Color = when (this) {
    TaskStage.STOPPED, TaskStage.FAILED -> Color(0xFFCF4B3E)
    TaskStage.RESULT -> Color(0xFF22A06B)
    TaskStage.IDLE -> Color(0xFF7A869A)
    else -> BrandBlue
}
