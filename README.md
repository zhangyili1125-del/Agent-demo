# Parallel Agent

`Parallel Agent` 是开发阶段临时代号。这是候选人自己的独立手机 Agent 项目，与出题公司的产品没有品牌、代码或产品归属关系。

当前版本已经包含第一条可真实执行的纵向切片：用户在 Redmi 的 Parallel Agent 用自然语言描述需求，电脑端大模型把需求整理成结构化任务计划；用户确认后，执行服务会在独立 Android 模拟器中操作 Standard Notes，重新读取标题和正文进行核对，再把真实结果返回手机。它仍不是能够任意操作第三方 App 的通用 Agent。

## 当前已实现

- Kotlin + Jetpack Compose + Material 3 单页应用。
- 中文自然语言输入和对话式首页。
- 通过 DeepSeek Responses API 理解自由表达的中文或英文需求，返回受 JSON Schema 约束的结构化结果。
- 信息不足时追问，不支持的任务明确拒绝。
- 模型输出先经过确定性代码编译，生成包含版本、意图、目标 App、风险等级、禁止动作、步骤、参数和成功判据的任务单。
- 当前任务计划固定拆成“打开 App → 创建笔记 → 重新读取并核验”三步，为后续增加更多步骤和能力模块保留统一结构。
- 创建笔记固定要求用户确认；确认与任务版本绑定，修改后的旧任务不能沿用确认。
- 通过 USB 端口转发连接只监听电脑本机的执行服务，不向局域网开放端口。
- 独立 Android 模拟器真实创建 Standard Notes 笔记，并重新读取标题和正文进行核验。
- 中文标题和正文可通过只安装在模拟器上的输入辅助组件写入。
- 失败时显示真实错误，不把未完成任务显示为成功。

## 当前仍受限制的部分

- 真实大模型调用依赖电脑端本地配置的 `DEEPSEEK_API_KEY`；密钥不会写进 APK，也不会发送给手机。
- 只允许创建 Standard Notes 笔记；删除、分享、账号修改和其他 App 操作全部禁止。
- 电脑端服务和 Android 模拟器需要由开发者启动，尚未打包成普通用户的一键安装产品。
- 任务发给执行端以后不提供中途停止，避免 GUI 停在不可确定状态；确认前仍可停止。

## 还没有实现

- 扩展到创建笔记之外的更多能力和 App。
- 执行端自动启动、断线恢复和任务历史。
- 基于截图的大模型页面决策；当前第一条场景使用确定性的控件识别和核验。
- 更多 App 和更多动作。

## 演示流程

```text
用户输入创建笔记需求
  → DeepSeek 模型提取意图和参数
  → 确定性安全代码生成三步任务计划
  → 用户确认该任务版本
  → 手机通过 USB 转发把任务发给电脑
  → 电脑操作独立模拟器中的 Standard Notes
  → 执行端重新读取标题和正文
  → 手机显示真实成功或失败结果
```

任务发送前可以点击“停止任务”。一旦发送到执行端，本轮不提供中途停止。

## 构建与安装

需要 JDK 17 或更高版本、包含 Android API 36 的 Android SDK，以及 DeepSeek API 密钥。第一次运行时先在项目根目录创建只保存在本机的 `.env`：

```bash
cp .env.example .env
chmod 600 .env
```

然后用文本编辑器把 `.env` 中的占位符替换成自己的 API 密钥。不要把密钥放进聊天、APK 或 Git。

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r inputhelper/build/outputs/apk/debug/inputhelper-debug.apk
adb -s <Redmi序列号> reverse tcp:8787 tcp:8787
./worker/run_worker.sh
```

修改提示词或模型后，可以运行 24 条自然语言评测输入：

```bash
python3 worker/run_evals.py
```

Debug APK 生成在：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 已验证环境

- Redmi Note 12 Turbo，Android 13（API 33）。
- Android 14 AOSP ARM64 模拟器（API 34），用于独立执行 Standard Notes 任务。
- 模型结果编译与安全边界的后端测试、Android 状态机单元测试、Lint 和 Debug APK 构建已通过。
- 真机已验证安装、冷启动、停止操作和真实任务链路：Redmi 下发任务后，模拟器创建并核对笔记，随后同一笔记成功同步回 Redmi 的 Standard Notes。
- DeepSeek `deepseek-flash` 的 24 条自然语言评测全部通过；Redmi 上“自然语言输入 → 模型任务计划 → 用户确认 → 虚拟手机创建并核验笔记”的完整链路已通过。
- 已验证模拟器工作时，Redmi 的前台设置搜索框、文字、焦点和键盘保持不变。

## 项目结构

```text
app/                                  Redmi 上的任务入口
├─ src/main/java/.../TaskSpec.kt          通用多步骤任务单
├─ src/main/java/.../TaskApiClient.kt     模型理解与执行端连接
├─ src/main/java/.../TaskStateMachine.kt  真实任务状态机
└─ src/test/                              解析和安全边界测试

inputhelper/                          仅安装到独立模拟器的中文粘贴组件
worker/model_interpreter.py           DeepSeek 调用与确定性安全编译
worker/agent_worker.py                只监听电脑本机的理解/执行服务
worker/evals/                         24 条自然语言评测输入

artifacts/                            本地 APK、截图和演示材料，不提交 Git
```

## 下一步

当前模型评测和 Redmi 真实端到端链路已经通过。下一步优先完善失败恢复和演示启动流程，再注册第二种能力；暂不直接扩大到高风险购物、支付或消息发送。
