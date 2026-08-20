# SilverPilot 大模型接入与 API 配置实战

> 适用范围：当前仓库（Java 25、Spring Boot 4.1、Vue 3），文档按 2026-08-19 的源码与厂商官方接口核对。
>
> 本文中的 Key 全是占位符。真实 Key 只能放在本机被 Git 忽略的配置、CI Secret 或云密钥管理服务中，不能写入本文、源码、前端 `VITE_*` 变量、截图、日志或提交记录。

## 1. 先看结论

当前项目已经真正接通并注册了 3 个 Provider ID：

| Provider ID | 当前状态 | 文本 | 工具调用 | JSON 照护方案 | 图片 |
| --- | --- | --- | --- | --- | --- |
| `deepseek` | 已实现，只需配置 Key | 是 | 是 | 是 | 否 |
| `doubao` | 已实现，需配置 Key 和模型/Endpoint ID | 是 | 是 | 是 | 是 |
| `mock` | 已实现，默认关闭 | 确定性模拟 | 仅本地只读路由 | 不代替真实模型评测 | 否 |

`auto` 的当前路由规则是：

- 纯文本：DeepSeek → 豆包 → 显式启用的 Mock；
- 带图片：只选择已配置的豆包；
- 所有写操作：模型只能创建 `PENDING` 动作，用户确认后才执行。

OpenAI、通义千问、智谱 GLM、Kimi、Gemini、腾讯混元、百度千帆、硅基流动、OpenRouter、Ollama 等虽然大多提供 OpenAI-compatible 接口，但当前源码没有注册它们的 Provider ID。不能只把它们的 Key 填进 `DEEPSEEK_API_KEY` 就宣称已经接入；正确做法是先增加一个通用 OpenAI-compatible 适配器，见第 6 节。

### 1.1 为什么普通聊天成功仍不算接入完成

SilverPilot 不是只调用一次模型的聊天页。一个可用于本项目的模型/端点至少要同时满足：

1. 接受非流式 `POST /chat/completions`；
2. 接受 `system/user/assistant/tool` 消息；
3. 支持 OpenAI 风格 `tools`、`tool_choice` 和 `tool_calls`；
4. 照护方案场景支持 `response_format={"type":"json_object"}`，或由专用适配器提供等价的结构化输出保证；
5. 若声明支持图片，必须接受 `content[] + image_url + data:image/...;base64,...`。

模型只会回答“你好”，但不能稳定返回 `tool_calls`，就无法驱动本项目的活动、服务、健康报告、菜谱和订单工具。

## 2. 当前调用链与关键文件

```text
Vue AiChat.vue
    -> POST /chat 或 POST /chat/care-plan
    -> ChatController：鉴权、输入校验、图片校验、安全预检
    -> AiProviderService：选择 Provider、装饰请求体、统计 Token
    -> DeepSeekClient / DoubaoClient：后端携带 Key 请求厂商 API
    -> ToolExecutor：执行真实业务只读工具或创建待确认写操作
    -> AgentRunService：记录模型、延迟、Token、工具轨迹和状态
```

| 文件 | 作用 |
| --- | --- |
| `SourceCode/cecsmsServe-springboot/src/main/resources/application.properties` | Spring 配置入口、默认 URL、超时和环境变量映射 |
| `SourceCode/cecsmsServe-springboot/src/main/java/com/cecsmsserve/service/AiProviderService.java` | Provider 白名单、自动路由、请求体、能力状态 |
| `SourceCode/cecsmsServe-springboot/src/main/java/com/cecsmsserve/service/DeepSeekClient.java` | DeepSeek HTTP、Bearer 鉴权、重试和错误转换 |
| `SourceCode/cecsmsServe-springboot/src/main/java/com/cecsmsserve/service/DoubaoClient.java` | 火山方舟/豆包 HTTP 客户端 |
| `SourceCode/cecsmsServe-springboot/src/main/java/com/cecsmsserve/controller/ChatController.java` | `/chat`、Provider 参数校验、工具循环和图片协议 |
| `SourceCode/cecsmsServe-springboot/src/main/java/com/cecsmsserve/service/CareRecommendationService.java` | JSON 照护方案及真实服务 ID/来源校验 |
| `SourceCode/cecsmsui-vue/src/components/front/ai/AiChat.vue` | 从 `/chat/status` 动态显示可用 Provider |
| `.env.docker` | 本机真实配置；已被 Git 忽略，不得提交 |
| `.env.docker.example` | 变量名模板；只能放占位符 |
| `compose.yaml` | 将 `SILVERPILOT_*` 映射到后端容器 |
| `scripts/docker-dev.ps1` | 生成本机配置、刷新 IDEA host profile、启动/重建容器 |

