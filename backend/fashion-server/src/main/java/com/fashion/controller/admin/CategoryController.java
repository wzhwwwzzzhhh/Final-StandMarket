package com.fashion.controller.admin;

import com.fashion.entity.Category;
import com.fashion.entity.PageResult;
import com.fashion.dto.CategorySaveDTO;
import com.fashion.dto.CategoryUpdateDTO;
import com.fashion.result.Result;
import com.fashion.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 分类管理
 */
@RestController
@RequestMapping("/admin/category")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    /**
     * 新增分类
     */
    @PostMapping
    public Result<String> save(@RequestBody CategorySaveDTO categorySaveDTO) {
        // 转换为Category实体
        Category category = new Category();
        category.setName(categorySaveDTO.getName());
        category.setType(categorySaveDTO.getType());
        category.setSort(categorySaveDTO.getSort());
        category.setStatus(categorySaveDTO.getStatus());
        
        categoryService.save(category);
        return Result.success();
    }

    /**
     * 分页查询
     */
    @GetMapping
    public Result<PageResult<Category>> page(int page, int pageSize) {
        // 调用Service层的分页查询方法
        PageResult<Category> pageResult = categoryService.pageCategories(page, pageSize);
        return Result.success(pageResult);
    }

    /**
     * 根据id查询分类
     */
    @GetMapping("/{id}")
    public Result<Category> getById(@PathVariable Long id) {
        if (id == null) {
            return Result.error("id不能为空");
        }
        Category category = categoryService.getById(id);
        if (category == null) {
            return Result.error("分类不存在");
        }
        return Result.success(category);
    }

    /**
     * 删除分类
     */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        if (id == null) {
            return Result.error("id不能为空");
        }
        boolean flag = categoryService.removeById(id);
        if (!flag) {
            return Result.error("删除失败");
        }
        return Result.success();
    }

    /**
     * 修改分类
     */
    @PutMapping("/{id}")
    public Result<String> update(@PathVariable Long id, @RequestBody CategoryUpdateDTO categoryUpdateDTO) {
        if (id == null) {
            return Result.error("id不能为空");
        }
        // 转换为Category实体
        Category category = new Category();
        category.setId(id);
        category.setName(categoryUpdateDTO.getName());
        category.setType(categoryUpdateDTO.getType());
        category.setSort(categoryUpdateDTO.getSort());
        category.setStatus(categoryUpdateDTO.getStatus());

        boolean flag = categoryService.update(category);
        if (!flag) {
            return Result.error("修改失败");
        }
        return Result.success();
    }

    /**
     * 根据类型查询分类
     */
    @GetMapping("/list")
    public Result<List<Category>> list(@RequestParam(required = false) Integer type) {
        List<Category> list = categoryService.listByType(type);
        return Result.success(list);
    }
}
