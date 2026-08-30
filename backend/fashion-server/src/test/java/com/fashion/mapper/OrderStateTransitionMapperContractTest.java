package com.fashion.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("B2 订单履约状态 CAS 合约")
class OrderStateTransitionMapperContractTest {

    @Test
    @DisplayName("发货和确认收货必须使用带合法前态的专用 CAS")
    void deliveryAndConfirmationUseDedicatedCas() throws Exception {
        String xml = new String(Files.readAllBytes(Paths.get(
                "src/main/resources/mapper/OrderMapper.xml")), StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ").toLowerCase();

        String deliver = statement(xml, "deliverpaidorder");
        assertTrue(deliver.contains("status = 3"));
        assertTrue(deliver.contains("where id = #{id}"));
        assertTrue(deliver.contains("status = 2"));
        assertTrue(deliver.contains("pay_status = 1"));
        assertTrue(deliver.contains("stock_deducted = 1"));

        String confirm = statement(xml, "confirmdeliveredorder");
        assertTrue(confirm.contains("status = 4"));
        assertTrue(confirm.contains("user_id = #{userid}"));
        assertTrue(confirm.contains("status = 3"));
        assertTrue(confirm.contains("pay_status = 1"));
        assertTrue(confirm.contains("stock_deducted = 1"));
    }

    private String statement(String xml, String id) {
        int start = xml.indexOf("<update id=\"" + id + "\"");
        assertTrue(start >= 0);
        return xml.substring(start, xml.indexOf("</update>", start));
    }
}
