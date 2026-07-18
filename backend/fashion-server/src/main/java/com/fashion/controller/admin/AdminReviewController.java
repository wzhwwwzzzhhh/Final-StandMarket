package com.fashion.controller.admin;

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
@RequestMapping("/admin/review")
public class AdminReviewController {

    private static final Logger log = LoggerFactory.getLogger(AdminReviewController.class);

    @Autowired
    private ReviewService reviewService;

    @GetMapping("/list")
    public Result<PageResult<Review>> list(@RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "10") Integer size,
                                           @RequestParam(required = false) String keyword) {
        PageResult<Review> result = reviewService.getAdminReviews(page, size, keyword);
        return Result.success(result);
    }

    @PutMapping("/status")
    public Result<String> updateStatus(@RequestBody Map<String, Object> params) {
        Object idObj = params.get("id");
        Object statusObj = params.get("status");
        if (idObj == null || statusObj == null) {
            return Result.error("参数不完整");
        }
        Long id = Long.valueOf(idObj.toString());
        Integer status = Integer.valueOf(statusObj.toString());
        try {
            reviewService.updateReviewStatus(id, status);
            return Result.success(status == 1 ? "已显示" : "已隐藏");
        } catch (Exception e) {
            log.error("更新评价状态失败 id={}, status={}: {}", id, status, e.getMessage(), e);
            return Result.error("操作失败");
        }
    }

    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        try {
            reviewService.deleteReview(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            log.error("删除评价失败 id={}: {}", id, e.getMessage(), e);
            return Result.error("删除失败");
        }
    }
}
