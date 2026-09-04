package com.fashion.mapper;

import com.fashion.entity.Review;
import com.fashion.dto.ReviewCreateDTO;
import com.fashion.vo.ReviewMineVO;
import com.fashion.vo.ReviewPublicVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ReviewMapper {
    int insertAuthorized(@Param("userId") Long userId,
                         @Param("review") ReviewCreateDTO review);

    ReviewMineVO selectMineByOrderProductUser(@Param("orderId") Long orderId,
                                               @Param("productId") Long productId,
                                               @Param("userId") Long userId);

    int existsByOrderProductUser(@Param("orderId") Long orderId,
                                 @Param("productId") Long productId,
                                 @Param("userId") Long userId);

    List<ReviewPublicVO> selectPublicByProductId(@Param("productId") Long productId,
                                                 @Param("rating") Integer rating);

    List<ReviewMineVO> selectMineByUserId(@Param("userId") Long userId);

    Review selectById(@Param("id") Long id);

    Review selectByOrderIdAndUserId(@Param("orderId") Long orderId, @Param("userId") Long userId);

    List<Review> selectAll(@Param("keyword") String keyword);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int deleteById(@Param("id") Long id);

    Map<String, Object> selectRatingStats(@Param("productId") Long productId);
}
