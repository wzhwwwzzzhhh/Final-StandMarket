package com.fashion.service.impl;

import com.fashion.context.BaseContext;
import com.fashion.entity.AddressBook;
import com.fashion.mapper.AddressBookMapper;
import com.fashion.service.AddressBookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 地址簿服务实现
 */
@Service
public class AddressBookServiceImpl implements AddressBookService {

    @Autowired
    private AddressBookMapper addressBookMapper;

    /**
     * 添加地址
     */
    @Transactional
    @Override
    public void add(AddressBook addressBook) {
        if (addressBook == null) {
            throw new IllegalArgumentException("地址不能为空");
        }
        Long userId = requireCurrentUserId();
        addressBook.setUserId(userId);

        if (addressBook.getIsDefault() != null && addressBook.getIsDefault() == 1) {
            addressBookMapper.resetDefaultByUserId(userId);
        }

        if (addressBookMapper.insert(addressBook) != 1) {
            throw new IllegalStateException("地址添加失败");
        }
    }

    /**
     * 删除地址
     */
    @Override
    public void delete(Long id) {
        Long userId = requireCurrentUserId();
        if (id == null || addressBookMapper.deleteByIdAndUserId(id, userId) != 1) {
            throw new IllegalStateException("地址不存在");
        }
    }

    /**
     * 更新地址
     */
    @Transactional
    @Override
    public void update(AddressBook addressBook) {
        Long userId = requireCurrentUserId();
        if (addressBook == null || addressBook.getId() == null
                || addressBookMapper.getByIdAndUserId(addressBook.getId(), userId) == null) {
            throw new IllegalStateException("地址不存在");
        }

        if (addressBook.getIsDefault() != null && addressBook.getIsDefault() == 1) {
            addressBookMapper.resetDefaultByUserId(userId);
        }

        if (addressBookMapper.updateByIdAndUserId(addressBook, userId) != 1) {
            throw new IllegalStateException("地址不存在");
        }
    }

    /**
     * 根据ID查询地址
     */
    @Override
    public AddressBook getById(Long id) {
        Long userId = requireCurrentUserId();
        return id == null ? null : addressBookMapper.getByIdAndUserId(id, userId);
    }

    /**
     * 查询用户的地址列表
     */
    @Override
    public List<AddressBook> list() {
        return addressBookMapper.listByUserId(requireCurrentUserId());
    }

    /**
     * 查询用户的默认地址
     */
    @Override
    public AddressBook getDefault() {
        return addressBookMapper.getDefaultByUserId(requireCurrentUserId());
    }

    /**
     * 设置默认地址
     */
    @Transactional
    @Override
    public void setDefault(Long id) {
        Long userId = requireCurrentUserId();
        if (id == null || addressBookMapper.getByIdAndUserId(id, userId) == null) {
            throw new IllegalStateException("地址不存在");
        }

        addressBookMapper.resetDefaultByUserId(userId);

        AddressBook addressBook = new AddressBook();
        addressBook.setId(id);
        addressBook.setIsDefault(1);
        if (addressBookMapper.updateByIdAndUserId(addressBook, userId) != 1) {
            throw new IllegalStateException("地址不存在");
        }
    }

    private Long requireCurrentUserId() {
        Long userId = BaseContext.getUserId();
        if (userId == null) {
            throw new IllegalStateException("请先登录");
        }
        return userId;
    }
}
