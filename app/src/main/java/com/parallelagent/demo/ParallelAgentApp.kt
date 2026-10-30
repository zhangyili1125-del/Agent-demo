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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

internal val BrandBlue = Color(0xFF335CFF)
internal val Canvas = Color(0xFFF8F9FD)

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
    val context = LocalContext.current
    val store = remember(context) { ConversationStore(context) }
    val initialConversations = remember {
        store.load().ifEmpty {
            listOf(ConversationRecord.create()).also(store::save)
        }
    }
    val apiClient = remember { TaskApiClient() }
    val interpreter = remember(apiClient) { RemoteTaskInterpreter(apiClient) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var conversations by remember { mutableStateOf(initialConversations) }
    var activeConversationId by rememberSaveable { mutableStateOf(initialConversations.first().id) }
    var state by remember {
        mutableStateOf(
            initialConversations.firstOrNull { it.id == activeConversationId }?.state
                ?: initialConversations.first().state,
        )
    }
    var previousTurns by remember {
        mutableStateOf(
            initialConversations.firstOrNull { it.id == activeConversationId }?.previousTurns.orEmpty(),
        )
    }
    var input by rememberSaveable { mutableStateOf("") }

    fun setConversationState(
        nextState: TaskState,
        nextPreviousTurns: List<TaskState> = previousTurns,
    ) {
        val now = System.currentTimeMillis()
        val updated = conversations.map { conversation ->
            if (conversation.id == activeConversationId) {
                conversation.copy(
                    updatedAt = now,
                    previousTurns = nextPreviousTurns,
                    state = nextState,
                )
            } else {
                conversation
            }
        }.sortedByDescending(ConversationRecord::updatedAt)
        state = nextState
        previousTurns = nextPreviousTurns
        conversations = updated
        store.save(updated)
    }

    fun dispatch(action: TaskAction) {
        setConversationState(reduceTaskState(state, action))
    }

    fun openConversation(conversation: ConversationRecord) {
        if (state.stage.isBusy) return
        activeConversationId = conversation.id
        state = conversation.state
        previousTurns = conversation.previousTurns
        input = ""
        scope.launch { drawerState.close() }
    }

    fun createConversation() {
        if (state.stage.isBusy) return
        val existingEmpty = conversations.firstOrNull {
            it.state.stage == TaskStage.IDLE && it.state.request.isBlank()
        }
        if (existingEmpty != null) {
            activeConversationId = existingEmpty.id
            state = existingEmpty.state
            previousTurns = existingEmpty.previousTurns
            input = ""
            scope.launch { drawerState.close() }
            return
        }
        val conversation = ConversationRecord.create()
        val updated = listOf(conversation) + conversations
        conversations = updated
        activeConversationId = conversation.id
        state = conversation.state
        previousTurns = conversation.previousTurns
        input = ""
        store.save(updated)
        scope.launch { drawerState.close() }
    }

    LaunchedEffect(activeConversationId, state.stage, state.request) {
        if (state.stage == TaskStage.UNDERSTANDING) {
            dispatch(TaskAction.Interpreted(interpreter.interpret(state.request)))
        }
    }

    LaunchedEffect(activeConversationId, state.stage, state.task?.taskVersion) {
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ConversationDrawer(
                conversations = conversations,
                activeConversationId = activeConversationId,
                switchingEnabled = !state.stage.isBusy,
                onNewConversation = ::createConversation,
                onConversationSelected = ::openConversation,
            )
        },
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                AppHeader(
                    stage = state.stage,
                    onOpenMenu = { scope.launch { drawerState.open() } },
                    onStop = { dispatch(TaskAction.Stop) },
                )
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
                        TaskStage.RESULT, TaskStage.FAILED, TaskStage.STOPPED -> "继续说说你想完成什么"
                        else -> "说说你想完成什么"
                    },
                    onInputChange = { input = it },
                    onSend = {
                        if (state.stage.isTerminal) {
                            val nextState = reduceTaskState(TaskState(), TaskAction.Submit(input))
                            setConversationState(nextState, previousTurns + state)
                        } else {
                            dispatch(TaskAction.Submit(input))
                        }
                        input = ""
                    },
                )
            },
        ) { paddingValues ->
            ConversationContent(
                previousTurns = previousTurns,
                state = state,
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                onFillExample = {
                    input = "帮我记一下，周末要整理房间，还要买些水果"
                },
                onConfirm = { version -> dispatch(TaskAction.Confirm(version)) },
                onStop = { dispatch(TaskAction.Stop) },
                onNewConversation = ::createConversation,
            )
        }
    }
}

@Composable
private fun ConversationDrawer(
    conversations: List<ConversationRecord>,
    activeConversationId: String,
    switchingEnabled: Boolean,
    onNewConversation: () -> Unit,
    onConversationSelected: (ConversationRecord) -> Unit,
) {
    ModalDrawerSheet(modifier = Modifier.fillMaxWidth(0.86f)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Text(
                "对话",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            FilledTonalButton(
                onClick = onNewConversation,
                enabled = switchingEnabled,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("新建对话")
            }
            if (!switchingEnabled) {
                Text(
                    "当前任务完成后可以切换对话。",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Spacer(Modifier.size(12.dp))
            }
            HorizontalDivider()
            Text(
                "历史对话",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(conversations, key = ConversationRecord::id) { conversation ->
                    NavigationDrawerItem(
                        label = {
                            Column {
                                Text(
                                    conversation.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    conversation.statusLabel,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        },
                        selected = conversation.id == activeConversationId,
                        onClick = {
                            if (switchingEnabled || conversation.id == activeConversationId) {
                                onConversationSelected(conversation)
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppHeader(
    stage: TaskStage,
    onOpenMenu: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(shadowElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenMenu, modifier = Modifier.size(48.dp)) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "打开对话菜单",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
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
            modifier = Modifier.fillMaxWidth().navigationBarsPadding()
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

private val TaskStage.isBusy: Boolean
    get() = this == TaskStage.UNDERSTANDING || this == TaskStage.RUNNING

private val TaskStage.isTerminal: Boolean
    get() = this == TaskStage.RESULT || this == TaskStage.FAILED || this == TaskStage.STOPPED

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
