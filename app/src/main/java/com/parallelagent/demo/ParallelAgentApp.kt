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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

internal val BrandBlue = Color(0xFF335CFF)
internal val Canvas = Color(0xFFF7F8FC)
internal val DemoOrange = Color(0xFFFFF0DC)
internal val DemoOrangeText = Color(0xFF8A4B00)

private val DemoStateSaver = listSaver<DemoState, Any>(
    save = {
        listOf(
            it.stage.name,
            it.request,
            it.completedSteps,
            it.lastProgressStage.name,
        )
    },
    restore = {
        DemoState(
            stage = DemoStage.valueOf(it[0] as String),
            request = it[1] as String,
            completedSteps = it[2] as Int,
            lastProgressStage = DemoStage.valueOf(it[3] as String),
        )
    },
)

@Composable
fun ParallelAgentTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = BrandBlue,
            background = Canvas,
            surface = Color.White,
        ),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParallelAgentApp(
    scenario: DemoScenario = PlaceholderScenario,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var demoState by rememberSaveable(stateSaver = DemoStateSaver) {
        mutableStateOf(DemoState())
    }

    fun dispatch(action: DemoAction) {
        demoState = reduceDemoState(demoState, action)
    }

    LaunchedEffect(demoState.stage, demoState.completedSteps) {
        when (demoState.stage) {
            DemoStage.UNDERSTANDING -> {
                delay(900)
                dispatch(DemoAction.UnderstandingCompleted)
            }

            DemoStage.READY_TO_ACT -> {
                delay(700)
                dispatch(DemoAction.CapabilityPrepared)
            }

            DemoStage.RUNNING -> {
                delay(900)
                dispatch(
                    DemoAction.ExecutionStepCompleted(
                        totalSteps = scenario.executionSteps.size,
                    ),
                )
            }

            else -> Unit
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppHeader(
                stage = demoState.stage,
                onStop = { dispatch(DemoAction.Stop) },
            )
        },
        bottomBar = {
            InputBar(
                input = input,
                exampleRequest = scenario.exampleRequest,
                inputLocked = demoState.stage.canStop,
                onInputChange = { input = it },
                onSend = {
                    dispatch(DemoAction.Submit(input))
                    input = ""
                },
            )
        },
    ) { paddingValues ->
        ConversationContent(
            state = demoState,
            scenario = scenario,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            onFillExample = { input = scenario.exampleRequest },
            onConfirm = { dispatch(DemoAction.Confirm) },
            onStop = { dispatch(DemoAction.Stop) },
            onReset = { dispatch(DemoAction.Reset) },
        )
    }
}

@Composable
private fun AppHeader(
    stage: DemoStage,
    onStop: () -> Unit,
) {
    Surface(shadowElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(stage.statusColor(), CircleShape),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Parallel Agent",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stage.statusText(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (stage.canStop) {
                TextButton(onClick = onStop) {
                    Text("停止")
                }
            }
            DemoModeBadge()
        }
    }
}

@Composable
internal fun DemoModeBadge() {
    Surface(color = DemoOrange, shape = RoundedCornerShape(999.dp)) {
        Text(
            text = "演示模式",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = DemoOrangeText,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun InputBar(
    input: String,
    exampleRequest: String,
    inputLocked: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                enabled = !inputLocked,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(if (inputLocked) "任务进行中…" else exampleRequest)
                },
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = onSend,
                enabled = !inputLocked && input.isNotBlank(),
                modifier = Modifier.size(52.dp),
                contentPadding = PaddingValues(0.dp),
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

private fun DemoStage.statusText(): String = when (this) {
    DemoStage.IDLE -> "待命"
    DemoStage.UNDERSTANDING -> "正在理解任务"
    DemoStage.READY_TO_ACT -> "正在准备能力"
    DemoStage.WAITING_CONFIRMATION -> "等待你确认"
    DemoStage.RUNNING -> "正在执行"
    DemoStage.RESULT -> "已完成"
    DemoStage.STOPPED -> "已停止"
}

private fun DemoStage.statusColor(): Color = when (this) {
    DemoStage.STOPPED -> Color(0xFFCF4B3E)
    DemoStage.RESULT -> Color(0xFF22A06B)
    DemoStage.IDLE -> Color(0xFF7A869A)
    else -> BrandBlue
}
