package com.fashion.mapper;

import com.fashion.entity.AddressBook;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
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
    int insert(AddressBook addressBook);

    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    int updateByIdAndUserId(@Param("addressBook") AddressBook addressBook,
                            @Param("userId") Long userId);

    AddressBook getByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

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
    int resetDefaultByUserId(Long userId);
}
