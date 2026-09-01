package com.fashion.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("B4 交易资源 Mapper 归属合约")
class ResourceOwnershipMapperContractTest {

    @Test
    @DisplayName("普通订单和支付流水查询在 SQL 中绑定当前用户")
    void ordinaryOrderAndPaymentQueriesAreOwnerScoped() throws Exception {
        String orderXml = normalized(read("src/main/resources/mapper/OrderMapper.xml"));
        String paymentXml = normalized(read("src/main/resources/mapper/PaymentMapper.xml"));

        assertTrue(statement(orderXml, "getByIdAndUserId")
                .contains("where id = #{id} and user_id = #{userId}"));

        String payment = statement(paymentXml, "getByPayNoAndUserId").toLowerCase();
        assertTrue(payment.contains("from payment p"));
        assertTrue(payment.contains("join orders o on o.id = p.order_id"));
        assertTrue(payment.contains("p.pay_no = #{payno}"));
        assertTrue(payment.contains("p.order_type = 0"));
        assertTrue(payment.contains("o.user_id = #{userid}"));
    }

    @Test
    @DisplayName("评价检查和秒杀详情取消都绑定当前用户")
    void reviewAndSeckillQueriesAreOwnerScoped() throws Exception {
        String reviewXml = normalized(read("src/main/resources/mapper/ReviewMapper.xml"));
        String seckillXml = normalized(read("src/main/resources/mapper/SeckillOrderMapper.xml"));

        assertTrue(statement(reviewXml, "selectByOrderIdAndUserId")
                .contains("where order_id = #{orderId} and user_id = #{userId}"));
        assertTrue(statement(seckillXml, "selectByOrderNumberAndUserId")
                .contains("where order_number = #{orderNumber} and user_id = #{userId}"));
        String cancel = statement(seckillXml, "cancelPendingByOrderNumberAndUserId");
        assertTrue(cancel.contains("where order_number = #{orderNumber}"));
        assertTrue(cancel.contains("and user_id = #{userId}"));
        assertTrue(cancel.contains("and status = 1"));
    }

    @Test
    @DisplayName("用户订单支付和 AI Controller 不调用可信内部裸订单读取")
    void userControllersDoNotUseTrustedOrderLookup() throws Exception {
        String userOrder = read("src/main/java/com/fashion/controller/user/UserOrderController.java");
        String payment = read("src/main/java/com/fashion/controller/user/PaymentController.java");
        String agent = read("src/main/java/com/fashion/controller/user/AgentController.java");

        assertFalse(userOrder.contains("orderService.getById("));
        assertFalse(payment.contains("orderService.getById("));
        assertFalse(agent.contains("orderService.getById("));
        assertFalse(payment.contains("paymentService.getPaymentStatus(outTradeNo)"));
    }

    @Test
    @DisplayName("评价 Mapper 不再暴露绕过 user_id 的订单评价读取")
    void reviewMapperDoesNotExposeUnscopedOrderLookup() throws Exception {
        String source = read("src/main/java/com/fashion/mapper/ReviewMapper.java");
        String xml = read("src/main/resources/mapper/ReviewMapper.xml");

        assertFalse(source.contains("Review selectByOrderId(@Param"));
        assertFalse(xml.contains("id=\"selectByOrderId\""));
    }

    private static String statement(String xml, String id) {
        int start = xml.indexOf("id=\"" + id + "\"");
        assertTrue(start >= 0, "missing mapper statement: " + id);
        int tagStart = xml.lastIndexOf('<', start);
        int tagEnd = xml.indexOf(' ', tagStart);
        String tagName = xml.substring(tagStart + 1, tagEnd);
        int bodyStart = xml.indexOf('>', start);
        int close = xml.indexOf("</" + tagName + ">", bodyStart);
        return xml.substring(bodyStart + 1, close).trim();
    }

    private static String read(String relativePath) throws Exception {
        Path path = Paths.get(System.getProperty("user.dir"), relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String normalized(String value) {
        return value.replace('`', ' ').replaceAll("\\s+", " ").trim();
    }
}
