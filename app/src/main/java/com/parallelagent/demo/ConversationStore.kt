package com.parallelagent.demo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class ConversationRecord(
    val id: String,
    val updatedAt: Long,
    val state: TaskState,
) {
    val title: String
        get() = state.task?.noteTitle
            ?.takeIf(String::isNotBlank)
            ?: state.request.substringBefore("\n").trim().takeIf(String::isNotBlank)?.take(24)
            ?: "新对话"

    val statusLabel: String
        get() = when (state.stage) {
            TaskStage.IDLE -> "等待开始"
            TaskStage.UNDERSTANDING -> "正在整理"
            TaskStage.CLARIFYING -> "等待补充"
            TaskStage.WAITING_CONFIRMATION -> "等待确认"
            TaskStage.RUNNING -> "正在处理"
            TaskStage.RESULT -> "已完成"
            TaskStage.FAILED -> "未完成"
            TaskStage.STOPPED -> "已取消"
        }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("updatedAt", updatedAt)
        .put("state", state.toHistoryJson())

    companion object {
        fun create(now: Long = System.currentTimeMillis()): ConversationRecord = ConversationRecord(
            id = UUID.randomUUID().toString(),
            updatedAt = now,
            state = TaskState(),
        )

        fun fromJson(json: JSONObject): ConversationRecord = ConversationRecord(
            id = json.getString("id"),
            updatedAt = json.getLong("updatedAt"),
            state = taskStateFromHistoryJson(json.getJSONObject("state")),
        )
    }
}

internal class ConversationStore(context: Context) {
    private val preferences = context.getSharedPreferences("conversation_history", Context.MODE_PRIVATE)

    fun load(): List<ConversationRecord> {
        val saved = preferences.getString(KEY_CONVERSATIONS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(saved)
            (0 until array.length())
                .map { ConversationRecord.fromJson(array.getJSONObject(it)) }
                .sortedByDescending(ConversationRecord::updatedAt)
        }.getOrDefault(emptyList())
    }

    fun save(conversations: List<ConversationRecord>) {
        val array = JSONArray()
        conversations.sortedByDescending(ConversationRecord::updatedAt).forEach {
            array.put(it.toJson())
        }
        preferences.edit().putString(KEY_CONVERSATIONS, array.toString()).apply()
    }

    private companion object {
        const val KEY_CONVERSATIONS = "conversations"
    }
}

private fun TaskState.toHistoryJson(): JSONObject = JSONObject()
    .put("stage", stage.name)
    .put("request", request)
    .put("lastUserMessage", lastUserMessage)
    .put("message", message)
    .put("completedSteps", completedSteps)
    .apply {
        task?.let { put("task", it.toJson()) }
    }

private fun taskStateFromHistoryJson(json: JSONObject): TaskState {
    val savedStage = TaskStage.valueOf(json.getString("stage"))
    return TaskState(
        stage = if (savedStage == TaskStage.RUNNING) TaskStage.FAILED else savedStage,
        request = json.optString("request"),
        lastUserMessage = json.optString("lastUserMessage"),
        task = json.optJSONObject("task")?.let(TaskSpec::fromJson),
        message = if (savedStage == TaskStage.RUNNING) {
            "上次执行在应用关闭时中断，请先在 Standard Notes 中确认是否已经创建。"
        } else {
            json.optString("message")
        },
        completedSteps = json.optInt("completedSteps"),
    )
}
