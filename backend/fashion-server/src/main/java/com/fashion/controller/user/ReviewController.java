package com.fashion.controller.user;

import com.fashion.context.BaseContext;
import com.fashion.entity.PageResult;
import com.fashion.dto.ReviewCreateDTO;
import com.fashion.exception.PublicBusinessException;
import com.fashion.result.Result;
import com.fashion.service.ReviewService;
import com.fashion.vo.ReviewMineVO;
import com.fashion.vo.ReviewPublicVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/user/review")
public class ReviewController {

    private static final Logger log = LoggerFactory.getLogger(ReviewController.class);

    @Autowired
    private ReviewService reviewService;

    @PostMapping("/add")
    public Result<ReviewMineVO> add(@RequestBody ReviewCreateDTO review) {
        Long userId = BaseContext.getUserId();
        if (userId == null) {
            return Result.error("请先登录");
        }
        try {
            ReviewMineVO saved = reviewService.addReview(review);
            return Result.success(saved);
        } catch (PublicBusinessException e) {
            return Result.error(e.getMessage());
        } catch (RuntimeException e) {
            logFailure("评价提交", e, userId,
                    review == null ? null : review.getProductId());
            return Result.error("评价提交失败，请稍后重试");
        }
    }

    @GetMapping("/list/{productId}")
    public Result<PageResult<ReviewPublicVO>> list(@PathVariable Long productId,
                                           @RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "10") Integer size,
                                           @RequestParam(required = false) Integer rating) {
        try {
            return Result.success(reviewService.getProductReviews(productId, page, size, rating));
        } catch (PublicBusinessException e) {
            return Result.error(e.getMessage());
        } catch (RuntimeException e) {
            logFailure("公开评价查询", e, null, productId);
            return Result.error("获取评价失败，请稍后重试");
        }
    }

    @GetMapping("/stats/{productId}")
    public Result<Map<String, Object>> stats(@PathVariable Long productId) {
        try {
            return Result.success(reviewService.getReviewStats(productId));
        } catch (PublicBusinessException e) {
            return Result.error(e.getMessage());
        } catch (RuntimeException e) {
            logFailure("评价统计查询", e, null, productId);
            return Result.error("获取评价失败，请稍后重试");
        }
    }

    @GetMapping("/my")
    public Result<PageResult<ReviewMineVO>> my(@RequestParam(defaultValue = "1") Integer page,
                                         @RequestParam(defaultValue = "10") Integer size) {
        Long userId = BaseContext.getUserId();
        if (userId == null) {
            return Result.error("请先登录");
        }
        try {
            return Result.success(reviewService.getMyReviews(page, size));
        } catch (PublicBusinessException e) {
            return Result.error(e.getMessage());
        } catch (RuntimeException e) {
            logFailure("本人评价查询", e, userId, null);
            return Result.error("获取评价失败，请稍后重试");
        }
    }

    @GetMapping("/check/{orderId}")
    public Result<Map<String, Boolean>> check(@PathVariable Long orderId,
                                               @RequestParam Long productId) {
        try {
            java.util.Map<String, Boolean> result = new java.util.HashMap<>();
            result.put("reviewed", reviewService.hasReviewed(orderId, productId));
            return Result.success(result);
        } catch (PublicBusinessException e) {
            return Result.error(e.getMessage());
        } catch (RuntimeException e) {
            logFailure("评价状态查询", e, BaseContext.getUserId(), productId);
            return Result.error("获取评价失败，请稍后重试");
        }
    }

    private void logFailure(String action, RuntimeException failure, Long userId, Long productId) {
        String traceId = UUID.randomUUID().toString();
        log.error("{}失败 traceId={}, userId={}, productId={}, exceptionType={}",
                action, traceId, userId, productId, failure.getClass().getName());
    }
}