项目没有引入 Spring AI 或 LangChain4j；当前使用 Spring `RestClient` 直接调用 Chat Completions。为了最小改动，新增同协议厂商时继续复用这一契约更快；若要迁移 Responses API 或厂商原生协议，应建立独立适配器，不要在现有客户端里混杂多套响应格式。

## 3. 零改代码：配置现有 DeepSeek、豆包或 Mock

### 3.1 Key 与模型 ID 的准备原则

在厂商控制台完成以下动作：

1. 新建仅供本项目开发环境使用的 Key，不复用个人长期主 Key；
2. 开通目标模型，并确认账户余额、并发、地域和内容合规设置；
3. 复制 API Key；
4. 复制准确的模型 ID。火山方舟还要复制控制台给出的模型/推理 Endpoint ID；
5. 设置预算或用量告警；
6. 生产环境另建 Key，开发和生产不可共用。

模型名区分大小写，并可能随厂商升级。本文中的模型名是当前可用示例，最终以对应账号控制台和官方 Models API 为准。

### 3.2 编辑 `.env.docker`

项目根目录已经存在 `.env.docker` 时，只修改模型相关行。不要用示例文件整体覆盖它，因为其中还有本项目 MySQL、Redis、JWT、MCP 等已生成的本机凭据。

DeepSeek 文本 Agent：

```dotenv
SILVERPILOT_DEEPSEEK_API_KEY=<在本机粘贴 DeepSeek API Key>
SILVERPILOT_DEEPSEEK_MODEL=deepseek-v4-flash
SILVERPILOT_DEEPSEEK_THINKING_ENABLED=false
```

建议第一次接入先关闭 thinking，先把工具调用、照护方案 JSON 和成本边界跑通，再单独评测思考模式。当前官方模型还可选 `deepseek-v4-pro`；不要再使用已于 2026-07-24 停用的旧别名 `deepseek-chat` 或 `deepseek-reasoner`。

豆包/火山方舟图文 Agent：

```dotenv
SILVERPILOT_DOUBAO_API_KEY=<在本机粘贴火山方舟 API Key>
SILVERPILOT_DOUBAO_MODEL=<从火山方舟控制台复制模型或推理 Endpoint ID>
```

项目内置豆包完整接口地址为：

```text
https://ark.cn-beijing.volces.com/api/v3/chat/completions
```

不要把网页聊天产品名称当作 `model`；使用控制台实际允许 API 调用的模型/Endpoint ID。

没有外部 Key，只做本地 UI、权限与只读工具联调：

```dotenv
SILVERPILOT_AI_MOCK_ENABLED=true
```

Mock 不会进行真实语义推理、图片理解或虚构写操作成功，不能用它证明真实模型效果。

### 3.3 Local/IDEA 模式应用配置

在项目根目录执行：

```powershell
.\scripts\docker-dev.ps1 config
```

脚本会把 `.env.docker` 中的 Provider 配置写入被忽略的：

```text
SourceCode/cecsmsServe-springboot/config/application-host.properties
```

然后在 IDEA 中完全停止并重新启动 `com.cecsmsserve.CecsmsServeApplication`。Key 在 Spring Bean 创建时读取，不支持只改文件而不重启后端。

如果是第一次准备 Local 模式，先运行：

```powershell
.\scripts\docker-dev.ps1 redis
```

它会生成/恢复 `.env.docker`、准备外置 host profile，并只启动本项目所需的 Docker Redis。宿主机 MySQL、IDEA 后端和前端仍按现有部署文档启动。

### 3.4 全 Docker 模式应用配置

第一次启动：

```powershell
.\scripts\docker-dev.ps1 full
```

已经运行，只是更换 Key 或模型：

```powershell
.\scripts\docker-dev.ps1 restart
```

`restart` 会重建容器以重新注入环境变量，但保留命名卷。不要为了换模型运行 `reset -Force`；该命令会删除数据库、Redis 和上传卷。

### 3.5 两组变量不要混用

