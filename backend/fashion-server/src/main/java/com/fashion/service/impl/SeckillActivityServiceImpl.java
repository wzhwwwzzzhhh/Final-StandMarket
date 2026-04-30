package com.fashion.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.fashion.entity.SeckillActivity;
import com.fashion.entity.PageResult;
import com.fashion.mapper.SeckillActivityMapper;
import com.fashion.service.SeckillActivityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class SeckillActivityServiceImpl implements SeckillActivityService {
    
    @Autowired
    private SeckillActivityMapper seckillActivityMapper;
    
    @Override
    public List<SeckillActivity> selectByCondition(Map<String, Object> params) {
        return seckillActivityMapper.selectByCondition(params);
    }
    
    @Override
    public List<SeckillActivity> listActivities(String name) {
        return seckillActivityMapper.listActivities(name);
    }
    
    @Override
    public PageResult<SeckillActivity> pageActivities(int page, int pageSize, String name) {
        // 开始分页
        PageHelper.startPage(page, pageSize);
        // 执行查询
        List<SeckillActivity> activities = seckillActivityMapper.listActivities(name);
        // 包装成PageInfo
        PageInfo<SeckillActivity> pageInfo = new PageInfo<>(activities);
        // 构造PageResult返回
        return new PageResult<>(pageInfo.getTotal(), pageInfo.getList());
    }
    
    @Override
    public SeckillActivity getById(Long id) {
        return seckillActivityMapper.getById(id);
    }
    
    @Override
    public boolean save(SeckillActivity seckillActivity) {
        return seckillActivityMapper.save(seckillActivity) > 0;
    }
    
    @Override
    public boolean update(SeckillActivity seckillActivity) {
        return seckillActivityMapper.update(seckillActivity) > 0;
    }
    
    @Override
    public boolean removeById(Long id) {
        return seckillActivityMapper.deleteById(id) > 0;
    }
}
