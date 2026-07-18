package com.fashion.mapper;

import com.fashion.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FavoriteMapper {
    int insert(Favorite favorite);
    int deleteByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);
    Favorite selectByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);
    List<Favorite> selectByUserId(@Param("userId") Long userId);
    int countByUserId(@Param("userId") Long userId);
    int deleteByProductId(@Param("productId") Long productId);
}
