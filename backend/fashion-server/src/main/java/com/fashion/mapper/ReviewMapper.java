package com.fashion.mapper;

import com.fashion.entity.Review;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ReviewMapper {
    int insert(Review review);

    Review selectById(@Param("id") Long id);

    List<Review> selectByProductId(@Param("productId") Long productId,
                                   @Param("status") Integer status,
                                   @Param("rating") Integer rating);

    List<Review> selectByUserId(@Param("userId") Long userId);

    Review selectByOrderIdAndUserId(@Param("orderId") Long orderId, @Param("userId") Long userId);

    List<Review> selectAll(@Param("keyword") String keyword);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int deleteById(@Param("id") Long id);

    Map<String, Object> selectRatingStats(@Param("productId") Long productId);
}
