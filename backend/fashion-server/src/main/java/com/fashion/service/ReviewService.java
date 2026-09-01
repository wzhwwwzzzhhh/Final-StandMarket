package com.fashion.service;

import com.fashion.entity.PageResult;
import com.fashion.entity.Review;

import java.util.Map;

public interface ReviewService {
    Review addReview(Review review);
    PageResult<Review> getProductReviews(Long productId, Integer page, Integer size, Integer rating);
    PageResult<Review> getMyReviews(Integer page, Integer size);
    Map<String, Object> getReviewStats(Long productId);
    Review getByOrderIdForCurrentUser(Long orderId);
    PageResult<Review> getAdminReviews(Integer page, Integer size, String keyword);
    void updateReviewStatus(Long id, Integer status);
    void deleteReview(Long id);
}
