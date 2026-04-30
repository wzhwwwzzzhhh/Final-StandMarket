package com.fashion.service.impl;

import com.fashion.context.BaseContext;
import com.fashion.entity.AddressBook;
import com.fashion.mapper.AddressBookMapper;
import com.fashion.service.AddressBookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
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
    @Override
    public void add(AddressBook addressBook) {
        System.out.println("添加地址，接收到的参数：" + addressBook);
        // 获取当前用户ID
        Long userId = BaseContext.getUserId() != null ? BaseContext.getUserId() : 1L;
        addressBook.setUserId(userId);
        System.out.println("设置userId后的地址：" + addressBook);

        // 如果是默认地址，先将用户的所有地址设置为非默认
        if (addressBook.getIsDefault() != null && addressBook.getIsDefault() == 1) {
            addressBookMapper.resetDefaultByUserId(userId);
        }

        addressBookMapper.insert(addressBook);
        System.out.println("地址添加成功");
    }

    /**
     * 删除地址
     */
    @Override
    public void delete(Long id) {
        addressBookMapper.deleteById(id);
    }

    /**
     * 更新地址
     */
    @Override
    public void update(AddressBook addressBook) {
        // 获取当前用户ID
        Long userId = BaseContext.getUserId() != null ? BaseContext.getUserId() : 1L;

        // 如果是默认地址，先将用户的所有地址设置为非默认
        if (addressBook.getIsDefault() != null && addressBook.getIsDefault() == 1) {
            addressBookMapper.resetDefaultByUserId(userId);
        }

        addressBookMapper.update(addressBook);
    }

    /**
     * 根据ID查询地址
     */
    @Override
    public AddressBook getById(Long id) {
        return addressBookMapper.getById(id);
    }

    /**
     * 查询用户的地址列表
     */
    @Override
    public List<AddressBook> list() {
        // 获取当前用户ID
        Long userId = BaseContext.getUserId() != null ? BaseContext.getUserId() : 1L;
        return addressBookMapper.listByUserId(userId);
    }

    /**
     * 查询用户的默认地址
     */
    @Override
    public AddressBook getDefault() {
        // 获取当前用户ID
        Long userId = BaseContext.getUserId() != null ? BaseContext.getUserId() : 1L;
        return addressBookMapper.getDefaultByUserId(userId);
    }

    /**
     * 设置默认地址
     */
    @Override
    public void setDefault(Long id) {
        // 获取当前用户ID
        Long userId = BaseContext.getUserId() != null ? BaseContext.getUserId() : 1L;

        // 先将用户的所有地址设置为非默认
        addressBookMapper.resetDefaultByUserId(userId);

        // 将指定地址设置为默认
        AddressBook addressBook = new AddressBook();
        addressBook.setId(id);
        addressBook.setIsDefault(1);
        addressBookMapper.update(addressBook);
    }
}