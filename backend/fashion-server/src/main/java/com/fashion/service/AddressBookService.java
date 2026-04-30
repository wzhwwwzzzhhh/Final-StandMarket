package com.fashion.service;

import com.fashion.entity.AddressBook;
import java.util.List;

/**
 * 地址簿服务
 */
public interface AddressBookService {

    /**
     * 添加地址
     * @param addressBook 地址信息
     */
    void add(AddressBook addressBook);

    /**
     * 删除地址
     * @param id 地址ID
     */
    void delete(Long id);

    /**
     * 更新地址
     * @param addressBook 地址信息
     */
    void update(AddressBook addressBook);

    /**
     * 根据ID查询地址
     * @param id 地址ID
     * @return 地址信息
     */
    AddressBook getById(Long id);

    /**
     * 查询用户的地址列表
     * @return 地址列表
     */
    List<AddressBook> list();

    /**
     * 查询用户的默认地址
     * @return 默认地址
     */
    AddressBook getDefault();

    /**
     * 设置默认地址
     * @param id 地址ID
     */
    void setDefault(Long id);

}