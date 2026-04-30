package com.fashion.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.fashion.entity.Category;
import com.fashion.entity.PageResult;
import com.fashion.mapper.CategoryMapper;
import com.fashion.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class CategoryServiceImpl implements CategoryService {
    
    @Autowired
    private CategoryMapper categoryMapper;
    
    @Override
    public List<Category> selectByCondition(Map<String, Object> params) {
        return categoryMapper.selectByCondition(params);
    }
    
    @Override
    public List<Category> list() {
        return categoryMapper.list();
    }
    
    @Override
    public PageResult<Category> pageCategories(int page, int pageSize) {
        // 开始分页
        PageHelper.startPage(page, pageSize);
        // 执行查询
        List<Category> categories = categoryMapper.list();
        // 包装成PageInfo
        PageInfo<Category> pageInfo = new PageInfo<>(categories);
        // 构造PageResult返回
        return new PageResult<>(pageInfo.getTotal(), pageInfo.getList());
    }
    
    @Override
    public List<Category> listByType(Integer type) {
        return categoryMapper.listByType(type);
    }
    
    @Override
    public boolean save(Category category) {
        return categoryMapper.save(category) > 0;
    }
    
    @Override
    public boolean update(Category category) {
        return categoryMapper.update(category) > 0;
    }
    
    @Override
    public boolean removeById(Long id) {
        return categoryMapper.deleteById(id) > 0;
    }

    @Override
    public Category getById(Long id) {
        return categoryMapper.selectById(id);
    }
}
