package com.fashion.service;

import com.fashion.entity.SpecialOffer;
import com.fashion.entity.PageResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 特价商品服务接口
 */

public interface SpecialOfferService {
    /**
     * 新增特价商品
     * @param specialOffer 特价商品信息
     */
    void save(SpecialOffer specialOffer);

    /**
     * 删除特价商品
     * @param id 特价商品id
     */
    void delete(Long id);

    /**
     * 更新特价商品信息
     * @param specialOffer 特价商品信息
     */
    void update(SpecialOffer specialOffer);

    /**
     * 根据id查询特价商品
     * @param id 特价商品id
     * @return 特价商品信息
     */
    SpecialOffer getById(Long id);

    /**
     * 分页查询特价商品
     * @param page 页码
     * @param pageSize 每页大小
     * @param productId 商品id
     * @param status 状态
     * @return 分页结果
     */
    PageResult<SpecialOffer> pageOffers(int page, int pageSize, Long productId, Integer status);

    /**
     * 查询特价商品列表
     * @return 特价商品列表
     */
    List<SpecialOffer> list();
}