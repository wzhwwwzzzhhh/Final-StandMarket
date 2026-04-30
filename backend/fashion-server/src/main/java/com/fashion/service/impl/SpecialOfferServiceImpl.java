package com.fashion.service.impl;

import com.fashion.entity.SpecialOffer;
import com.fashion.entity.PageResult;
import com.fashion.mapper.SpecialOfferMapper;
import com.fashion.service.SpecialOfferService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 特价商品服务实现类
 */
@Service
public class SpecialOfferServiceImpl implements SpecialOfferService {

    @Autowired
    private SpecialOfferMapper specialOfferMapper;

    @Override
    public void save(SpecialOffer specialOffer) {
        // 设置创建和更新时间
        specialOffer.setCreateTime(LocalDateTime.now());
        specialOffer.setUpdateTime(LocalDateTime.now());
        // 保存特价商品
        specialOfferMapper.insert(specialOffer);
    }

    @Override
    public void delete(Long id) {
        specialOfferMapper.deleteById(id);
    }

    @Override
    public void update(SpecialOffer specialOffer) {
        // 设置更新时间
        specialOffer.setUpdateTime(LocalDateTime.now());
        specialOfferMapper.update(specialOffer);
    }

    @Override
    public SpecialOffer getById(Long id) {
        return specialOfferMapper.selectById(id);
    }

    @Override
    public PageResult<SpecialOffer> pageOffers(int page, int pageSize, Long productId, Integer status) {
        // 计算偏移量
        int offset = (page - 1) * pageSize;
        // 查询数据
        List<SpecialOffer> offers = specialOfferMapper.list(offset, pageSize, productId, status);
        // 查询总数
        int total = specialOfferMapper.count(productId, status);
        // 构建分页结果
        return new PageResult<>(total, offers);
    }

    /**
     * 查询所有特价商品，用户端
     * @return 特价商品列表
     */
    @Override
    public List<SpecialOffer> list() {
        return specialOfferMapper.list01();
    }
}