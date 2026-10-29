# Parallel Agent

`Parallel Agent` 是开发阶段临时代号。这是候选人自己的独立手机 Agent 项目，与出题公司的产品没有品牌、代码或产品归属关系。

当前版本是一个可安装的 Android 前端原型，用于验证“手机统一任务入口”的产品交互。它不是已经能自主操作第三方 App 的完整 Agent。

## 当前已实现

- Kotlin + Jetpack Compose + Material 3 单页应用。
- 中文自然语言输入和对话式首页。
- 本地状态机：理解任务、准备能力、等待确认、执行进度、返回结果和停止任务。
- 关键操作前的用户确认。
- 任务进行中停止，并忽略停止后到达的迟到事件。
- 流程卡片和结果卡均明确标注“演示模式”。

## 当前仍是演示的部分

- Agent 对意图的理解文案来自本地 `DemoScenario`，尚未调用大模型。
- 调用手机能力、执行步骤和结果是本地状态机演示。
- 没有登录或操作任何第三方 App。
- 没有下单、支付或改变真实 App 数据。

## 还没有实现

- 大模型调用和结构化意图识别。
- 电脑端任务服务。
- 真实第三方 App 的截图、决策和 GUI 操作。
- 不抢用户真机前台、焦点和键盘的独立 Android 执行环境。
- 账号登录、验证码、风控、失败恢复和真实结果核验。

## 演示流程

```text
用户输入需求
  → Agent 展示意图理解
  → 说明准备调用的能力
  → 用户确认
  → 展示分步执行进度
  → 返回明确标注的演示结果
```

运行中可以点击顶部“停止”，或在确认卡中选择“停止任务”。

## 构建与安装

需要 JDK 17 或更高版本，以及包含 Android API 36 的 Android SDK。

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug APK 生成在：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 已验证环境

- Redmi Note 12 Turbo，Android 13（API 33）。
- Android 14 AOSP ARM64 模拟器（API 34），仅用于前端 QA。
- 状态机单元测试、Android Lint 和 Debug APK 构建已通过。
- 真机已验证安装、冷启动、完整演示链路和停止操作。
- 当前前端切到后台后不会自行返回前台或重新请求键盘。这个结论只适用于当前前端，不代表未来的真实 App 执行器已经解决焦点隔离。

## 项目结构

```text
app/                                  Android 前端
├─ src/main/java/.../DemoStateMachine.kt  本地状态机
├─ src/main/java/.../DemoScenario.kt      可替换演示文案
├─ src/main/java/.../ConversationContent.kt
└─ src/test/                            状态机单元测试

artifacts/                            本地 APK、截图和演示材料，不提交 Git
```

## 下一步

先增加可替换的 `TaskInterpreter` 接口和电脑端服务，只把“理解任务”替换为真实大模型输出。手机执行和结果仍保持演示模式，不把模型输出冒充为真实 App 操作结果。
