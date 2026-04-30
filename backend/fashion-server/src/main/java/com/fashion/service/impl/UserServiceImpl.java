package com.fashion.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.fashion.constant.RedisKey;
import com.fashion.dto.UserLoginDto;
import com.fashion.entity.User;
import com.fashion.entity.PageResult;

import com.fashion.mapper.UserMapper;

import com.fashion.result.Result;
import com.fashion.service.UserService;
import com.fashion.vo.UserInfo;
import com.fashion.vo.UserLoginVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 用户服务实现类
 */
@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public void save(User user) {
        // 设置创建时间
        user.setCreateTime(LocalDateTime.now());
        // 保存用户
        userMapper.insert(user);
    }

    @Override
    public void delete(Long id) {
        userMapper.deleteById(id);
    }

    @Override
    public void update(User user) {
        userMapper.update(user);
    }

    @Override
    public User getById(Long id) {
        return userMapper.selectById(id);
    }

    @Override
    public PageResult<User> pageUsers(int page, int pageSize, String name, String phone) {
        // 计算偏移量
        int offset = (page - 1) * pageSize;
        // 查询数据
        List<User> users = userMapper.list(offset, pageSize, name, phone);
        // 查询总数
        int total = userMapper.count(name, phone);
        // 构建分页结果
        return new PageResult<>(total, users);
    }

    /**
     * 统计用户数量
     * @return 用户数量
     */
    public int count() {
        return userMapper.count(null, null);
    }

    /**
     * 发送短信验证码
     * @param phone
     * @return
     */
    @Override
    public Result<String> sendSmsCode(String phone) {
        //先校验手机号是否合法
        if(phone == null || !phone.trim().matches("1[3-9]\\d{9}")){
            return Result.error("手机号不合法");
        }
        //限制验证码生成频率
        String codekey = RedisKey.USER_LOGIN_CODE_KEY + phone;
        if(redisTemplate.hasKey(codekey)){
            return Result.error("验证码已发送，请稍后再试");
        }
        //生成验证码
        String code = RandomUtil.randomNumbers(6);
        //把生成的验证码保存到redis 中，过期时间为2分钟
        redisTemplate.opsForValue().set(codekey, code, 2 * 60, TimeUnit.SECONDS);
        //发送验证码
        log.info("发送验证码：{}", code);
        //返回发送成功的信息
        return Result.success("验证码发送成功");
    }

    @Override
    @Transactional
    public Result<UserLoginVo> login(UserLoginDto userLoginDto, HttpSession session) {
        log.info("登录请求参数: {}", userLoginDto);
        // 先判断登录类型
        String type = userLoginDto.getType();
        log.info("登录类型: {}", type);
        //根据 手机号查询用户
        User user = userMapper.selectByPhone(userLoginDto.getPhone());
        log.info("查询到的用户: {}", user);
        if("sms".equals(type)){
            String codekey = RedisKey.USER_LOGIN_CODE_KEY + userLoginDto.getPhone();
            // 短信登录查询redis中的验证码
            String code = redisTemplate.opsForValue().get(codekey);
            log.info("Redis中的验证码: {}", code);
            if(code == null || !code.equals(userLoginDto.getCode())){
                log.info("验证码错误，输入的验证码: {}", userLoginDto.getCode());
                return Result.error("验证码错误");
            }
            // 验证通过后删除验证码，防止重复使用
            redisTemplate.delete(codekey);
            if(user == null){
                //创建用户
                user = new User();
                user.setPhone(userLoginDto.getPhone());
                //设置默认数据
                user.setName("用户" + RandomUtil.randomString(5));
                user.setAvatar("https://picsum.photos/200/300");
                user.setCreateTime(LocalDateTime.now());
                userMapper.insert(user);
                log.info("创建新用户: {}", user);
            }
        }else{
            //密码登录
            // 密码登录查询数据库中的密码
            if(user == null){
                log.info("用户不存在，手机号: {}", userLoginDto.getPhone());
                return Result.error("用户不存在");
            }
            //校验密码
            String dbPassword = user.getPassword();
            log.info("数据库中的密码: {}", dbPassword);
            log.info("输入的密码: {}", userLoginDto.getPassword());
            if(dbPassword == null || !dbPassword.equals(userLoginDto.getPassword())){
                log.info("密码错误");
                return Result.error("密码错误");
            }
        }
        //到达这步 说明用户登录成功
        //生成token
        String token = UUID.randomUUID().toString();
        log.info("生成的token: {}", token);
        //redis中用什么数据结构保存token?  ,用HashMap
        //key 为login+token , value 为用户信息
        Map<String, Object> userMap = BeanUtil.beanToMap(user,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) ->
                            fieldValue != null ? fieldValue.toString() : null));
        log.info("用户信息Map: {}", userMap);
        redisTemplate.opsForHash().putAll(RedisKey.USER_LOGIN_KEY + token, userMap);
        //把token保存到redis中，过期时间为30分钟
        redisTemplate.expire(RedisKey.USER_LOGIN_KEY + token, 30 * 60, TimeUnit.SECONDS);
        log.info("Token保存到Redis成功");

        UserInfo userInfo = new UserInfo();
        userInfo.setId(user.getId());
        userInfo.setName(user.getName());
        userInfo.setPhone(user.getPhone());
        userInfo.setAvatar(user.getAvatar());
        UserLoginVo userLoginVo = new UserLoginVo();
        userLoginVo.setToken(token);
        userLoginVo.setUserInfo(userInfo);
        log.info("登录成功，返回的用户信息: {}", userLoginVo);
        return Result.success(userLoginVo);
    }

    /**
     *
     * @param token
     * @return
     */
    @Override
    public Result<User> getUserInfo(String token) {
        try {
            // 从 token 中提取实际的 token 值（去掉 Bearer 前缀）
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            // 从 Redis 中获取用户信息
            Map<Object, Object> userMap = redisTemplate.opsForHash().entries(RedisKey.USER_LOGIN_KEY + token);
            if (userMap.isEmpty()) {
                return Result.error("用户未登录");
            }
            // 从 userMap 中获取用户 ID
            String userIdStr = (String) userMap.get("id");
            if (userIdStr == null) {
                return Result.error("用户信息不存在");
            }
            Long userId = Long.parseLong(userIdStr);
            // 根据 ID 查询用户信息
            User user = userMapper.selectById(userId);
            if (user == null) {
                return Result.error("用户不存在");
            }
            return Result.success(user);
        } catch (Exception e) {
            log.error("获取用户信息失败", e);
            return Result.error("获取用户信息失败");
        }
    }

    @Override
    public Result<String> updateUserInfo(String token, User user) {
        try {
            // 从 token 中提取实际的 token 值（去掉 Bearer 前缀）
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            // 从 Redis 中获取用户信息
            Map<Object, Object> userMap = redisTemplate.opsForHash().entries(RedisKey.USER_LOGIN_KEY + token);
            if (userMap.isEmpty()) {
                return Result.error("用户未登录");
            }
            // 从 userMap 中获取用户 ID
            String userIdStr = (String) userMap.get("id");
            if (userIdStr == null) {
                return Result.error("用户信息不存在");
            }
            Long userId = Long.parseLong(userIdStr);
            // 设置用户 ID
            user.setId(userId);
            // 更新用户信息
            userMapper.update(user);
            // 更新 Redis 中的用户信息
            Map<String, Object> updatedUserMap = BeanUtil.beanToMap(user, new HashMap<>(),
                    CopyOptions.create()
                            .setIgnoreNullValue(true)
                            .setFieldValueEditor((fieldName, fieldValue) ->
                                    fieldValue != null ? fieldValue.toString() : null));
            redisTemplate.opsForHash().putAll(RedisKey.USER_LOGIN_KEY + token, updatedUserMap);
            return Result.success("更新成功");
        } catch (Exception e) {
            log.error("更新用户信息失败", e);
            return Result.error("更新用户信息失败");
        }
    }

    @Override
    public Result<String> changePassword(String token, String oldPassword, String newPassword) {
        try {
            // 从 token 中提取实际的 token 值（去掉 Bearer 前缀）
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            // 从 Redis 中获取用户信息
            Map<Object, Object> userMap = redisTemplate.opsForHash().entries(RedisKey.USER_LOGIN_KEY + token);
            if (userMap.isEmpty()) {
                return Result.error("用户未登录");
            }
            // 从 userMap 中获取用户 ID
            String userIdStr = (String) userMap.get("id");
            if (userIdStr == null) {
                return Result.error("用户信息不存在");
            }
            Long userId = Long.parseLong(userIdStr);
            // 根据 ID 查询用户信息
            User user = userMapper.selectById(userId);
            if (user == null) {
                return Result.error("用户不存在");
            }
            
            // 验证旧密码
            String dbPassword = user.getPassword();
            if (dbPassword == null || !dbPassword.equals(oldPassword)) {
                log.info("修改密码 - 旧密码验证失败");
                return Result.error("旧密码错误");
            }
            
            // 更新密码
            user.setPassword(newPassword);
            userMapper.update(user);
            
            log.info("修改密码 - 密码更新成功");
            
            // 更新 Redis 中的用户信息
            Map<String, Object> updatedUserMap = BeanUtil.beanToMap(user, new HashMap<>(),
                    CopyOptions.create()
                            .setIgnoreNullValue(true)
                            .setFieldValueEditor((fieldName, fieldValue) ->
                                    fieldValue != null ? fieldValue.toString() : null));
            redisTemplate.opsForHash().putAll(RedisKey.USER_LOGIN_KEY + token, updatedUserMap);
            
            log.info("修改密码 - Redis缓存更新成功");
            
            return Result.success("密码修改成功");
        } catch (Exception e) {
            log.error("修改密码失败", e);
            return Result.error("修改密码失败");
        }
    }

    @Override
    public Result<String> logout(String token) {
        try {
            // 从 token 中提取实际的 token 值（去掉 Bearer 前缀）
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            // 从 Redis 中删除用户登录信息
            redisTemplate.delete(RedisKey.USER_LOGIN_KEY + token);
            log.info("退出登录 - Redis缓存删除成功");
            return Result.success("退出登录成功");
        } catch (Exception e) {
            log.error("退出登录失败", e);
            return Result.error("退出登录失败");
        }
    }

    @Override
    public Result<String> register(User user) {
        try {
            // 检查手机号是否已存在
            User existingUser = userMapper.selectByPhone(user.getPhone());
            if (existingUser != null) {
                return Result.error("手机号已注册");
            }
            
            // 设置创建时间
            user.setCreateTime(LocalDateTime.now());
            
            // 保存用户
            userMapper.insert(user);
            
            return Result.success("注册成功");
        } catch (Exception e) {
            log.error("注册失败", e);
            return Result.error("注册失败");
        }
    }
}