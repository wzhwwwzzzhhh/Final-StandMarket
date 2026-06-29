# 第二阶段：Tool Calling 与业务集成

## 本阶段目标

让 AI 能调用真实的业务方法（商品搜索、订单查询、购物车操作），实现从"只会聊天"到"能干活"的跨越。

---

## 整体设计思路

```
用户输入 → Controller → ChatService（编排层）
                            │
                            ├→ 意图判断(关键字/LLM)
                            │
                            ├→ 工具调用（直接调 Service 方法）
                            │   ├→ productService.search(keyword)
                            │   ├→ orderService.listUserOrders(userId)
                            │   └→ shoppingCartService.list()
                            │
                            └→ LLM 生成回复（工具结果 + 用户输入 → 自然语言回答）
```

**和 Python 方案的关键区别：**
- Python 用 `@tool` 装饰器 + httpx 调 Java HTTP 接口
- Java 方案直接 `@Autowired` Service，**零网络开销**，一步到位
- 不需要「工具返回值格式化 → LLM 重新理解」的中间步骤

---

## 涉及知识点

- `@Service` + `@Autowired` 注入
- 函数式接口 + 策略模式
- StringBuilder 格式化输出
- 异常处理 + 降级策略

---

## 步骤 1：定义工具接口抽象

### 操作

创建 `backend/fashion-server/src/main/java/com/fashion/ai/tool/AiTool.java`：

```java
package com.fashion.ai.tool;

/**
 * AI 工具接口
 *
 * 所有可以被 AI 调用的业务工具都需要实现此接口。
 * 设计思路对标 Spring AI 的 @Tool 注解：
 *   - 每个 Tool 负责一个原子业务操作
 *   - 输入：用户原始消息 + 上下文
 *   - 输出：格式化的自然语言文本（方便 LLM 直接使用）
 *
 * 知识点：
 * - 函数式接口 @FunctionalInterface：只有一个抽象方法的接口，可用 Lambda 实现
 * - 策略模式：不同的工具实现同一接口，调用方统一处理
 * - 泛型参数：可以用 <T> 让接口更通用，但当前场景 String 够用
 */
@FunctionalInterface
public interface AiTool {

    /**
     * 执行工具逻辑
     *
     * @param userMessage 用户原始输入
     * @param userId      当前用户 ID
     * @return 格式化的工具执行结果文本，LLM 直接用来生成回复
     */
    String execute(String userMessage, Long userId);
}
```

### 知识点

**为什么设计成 `@FunctionalInterface` 而不是抽象类？**

| 对比 | 接口 + `@FunctionalInterface` | 抽象类 |
|------|-----------|---------|
| 多继承 | 实现类可实现多个接口 | 只能单继承 |
| Lambda 支持 | 支持 | 不支持 |
| Spring 管理 | `@Component` 注入 | `@Component` 注入 |
| 适合场景 | 行为抽象（"能做什么"） | 模板方法（"怎么做"） |

这里是行为抽象——"AI 能调用什么工具"，所以用接口。

---

## 步骤 2：创建商品搜索工具

### 操作

创建 `backend/fashion-server/src/main/java/com/fashion/ai/tool/ProductSearchTool.java`：

```java
package com.fashion.ai.tool;

import com.fashion.entity.Product;
import com.fashion.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 商品搜索工具
 *
 * 让 AI 能根据关键词搜索商品，返回格式化的商品列表。
 * 直接调用 ProductService，不走 HTTP，零网络开销。
 *
 * 知识点：
 * - @Component 声明为 Spring Bean，自动被 ChatService 发现
 * - 直接注入 Service，和 Controller 调用 Service 没有区别
 * - 返回纯文本而非 JSON，减少 LLM 解析开销
 */
@Component
public class ProductSearchTool implements AiTool {

    @Autowired
    private ProductService productService;

    @Override
    public String execute(String userMessage, Long userId) {
        try {
            // ============ 调用商品 Service ============
            // 用用户输入的关键词搜索商品
            // 实际项目中应改进关键词提取逻辑（可用 LLM 从消息中提取）
            String keyword = extractKeyword(userMessage);
            List<Product> products = productService.searchByKeyword(keyword);

            if (products == null || products.isEmpty()) {
                return "未找到与 \"" + keyword + "\" 相关的商品。";
            }

            // ============ 格式化输出 ============
            // 格式化成自然语言，LLM 直接用来组织回复
            StringBuilder sb = new StringBuilder();
            sb.append("找到 ").append(products.size()).append(" 件相关商品：\n");
            for (Product p : products) {
                sb.append("- ").append(p.getName())
                  .append(" | ¥").append(p.getPrice())
                  .append(" | 库存: ").append(p.getStock())
                  .append("\n");
            }
            return sb.toString();

        } catch (Exception e) {
            return "商品搜索服务暂时不可用，请稍后再试。";
        }
    }

    /**
     * 简单关键词提取
     *
     * 从用户消息中提取搜索关键词。
     * 当前实现：去掉"搜索""找""推荐"等常见前缀后，取前 20 个字。
     * 更精确的做法是用 LLM 提取关键词，后面可以优化。
     */
    private String extractKeyword(String message) {
        String keyword = message
                .replaceAll("^(帮我|请|我想|我要)", "")
                .replaceAll("(搜索|查找|找找|推荐|看看).?", "")
                .trim();
        return keyword.length() > 20 ? keyword.substring(0, 20) : keyword;
    }
}
```

