package com.fashion.service.impl;

import com.fashion.context.BaseContext;
import com.fashion.entity.PageResult;
import com.fashion.entity.Review;
import com.fashion.mapper.ReviewMapper;
import com.fashion.service.ReviewService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ReviewServiceImpl implements ReviewService {

    @Autowired
    private ReviewMapper reviewMapper;

    @Override
    @Transactional
    public Review addReview(Review review) {
        if (review == null) {
            throw new IllegalArgumentException("评价不能为空");
        }
        review.setUserId(requireCurrentUserId());
        review.setStatus(1);
        review.setCreateTime(LocalDateTime.now());
        review.setUpdateTime(LocalDateTime.now());
        reviewMapper.insert(review);
        return review;
    }

    @Override
    public PageResult<Review> getProductReviews(Long productId, Integer page, Integer size, Integer rating) {
        PageHelper.startPage(page, size);
        List<Review> reviews = reviewMapper.selectByProductId(productId, 1, rating);
        PageInfo<Review> pageInfo = new PageInfo<>(reviews);
        return new PageResult<>(pageInfo.getTotal(), pageInfo.getList());
    }

    @Override
    public PageResult<Review> getMyReviews(Integer page, Integer size) {
        Long userId = requireCurrentUserId();
        PageHelper.startPage(page, size);
        List<Review> reviews = reviewMapper.selectByUserId(userId);
        PageInfo<Review> pageInfo = new PageInfo<>(reviews);
        return new PageResult<>(pageInfo.getTotal(), pageInfo.getList());
    }

    @Override
    public Map<String, Object> getReviewStats(Long productId) {
        return reviewMapper.selectRatingStats(productId);
    }

    @Override
    public Review getByOrderIdForCurrentUser(Long orderId) {
        Long userId = requireCurrentUserId();
        return orderId == null ? null : reviewMapper.selectByOrderIdAndUserId(orderId, userId);
    }

    @Override
    public PageResult<Review> getAdminReviews(Integer page, Integer size, String keyword) {
        PageHelper.startPage(page, size);
        List<Review> reviews = reviewMapper.selectAll(keyword);
        PageInfo<Review> pageInfo = new PageInfo<>(reviews);
        return new PageResult<>(pageInfo.getTotal(), pageInfo.getList());
    }

    @Override
    public void updateReviewStatus(Long id, Integer status) {
        reviewMapper.updateStatus(id, status);
    }

    @Override
    public void deleteReview(Long id) {
        reviewMapper.deleteById(id);
    }

    private Long requireCurrentUserId() {
        Long userId = BaseContext.getUserId();
        if (userId == null) {
            throw new IllegalStateException("请先登录");
        }
        return userId;
    }
}
