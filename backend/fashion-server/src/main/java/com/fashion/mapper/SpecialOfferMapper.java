package com.fashion.mapper;

import com.fashion.entity.SpecialOffer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 特价商品Mapper
 */
@Mapper
public interface SpecialOfferMapper {
    /**
     * 新增特价商品
     * @param specialOffer 特价商品信息
     */
    void insert(SpecialOffer specialOffer);

    /**
     * 根据id删除特价商品
     * @param id 特价商品id
     */
    void deleteById(Long id);

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
    SpecialOffer selectById(Long id);

    /**
     * 分页查询特价商品
     * @param page 页码
     * @param pageSize 每页大小
     * @param productId 商品id
     * @param status 状态
     * @return 特价商品列表
     */
    List<SpecialOffer> list(@Param("page") int page, @Param("pageSize") int pageSize, @Param("productId") Long productId, @Param("status") Integer status);

    /**
     * 查询特价商品总数
     * @param productId 商品id
     * @param status 状态
     * @return 总数
     */
    int count(@Param("productId") Long productId, @Param("status") Integer status);

    /**
     * 查询特价商品列表（用户端）
     * @return 特价商品列表
     */
    List<SpecialOffer> list01();
}