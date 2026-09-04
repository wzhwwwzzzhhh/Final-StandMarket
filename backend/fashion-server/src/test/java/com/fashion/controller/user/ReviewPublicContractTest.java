package com.fashion.controller.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.vo.ReviewPublicVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("B7 公开评价与二元检查契约")
class ReviewPublicContractTest {

    @Test
    @DisplayName("公开评价 JSON 只包含白名单字段")
    void publicReviewSerializationExcludesInternalIdentifiers() throws Exception {
        ReviewPublicVO review = new ReviewPublicVO();
        review.setId(1L);
        review.setRating(5);
        review.setContent("很好");
        review.setImages("[]");
        review.setCreateTime(LocalDateTime.of(2026, 9, 3, 18, 0));
        review.setDisplayName("张**");

        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(review);

        assertTrue(json.contains("\"id\":1"));
        assertTrue(json.contains("\"displayName\":\"张**\""));
        assertFalse(json.contains("userId"));
        assertFalse(json.contains("orderId"));
        assertFalse(json.contains("phone"));
    }

    @Test
    @DisplayName("评价检查的 productId 是必填查询参数")
    void checkRequiresProductId() throws Exception {
        Method check = ReviewController.class.getMethod("check", Long.class, Long.class);
        RequestParam annotation = null;
        for (Parameter parameter : check.getParameters()) {
            RequestParam candidate = parameter.getAnnotation(RequestParam.class);
            if (candidate != null) {
                annotation = candidate;
            }
        }

        assertNotNull(annotation);
        assertTrue(annotation.required());
    }
}
