package com.fashion.mapper;

import com.fashion.entity.Refund;
import com.fashion.service.impl.RefundServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("B3 退款 Mapper 与依赖边界")
class RefundStateMapperContractTest {

    @Test
    @DisplayName("退款同意和拒绝使用固定目标状态的专用 CAS")
    void refundTransitionsUseDedicatedCasStatements() throws Exception {
        String xml = normalized("src/main/resources/mapper/RefundMapper.xml");

        String approve = statement(xml, "approvepending");
        assertTrue(approve.contains("status = 1"));
        assertTrue(approve.contains("where id = #{id} and status = 0"));
        assertFalse(approve.contains("refund_time"));

        String reject = statement(xml, "rejectpending");
        assertTrue(reject.contains("status = 3"));
        assertTrue(reject.contains("where id = #{id} and status = 0"));
        assertFalse(reject.contains("refund_time"));

        assertFalse(xml.contains("<update id=\"update\""));
    }

    @Test
    @DisplayName("退款申请和拒绝恢复使用带归属与合法前态的订单 CAS")
    void orderTransitionsUseDedicatedCasStatements() throws Exception {
        String xml = normalized("src/main/resources/mapper/OrderMapper.xml");

        String apply = statement(xml, "markrefunding");
        assertTrue(apply.contains("status = 6"));
        assertTrue(apply.contains("user_id = #{userid}"));
        assertTrue(apply.contains("status = #{expectedstatus}"));

        String restore = statement(xml, "restorerejectedrefundorder");
        assertTrue(restore.contains("status = #{targetstatus}"));
        assertTrue(restore.contains("status = 6"));
        assertTrue(restore.contains("#{targetstatus} in (3,4)"));
    }

    @Test
    @DisplayName("退款状态常量固定且服务不再依赖库存明细 Mapper")
    void constantsAndDependencyBoundaryAreExplicit() throws Exception {
        assertEquals(0, constant("STATUS_PENDING"));
        assertEquals(1, constant("STATUS_WAITING_EXTERNAL_REFUND"));
        assertEquals(2, constant("STATUS_COMPLETED"));
        assertEquals(3, constant("STATUS_REJECTED"));

        assertFalse(hasField(RefundServiceImpl.class, "orderDetailMapper"));
        assertFalse(hasField(RefundServiceImpl.class, "productMapper"));

        String contract = normalized("src/main/java/com/fashion/service/RefundService.java");
        assertTrue(contract.contains("等待外部退款处理"));
        assertFalse(contract.contains("恢复库存"));
    }

    private int constant(String name) throws Exception {
        Field field = ReflectionUtils.findField(Refund.class, name);
        assertNotNull(field, "missing refund status constant " + name);
        ReflectionUtils.makeAccessible(field);
        return field.getInt(null);
    }

    private boolean hasField(Class<?> type, String name) {
        for (Field field : type.getDeclaredFields()) {
            if (field.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private String normalized(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ")
                .toLowerCase();
    }

    private String statement(String xml, String id) {
        int start = xml.indexOf("<update id=\"" + id + "\"");
        assertTrue(start >= 0, "missing update " + id);
        int end = xml.indexOf("</update>", start);
        assertTrue(end > start, "unterminated update " + id);
        String value = xml.substring(start, end);
        assertNotNull(value);
        return value;
    }
}
