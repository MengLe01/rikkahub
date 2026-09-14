# Repository Guidelines

## Project Overview

RikkaHub is a native Android LLM chat client that supports switching between different AI providers
for conversations.
Built with Jetpack Compose, Kotlin, and follows Material Design 3 principles.

## Build, Test, and Development Commands

```bash
./gradlew assembleDebug          # 构建 Debug APK
./gradlew test                   # 运行所有模块的 JVM 单元测试
./gradlew lint                   # 运行 Android Lint
```

## Module Structure

- **app**: Main application module with UI, ViewModels, and core logic
- **ai**: AI SDK abstraction layer for different providers (OpenAI, Google, Anthropic)
- **common**: Common utilities and extensions
- **document**: Document parsing module for handling PDF, DOCX, PPTX, and EPUB files
- **highlight**: Code syntax highlighting implementation
- **material3**: Material color utility extensions used by the app UI
- **search**: Search functionality SDK for multiple providers (Exa, Tavily, Zhipu, Bing, Brave, SearXNG, and others)
- **speech**: Speech module for TTS and ASR implementations
- **web**: Embedded web server module that provides Ktor server startup function and hosts static frontend build files (
  built from web-ui/ React project)
- **workspace**: Sandboxed per-workspace file system and shell execution environment exposed to the AI as tools.

## Concepts

- **Assistant**: An assistant configuration with system prompts, model parameters, and conversation isolation. Each
  assistant maintains its own settings including temperature, context size, custom headers, tools, memory options, regex
  transformations, and prompt injections (mode/lorebook). Assistants provide isolated chat environments with specific
  behaviors and capabilities. (app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt)

- **Conversation**: A persistent conversation thread between the user and an assistant. Each conversation maintains a
  list of MessageNodes in a tree structure to support message branching, along with metadata like title, creation time,
  update time, pin status, chat suggestions, optional conversation-level system prompt, and prompt injection bindings. (
  app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt)

- **UIMessage**: A platform-agnostic message abstraction that encapsulates chat messages with different types of content
  parts (text, images, documents, reasoning, tool calls/results, etc.). Each message has a role (USER, ASSISTANT,
  SYSTEM, TOOL), creation timestamp, model ID, token usage information, and optional annotations. UIMessages support
  streaming updates through chunk merging. (ai/src/main/java/me/rerere/ai/ui/Message.kt)

- **MessageNode**: A container holding one or more UIMessages to implement message branching functionality. Each node
  maintains a list of alternative messages and tracks which message is currently selected (selectIndex). This enables
  users to regenerate responses and switch between different conversation branches, creating a tree-like conversation
  structure. (app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt)

- **Message Transformer**: A pipeline mechanism for transforming messages before sending to AI providers (
  InputMessageTransformer) or after receiving responses (OutputMessageTransformer). Transformers can modify message
  content, add metadata, apply templates, handle special tags, convert formats, and perform OCR. Common transformers
  include:
  - TemplateTransformer: Apply Pebble templates to user messages with variables like time/date
  - ThinkTagTransformer: Extract `<think>` tags and convert to reasoning parts
  - RegexOutputTransformer: Apply regex replacements to assistant responses
  - DocumentAsPromptTransformer: Convert document attachments to text prompts
  - Base64ImageToLocalFileTransformer: Convert base64 images to local file references
  - OcrTransformer: Perform OCR on images to extract text

  Output transformers support `visualTransform()` for UI display during streaming and `onGenerationFinish()` for final
  processing after generation completes.
  (app/src/main/java/me/rerere/rikkahub/data/ai/transformers/Transformer.kt)

## Internationalization

- String resources are usually located in `app/src/main/res/values*/strings.xml`; feature modules such as `search`
  may also maintain their own `values*/strings.xml`
- Use `stringResource(R.string.key_name)` in Compose
- Page-specific strings should use page prefix (e.g., `setting_page_`)
- If the user does not explicitly request localization, prioritize implementing functionality without considering
  localization. (e.g `Text("Hello world")`)
- For `locale-tui` operations, use the `locale-tui-localization` skill.

以上为原项目的要求，以下为本项目的要求
本项目名为airhub，是基于原项目rikkahub进行二次开发的项目，有如下要求，如果和以上要求有冲突则以如下要求为准：
# AIRhub 协作开发偏好

本文档用于记录用户与 AI 在 AIRhub / RikkaHub 开发过程中的协作习惯。除非用户在当前任务中明确提出不同要求，后续工作应优先遵循本文档。

## 1. 先评估，再写代码

- 收到功能需求或 Bug 描述后，不要立即修改代码。
- AI 应先结合现有实现梳理：
  - 问题可能出现在哪些模块；
  - 需求是否会影响现有交互、动画或状态管理；
  - 推荐的实现方案及主要取舍；
  - 是否存在更简单、稳定或改动范围更小的方案。
