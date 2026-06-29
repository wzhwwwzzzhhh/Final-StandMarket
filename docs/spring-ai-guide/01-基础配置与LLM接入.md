# 第一阶段：基础配置与 LLM 接入

## 本阶段目标

在 Java 后端（fashion-server）中接入 DeepSeek 大模型，实现最基础的聊天对话能力。

---

## 重要说明：为什么不用 Spring AI Starter？

Spring AI 官方要求 **Spring Boot 3.x + Java 17**，而本项目基于 **Java 8 + Spring Boot 2.7.15**。如果为了 Spring AI 升级整个项目，成本太高且破坏稳定性。

**本方案的做法**：用 Spring 自带的 `RestTemplate` 手动封装 LLM 调用，核心架构和设计模式与 Spring AI 完全一致。面试时你照样可以说：

> "基于 Spring 生态实现 LLM 集成，采用 ChatClient 模式封装对话交互，通过 `@Service` + 函数式接口实现 Tool Calling 模式。"

如果你未来项目升级到 Spring Boot 3.x，把这里的 `RestTemplate` 调用替换成 `spring-ai-openai-spring-boot-starter` 只需要改配置类，业务代码零改动。

---

## 步骤 1：添加依赖

### 操作

修改 `backend/fashion-server/pom.xml`，添加 Jackson 和 HttpClient 依赖（Jackson 通常已在 Spring Boot Web Starter 中自带，确认一下）：

```xml
<!-- Jackson 已由 spring-boot-starter-web 传递引入，无需重复添加 -->

<!-- 如果需要更精细的 HTTP 连接池控制，可添加： -->
<dependency>
    <groupId>org.apache.httpcomponents</groupId>
    <artifactId>httpclient</artifactId>
    <version>4.5.14</version>
</dependency>
```

### 知识点

**为什么用 RestTemplate 而不是直接 HttpClient？**
- `RestTemplate` 是 Spring 标准的 HTTP 客户端封装，项目中已有
- 自带 JSON 序列化/反序列化（通过 Jackson `HttpMessageConverter`）
- 统一异常处理（`RestClientResponseException`）
- 面试时可以说：Spring 生态内推荐 RestTemplate（或 WebClient），与框架无缝集成

---

## 步骤 2：配置 DeepSeek API 参数

### 操作

在 `application.yml` 中追加配置：

```yaml
fashion:
  # ... 已有配置 ...
  ai:
    deepseek:
      api-key: ${fashion.ai.deepseek.api-key:sk-your-key-here}
      base-url: ${fashion.ai.deepseek.base-url:https://api.deepseek.com}
      model: deepseek-chat
      timeout: 30
```

在 `application-dev.yml`（或你的开发配置）中填入真实 key：

```yaml
fashion:
  ai:
    deepseek:
      api-key: sk-your-real-key
      base-url: https://api.deepseek.com
```

### 知识点

**为什么配置成 `@ConfigurationProperties` 而不是直接 `@Value`？**

| 方式 | 优点 | 缺点 |
|------|------|------|
| `@Value("${xxx}")` | 简单直接 | 散落在各处，不好统一管理 |
| `@ConfigurationProperties` | 统一前缀、自动校验、IDE 提示 | 需多写一个类 |

后者更符合 Spring Boot 的最佳实践，参数多了也能保持整洁。

---

## 步骤 3：创建配置属性类

### 操作

创建 `backend/fashion-server/src/main/java/com/fashion/config/AiProperties.java`：

```java
package com.fashion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 模型配置属性
 *
 * 知识点：
 * - @ConfigurationProperties + prefix 绑定 application.yml 中以 "fashion.ai.deepseek" 开头的属性
 * - 默认值通过 @Value 的 ":" 语法或直接字段赋值实现
 * - 生产环境配置建议通过环境变量注入，不写死在配置文件里
 */
@Component
@ConfigurationProperties(prefix = "fashion.ai.deepseek")
public class AiProperties {

    /**
     * DeepSeek API Key
     */
    private String apiKey = "sk-your-key-here";

    /**
     * DeepSeek API 地址
     * DeepSeek 兼容 OpenAI API 格式，所以 base_url 指向 https://api.deepseek.com
     */
    private String baseUrl = "https://api.deepseek.com";

    /**
     * 模型名称
     * deepseek-chat 是 DeepSeek-V3 系列
     */
    private String model = "deepseek-chat";

    /**
     * HTTP 超时时间（秒）
     */
    private int timeout = 30;

    // ========== getters / setters ==========

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public int getTimeout() { return timeout; }
    public void setTimeout(int timeout) { this.timeout = timeout; }
}
```

