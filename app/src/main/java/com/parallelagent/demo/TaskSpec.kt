package com.parallelagent.demo

import org.json.JSONArray
import org.json.JSONObject

enum class TaskIntent { CREATE_NOTE }
enum class RiskLevel { MODIFIES_ACCOUNT }
enum class StepAction { LAUNCH_APP, CREATE_NOTE, VERIFY_NOTE }

data class TaskParameter(val key: String, val value: String)

data class SuccessCriterion(
    val type: String,
    val field: String,
    val expectedValue: String,
)

data class TaskStep(
    val stepId: String,
    val order: Int,
    val action: StepAction,
    val capability: String,
    val target: String,
    val parameters: List<TaskParameter>,
    val successCriteria: List<SuccessCriterion>,
    val sideEffect: String,
    val reversible: Boolean,
) {
    fun parameter(key: String): String? = parameters.firstOrNull { it.key == key }?.value
}

data class TaskSpec(
    val schemaVersion: Int,
    val taskVersion: String,
    val intent: TaskIntent,
    val targetApp: String,
    val userGoal: String,
    val riskLevel: RiskLevel,
    val confirmationRequired: Boolean,
    val forbiddenActions: List<String>,
    val userVisibleSummary: String,
    val steps: List<TaskStep>,
) {
    val noteTitle: String
        get() = steps.firstOrNull { it.action == StepAction.CREATE_NOTE }
            ?.parameter("NOTE_TITLE").orEmpty()

    val noteBody: String
        get() = steps.firstOrNull { it.action == StepAction.CREATE_NOTE }
            ?.parameter("NOTE_BODY").orEmpty()

    fun toJson(): JSONObject = JSONObject()
        .put("schemaVersion", schemaVersion)
        .put("taskVersion", taskVersion)
        .put("intent", intent.name)
        .put("targetApp", targetApp)
        .put("userGoal", userGoal)
        .put("riskLevel", riskLevel.name)
        .put("confirmationRequired", confirmationRequired)
        .put("forbiddenActions", JSONArray(forbiddenActions))
        .put("userVisibleSummary", userVisibleSummary)
        .put("steps", JSONArray(steps.map(TaskStep::toJson)))

    companion object {
        fun fromJson(json: JSONObject): TaskSpec = TaskSpec(
            schemaVersion = json.getInt("schemaVersion"),
            taskVersion = json.getString("taskVersion"),
            intent = TaskIntent.valueOf(json.getString("intent")),
            targetApp = json.getString("targetApp"),
            userGoal = json.getString("userGoal"),
            riskLevel = RiskLevel.valueOf(json.getString("riskLevel")),
            confirmationRequired = json.getBoolean("confirmationRequired"),
            forbiddenActions = json.getJSONArray("forbiddenActions").toStringList(),
            userVisibleSummary = json.getString("userVisibleSummary"),
            steps = json.getJSONArray("steps").toObjectList(::taskStepFromJson),
        )
    }
}

private fun TaskStep.toJson(): JSONObject = JSONObject()
    .put("stepId", stepId)
    .put("order", order)
    .put("action", action.name)
    .put("capability", capability)
    .put("target", target)
    .put("parameters", JSONArray(parameters.map {
        JSONObject().put("key", it.key).put("value", it.value)
    }))
    .put("successCriteria", JSONArray(successCriteria.map {
        JSONObject()
            .put("type", it.type)
            .put("field", it.field)
            .put("expectedValue", it.expectedValue)
    }))
    .put("sideEffect", sideEffect)
    .put("reversible", reversible)

private fun taskStepFromJson(json: JSONObject): TaskStep = TaskStep(
    stepId = json.getString("stepId"),
    order = json.getInt("order"),
    action = StepAction.valueOf(json.getString("action")),
    capability = json.getString("capability"),
    target = json.getString("target"),
    parameters = json.getJSONArray("parameters").toObjectList {
        TaskParameter(it.getString("key"), it.getString("value"))
    },
    successCriteria = json.getJSONArray("successCriteria").toObjectList {
        SuccessCriterion(
            type = it.getString("type"),
            field = it.getString("field"),
            expectedValue = it.getString("expectedValue"),
        )
    },
    sideEffect = json.getString("sideEffect"),
    reversible = json.getBoolean("reversible"),
)

private fun JSONArray.toStringList(): List<String> = (0 until length()).map(::getString)

private fun <T> JSONArray.toObjectList(transform: (JSONObject) -> T): List<T> =
    (0 until length()).map { transform(getJSONObject(it)) }

sealed interface InterpretationResult {
    data class Ready(val task: TaskSpec) : InterpretationResult
    data class NeedsClarification(val question: String) : InterpretationResult
    data class Unsupported(val message: String) : InterpretationResult
}

interface TaskInterpreter {
    suspend fun interpret(request: String): InterpretationResult
}

class RemoteTaskInterpreter(
    private val apiClient: TaskApiClient,
) : TaskInterpreter {
    override suspend fun interpret(request: String): InterpretationResult =
        apiClient.interpret(request).getOrElse { error ->
            InterpretationResult.Unsupported(
                error.message ?: "无法连接电脑上的大模型理解服务。",
            )
        }
}
