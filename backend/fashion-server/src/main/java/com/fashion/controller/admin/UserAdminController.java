package com.fashion.controller.admin;

import com.fashion.entity.User;
import com.fashion.entity.PageResult;
import com.fashion.result.Result;
import com.fashion.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理
 */
@RestController
@RequestMapping("/admin/user")
public class UserAdminController {

    @Autowired
    private UserService userService;

    /**
     * 新增用户
     */
    @PostMapping
    public Result<String> save(@RequestBody User user) {
        userService.save(user);
        return Result.success();
    }

    /**
     * 分页查询
     */
    @GetMapping("/page")
    public Result<PageResult<User>> page(int page, int pageSize, String name, String phone) {
        // 调用Service层的分页查询方法
        PageResult<User> pageResult = userService.pageUsers(page, pageSize, name, phone);
        return Result.success(pageResult);
    }

    /**
     * 删除用户
     */
    @DeleteMapping
    public Result<String> delete(@RequestParam Long id) {
        if(id == null){
            return Result.error("id不能为空");
        }
        userService.delete(id);
        return Result.success();
    }

    /**
     * 修改用户
     */
    @PutMapping
    public Result<String> update(@RequestBody User user) {
        if(user.getId() == null){
            return Result.error("id不能为空");
        }
        userService.update(user);
        return Result.success();
    }

    /**
     * 根据id查询用户
     */
    @GetMapping("/getById")
    public Result<User> getById(@RequestParam Long id) {
        if(id == null){
            return Result.error("id不能为空");
        }
        User user = userService.getById(id);
        if(user == null){
            return Result.error("用户不存在");
        }
        return Result.success(user);
    }
}