package com.parallelagent.demo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ExecutionResult(
    val message: String,
    val verifiedTitle: String,
    val verifiedBody: String,
)

class TaskApiClient(
    private val baseUrl: String = "http://127.0.0.1:8787",
) {
    suspend fun interpret(request: String): Result<InterpretationResult> = withContext(Dispatchers.IO) {
        runCatching {
            val response = post(
                path = "/v1/interpret",
                body = JSONObject().put("request", request),
                readTimeoutMillis = 55_000,
            )
            when (response.getString("status")) {
                "READY" -> InterpretationResult.Ready(
                    TaskSpec.fromJson(response.getJSONObject("task")),
                )
                "NEEDS_CLARIFICATION" -> InterpretationResult.NeedsClarification(
                    response.getString("message"),
                )
                "UNSUPPORTED" -> InterpretationResult.Unsupported(response.getString("message"))
                else -> error("理解服务返回了未知状态")
            }
        }
    }

    suspend fun execute(task: TaskSpec): Result<ExecutionResult> = withContext(Dispatchers.IO) {
        runCatching {
            val request = task.toJson().put("confirmedTaskVersion", task.taskVersion)
            val response = post(
                path = "/v1/tasks",
                body = request,
                readTimeoutMillis = 45_000,
            )
            ExecutionResult(
                message = response.getString("message"),
                verifiedTitle = response.getString("verifiedTitle"),
                verifiedBody = response.getString("verifiedBody"),
            )
        }
    }

    private fun post(
        path: String,
        body: JSONObject,
        readTimeoutMillis: Int,
    ): JSONObject {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 5_000
            connection.readTimeout = readTimeoutMillis
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(Charsets.UTF_8))
            }
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: "{}"
            val response = JSONObject(responseText)
            if (responseCode !in 200..299 || response.optBoolean("success") == false && response.has("success")) {
                error(response.optString("message", "任务服务暂时遇到问题，请稍后重试"))
            }
            return response
        } finally {
            connection.disconnect()
        }
    }
}