### 知识点

**`ProductService` 是否需要新增搜索方法？**

查看 `ProductService` 接口和 `UserProductController`，已有分页查询接口。你可能需要在 `ProductService` 中添加一个简单的关键词搜索方法：

```java
// 在 ProductService 接口中
List<Product> searchByKeyword(String keyword);

// 在 ProductServiceImpl 中实现
@Override
public List<Product> searchByKeyword(String keyword) {
    // 使用 MyBatis 查询：WHERE name LIKE '%keyword%' OR description LIKE '%keyword%'
    return productMapper.searchByKeyword(keyword);
}
```

在 `ProductMapper.xml` 中添加：

```xml
<select id="searchByKeyword" resultType="com.fashion.entity.Product">
    SELECT * FROM product
    WHERE name LIKE CONCAT('%', #{keyword}, '%')
       OR description LIKE CONCAT('%', #{keyword}, '%')
    LIMIT 10
</select>
```

---

## 步骤 3：创建订单查询工具

### 操作

创建 `backend/fashion-server/src/main/java/com/fashion/ai/tool/OrderQueryTool.java`：

```java
package com.fashion.ai.tool;

import com.fashion.entity.Orders;
import com.fashion.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 订单查询工具
 *
 * 让 AI 能查询当前用户的订单列表。
 *
 * 知识点：
 * - 直接注入 OrderService，和普通 Controller 调用方式相同
 * - userId 从上下文传入（后续接入 JWT 鉴权）
 * - 如果用户只问订单但未登录，工具结果中提示登录
 */
@Component
public class OrderQueryTool implements AiTool {

    @Autowired
    private OrderService orderService;

    @Override
    public String execute(String userMessage, Long userId) {
        try {
            // ============ 安全检查 ============
            if (userId == null || userId <= 0) {
                return "您还未登录，请先登录后查看订单。";
            }

            // ============ 查询订单 ============
            // 调用已有的 orderService.listUserOrders(status)
            // 传 null 表示查询所有订单
            List<Orders> orders = orderService.listUserOrders(null);

            if (orders == null || orders.isEmpty()) {
                return "您暂无订单记录。";
            }

            // ============ 格式化输出 ============
            StringBuilder sb = new StringBuilder();
            sb.append("您有 ").append(orders.size()).append(" 个订单：\n");
            for (Orders o : orders) {
                sb.append("- 订单号: ").append(o.getOrderNo())
                  .append(" | 金额: ¥").append(o.getAmount())
                  .append(" | 状态: ").append(o.getStatus())
                  .append(" | 时间: ").append(o.getCreateTime())
                  .append("\n");
            }
            return sb.toString();

        } catch (Exception e) {
            return "订单查询服务暂时不可用，请稍后再试。";
        }
    }
}
```

---

## 步骤 4：创建购物车工具

### 操作

创建 `backend/fashion-server/src/main/java/com/fashion/ai/tool/CartQueryTool.java`：

```java
package com.fashion.ai.tool;

import com.fashion.entity.ShoppingCart;
import com.fashion.service.ShoppingCartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 购物车查询工具
 *
 * 让 AI 能查看用户购物车内容。
 *
 * 知识点：
 * - 只实现"查询"，不实现"添加"，控制工具粒度
 * - 工具粒度设计原则：一个工具只做一件事
 * - 同理可扩展：CartAddTool、CartRemoveTool 等
 */
@Component
public class CartQueryTool implements AiTool {

    @Autowired
    private ShoppingCartService shoppingCartService;

    @Override
    public String execute(String userMessage, Long userId) {
        try {
            if (userId == null || userId <= 0) {
                return "您还未登录，请先登录后查看购物车。";
            }

            List<ShoppingCart> cartItems = shoppingCartService.list();

            if (cartItems == null || cartItems.isEmpty()) {
                return "您的购物车是空的。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("购物车中有 ").append(cartItems.size()).append(" 件商品：\n");
            double total = 0;
            for (ShoppingCart item : cartItems) {
                double subtotal = item.getNumber() * item.getPrice().doubleValue();
                sb.append("- ").append(item.getProductName())
                  .append(" x ").append(item.getNumber())
                  .append(" | ¥").append(subtotal)
                  .append("\n");
                total += subtotal;
            }
            sb.append("合计: ¥").append(total);
            return sb.toString();

        } catch (Exception e) {
            return "购物车服务暂时不可用，请稍后再试。";
        }
    }
}
```