### 知识点

**`@ConfigurationProperties` 需要额外引入依赖吗？**

Spring Boot 2.7 中已内置，无需额外依赖。如果使用 IDE 需要提示功能，可添加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-configuration-processor</artifactId>
    <optional>true</optional>
</dependency>
```

---

## 步骤 4：创建 ChatClient — LLM 调用封装

### 操作

创建 `backend/fashion-server/src/main/java/com/fashion/ai/ChatClient.java`：

```java
package com.fashion.ai;

import com.fashion.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * LLM 聊天客户端
 *
 * 封装对 DeepSeek API 的调用，提供 Chat 风格接口。
 * 设计思路对标 Spring AI 的 ChatClient：
 *   - 用户只需传入消息列表，得到回复文本
 *   - 内部处理 HTTP 请求、JSON 序列化、错误处理
 *   - 调用方无需关心 API 细节
 *
 * 知识点：
 * - RestTemplate 是 Spring 同步 HTTP 客户端，线程安全可复用
 * - DeepSeek API 兼容 OpenAI Chat Completion 格式
 * - 把 LLM 调用封装成独立的 Component，方便替换实现（如换成文心、通义等）
 */
@Component
public class ChatClient {

    private static final Logger log = LoggerFactory.getLogger(ChatClient.class);

    private final RestTemplate restTemplate;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public ChatClient(RestTemplate restTemplate, AiProperties aiProperties, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 发送聊天消息，返回模型回复文本
     *
     * @param messages 消息列表，格式：[{"role": "user", "content": "你好"}]
     * @return 模型回复的文本内容
     */
    public String chat(List<Map<String, String>> messages) {
        // ============ 1. 构建请求体 ============
        // DeepSeek API 请求格式（兼容 OpenAI）：
        // {
        //   "model": "deepseek-chat",
        //   "messages": [{"role": "user", "content": "你好"}],
        //   "temperature": 0.7
        // }
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", aiProperties.getModel());
        requestBody.put("temperature", 0.7);

        ArrayNode messagesArray = requestBody.putArray("messages");
        for (Map<String, String> msg : messages) {
            ObjectNode msgNode = messagesArray.addObject();
            msgNode.put("role", msg.get("role"));
            msgNode.put("content", msg.get("content"));
        }

        // ============ 2. 构建 HTTP 请求头 ============
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(aiProperties.getApiKey());

        HttpEntity<String> request = new HttpEntity<>(
                objectMapper.writeValueAsString(requestBody), headers
        );

        try {
            // ============ 3. 发送请求 ============
            // DeepSeek API 地址: POST /chat/completions
            String url = aiProperties.getBaseUrl() + "/chat/completions";
            log.debug("Calling LLM API: model={}, messages={}", aiProperties.getModel(), messages.size());

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            // ============ 4. 解析响应 ============
            // 响应格式：
            // {
            //   "choices": [{"message": {"role": "assistant", "content": "回复文本"}}]
            // }
            JsonNode responseJson = objectMapper.readTree(response.getBody());
            String content = responseJson
                    .get("choices").get(0)
                    .get("message").get("content")
                    .asText();

            log.debug("LLM response received, length={}", content.length());
            return content;

        } catch (Exception e) {
            log.error("LLM API call failed: {}", e.getMessage());
            return "抱歉，我暂时无法处理您的请求，请稍后再试。";
        }
    }

    /**
     * 简单单轮对话
     *
     * @param userMessage 用户输入
     * @return AI 回复
     */
    public String chat(String userMessage) {
        return chat(List.of(Map.of("role", "user", "content", userMessage)));
    }
}
```

### 知识点

**RestTemplate vs WebClient：**

| 对比项 | RestTemplate | WebClient |
|--------|-------------|-----------|
| 模型 | 同步阻塞 | 异步非阻塞 |
| Spring 版本 | 自 Spring 3 | Spring 5+ Reactive |
| 学习成本 | 低 | 中 |
| 适用场景 | 本项目（简单调用） | 高并发、流式响应 |

本项目选 RestTemplate 是因为：调用 LLM 本身就是 3-10 秒的同步等待，WebFlux 的异步优势在这里体现不出来，用 RestTemplate 代码更简洁。

**ObjectMapper 的 `putArray` / `addObject` 用法：**
- Jackson 的树模型 API 允许动态构建 JSON
- `createObjectNode()` 创建对象节点，`put(key, value)` 设字段
- `putArray(key)` 创建数组，`addObject()` 在数组中添加对象

**为什么异常时返回固定文案而不是抛异常？**
- LLM 调用失败不应该导致整个请求失败（降级设计）
- 向用户展示友好提示，服务端内部通过 log.error 记录详情
- 面试时可以进一步说：实际可以加熔断（Resilience4j）或备用模型切换

---

## 步骤 5：配置 RestTemplate Bean

### 操作

创建 `backend/fashion-server/src/main/java/com/fashion/config/AiConfig.java`：

```java
package com.fashion.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * AI 模块配置
 *
 * 知识点：
 * - RestTemplateBuilder 是 Spring Boot 提供的 Builder，比 new RestTemplate() 更灵活
 * - connectTimeout 和 readTimeout 区分：连接超时 vs 读取超时
 * - 单独的配置类隔离 AI 相关的 Bean，不污染全局配置
 */
@Configuration
public class AiConfig {

