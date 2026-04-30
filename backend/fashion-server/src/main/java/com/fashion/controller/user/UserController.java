package com.fashion.controller.user;

import com.fashion.dto.UserLoginDto;
import com.fashion.entity.User;
import com.fashion.result.Result;
import com.fashion.service.UserService;
import com.fashion.vo.UserLoginVo;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;

@RequestMapping("/user")
@RestController
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public Result<String> register(@RequestBody User user) {
        return userService.register(user);
    }

    @PostMapping("/login")
    public Result<UserLoginVo> login(@RequestBody UserLoginDto userLoginDto, HttpSession session) {
        return userService.login(userLoginDto, session);
    }

    @PostMapping("/sms-code")
    public Result<String> sendSmsCode(@RequestBody String phone) {
        return userService.sendSmsCode(phone);
    }

    @GetMapping("/me")
    public Result<User> getUserInfo(@RequestHeader("Authorization") String token) {
        return userService.getUserInfo(token);
    }

    @PutMapping
    public Result<String> updateUserInfo(@RequestHeader("Authorization") String token, @RequestBody User user) {
        return userService.updateUserInfo(token, user);
    }

    @PutMapping("/password")
    public Result<String> changePassword(@RequestHeader("Authorization") String token, @RequestParam String oldPassword, @RequestParam String newPassword) {
        return userService.changePassword(token, oldPassword, newPassword);
    }

    @PostMapping("/logout")
    public Result<String> logout(@RequestHeader("Authorization") String token) {
        return userService.logout(token);
    }
}
