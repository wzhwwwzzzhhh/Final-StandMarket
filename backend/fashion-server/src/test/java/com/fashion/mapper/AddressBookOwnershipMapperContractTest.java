package com.fashion.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("B4 地址 Mapper 资源归属合约")
class AddressBookOwnershipMapperContractTest {

    @Test
    @DisplayName("用户地址详情更新删除都在 SQL 中同时匹配 id 和 user_id")
    void userMutationsCarryOwnershipPredicate() throws Exception {
        String xml = normalized(read("src/main/resources/mapper/AddressBookMapper.xml"));

        assertTrue(statement(xml, "getByIdAndUserId").contains("where id = #{id} and user_id = #{userId}"));
        assertTrue(statement(xml, "deleteByIdAndUserId").contains("where id = #{id} and user_id = #{userId}"));
        assertTrue(statement(xml, "updateByIdAndUserId").contains("where id = #{addressBook.id} and user_id = #{userId}"));
    }

    @Test
    @DisplayName("地址写入接口返回影响行数以便严格检查")
    void mapperWritesExposeAffectedRows() throws Exception {
        String source = normalized(read("src/main/java/com/fashion/mapper/AddressBookMapper.java"));

        assertTrue(source.contains("int insert(AddressBook addressBook)"));
        assertTrue(source.contains("int deleteByIdAndUserId("));
        assertTrue(source.contains("int updateByIdAndUserId("));
        assertTrue(source.contains("int resetDefaultByUserId(Long userId)"));
    }

    @Test
    @DisplayName("地址 Mapper 不再暴露绕过 user_id 的裸读写入口")
    void mapperDoesNotExposeUnscopedAddressAccess() throws Exception {
        String source = normalized(read("src/main/java/com/fashion/mapper/AddressBookMapper.java"));
        String xml = normalized(read("src/main/resources/mapper/AddressBookMapper.xml"));

        assertFalse(source.contains("deleteById(Long id)"));
        assertFalse(source.contains("void update(AddressBook addressBook)"));
        assertFalse(source.contains("AddressBook getById(Long id)"));
        assertFalse(xml.contains("id=\"deleteById\""));
        assertFalse(xml.contains("id=\"update\""));
        assertFalse(xml.contains("id=\"getById\""));
    }

    private static String statement(String xml, String id) {
        int start = xml.indexOf("id=\"" + id + "\"");
        assertTrue(start >= 0, "missing mapper statement: " + id);
        int tagStart = xml.lastIndexOf('<', start);
        int tagEnd = xml.indexOf(' ', tagStart);
        String tagName = xml.substring(tagStart + 1, tagEnd);
        int end = xml.indexOf('>', start);
        int close = xml.indexOf("</" + tagName + ">", end);
        return xml.substring(end + 1, close).trim();
    }

    private static String read(String relativePath) throws Exception {
        Path path = Paths.get(System.getProperty("user.dir"), relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String normalized(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