    @Bean
    public RestTemplate aiRestTemplate(RestTemplateBuilder builder, AiProperties aiProperties) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(aiProperties.getTimeout()))
                .build();
    }
}
```

### 知识点

**为什么单独创建一个 `aiRestTemplate` Bean 而不是用项目默认的 RestTemplate？**

1. 超时不同：普通接口 5 秒超时，LLM 调用需要 30 秒
2. 职责分离：AI 模块的 HTTP 配置独立变化
3. 避免冲突：不影响项目中已有的 RestTemplate

**`RestTemplateBuilder` 是何时注入的？**
- Spring Boot 自动配置了 `RestTemplateBuilder` Bean
- 注入到配置类中，调用 `build()` 创建 `RestTemplate` 实例

---

## 步骤 6：创建聊天 Controller

### 操作

修改 `AgentController.java`，替换原有空实现：

```java
package com.fashion.controller.user;

import com.fashion.ai.ChatClient;
import com.fashion.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * AI 智能客服接口
 *
 * 用户端对话入口，接收前端聊天请求并返回 LLM 回复。
 * 命名保持 AgentController，与前端 API 路径一致。
 */
@RestController
@RequestMapping("/user/agent")
public class AgentController {

    @Autowired
    private ChatClient chatClient;

    /**
     * 发送聊天消息（单轮，暂不记上下文）
     *
     * @param request 请求体 { message: "你好" }
     * @return { reply: "你好！有什么可以帮助你的？" }
     */
    @PostMapping("/chat")
    public Result<Map<String, String>> chat(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        if (message == null || message.trim().isEmpty()) {
            return Result.error("消息不能为空");
        }

        String reply = chatClient.chat(message.trim());

        Map<String, String> result = new HashMap<>();
        result.put("reply", reply);
        return Result.success(result);
    }
}
```

### 知识点

**请求体为什么用 `Map<String, String>` 而不是 DTO？**

- 这个阶段只有 `message` 一个字段，用 Map 更轻量
- 后续扩展（加入 `sessionId` 等）后，再提取成正式的 DTO 类
- 实际生产中建议用 DTO + `@Valid` 校验，下一阶段会重构

---

## 步骤 7：验证

### 操作

1. 启动项目
2. 用 Swagger 或 curl 测试：

```bash
curl -X POST http://localhost:8080/user/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "你好，帮我推荐一件连衣裙"}'
```

预期返回类似：
```json
{
  "code": 1,
  "data": {
    "reply": "你好！我帮你找找连衣裙，请稍等..."
  },
  "msg": "success"
}
```

### 验收清单

- [ ] 项目启动不报错，`RestTemplate` Bean 注入正常
- [ ] `curl` 调用 `/user/agent/chat` 返回 AI 回复
- [ ] DeepSeek API key 错误时返回友好提示（"暂时无法处理"），控制台打印错误日志
- [ ] 消息为空时返回 `code: 0` 错误提示

---

## 知识点总结

| 概念 | 说明 |
|------|------|
| `@ConfigurationProperties` | 类型安全的配置绑定，比 `@Value` 更规范 |
| `RestTemplate` | Spring 同步 HTTP 客户端，线程安全 |
| `ObjectMapper` 树模型 | 动态构建/解析 JSON，无需 POJO 类 |
| DeepSeek API | 兼容 OpenAI 格式，`POST /chat/completions` |
| 降级策略 | LLM 调用失败返回固定文案，不抛异常到前端 |
| Builder 模式 | `RestTemplateBuilder` 分离构建与使用 |

**下一阶段**：让 AI 能调用电商业务工具，实现搜商品、查订单、操作购物车。