- AI 与用户对目标和方案达成一致后，再开始写代码。
- 如果实际检查代码后发现原先判断不成立、修改范围明显扩大或可能引入额外行为，应先向用户说明并重新评估，不要擅自扩大实现范围。

## 2. 不在本机构建或测试

- **不要在当前 Windows 本机运行 Gradle 构建、测试、Lint 或 Android 编译。**
- 本机没有配置完整的 Android 构建环境，构建会由用户在 Linux 虚拟机或 GitHub Actions 中完成。
- 修改完成后，只需进行代码层面的静态检查，例如查看 diff、检查引用关系和明显语法问题。
- 不要为了“验证”而下载依赖、配置 Android SDK、启动 Gradle Daemon，或生成无关的临时构建文件。
- 用户提供构建日志后，根据日志修复问题；除非用户明确改变要求，否则仍不要在本机重新构建。

## 3. 控制修改范围

- 只修改当前需求直接涉及的代码，不顺手重构无关模块。
- 不要擅自修复、格式化或提交与当前任务无关的文件。
- 一个需求如果包含多个可以独立验证的功能，应按用户要求拆成多个 commit，方便逐项构建、验证和回退。
- 修改 UI 行为时，优先减少额外动画、重复状态变化和持续强制跟随，避免一个操作触发多个不必要的界面位移。
- 对一次性事件优先采用一次性触发逻辑，不要写成条件成立期间持续执行的“循环式”控制。
- 如果此前的尝试没有实际解决问题，应先按用户要求回退无效修改，再基于原因重新实现，避免在错误方案上不断叠加补丁。

## 4. Git commit 规范

提交信息采用 **Conventional Commits（约定式提交）** 风格：

```text
<type>(<scope>): <中文简短标题>
```

常用类型：

- `fix`：修复 Bug；
- `feat`：新增功能；
- `chore`：构建脚本、配置或其他维护工作；
- `refactor`：不改变功能的代码重构；
- `docs`：文档修改。

常用 scope 可根据实际模块选择，例如 `chat`、`ui`、`build`、`assistant`。

### 提交信息要求

- 标题简洁、准确，说明最终实现了什么，不记录已经撤回或失败的中间尝试。
- 使用多个 `-m` 参数编写多段提交信息，而不是在一个 `-m` 中手动写 `\n` 转义。
- 第一段是标题，第二段是正文；正文概括主要行为和实现结果，但不要细化到每一行代码或每个变量。
- 正文通常写 1～2 个自然段即可，重点说明用户可感知的变化和必要的实现策略。

示例：

```bash
git commit \
  -m "fix(chat): 优化侧边栏滑动手势" \
  -m "关闭抽屉默认手势并增加单次手势周期内的方向锁定，使垂直滚动与侧边栏拖动互不抢占。\n\n侧向拖动期间侧边栏保持跟手，并根据位移和速度决定展开或收回，避免停顿时闪出后自动回弹。"
```

提交前应检查：

```bash
git status --short
git diff --check
git diff --cached
```

确认暂存区只包含当前 commit 对应的修改后再提交。

## 5. Push 与历史操作

- 默认由用户负责执行 `git push`，AI 完成 commit 后提醒用户 push 即可。
- 除非用户明确要求，否则 AI 不要自行 push、force push、rebase 或改写提交历史。
- `reset --hard`、强制回退、修改已有 commit 等操作必须以用户的明确要求为依据。
- 如果需要修改最新提交且用户要求保留原提交信息，可使用 amend，但应先确认工作区和暂存区中没有混入无关内容。

## 6. 汇报方式

修改完成后，AI 应简要汇报：

1. 修改了哪些行为；
2. 涉及哪些主要文件或模块；
3. 是否已经创建 commit，以及 commit 的哈希和标题；
4. 明确说明未在本机构建或测试；
5. 如果由用户负责推送，提醒运行 `git push`。

不要把大量内部推理、逐行实现细节或无关命令输出堆进汇报；对于可能影响验证的限制、风险和待确认项则应明确说明。

## 7. 处理构建与验证反馈

- 用户会在虚拟机或 GitHub Actions 中验证修改。
- 收到编译错误时，先定位错误对应的具体类型、调用约束或 API 使用方式，再做最小修复。
- 收到交互问题反馈时，以用户描述的复现步骤为准，区分“现象被掩盖”和“根因已修复”。
- 不要因为代码逻辑看起来合理就宣称问题已经彻底解决；应表述为“已按当前判断修改，等待构建或实际交互验证”。
- 已经由用户确认有效的行为应尽量保持不变，后续修改避免造成回归。
