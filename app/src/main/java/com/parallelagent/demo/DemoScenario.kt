package com.parallelagent.demo

data class DemoScenario(
    val exampleRequest: String,
    val welcomeTitle: String,
    val welcomeMessage: String,
)

val PlaceholderScenario = DemoScenario(
    exampleRequest = "帮我完成一个需要多步操作的手机任务",
    welcomeTitle = "你想让手机帮你做什么？",
    welcomeMessage = "用一句自然语言描述任务。Agent 会先说明它的理解和准备调用的能力，关键操作仍由你确认。",
)