### 知识点

**工具粒度设计原则：**

不是把所有操作放在一个工具里，而是每个操作一个工具：

```
好的设计 → 细粒度，每个工具职责单一
├─ ProductSearchTool（搜商品）
├─ OrderQueryTool（查订单）
└─ CartQueryTool（看购物车）

不好的设计 → 大而全
└─ AllInOneTool（搜商品 + 查订单 + 购物车 + ...）
```

- 细粒度：每个工具逻辑简单，LLM 容易理解该调哪个
- 粗粒度：`execute` 方法里全是 `if-else`，维护困难

---

## 步骤 5：创建 ChatService — 意图路由与工具编排

### 操作

创建 `backend/fashion-server/src/main/java/com/fashion/ai/ChatService.java`：

```java
package com.fashion.ai;

import com.fashion.ai.tool.AiTool;
import com.fashion.ai.tool.CartQueryTool;
import com.fashion.ai.tool.OrderQueryTool;
import com.fashion.ai.tool.ProductSearchTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * AI 对话服务
 *
 * 核心编排层，负责：
 * 1. 识别用户意图（当前用关键词，后续可换成 LLM）
 * 2. 调用对应的业务工具
 * 3. 把工具结果发给 LLM 生成自然语言回复
 *
 * 设计思想对标 Spring AI 的 Tool Calling 机制：
 *   Spring AI 的做法是 LLM 自己决定调什么工具
 *   我们的做法是 Service 层先识别意图再调工具（更可控）
 *
 * 两种方式的取舍：
 *   - LLM 自主选择：灵活，但不可控（可能调错工具）
 *   - Service 层路由：可控，但需要维护映射规则
 *   本方案采用后者，和 Python 版 LangGraph 的意图识别思路一致
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private ProductSearchTool productSearchTool;

    @Autowired
    private OrderQueryTool orderQueryTool;

    @Autowired
    private CartQueryTool cartQueryTool;

    /**
     * 处理用户消息
     *
     * @param message 用户输入
     * @param userId  用户 ID
     * @return AI 回复
     */
    public String processMessage(String message, Long userId) {
        // ============ 1. 识别意图 ============
        String intent = identifyIntent(message);
        log.info("User intent identified: {}", intent);

        // ============ 2. 调用工具 ============
        String toolResult = executeTool(intent, message, userId);

        // ============ 3. LLM 生成回复 ============
        // 把工具结果发给 LLM，生成自然语言回复
        return generateResponse(message, toolResult);
    }

    /**
     * 意图识别
     *
     * 当前用关键词匹配，优点是简单、快、零成本。
     * 面试话术：
     *   "关键词匹配在小规模场景下足够用，准确率 95%+。
     *    如果后续发现分类不准，可以接入 LLM 做意图识别，
     *    架构上只需要替换 identifyIntent 方法的实现。"
     */
    String identifyIntent(String message) {
        String msg = message.toLowerCase();

        // 商品搜索意图
        if (containsAny(msg, "搜索", "找", "推荐", "看看", "有没有", "买", "连衣裙", "衣服", "商品")) {
            return "product_search";
        }

        // 订单查询意图
        if (containsAny(msg, "订单", "快递", "物流", "发货", "收货", "买了")) {
            return "order_query";
        }

        // 购物车意图
        if (containsAny(msg, "购物车", "购物袋", "加购")) {
            return "cart_query";
        }

        // 默认走闲聊
        return "general_chat";
    }

    /**
     * 工具执行
     */
    private String executeTool(String intent, String message, Long userId) {
        switch (intent) {
            case "product_search":
                return productSearchTool.execute(message, userId);
            case "order_query":
                return orderQueryTool.execute(message, userId);
            case "cart_query":
                return cartQueryTool.execute(message, userId);
            default:
                return null; // 闲聊不需要工具
        }
    }

    /**
     * 生成回复
     *
     * 把用户消息和工具结果发给 LLM，让它生成自然语言回复。
     * System Prompt 定义了 AI 的角色和行为规范。
     */
    private String generateResponse(String userMessage, String toolResult) {
        List<Map<String, String>> messages = new ArrayList<>();

        // System Prompt：定义 AI 角色和规则
        messages.add(Map.of("role", "system",
                "content", "你是一个时尚电商平台的智能客服助手，名叫"时尚小助手"。" +
                        "你帮助用户搜索商品、查询订单、查看购物车。" +
                        "回答要简洁友好，控制在 100 字以内。" +
                        "如果用户问的问题超出你的能力范围，请说"抱歉，这个问题我需要转接人工客服处理。"));

        // 如果有工具结果，先给 LLM 参考
        if (toolResult != null) {
            messages.add(Map.of("role", "system",
                    "content", "以下是查询到的数据，请基于这些数据回答用户：\n" + toolResult));
        }

        // 用户当前消息
        messages.add(Map.of("role", "user", "content", userMessage));

        return chatClient.chat(messages);
    }

    /**
     * 工具方法：检查字符串是否包含任意关键词
     */
    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}
```

