# Debug Session: structured-plan-truncation

**Status**: [CLOSED]
**Date**: 2026-07-14
**Description**: 结构化行程规划 JSON 被截断，导致 Jackson 解析失败

## Hypotheses

1. **H1: finish_reason = "length"** — AI 模型输出达到 maxTokens 限制，长行程（15天）需要的 token 数超过 10000 ✅ 确认，但根因更深
2. **H2: WebClient 响应截断** — WebClient/Netty 有响应体大小限制 ❌ 排除
3. **H3: 模型端点输出限制** — 豆包模型端点在控制台侧有更低的输出 token 上限 ❌ 排除
4. **H4: 连接提前关闭** — 网络超时或连接中断导致响应不完整 ❌ 排除
5. **H5: JSON 特殊字符** — 内容中包含破坏 JSON 结构的字符 ❌ 排除

## Root Cause

`ChatRequest.maxTokens` 字段序列化为 `maxTokens`（驼峰），而 OpenAI 兼容 API 期望 `max_tokens`（下划线），导致 `max_tokens` 参数未传递给 API。API 使用默认约 4096 tokens 的输出限制，长行程（如 15 天）生成的 JSON 超过此限制被截断，导致 Jackson 解析失败。

同时发现 `ChatResponse.Choice.finishReason` 也有同样的字段名不匹配问题（API 返回 `finish_reason`），导致 `finishReason` 始终为 null。

## Fix

1. `ChatRequest.maxTokens` 添加 `@JsonProperty("max_tokens")` 注解
2. `ChatResponse.Choice.finishReason` 添加 `@JsonProperty("finish_reason")` 注解
3. 将 `AIService` 中手动 `new ObjectMapper()` 改为 Spring 注入的 ObjectMapper

## Fix Log

| Timestamp | Change | Result |
|-----------|--------|--------|
| 2026-07-14 03:44 | 添加 @JsonProperty 注解 + Spring 注入 ObjectMapper | 15天日本行程：content=15636, finishReason=stop, code=200 ✅ |

## Verification

- **修复前**：15天日本行程 content=11901, finishReason=length, 解析失败
- **修复后**：15天日本行程 content=15636, finishReason=stop, code=200

