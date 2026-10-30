package com.parallelagent.demo

enum class TaskStage {
    IDLE,
    UNDERSTANDING,
    CLARIFYING,
    WAITING_CONFIRMATION,
    RUNNING,
    RESULT,
    FAILED,
    STOPPED,
    ;

    val canStop: Boolean
        get() = this == UNDERSTANDING || this == CLARIFYING || this == WAITING_CONFIRMATION
}

data class TaskState(
    val stage: TaskStage = TaskStage.IDLE,
    val request: String = "",
    val lastUserMessage: String = "",
    val task: TaskSpec? = null,
    val message: String = "",
    val completedSteps: Int = 0,
)

sealed interface TaskAction {
    data class Submit(val request: String) : TaskAction
    data class Interpreted(val result: InterpretationResult) : TaskAction
    data class Confirm(val taskVersion: String) : TaskAction
    data class ExecutionSucceeded(val result: ExecutionResult) : TaskAction
    data class ExecutionFailed(val message: String) : TaskAction
    data object Stop : TaskAction
    data object Reset : TaskAction
}

fun reduceTaskState(state: TaskState, action: TaskAction): TaskState = when (action) {
    is TaskAction.Submit -> {
        val request = action.request.trim()
        when {
            request.isEmpty() -> state
            state.stage == TaskStage.CLARIFYING -> state.copy(
                stage = TaskStage.UNDERSTANDING,
                request = state.request + "\n用户补充：" + request,
                lastUserMessage = request,
                message = "",
            )
            state.stage == TaskStage.UNDERSTANDING ||
                state.stage == TaskStage.WAITING_CONFIRMATION ||
                state.stage == TaskStage.RUNNING -> state
            else -> TaskState(
                stage = TaskStage.UNDERSTANDING,
                request = request,
                lastUserMessage = request,
            )
        }
    }

    is TaskAction.Interpreted -> {
        if (state.stage != TaskStage.UNDERSTANDING) state
        else when (val result = action.result) {
            is InterpretationResult.Ready -> state.copy(
                stage = TaskStage.WAITING_CONFIRMATION,
                task = result.task,
                message = result.task.userVisibleSummary,
            )
            is InterpretationResult.NeedsClarification -> state.copy(
                stage = TaskStage.CLARIFYING,
                message = result.question,
            )
            is InterpretationResult.Unsupported -> state.copy(
                stage = TaskStage.FAILED,
                message = result.message,
            )
        }
    }

    is TaskAction.Confirm -> {
        if (state.stage == TaskStage.WAITING_CONFIRMATION &&
            state.task?.taskVersion == action.taskVersion
        ) state.copy(stage = TaskStage.RUNNING) else state
    }

    is TaskAction.ExecutionSucceeded -> {
        if (state.stage != TaskStage.RUNNING) state
        else state.copy(
            stage = TaskStage.RESULT,
            completedSteps = 3,
            message = action.result.message,
        )
    }

    is TaskAction.ExecutionFailed -> {
        if (state.stage != TaskStage.RUNNING) state
        else state.copy(stage = TaskStage.FAILED, message = action.message)
    }

    TaskAction.Stop -> if (state.stage.canStop) state.copy(stage = TaskStage.STOPPED) else state
    TaskAction.Reset -> TaskState()
}
