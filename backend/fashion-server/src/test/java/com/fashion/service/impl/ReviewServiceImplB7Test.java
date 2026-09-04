package com.fashion.service.impl;

import com.fashion.context.BaseContext;
import com.fashion.dto.ReviewCreateDTO;
import com.fashion.exception.BaseException;
import com.fashion.mapper.ReviewMapper;
import com.fashion.vo.ReviewMineVO;
import com.fashion.vo.ReviewPublicVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B7 评价授权、幂等与公开契约")
class ReviewServiceImplB7Test {

    private ReviewServiceImpl service;
    private ReviewMapper reviewMapper;

    @BeforeEach
    void setUp() {
        BaseContext.setUserId(7L);
        reviewMapper = mock(ReviewMapper.class);
        service = new ReviewServiceImpl();
        ReflectionTestUtils.setField(service, "reviewMapper", reviewMapper);
    }

    @AfterEach
    void cleanContext() {
        BaseContext.removeUserId();
    }

    @Test
    @DisplayName("提交只使用登录用户和输入白名单并依赖受约束写入")
    void addsReviewThroughAuthorizedInsert() {
        ReviewCreateDTO request = validRequest();
        ReviewMineVO saved = new ReviewMineVO();
        saved.setOrderId(10L);
        saved.setProductId(20L);
        when(reviewMapper.insertAuthorized(7L, request)).thenReturn(1);
        when(reviewMapper.selectMineByOrderProductUser(10L, 20L, 7L)).thenReturn(saved);

        ReviewMineVO result = service.addReview(request);

        assertEquals(10L, result.getOrderId());
        assertEquals(20L, result.getProductId());
        verify(reviewMapper).insertAuthorized(7L, request);
    }

    @Test
    @DisplayName("资格写入零行返回不泄露订单事实的稳定错误")
    void rejectsUnauthorizedOrIncompleteOrder() {
        ReviewCreateDTO request = validRequest();
        when(reviewMapper.insertAuthorized(7L, request)).thenReturn(0);

        BaseException error = assertThrows(BaseException.class, () -> service.addReview(request));

        assertEquals("订单不存在、未完成或商品不属于订单", error.getMessage());
    }

    @Test
    @DisplayName("唯一冲突映射为稳定重复评价错误")
    void mapsDuplicateReviewToStableBusinessError() {
        ReviewCreateDTO request = validRequest();
        when(reviewMapper.insertAuthorized(7L, request))
                .thenThrow(new DuplicateKeyException("uk_review_order_product leaked"));

        BaseException error = assertThrows(BaseException.class, () -> service.addReview(request));

        assertEquals("该订单商品已评价", error.getMessage());
    }

    @Test
    @DisplayName("检查评价必须同时使用订单和商品")
    void checksByOrderAndProduct() {
        when(reviewMapper.existsByOrderProductUser(10L, 20L, 7L)).thenReturn(1);

        boolean reviewed = service.hasReviewed(10L, 20L);

        assertEquals(true, reviewed);
        verify(reviewMapper).existsByOrderProductUser(10L, 20L, 7L);
    }

    @Test
    @DisplayName("公开昵称按首个 Unicode code point 脱敏且空白匿名")
    void masksPublicDisplayNameByUnicodeCodePoint() {
        ReviewPublicVO emoji = new ReviewPublicVO();
        emoji.setDisplayName("😀用户");
        ReviewPublicVO blank = new ReviewPublicVO();
        blank.setDisplayName("   ");
        when(reviewMapper.selectPublicByProductId(20L, null)).thenReturn(Arrays.asList(emoji, blank));

        java.util.List<ReviewPublicVO> records = service.getProductReviews(20L, 1, 10, null).getRecords();

        assertEquals("😀**", records.get(0).getDisplayName());
        assertEquals("匿名用户", records.get(1).getDisplayName());
    }

    @Test
    @DisplayName("非法评分筛选和越界分页显式拒绝")
    void rejectsInvalidPublicQuery() {
        assertThrows(BaseException.class, () -> service.getProductReviews(20L, 0, 10, null));
        assertThrows(BaseException.class, () -> service.getProductReviews(20L, 1, 51, null));
        assertThrows(BaseException.class, () -> service.getProductReviews(20L, 1, 10, 2));
    }

    private ReviewCreateDTO validRequest() {
        ReviewCreateDTO request = new ReviewCreateDTO();
        request.setOrderId(10L);
        request.setProductId(20L);
        request.setRating(5);
        request.setContent("很好");
        request.setImages("a.jpg");
        return request;
    }
}
