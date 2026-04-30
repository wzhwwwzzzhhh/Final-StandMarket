package com.fashion.mapper;

import com.fashion.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户Mapper
 */
@Mapper
public interface UserMapper {
    /**
     * 新增用户
     * @param user 用户信息
     */
    void insert(User user);

    /**
     * 根据id删除用户
     * @param id 用户id
     */
    void deleteById(Long id);

    /**
     * 更新用户信息
     * @param user 用户信息
     */
    void update(User user);

    /**
     * 根据id查询用户
     * @param id 用户id
     * @return 用户信息
     */
    User selectById(Long id);

    /**
     * 分页查询用户
     * @param page 页码
     * @param pageSize 每页大小
     * @param name 用户名
     * @param phone 手机号
     * @return 用户列表
     */
    List<User> list(@Param("page") int page, @Param("pageSize") int pageSize, @Param("name") String name, @Param("phone") String phone);

    /**
     * 查询用户总数
     * @param name 用户名
     * @param phone 手机号
     * @return 总数
     */
    int count(@Param("name") String name, @Param("phone") String phone);

    /**
     * 根据手机号查询用户
     * @param phone
     * @return
     */
    User selectByPhone(String phone);
}