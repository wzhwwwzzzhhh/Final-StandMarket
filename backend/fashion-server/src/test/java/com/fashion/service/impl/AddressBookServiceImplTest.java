package com.fashion.service.impl;

import com.fashion.context.BaseContext;
import com.fashion.entity.AddressBook;
import com.fashion.mapper.AddressBookMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B4 地址服务资源归属")
class AddressBookServiceImplTest {

    private AddressBookServiceImpl service;
    private AddressBookMapper mapper;

    @BeforeEach
    void setUp() {
        BaseContext.removeUserId();
        mapper = mock(AddressBookMapper.class);
        service = new AddressBookServiceImpl();
        ReflectionTestUtils.setField(service, "addressBookMapper", mapper);
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeUserId();
    }

    @Test
    @DisplayName("未登录不回退到用户1且不访问 Mapper")
    void anonymousNeverFallsBackToUserOne() {
        assertThrows(IllegalStateException.class, () -> service.list());
        assertThrows(IllegalStateException.class, () -> service.getById(9L));
        assertThrows(IllegalStateException.class, () -> service.delete(9L));

        verify(mapper, never()).listByUserId(any());
        verify(mapper, never()).getByIdAndUserId(any(), any());
        verify(mapper, never()).deleteByIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("新增地址强制覆盖当前用户且严格检查插入行数")
    void addUsesCurrentUserAndChecksAffectedRow() {
        BaseContext.setUserId(7L);
        AddressBook address = new AddressBook();
        address.setUserId(99L);
        when(mapper.insert(address)).thenReturn(1);

        service.add(address);

        assertEquals(7L, address.getUserId());
        verify(mapper).insert(address);

        when(mapper.insert(any(AddressBook.class))).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> service.add(new AddressBook()));
    }

    @Test
    @DisplayName("详情列表和默认地址只查询当前用户")
    void readsAreOwnerScoped() {
        BaseContext.setUserId(7L);
        AddressBook owned = new AddressBook();
        owned.setId(9L);
        when(mapper.getByIdAndUserId(9L, 7L)).thenReturn(owned);
        when(mapper.listByUserId(7L)).thenReturn(Collections.singletonList(owned));
        when(mapper.getDefaultByUserId(7L)).thenReturn(owned);

        assertSame(owned, service.getById(9L));
        assertEquals(Collections.singletonList(owned), service.list());
        assertSame(owned, service.getDefault());
    }

    @Test
    @DisplayName("越权或不存在地址不能更新删除")
    void missingOwnershipRejectsMutation() {
        BaseContext.setUserId(7L);
        AddressBook update = new AddressBook();
        update.setId(9L);

        assertThrows(IllegalStateException.class, () -> service.update(update));
        assertThrows(IllegalStateException.class, () -> service.delete(9L));

        verify(mapper, never()).updateByIdAndUserId(any(), any());
        verify(mapper).deleteByIdAndUserId(9L, 7L);
    }

    @Test
    @DisplayName("设置默认先验证目标归属再重置并按归属更新")
    void setDefaultValidatesTargetBeforeReset() {
        BaseContext.setUserId(7L);
        AddressBook owned = new AddressBook();
        owned.setId(9L);
        when(mapper.getByIdAndUserId(9L, 7L)).thenReturn(owned);
        when(mapper.updateByIdAndUserId(any(AddressBook.class), org.mockito.ArgumentMatchers.eq(7L)))
                .thenReturn(1);

        service.setDefault(9L);

        org.mockito.InOrder order = inOrder(mapper);
        order.verify(mapper).getByIdAndUserId(9L, 7L);
        order.verify(mapper).resetDefaultByUserId(7L);
        order.verify(mapper).updateByIdAndUserId(any(AddressBook.class), org.mockito.ArgumentMatchers.eq(7L));
    }

    @Test
    @DisplayName("默认地址目标不属于当前用户时不重置任何地址")
    void setDefaultDoesNotResetBeforeOwnershipCheck() {
        BaseContext.setUserId(7L);
        when(mapper.getByIdAndUserId(9L, 7L)).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> service.setDefault(9L));

        verify(mapper, never()).resetDefaultByUserId(any());
        verify(mapper, never()).updateByIdAndUserId(any(), any());
    }
}
