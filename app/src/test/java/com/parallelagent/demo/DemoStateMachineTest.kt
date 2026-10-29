package com.parallelagent.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoStateMachineTest {
    @Test
    fun fullFlow_reachesResultAfterEveryExecutionStep() {
        var state = reduceDemoState(DemoState(), DemoAction.Submit("  测试任务  "))
        assertEquals(DemoStage.UNDERSTANDING, state.stage)
        assertEquals("测试任务", state.request)

        state = reduceDemoState(state, DemoAction.UnderstandingCompleted)
        assertEquals(DemoStage.READY_TO_ACT, state.stage)

        state = reduceDemoState(state, DemoAction.CapabilityPrepared)
        assertEquals(DemoStage.WAITING_CONFIRMATION, state.stage)

        state = reduceDemoState(state, DemoAction.Confirm)
        assertEquals(DemoStage.RUNNING, state.stage)

        repeat(2) {
            state = reduceDemoState(state, DemoAction.ExecutionStepCompleted(totalSteps = 3))
            assertEquals(DemoStage.RUNNING, state.stage)
        }
        state = reduceDemoState(state, DemoAction.ExecutionStepCompleted(totalSteps = 3))

        assertEquals(DemoStage.RESULT, state.stage)
        assertEquals(3, state.completedSteps)
        assertTrue(state.hasReached(DemoStage.RESULT))
    }

    @Test
    fun stop_preservesReachedStageAndIgnoresLaterCompletion() {
        val waiting = DemoState(
            stage = DemoStage.WAITING_CONFIRMATION,
            request = "测试任务",
            lastProgressStage = DemoStage.WAITING_CONFIRMATION,
        )

        val stopped = reduceDemoState(waiting, DemoAction.Stop)
        val afterLateConfirm = reduceDemoState(stopped, DemoAction.Confirm)

        assertEquals(DemoStage.STOPPED, stopped.stage)
        assertEquals(DemoStage.WAITING_CONFIRMATION, stopped.lastProgressStage)
        assertTrue(stopped.hasReached(DemoStage.READY_TO_ACT))
        assertFalse(stopped.hasReached(DemoStage.RUNNING))
        assertSame(stopped, afterLateConfirm)
    }

    @Test
    fun stopWhileRunning_ignoresLateExecutionStep() {
        val running = DemoState(
            stage = DemoStage.RUNNING,
            request = "测试任务",
            completedSteps = 1,
            lastProgressStage = DemoStage.RUNNING,
        )

        val stopped = reduceDemoState(running, DemoAction.Stop)
        val afterLateStep = reduceDemoState(
            stopped,
            DemoAction.ExecutionStepCompleted(totalSteps = 3),
        )

        assertEquals(DemoStage.STOPPED, stopped.stage)
        assertEquals(1, stopped.completedSteps)
        assertSame(stopped, afterLateStep)
    }

    @Test
    fun blankSubmit_isIgnored() {
        val initial = DemoState()

        val result = reduceDemoState(initial, DemoAction.Submit("   "))

        assertSame(initial, result)
    }

    @Test
    fun submitAfterResult_startsCleanTask() {
        val finished = DemoState(
            stage = DemoStage.RESULT,
            request = "旧任务",
            completedSteps = 3,
            lastProgressStage = DemoStage.RESULT,
        )

        val restarted = reduceDemoState(finished, DemoAction.Submit("新任务"))

        assertEquals(DemoStage.UNDERSTANDING, restarted.stage)
        assertEquals("新任务", restarted.request)
        assertEquals(0, restarted.completedSteps)
    }
}