| 场景 | DeepSeek | 豆包 |
| --- | --- | --- |
| 根目录 `.env.docker` | `SILVERPILOT_DEEPSEEK_API_KEY/MODEL/THINKING_ENABLED` | `SILVERPILOT_DOUBAO_API_KEY/MODEL` |
| 直接启动 Spring 进程 | `DEEPSEEK_API_URL/API_KEY/MODEL/THINKING_ENABLED` | `DOUBAO_API_URL/API_KEY/MODEL` |

`SourceCode/cecsmsServe-springboot/.env.example` 只是说明，Spring Boot 不会自动加载它。

当前 `compose.yaml` 和 `docker-dev.ps1` 没有转发 `SILVERPILOT_DEEPSEEK_API_URL` 或 `SILVERPILOT_DOUBAO_API_URL`。内置官方 URL 无需设置；若要更换网关或接入其他兼容端点，必须按第 6 节把 URL 纳入完整配置链，不能只在 `.env.docker` 随意增加一个不会被读取的变量。

## 4. 配置后的分层验证

### 4.1 第 1 层：后端健康

```powershell
$health = Invoke-RestMethod 'http://127.0.0.1:8083/actuator/health'
$health.status
```

期望为 `UP`。这只能证明应用、数据库和已启用基础设施健康，不能证明模型 API 可用。

### 4.2 第 2 层：Provider 状态

登录前端后进入“小伴”页面，确认：

- “文字办理”显示可用；
- Provider 下拉框中目标模型不是禁用状态；
- 页面只显示 Provider、模型和能力，不显示 Key；
- 配置图片模型时“看图可用”才会变为可用。

后端状态接口是 `GET /chat/status` 和 `GET /chat/providers`，受 JWT 保护。不要为了方便验证而把它们从鉴权拦截器中排除。

### 4.3 第 3 层：四类真实能力

依次使用以下测试语句：

1. 工具调用：`请先查询当前可预约的养老服务，再根据真实结果告诉我有哪些选择。`
2. 知识来源：`请查询“写操作人工确认安全策略”，回答中保留 [KB:...] 来源。`
3. 结构化照护方案：`老人行动不便，希望获得安全的上门助浴支持，请给出风险提示和可核验下一步。`
4. 写操作保护：给出完整服务 ID、日期和地址，确认模型只生成待确认卡片；不点击确认时数据库业务状态不能改变。

配置视觉模型后再上传一张不含真实个人信息的 JPG/PNG/WebP，验证图片请求确实由视觉 Provider 处理。不要用姓名、身份证、病历或家庭住址做联调样本。

### 4.4 本地自动验收

后端单元测试：

```powershell
Set-Location .\SourceCode\cecsmsServe-springboot
.\mvnw.cmd "-Dtest=AiProviderServiceTests,DeepSeekClientTests,CareRecommendationServiceTests,ChatControllerImageValidationTests" test
Set-Location ..\..
```

已启动本地全栈后，可运行在线 Agent 冒烟：

```powershell
.\scripts\smoke-agent.ps1 -EnvFile .\.env.docker
```

当前脚本明确选择 `deepseek`，因此只在 DeepSeek 已配置时运行。该脚本会写入 Agent 审计记录，并创建后立即取消一条本地待确认动作；只应在本地测试库执行，不要直接对生产库运行。豆包或以后新增的通用 Provider 使用下一条带 `-Provider` 参数的评测命令。

小批量在线评测：

```powershell
.\scripts\run-agent-evaluation.ps1 -EnvFile .\.env.docker -Provider auto -MaxCases 6
```

评测会产生真实模型费用、Agent 运行记录和受控的待确认/取消记录。通过它只能证明工程契约，不代表医疗有效性、生产稳定性或商业效果。

## 5. 主流模型的接入参数速查

下表中的 URL 均为“完整 Chat Completions 请求地址”，便于直接填入第 6 节建议的 `GENERIC_LLM_API_URL`。模型示例只用于首次联调；上线前重新查询官方模型列表和账号权限。