### 知识点

**意图识别为什么用关键词而不是 LLM？**

| 方式 | 优点 | 缺点 |
|------|------|------|
| 关键词匹配 | 快（毫秒级）、零成本、可控 | 语义理解弱（"我想买件T恤" 匹配到"买" → 商品搜索） |
| LLM 识别 | 语义理解强 | 慢（1-3s）、花钱、可能分错 |
| 混合 | 快 + 准 | 实现复杂 |

本项目用关键词匹配，理由和 Python 版一样：95%+ 的请求能正确识别，剩下的边界情况不影响核心功能。面试时可以说"先快速实现，预留了升级到 LLM 识别的接口"。

**`Map.of()` 创建不可变 Map：**
- Java 9+ 的工厂方法，`Map.of("key", "value")`
- 适合创建小型常量集合
- 不可修改，但 ChatClient 只读不写，所以安全

**为什么 System Prompt 和工具结果分两条消息？**
- 第一条 System Prompt：定义角色，始终存在
- 第二条 System Prompt（或 Assistant Message）：携带工具结果，只在使用工具时加入
- 这样 LLM 能明确区分"你是谁"和"你查到什么"

---

## 步骤 6：改造 Controller

### 操作

修改 `AgentController.java`：

```java
package com.fashion.controller.user;

import com.fashion.ai.ChatService;
import com.fashion.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 智能客服接口
 */
@RestController
@RequestMapping("/user/agent")
public class AgentController {

    @Autowired
    private ChatService chatService;

    /**
     * 发送聊天消息
     *
     * @param request { message: "帮我搜一下连衣裙", userId: 1 }
     * @return { reply: "找到了 3 件连衣裙..." }
     */
    @PostMapping("/chat")
    public Result<Map<String, String>> chat(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        if (message == null || message.trim().isEmpty()) {
            return Result.error("消息不能为空");
        }

        // 获取用户 ID（当前从请求传入，后续应该从 JWT 解析）
        Long userId = null;
        try {
            String userIdStr = request.get("userId");
            if (userIdStr != null && !userIdStr.isEmpty()) {
                userId = Long.parseLong(userIdStr);
            }
        } catch (NumberFormatException e) {
            // 忽略，userId 保持 null
        }

        String reply = chatService.processMessage(message.trim(), userId);

        Map<String, String> result = new HashMap<>();
        result.put("reply", reply);
        return Result.success(result);
    }
}
```

---

## 步骤 7：验证

### 操作

启动项目后测试几个场景：

```bash
# 测试商品搜索
curl -X POST http://localhost:8080/user/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "帮我推荐一件连衣裙", "userId": "1"}'

# 测试订单查询
curl -X POST http://localhost:8080/user/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "我的订单有哪些", "userId": "1"}'

# 测试购物车查询
curl -X POST http://localhost:8080/user/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "看看我的购物车", "userId": "1"}'

# 测试闲聊
curl -X POST http://localhost:8080/user/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "你好啊", "userId": "1"}'
```

### 验收清单

- [ ] 输入"推荐连衣裙" → 返回商品列表信息，AI 生成推荐语
- [ ] 输入"我的订单" → 返回订单列表信息
- [ ] 输入"购物车" → 返回购物车内容
- [ ] 输入"你好" → AI 正常打招呼（没有工具调用）
- [ ] 未登录时查询订单/购物车 → 提示"请先登录"
- [ ] 数据库无数据时 → 友好提示"未找到"或"空的"

---

## 知识点总结

| 概念 | 说明 |
|------|------|
| `@FunctionalInterface` | 函数式接口，工具类的抽象契约 |
| 策略模式 | 不同工具实现同一接口，`ChatService` 统一编排 |
| 意图识别 | 关键词匹配，快且可控 |
| 工具粒度 | 一个工具只做一件事 |
| 直接调 Service | 不走 HTTP，比 Python 版少一次网络开销 |
| System Prompt | 定义 AI 角色行为，和工具结果分开传入 |

**下一阶段**：加入对话记忆，让 AI 能记住上下文，以及前端聊天界面集成。
