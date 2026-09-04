package com.fashion.service.impl;

import com.fashion.context.BaseContext;
import com.fashion.entity.PageResult;
import com.fashion.entity.Review;
import com.fashion.dto.ReviewCreateDTO;
import com.fashion.exception.PublicBusinessException;
import com.fashion.exception.PublicBusinessException.Code;
import com.fashion.mapper.ReviewMapper;
import com.fashion.service.ReviewService;
import com.fashion.vo.ReviewMineVO;
import com.fashion.vo.ReviewPublicVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static com.fashion.exception.PublicBusinessException.Code.*;
import static com.fashion.exception.PublicBusinessException.of;

@Service
public class ReviewServiceImpl implements ReviewService {

    @Autowired
    private ReviewMapper reviewMapper;

    @Override
    @Transactional
    public ReviewMineVO addReview(ReviewCreateDTO review) {
        validateReview(review);
        Long userId = requireCurrentUserId();
        final int rows;
        try {
            rows = reviewMapper.insertAuthorized(userId, review);
        } catch (DuplicateKeyException e) {
            if (e.getMessage() != null && e.getMessage().contains("uk_review_order_product")) {
                throw of(REVIEW_DUPLICATE);
            }
            throw e;
        }
        if (rows != 1) {
            throw of(REVIEW_NOT_ELIGIBLE);
        }
        ReviewMineVO saved = reviewMapper.selectMineByOrderProductUser(
                review.getOrderId(), review.getProductId(), userId);
        if (saved == null) {
            throw new IllegalStateException("评价写入后读取失败");
        }
        return saved;
    }

    @Override
    public PageResult<ReviewPublicVO> getProductReviews(Long productId, Integer page, Integer size, Integer rating) {
        validateProductQuery(productId, page, size, rating);
        PageHelper.startPage(page, size);
        List<ReviewPublicVO> reviews = reviewMapper.selectPublicByProductId(productId, rating);
        reviews.forEach(review -> review.setDisplayName(maskDisplayName(review.getDisplayName())));
        PageInfo<ReviewPublicVO> pageInfo = new PageInfo<>(reviews);
        return new PageResult<>(pageInfo.getTotal(), pageInfo.getList());
    }

    @Override
    public PageResult<ReviewMineVO> getMyReviews(Integer page, Integer size) {
        Long userId = requireCurrentUserId();
        validatePage(page, size);
        PageHelper.startPage(page, size);
        List<ReviewMineVO> reviews = reviewMapper.selectMineByUserId(userId);
        PageInfo<ReviewMineVO> pageInfo = new PageInfo<>(reviews);
        return new PageResult<>(pageInfo.getTotal(), pageInfo.getList());
    }

    @Override
    public Map<String, Object> getReviewStats(Long productId) {
        requirePositive(productId, PRODUCT_ID_INVALID);
        return reviewMapper.selectRatingStats(productId);
    }

    @Override
    public boolean hasReviewed(Long orderId, Long productId) {
        Long userId = requireCurrentUserId();
        requirePositive(orderId, ORDER_ID_INVALID);
        requirePositive(productId, PRODUCT_ID_INVALID);
        return reviewMapper.existsByOrderProductUser(orderId, productId, userId) > 0;
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
            throw of(LOGIN_REQUIRED);
        }
        return userId;
    }

    private void validateReview(ReviewCreateDTO review) {
        if (review == null) {
            throw of(REVIEW_REQUIRED);
        }
        requirePositive(review.getOrderId(), ORDER_ID_INVALID);
        requirePositive(review.getProductId(), PRODUCT_ID_INVALID);
        if (review.getRating() == null || review.getRating() < 1 || review.getRating() > 5) {
            throw of(RATING_INVALID);
        }
        if (review.getContent() != null && review.getContent().length() > 500) {
            throw of(REVIEW_CONTENT_TOO_LONG);
        }
        if (review.getImages() != null && review.getImages().length() > 1000) {
            throw of(REVIEW_IMAGES_TOO_LONG);
        }
    }

    private void validateProductQuery(Long productId, Integer page, Integer size, Integer rating) {
        requirePositive(productId, PRODUCT_ID_INVALID);
        validatePage(page, size);
        if (rating != null && rating != 3 && rating != 4 && rating != 5) {
            throw of(RATING_FILTER_INVALID);
        }
    }

    private void validatePage(Integer page, Integer size) {
        if (page == null || page < 1 || page > 10000 || size == null || size < 1 || size > 50) {
            throw of(PAGE_INVALID);
        }
    }

    private void requirePositive(Long value, Code code) {
        if (value == null || value <= 0) {
            throw of(code);
        }
    }

    private String maskDisplayName(String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) {
            return "匿名用户";
        }
        String name = rawName.trim();
        int firstCodePoint = name.codePointAt(0);
        return new String(Character.toChars(firstCodePoint)) + "**";
    }
}
