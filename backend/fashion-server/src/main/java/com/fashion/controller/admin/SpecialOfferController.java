package com.fashion.controller.admin;

import com.fashion.entity.SpecialOffer;
import com.fashion.entity.PageResult;
import com.fashion.result.Result;
import com.fashion.service.SpecialOfferService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 特价商品管理
 */
@RestController
@RequestMapping("/admin/special/offer")
public class SpecialOfferController {

    @Autowired
    private SpecialOfferService specialOfferService;

    /**
     * 新增特价商品
     */
    @PostMapping
    public Result<String> save(@RequestBody SpecialOffer specialOffer) {
        specialOfferService.save(specialOffer);
        return Result.success();
    }

    /**
     * 分页查询
     */
    @GetMapping("/page")
    public Result<PageResult<SpecialOffer>> page(int page, int pageSize, Long productId, Integer status) {
        // 调用Service层的分页查询方法
        PageResult<SpecialOffer> pageResult = specialOfferService.pageOffers(page, pageSize, productId, status);
        return Result.success(pageResult);
    }

    /**
     * 删除特价商品
     */
    @DeleteMapping
    public Result<String> delete(@RequestParam Long id) {
        if(id == null){
            return Result.error("id不能为空");
        }
        specialOfferService.delete(id);
        return Result.success();
    }

    /**
     * 修改特价商品
     */
    @PutMapping
    public Result<String> update(@RequestBody SpecialOffer specialOffer) {
        if(specialOffer.getId() == null){
            return Result.error("id不能为空");
        }
        specialOfferService.update(specialOffer);
        return Result.success();
    }

    /**
     * 根据id查询特价商品
     */
    @GetMapping("/{id}")
    public Result<SpecialOffer> getById(@PathVariable Long id) {
        if(id == null){
            return Result.error("id不能为空");
        }
        SpecialOffer specialOffer = specialOfferService.getById(id);
        if(specialOffer == null){
            return Result.error("特价商品不存在");
        }
        return Result.success(specialOffer);
    }
}