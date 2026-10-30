package com.parallelagent.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskFlowTest {
    @Test
    fun taskPlan_roundTripsThroughJsonWithoutLosingSteps() {
        val task = createTask()

        val restored = TaskSpec.fromJson(task.toJson())

        assertEquals(task, restored)
        assertEquals("旅行清单", restored.noteTitle)
        assertEquals("护照和充电器", restored.noteBody)
        assertEquals(3, restored.steps.size)
    }

    @Test
    fun confirmation_isBoundToExactTaskVersion() {
        val task = createTask()
        var state = reduceTaskState(TaskState(), TaskAction.Submit("帮我记一下旅行用品"))
        state = reduceTaskState(state, TaskAction.Interpreted(InterpretationResult.Ready(task)))

        val wrongVersion = reduceTaskState(state, TaskAction.Confirm("old-version"))
        assertEquals(TaskStage.WAITING_CONFIRMATION, wrongVersion.stage)

        val confirmed = reduceTaskState(state, TaskAction.Confirm(task.taskVersion))
        assertEquals(TaskStage.RUNNING, confirmed.stage)
    }

    @Test
    fun clarificationReply_isCombinedWithOriginalRequest() {
        var state = reduceTaskState(TaskState(), TaskAction.Submit("帮我记一下"))
        state = reduceTaskState(
            state,
            TaskAction.Interpreted(InterpretationResult.NeedsClarification("记录什么内容？")),
        )

        val continued = reduceTaskState(state, TaskAction.Submit("明天上午九点开会"))

        assertEquals(TaskStage.UNDERSTANDING, continued.stage)
        assertEquals("明天上午九点开会", continued.lastUserMessage)
        assertTrue(continued.request.contains("帮我记一下"))
        assertTrue(continued.request.contains("用户补充：明天上午九点开会"))
    }

    @Test
    fun failedExecution_neverBecomesSuccess() {
        val running = TaskState(stage = TaskStage.RUNNING, request = "test")

        val failed = reduceTaskState(running, TaskAction.ExecutionFailed("worker offline"))

        assertEquals(TaskStage.FAILED, failed.stage)
        assertEquals("worker offline", failed.message)
        assertFalse(failed.stage == TaskStage.RESULT)
    }

    @Test
    fun lateInterpretationAfterStop_isIgnored() {
        val understanding = TaskState(stage = TaskStage.UNDERSTANDING, request = "test")
        val stopped = reduceTaskState(understanding, TaskAction.Stop)
        val late = reduceTaskState(
            stopped,
            TaskAction.Interpreted(InterpretationResult.Unsupported("late")),
        )

        assertEquals(TaskStage.STOPPED, stopped.stage)
        assertSame(stopped, late)
    }

    private fun createTask(): TaskSpec = TaskSpec(
        schemaVersion = 2,
        taskVersion = "task-v1",
        intent = TaskIntent.CREATE_NOTE,
        targetApp = "Standard Notes",
        userGoal = "记录旅行物品",
        riskLevel = RiskLevel.MODIFIES_ACCOUNT,
        confirmationRequired = true,
        forbiddenActions = listOf("DELETE", "SHARE", "CHANGE_ACCOUNT_SETTINGS"),
        userVisibleSummary = "创建旅行清单",
        steps = listOf(
            step(1, StepAction.LAUNCH_APP),
            step(
                2,
                StepAction.CREATE_NOTE,
                listOf(
                    TaskParameter("NOTE_TITLE", "旅行清单"),
                    TaskParameter("NOTE_BODY", "护照和充电器"),
                ),
            ),
            step(3, StepAction.VERIFY_NOTE),
        ),
    )

    private fun step(
        order: Int,
        action: StepAction,
        parameters: List<TaskParameter> = emptyList(),
    ) = TaskStep(
        stepId = "step-$order",
        order = order,
        action = action,
        capability = "TEST",
        target = "Standard Notes",
        parameters = parameters,
        successCriteria = emptyList(),
        sideEffect = if (action == StepAction.CREATE_NOTE) "ACCOUNT_DATA_CREATE" else "NONE",
        reversible = true,
    )
}
