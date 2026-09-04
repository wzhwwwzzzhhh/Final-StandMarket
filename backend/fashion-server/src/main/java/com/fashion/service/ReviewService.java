package com.fashion.service;

import com.fashion.entity.PageResult;
import com.fashion.entity.Review;
import com.fashion.dto.ReviewCreateDTO;
import com.fashion.vo.ReviewMineVO;
import com.fashion.vo.ReviewPublicVO;

import java.util.Map;

public interface ReviewService {
    ReviewMineVO addReview(ReviewCreateDTO review);
    PageResult<ReviewPublicVO> getProductReviews(Long productId, Integer page, Integer size, Integer rating);
    PageResult<ReviewMineVO> getMyReviews(Integer page, Integer size);
    Map<String, Object> getReviewStats(Long productId);
    boolean hasReviewed(Long orderId, Long productId);
    PageResult<Review> getAdminReviews(Integer page, Integer size, String keyword);
    void updateReviewStatus(Long id, Integer status);
    void deleteReview(Long id);
}
