package com.fashion.controller.user;

import com.fashion.entity.SpecialOffer;
import com.fashion.result.Result;
import com.fashion.service.SpecialOfferService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端特价商品管理
 */
@RestController
@RequestMapping("/user/special-offer")
public class UserSpecialOfferController {

    @Autowired
    private SpecialOfferService specialOfferService;

    /**
     * 获取特价商品列表
     */
    @GetMapping("/list")
    public Result<List<SpecialOffer>> list() {
        List<SpecialOffer> offers = specialOfferService.list();
        return Result.success(offers);
    }
}