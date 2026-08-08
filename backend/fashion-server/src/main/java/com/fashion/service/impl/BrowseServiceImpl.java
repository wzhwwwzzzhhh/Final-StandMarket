package com.fashion.service.impl;

import com.fashion.constant.RedisKey;
import com.fashion.entity.Product;
import com.fashion.mapper.ProductMapper;
import com.fashion.service.BrowseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 浏览历史实现
 * 数据结构：Redis List，key = user:browse:{userId}，元素为商品ID字符串
 * - 记录：Lua 脚本原子完成 LREM 去重 → LPUSH 置顶 → LTRIM 截断 50 条 → 续期 TTL
 * - 查询：LRANGE 0 -1 按时间倒序取商品ID，批量查询商品信息并保持顺序
 */
@Service
public class BrowseServiceImpl implements BrowseService {

    /** 最多保留的浏览记录条数 */
    private static final long MAX_SIZE = 50;

    /** 浏览历史 key 过期时间（秒），续期制避免无限累积 */
    private static final long KEY_TTL_SECONDS = 30L * 24 * 60 * 60;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ProductMapper productMapper;

    private final DefaultRedisScript<Long> recordScript = new DefaultRedisScript<>();

    @PostConstruct
    public void init() {
        recordScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/browse_record.lua")));
        recordScript.setResultType(Long.class);
    }

    @Override
    public void record(Long userId, Long productId) {
        stringRedisTemplate.execute(
                recordScript,
                Collections.singletonList(RedisKey.USER_BROWSE_KEY + userId),
                String.valueOf(productId),
                String.valueOf(MAX_SIZE),
                String.valueOf(KEY_TTL_SECONDS)
        );
    }

    @Override
    public List<Product> listProducts(Long userId) {
        String key = RedisKey.USER_BROWSE_KEY + userId;
        List<String> ids = stringRedisTemplate.opsForList().range(key, 0, -1);
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> idList = new ArrayList<>(ids.size());
        for (String id : ids) {
            try {
                idList.add(Long.valueOf(id));
            } catch (NumberFormatException ignored) {
                // 忽略脏数据
            }
        }
        if (idList.isEmpty()) {
            return Collections.emptyList();
        }
        List<Product> products = productMapper.selectBatchByIds(idList);
        Map<Long, Product> productMap = new HashMap<>();
        for (Product product : products) {
            productMap.put(product.getId(), product);
        }
        // 保持 Redis 中的浏览顺序（最新在前），过滤已删除/下架的商品
        List<Product> result = new ArrayList<>(idList.size());
        for (Long id : idList) {
            Product product = productMap.get(id);
            if (product != null) {
                result.add(product);
            }
        }
        return result;
    }

    @Override
    public void clear(Long userId) {
        stringRedisTemplate.delete(RedisKey.USER_BROWSE_KEY + userId);
    }
}