| 厂商/平台 | 完整接口地址 | 当前模型示例 | 本项目接入判断 |
| --- | --- | --- | --- |
| [OpenAI](https://developers.openai.com/api/reference/resources/chat) | `https://api.openai.com/v1/chat/completions` | 从 `/v1/models` 选择明确支持工具调用的模型 | 通用适配器；本项目当前仍用 Chat Completions，不要直接换成 Responses 响应格式 |
| [DeepSeek](https://api-docs.deepseek.com/api/create-chat-completion/) | `https://api.deepseek.com/v1/chat/completions` | `deepseek-v4-flash`、`deepseek-v4-pro` | 已内置；优先按第 3 节配置 |
| [火山方舟/豆包](https://api.volcengine.com/api-docs/view?action=ChatCompletions&serviceCode=ark&version=2024-01-01) | `https://ark.cn-beijing.volces.com/api/v3/chat/completions` | 控制台模型/Endpoint ID | 已内置；当前唯一自动图片通道 |
| [阿里云百炼/通义千问](https://help.aliyun.com/zh/model-studio/qwen-api-via-openai-chat-completions) | `https://{WorkspaceId}.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions` | `qwen-plus` 或控制台当前模型 ID | 通用适配器；Key、Workspace 和地域必须匹配，逐模型验证 tools/JSON/图片 |
| [智谱 GLM](https://docs.bigmodel.cn/cn/guide/develop/openai/introduction) | `https://open.bigmodel.cn/api/paas/v4/chat/completions` | `glm-5.2` | 通用适配器；官方兼容接口支持 Function Calling，视觉模型需单独验证 |
| [Kimi 中国站](https://platform.kimi.com/docs/api/quickstart) | `https://api.moonshot.cn/v1/chat/completions` | `kimi-k2.6` 或 Models API 当前 ID | 通用适配器；不要混用 `.cn` Key 与国际站 `.ai` 端点 |
| [Kimi 国际站](https://platform.kimi.ai/docs/api/overview) | `https://api.moonshot.ai/v1/chat/completions` | `kimi-k2.6` 或 Models API 当前 ID | 通用适配器；工具、图片和 JSON 能力按所选模型验收 |
| [Google Gemini](https://ai.google.dev/gemini-api/docs/openai) | `https://generativelanguage.googleapis.com/v1beta/openai/chat/completions` | `gemini-3.7-flash` | 通用适配器；官方兼容层支持 Function Calling、图片和结构化输出，但目前仍标注 beta |
| [腾讯混元](https://cloud.tencent.com/document/product/1729/111007) | `https://api.hunyuan.cloud.tencent.com/v1/chat/completions` | `hunyuan-turbos-latest` | 通用适配器；模型能力有差异，且官方正在向 TokenHub 迁移，新开通前查控制台 |
| [百度千帆](https://cloud.baidu.com/doc/qianfan/s/Hmh4suq26) | `https://qianfan.baidubce.com/v2/chat/completions` | 从控制台选择 ERNIE、DeepSeek、GLM 等已开通模型 | 通用适配器；同一网关下仍要按具体模型验证能力 |
| [硅基流动](https://docs.siliconflow.cn/en/api-reference/chat-completions/chat-completions) | `https://api.siliconflow.cn/v1/chat/completions` | 使用完整模型 slug，如控制台所示 | 通用适配器；不同托管模型对 tools/JSON 支持不同 |
| [OpenRouter](https://openrouter.ai/docs/api/api-reference/chat/send-chat-completion-request) | `https://openrouter.ai/api/v1/chat/completions` | 使用完整 `provider/model` slug | 通用适配器；先在模型元数据检查 `tools`、`tool_choice`、`response_format` |
| [Ollama](https://docs.ollama.com/api/openai-compatibility) | Local：`http://127.0.0.1:11434/v1/chat/completions` | 如已拉取且支持工具的本地模型 | 通用适配器；API Key 填非空占位值 `ollama`，服务会忽略；能力取决于本地模型 |

### 5.1 两类不能直接按普通 Bearer 通用适配器上线的情况

Anthropic Claude：官方提供 `https://api.anthropic.com/v1/` 的 OpenAI SDK 兼容层，但明确说明它主要用于测试和模型比较，不是多数生产场景的长期方案；其中 `response_format` 会被忽略。SilverPilot 的照护方案依赖结构化 JSON，因此正式接入应实现原生 `POST /v1/messages` 适配器，并转换 `system`、`tools/tool_use/tool_result`、图片、Token 和错误格式。参考：[OpenAI SDK compatibility](https://platform.claude.com/docs/en/cli-sdks-libraries/libraries/openai-sdk)、[Messages API](https://platform.claude.com/docs/en/api/messages/create)。

Azure OpenAI / Microsoft Foundry：新 v1 接口更接近 OpenAI，但 Key 鉴权常使用 `api-key` 请求头，旧部署式 URL 还包含 deployment 和 `api-version`。若不用 Entra Bearer Token，通用客户端必须支持可配置鉴权头，不能固定写死 `Authorization: Bearer ...`。参考：[Azure OpenAI v1 API](https://learn.microsoft.com/en-us/azure/foundry/openai/api-version-lifecycle)。

## 6. 一次改造，快速切换 OpenAI-compatible 厂商

### 6.1 不要复用 DeepSeek 槽位冒充其他厂商

`AiProviderService` 会对 `deepseek` 请求额外加入：

```json
{
  "thinking": { "type": "disabled" },
  "user_id": "cecsms-user-17"
}
```

这些不是所有 OpenAI-compatible 厂商都接受。复用 DeepSeek 槽位还会让前端状态、运行审计、自动路由和错误提示全部错误。因此应新增 `generic`，而不是只替换 DeepSeek URL。

### 6.2 建议的通用配置契约

先支持“一次启用一个额外兼容厂商”，配置保持简单：

```properties
generic.api.name=${GENERIC_LLM_NAME:OpenAI Compatible}
generic.api.url=${GENERIC_LLM_API_URL:}
generic.api.key=${GENERIC_LLM_API_KEY:}
generic.api.model=${GENERIC_LLM_MODEL:}
generic.api.auth-type=${GENERIC_LLM_AUTH_TYPE:bearer}
generic.api.vision=${GENERIC_LLM_VISION:false}
generic.api.connect-timeout=${GENERIC_LLM_CONNECT_TIMEOUT:5s}
generic.api.read-timeout=${GENERIC_LLM_READ_TIMEOUT:90s}
```

`auth-type` 至少支持：

| 值 | 请求头 |
| --- | --- |
| `bearer` | `Authorization: Bearer <key>` |
| `api-key` | `api-key: <key>`，用于部分 Azure/Foundry Key 鉴权 |
| `x-api-key` | `x-api-key: <key>`，用于厂商原生协议适配器 |
| `none` | 不发送鉴权头；只允许明确的本机回环服务 |

推荐即使 Ollama 忽略 Key，也保留 `bearer` 并使用非空占位值 `ollama`，避免错误地开放 `none` 鉴权到远端 URL。

### 6.3 最小代码改造清单

按以下顺序修改，缺一项都会出现“配置写了但页面不可选”或“后端拒绝 Provider”的情况：

1. 复制 `DoubaoClient.java` 的 HTTP、超时、重试和错误处理骨架，创建 `OpenAiCompatibleClient.java`；把 URL、Key、model、显示名、vision 和鉴权方式改为 `generic.api.*`。
2. 客户端必须接收“完整请求地址”，继续返回 `Map<String,Object>`；响应仍按 `choices[0].message`、`tool_calls` 和 `usage` 解析。
3. 在 `AiProviderService` 增加固定 ID `GENERIC="generic"`、客户端依赖、`resolve` 分支、`chatCompletion` switch、`statuses()` 项和 `ProviderSelection`。重写当前 `needsVision` 分支：显式请求 `generic` 时只有 `GENERIC_LLM_VISION=true` 才能接图片；`auto` 也只能在该能力为真时选择它。
4. 通用请求体只保留标准字段：`model/messages/tools/tool_choice/stream/temperature/max_tokens/user/response_format`。不要加入 DeepSeek 专属 `thinking`、`user_id`，也不要擅自传其他厂商扩展字段。
5. 把 `ChatController.ChatRequest` 和 `CarePlanRequest` 的 Provider 校验从 `auto|deepseek|doubao|mock` 扩展为包含 `generic`；更好的长期做法是改为由 `AiProviderService` 统一校验，避免两处白名单漂移。
6. 在 `application.properties`、后端 `.env.example`、根 `.env.docker.example`、`compose.yaml` 和 `scripts/docker-dev.ps1` 的 host profile 写入逻辑中贯通全部 `GENERIC_LLM_*` 变量。
7. 给 `AiProviderServiceTests`、新 Client 测试、`CareRecommendationServiceTests` 和 Controller Provider 校验补用例。
8. 重启后端，前端会从 `/chat/status` 动态读取新 Provider；通常不需要把厂商名硬编码进 Vue。

通用客户端的核心发送逻辑应保持类似：

```java
RestClient.RequestBodySpec request = restClient.post()
        .uri(apiUrl)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON);

request = switch (authType) {
    case "bearer" -> request.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
    case "api-key" -> request.header("api-key", apiKey);
    case "x-api-key" -> request.header("x-api-key", apiKey);
    case "none" -> request;
    default -> throw new IllegalArgumentException("不支持的模型鉴权方式");
};

Map<String, Object> response = request
        .body(body)
        .retrieve()
        .body(RESPONSE_TYPE);
```

还要保留现有客户端的安全行为：

- 只有 429 和 5xx 等暂时性错误才有限重试；
- 401/403 返回脱敏提示，不能把响应体或 Key 写入日志；
- 连接和读取超时有上限，不允许无限等待；
- `isConfigured()` 必须同时检查 URL、Key、model；
- `none` 鉴权只允许 `127.0.0.1`、`localhost` 或经过严格校验的本地地址。

### 6.4 `.env.docker` 通用模板

完成上述代码改造后，从第 5 节复制完整 URL 和模型 ID：

```dotenv
GENERIC_LLM_NAME=通义千问
GENERIC_LLM_API_URL=https://<WorkspaceId>.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions
GENERIC_LLM_API_KEY=<只在本机粘贴>
GENERIC_LLM_MODEL=qwen-plus
GENERIC_LLM_AUTH_TYPE=bearer
GENERIC_LLM_VISION=false
GENERIC_LLM_CONNECT_TIMEOUT=5s
GENERIC_LLM_READ_TIMEOUT=90s
```

切换为 Gemini 时只替换：

```dotenv
GENERIC_LLM_NAME=Google Gemini
GENERIC_LLM_API_URL=https://generativelanguage.googleapis.com/v1beta/openai/chat/completions
GENERIC_LLM_API_KEY=<只在本机粘贴>
GENERIC_LLM_MODEL=gemini-3.7-flash
GENERIC_LLM_AUTH_TYPE=bearer
GENERIC_LLM_VISION=true
```

切换为本机 Ollama 时：

```dotenv
GENERIC_LLM_NAME=Ollama 本地模型
GENERIC_LLM_API_URL=http://127.0.0.1:11434/v1/chat/completions
GENERIC_LLM_API_KEY=ollama
GENERIC_LLM_MODEL=<ollama list 中已拉取且支持 tools 的模型名>
GENERIC_LLM_AUTH_TYPE=bearer
GENERIC_LLM_VISION=false
```

Ollama 最适合 IDEA/Local 模式。全 Docker 后端中的 `127.0.0.1` 指向容器自身，不能访问宿主机 Ollama；若改用 `host.docker.internal`，还必须让 Ollama 监听容器可访问的本机接口，并用 Windows 防火墙阻止公网访问 11434。不要为了省一步把 Ollama 裸露到局域网或公网。

### 6.5 同时启用多个新厂商

需要在 UI 中同时比较 OpenAI、Qwen、GLM、Gemini 等多个 Provider 时，不要复制出多份几乎相同的 HTTP Client。应进一步抽象为：

```text
AiProviderService
    -> Map<String, OpenAiCompatibleClient>
       -> ProviderConfig(id, name, url, key, model, authType, vision, capabilities)
```

每个 Provider 还应显式声明 `tools`、`jsonMode`、`vision` 能力。自动路由必须按任务所需能力筛选，不能因为“Key 已配置”就把图片或结构化方案发给不支持的模型。

## 7. 新 Provider 的验收标准

接入 PR 或本地改造至少要通过以下门禁：

| 类别 | 必须证明 |
| --- | --- |
| 配置 | 未配置时状态明确；配置后显示正确 ID、名称、模型，不暴露 Key |
| 普通文本 | 能完成非流式回复，中文无乱码，超时可控 |
| 工具调用 | 能返回标准 `tool_calls`，工具结果回填后能继续生成最终答复 |
| 参数安全 | 幻觉工具名、非法 JSON 参数、越权 userId 都会被后端拒绝 |
| JSON 方案 | 输出可解析，并通过 urgency、服务 ID、来源 allowlist 等现有校验 |
| 图片 | 只有声明 vision 的模型可选；JPG/PNG/WebP Data URL 能处理，超限图片被本地拒绝 |
| 写操作 | 只能创建 `PENDING`；未确认不改变业务数据 |
| 错误 | 401、403、429、5xx、DNS、超时均转换为稳定且不泄密的错误 |
| 成本 | `usage.prompt_tokens/completion_tokens/total_tokens` 能记录；缺失 usage 时不能假装成本已统计 |
| 回归 | DeepSeek、豆包、Mock、自动路由和本地知识降级行为不受影响 |

建议增加一个新 Client 测试类，至少模拟：200 文本、200 tool call、401、429 后重试、500 后重试、空响应、畸形 `choices`、超时和 Key 缺失。

## 8. 常见错误与定位

| 现象 | 高概率原因 | 处理 |
| --- | --- | --- |
| `/chat` 返回 503“尚未配置” | Key/model 未贯通，或改配置后没有重启 | 查看 `.env.docker` 对应行，执行 `config` 或 `restart`，再重启 IDEA 后端 |
| 400：unknown field `thinking`/`user_id` | 把其他厂商错误塞进 DeepSeek 路由 | 新建通用 Provider；不要复用 `deepseek` ID |
| 400：`tools`/`tool_choice` 不支持 | 所选模型只支持普通聊天 | 换支持 Function Calling 的模型，或限制该 Provider 不进入 Agent 路由 |
| 400：`response_format` 不支持 | 兼容层或模型没有 JSON mode | 换模型/原生结构化输出适配器；不能关闭后端 JSON 校验来掩盖问题 |
| 401/403 | Key 错误、地域不匹配、Key 无模型权限或鉴权头错误 | 在同一厂商控制台核对区域、项目、Endpoint、鉴权方式，必要时轮换 Key |
| 404 | URL 只填了 base URL、漏 `/chat/completions`，或 deployment/model ID 错 | 使用第 5 节“完整接口地址”；Azure 还要核对 deployment 路径 |
| 429 | 余额、RPM/TPM、并发或账号限额 | 查厂商用量页；降低评测并发，保留有限退避，不做无限重试 |
| 502“返回格式异常” | 端点不是 Chat Completions，或网关改变了 `choices[0].message` | 抓取脱敏后的结构字段，写专用响应转换器 |
| 文字可用、图片 503 | 目标 Provider 未声明 vision，或 `auto` 当前只识别豆包图片 | 配置豆包；或完成通用 vision 路由改造与图片验收 |
| 照护方案偶发失败 | 模型没有稳定 JSON 输出，或生成了不存在的服务 ID/来源 | 保留现有校验，调整模型/Prompt；绝不把无效 JSON 包装成成功 |
| Ollama Local 可用、Docker 不通 | 容器内 `127.0.0.1` 不是宿主机 | 使用受控的 `host.docker.internal` 方案并限制防火墙；优先 Local 模式 |
| 后端健康但模型不可用 | `/actuator/health` 不会替你消费额度调用模型 | 继续执行 Provider 状态、工具调用和结构化方案验证 |

调试时可以记录以下字段：Provider ID、模型 ID、HTTP 状态、耗时、请求 ID、重试次数、Token。禁止记录：Authorization/api-key/x-api-key、完整请求体、图片 Base64、原始健康信息、厂商返回中可能包含的敏感文本。

## 9. 安全、隐私与费用底线

1. Vue 只调用本项目 `/api`，不得直接调用任何模型厂商；否则 Key 会进入浏览器。
2. `.env.docker`、外置 `config/application-host.properties` 和 DPAPI 备份都不得提交或打包进镜像。
3. Key 按开发、测试、生产分离；人员离开、截图/日志泄露或误提交时立即轮换。
4. 对厂商开启额度上限、用量告警和允许的模型范围；不能只依赖应用端 Token 上限。
5. 外部模型会接收经安全层处理后的对话。涉及真实老人健康、联系方式、住址或图片前，必须完成数据处理目的、地域、保留期限、训练使用、访问控制和供应商条款评估。
6. 本项目当前会脱敏身份证号、手机号和邮箱，并阻断明显 Key/私钥；这不是完整的医疗数据合规方案。
7. 不向用户展示或存储模型私有思维链；记录工具、来源、状态、延迟和 Token 即可。
8. Provider 超时或失败不能绕过写操作确认，也不能把模型文字当成数据库成功证据。
9. 聚合网关会增加一层数据处理方和故障面；使用 OpenRouter、硅基流动、云市场转售模型前，单独核对数据链路和计费主体。

## 10. 选型建议

| 目标 | 建议起点 | 原因与边界 |
| --- | --- | --- |
| 最少改代码完成文本 Agent | DeepSeek `deepseek-v4-flash` | 当前已内置且覆盖 tools/JSON；仍需真实在线评测和费用监控 |
| 需要图片理解 | 豆包/火山方舟 | 当前图片路由已实现；必须配置准确 Endpoint ID |
| 无 Key 做前后端联调 | 显式 Mock | 不收费且可验证权限/只读工具；不能代表模型质量 |
| 快速比较多个兼容模型 | 先实现第 6 节通用适配器 | 保持同一工具契约与观测口径；每个模型仍需单独验收 |
| 数据尽量留在本机 | Ollama + 支持 tools 的本地模型 | 需要足够硬件、模型能力验证和本机服务加固，不等于天然安全 |
| Claude 生产接入 | 原生 Messages 适配器 | 官方 OpenAI 兼容层主要用于测试，结构化输出能力不能照搬 |
| 企业 Azure/Foundry | 支持 `api-key` 或 Entra 的专用鉴权 | 需要 deployment、身份、网络和治理配置，不应硬塞进固定 Bearer 客户端 |

模型质量不能只看一次主观回答。应固定同一套 16 项离线/在线契约集，对每个 Provider 比较：工具选择正确率、参数合法率、结构化输出通过率、无依据成功声明率、平均/P95 延迟、Token、费用、429/5xx 比例和图片任务通过率。

## 11. 接入完成检查单

- [ ] Key 只存在于本机 Secret/环境配置，Git diff 和前端产物中没有 Key；
- [ ] URL 是完整 Chat Completions 地址，Key、地域、Workspace/项目匹配；
- [ ] 模型 ID 来自当前控制台或 Models API，不是网页产品名；
- [ ] `/chat/status` 显示正确 Provider、模型和能力，不显示敏感配置；
- [ ] 普通文本、工具调用、知识来源、JSON 照护方案均通过；
- [ ] 声明 vision 时已通过真实无敏感信息图片测试；
- [ ] 写操作仍停在 `PENDING` 并要求当前用户确认；
- [ ] 401/403/429/5xx/超时测试不泄露 Key，也不会无限重试；
- [ ] Token、延迟、成功/失败和 Provider 已进入 `agent_run` 观测；
- [ ] 开启供应商预算告警，并记录本次测试的模型版本、日期和区域；
- [ ] 全量后端测试、前端测试/构建和受控在线评测通过；
- [ ] 文档、示例配置、Compose、Local host profile 和代码变量名保持一致。

## 12. 官方资料

- [OpenAI Chat API Reference](https://developers.openai.com/api/reference/resources/chat)
- [DeepSeek Chat Completion](https://api-docs.deepseek.com/api/create-chat-completion/) 与 [Models & Pricing](https://api-docs.deepseek.com/quick_start/pricing/)
- [火山方舟 ChatCompletions](https://api.volcengine.com/api-docs/view?action=ChatCompletions&serviceCode=ark&version=2024-01-01)
- [阿里云百炼 OpenAI 兼容 Chat](https://help.aliyun.com/zh/model-studio/qwen-api-via-openai-chat-completions)
- [智谱 OpenAI API 兼容](https://docs.bigmodel.cn/cn/guide/develop/openai/introduction)
- [Kimi API 中国站](https://platform.kimi.com/docs/api/quickstart) / [国际站](https://platform.kimi.ai/docs/api/overview)
- [Gemini OpenAI compatibility](https://ai.google.dev/gemini-api/docs/openai)
- [腾讯混元 OpenAI 兼容接口](https://cloud.tencent.com/document/product/1729/111007)
- [百度千帆 OpenAI SDK 兼容介绍](https://cloud.baidu.com/doc/qianfan/s/Hmh4suq26)
- [SiliconFlow Chat Completions](https://docs.siliconflow.cn/en/api-reference/chat-completions/chat-completions)
- [OpenRouter Chat Completions](https://openrouter.ai/docs/api/api-reference/chat/send-chat-completion-request)
- [Ollama OpenAI compatibility](https://docs.ollama.com/api/openai-compatibility)
- [Claude OpenAI SDK compatibility](https://platform.claude.com/docs/en/cli-sdks-libraries/libraries/openai-sdk) 与 [Messages API](https://platform.claude.com/docs/en/api/messages/create)
- [Azure OpenAI v1 API](https://learn.microsoft.com/en-us/azure/foundry/openai/api-version-lifecycle)
