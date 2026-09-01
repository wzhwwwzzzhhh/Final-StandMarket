package com.fashion.controller.user;

import com.fashion.context.BaseContext;
import com.fashion.entity.PageResult;
import com.fashion.entity.Review;
import com.fashion.result.Result;
import com.fashion.service.ReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/user/review")
public class ReviewController {

    private static final Logger log = LoggerFactory.getLogger(ReviewController.class);

    @Autowired
    private ReviewService reviewService;

    @PostMapping("/add")
    public Result<Review> add(@RequestBody Review review) {
        Long userId = BaseContext.getUserId();
        if (userId == null) {
            return Result.error("请先登录");
        }
        // 校验评分范围
        if (review.getRating() == null || review.getRating() < 1 || review.getRating() > 5) {
            return Result.error("评分必须在1-5之间");
        }
        // 校验评价内容长度
        if (review.getContent() != null && review.getContent().length() > 500) {
            return Result.error("评价内容不能超过500字");
        }
        // 校验订单是否已评价
        if (review.getOrderId() != null) {
            Review existing = reviewService.getByOrderIdForCurrentUser(review.getOrderId());
            if (existing != null) {
                return Result.error("该订单已评价");
            }
        }
        review.setUserId(userId);
        try {
            Review saved = reviewService.addReview(review);
            return Result.success(saved);
        } catch (Exception e) {
            log.error("评价提交失败 userId={}, productId={}: {}", userId, review.getProductId(), e.getMessage(), e);
            return Result.error("评价提交失败");
        }
    }

    @GetMapping("/list/{productId}")
    public Result<PageResult<Review>> list(@PathVariable Long productId,
                                           @RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "10") Integer size,
                                           @RequestParam(required = false) Integer rating) {
        PageResult<Review> result = reviewService.getProductReviews(productId, page, size, rating);
        return Result.success(result);
    }

    @GetMapping("/stats/{productId}")
    public Result<Map<String, Object>> stats(@PathVariable Long productId) {
        Map<String, Object> stats = reviewService.getReviewStats(productId);
        return Result.success(stats);
    }

    @GetMapping("/my")
    public Result<PageResult<Review>> my(@RequestParam(defaultValue = "1") Integer page,
                                         @RequestParam(defaultValue = "10") Integer size) {
        Long userId = BaseContext.getUserId();
        if (userId == null) {
            return Result.error("请先登录");
        }
        PageResult<Review> result = reviewService.getMyReviews(page, size);
        return Result.success(result);
    }

    @GetMapping("/check/{orderId}")
    public Result<Map<String, Boolean>> check(@PathVariable Long orderId) {
        Review existing = reviewService.getByOrderIdForCurrentUser(orderId);
        java.util.Map<String, Boolean> result = new java.util.HashMap<>();
        result.put("reviewed", existing != null);
        return Result.success(result);
    }
}
