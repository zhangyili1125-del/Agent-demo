package com.parallelagent.demo

enum class DemoStage(val progressOrder: Int) {
    IDLE(0),
    UNDERSTANDING(1),
    READY_TO_ACT(2),
    WAITING_CONFIRMATION(3),
    RUNNING(4),
    RESULT(5),
    STOPPED(-1),
    ;

    val canStop: Boolean
        get() = when (this) {
            UNDERSTANDING, READY_TO_ACT, WAITING_CONFIRMATION, RUNNING -> true
            IDLE, RESULT, STOPPED -> false
        }
}

data class DemoState(
    val stage: DemoStage = DemoStage.IDLE,
    val request: String = "",
    val completedSteps: Int = 0,
    val lastProgressStage: DemoStage = DemoStage.IDLE,
) {
    fun hasReached(target: DemoStage): Boolean =
        lastProgressStage.progressOrder >= target.progressOrder
}

sealed interface DemoAction {
    data class Submit(val request: String) : DemoAction
    data object UnderstandingCompleted : DemoAction
    data object CapabilityPrepared : DemoAction
    data object Confirm : DemoAction
    data class ExecutionStepCompleted(val totalSteps: Int) : DemoAction
    data object Stop : DemoAction
    data object Reset : DemoAction
}

fun reduceDemoState(
    state: DemoState,
    action: DemoAction,
): DemoState = when (action) {
    is DemoAction.Submit -> {
        val request = action.request.trim()
        if (request.isEmpty() || state.stage.canStop) {
            state
        } else {
            DemoState(
                stage = DemoStage.UNDERSTANDING,
                request = request,
                lastProgressStage = DemoStage.UNDERSTANDING,
            )
        }
    }

    DemoAction.UnderstandingCompleted -> state.moveFromTo(
        expected = DemoStage.UNDERSTANDING,
        next = DemoStage.READY_TO_ACT,
    )

    DemoAction.CapabilityPrepared -> state.moveFromTo(
        expected = DemoStage.READY_TO_ACT,
        next = DemoStage.WAITING_CONFIRMATION,
    )

    DemoAction.Confirm -> state.moveFromTo(
        expected = DemoStage.WAITING_CONFIRMATION,
        next = DemoStage.RUNNING,
    )

    is DemoAction.ExecutionStepCompleted -> {
        if (state.stage != DemoStage.RUNNING) {
            state
        } else {
            val totalSteps = action.totalSteps.coerceAtLeast(1)
            val completedSteps = (state.completedSteps + 1).coerceAtMost(totalSteps)
            if (completedSteps == totalSteps) {
                state.copy(
                    stage = DemoStage.RESULT,
                    completedSteps = completedSteps,
                    lastProgressStage = DemoStage.RESULT,
                )
            } else {
                state.copy(completedSteps = completedSteps)
            }
        }
    }

    DemoAction.Stop -> {
        if (state.stage.canStop) state.copy(stage = DemoStage.STOPPED) else state
    }

    DemoAction.Reset -> DemoState()
}

private fun DemoState.moveFromTo(
    expected: DemoStage,
    next: DemoStage,
): DemoState = if (stage == expected) {
    copy(stage = next, lastProgressStage = next)
} else {
    this
}
