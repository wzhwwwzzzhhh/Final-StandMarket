package com.fashion.mapper;

import com.fashion.entity.AddressBook;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/**
 * 地址簿Mapper
 */
@Mapper
public interface AddressBookMapper {

    /**
     * 插入地址
     * @param addressBook 地址信息
     */
    void insert(AddressBook addressBook);

    /**
     * 根据ID删除地址
     * @param id 地址ID
     */
    void deleteById(Long id);

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
     * @param userId 用户ID
     * @return 地址列表
     */
    List<AddressBook> listByUserId(Long userId);

    /**
     * 查询用户的默认地址
     * @param userId 用户ID
     * @return 默认地址
     */
    AddressBook getDefaultByUserId(Long userId);

    /**
     * 将用户的所有地址设置为非默认
     * @param userId 用户ID
     */
    void resetDefaultByUserId(Long userId);
}