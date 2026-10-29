package com.parallelagent.demo

data class DemoScenario(
    val exampleRequest: String,
    val welcomeTitle: String,
    val welcomeMessage: String,
    val intentSummary: String,
    val capabilityTitle: String,
    val capabilityDescription: String,
    val confirmationQuestion: String,
    val executionSteps: List<String>,
    val resultTitle: String,
    val resultSummary: String,
)

val PlaceholderScenario = DemoScenario(
    exampleRequest = "帮我完成一个需要多步操作的手机任务",
    welcomeTitle = "你想让手机帮你做什么？",
    welcomeMessage = "用一句自然语言描述任务。Agent 会先说明它的理解和准备调用的能力，关键操作仍由你确认。",
    intentSummary = "我理解为：你希望我代你完成一个多步手机任务，并在关键操作前征求你的确认。",
    capabilityTitle = "手机任务执行能力",
    capabilityDescription = "本次将使用本地演示执行器展示调用 App、分步执行和结果核验的产品流程，不会真实操作第三方 App。",
    confirmationQuestion = "是否允许 Agent 启动演示执行流程？",
    executionSteps = listOf(
        "检查任务边界",
        "演示调用手机能力",
        "核对并整理结果",
    ),
    resultTitle = "演示任务已完成",
    resultSummary = "已跑通需求理解、能力说明、用户确认、执行进度和结果返回。这是本地演示结果，没有真实操作第三方 App。",
)
