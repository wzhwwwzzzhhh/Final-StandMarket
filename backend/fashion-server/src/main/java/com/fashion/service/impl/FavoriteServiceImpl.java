package com.fashion.service.impl;

import com.fashion.entity.Favorite;
import com.fashion.mapper.FavoriteMapper;
import com.fashion.service.FavoriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FavoriteServiceImpl implements FavoriteService {

    @Autowired
    private FavoriteMapper favoriteMapper;

    @Override
    public boolean add(Long userId, Long productId) {
        Favorite existing = favoriteMapper.selectByUserIdAndProductId(userId, productId);
        if (existing != null) {
            return true;
        }
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setProductId(productId);
        favorite.setCreateTime(LocalDateTime.now());
        return favoriteMapper.insert(favorite) > 0;
    }

    @Override
    public boolean remove(Long userId, Long productId) {
        return favoriteMapper.deleteByUserIdAndProductId(userId, productId) > 0;
    }

    @Override
    public boolean isFavorited(Long userId, Long productId) {
        return favoriteMapper.selectByUserIdAndProductId(userId, productId) != null;
    }

    @Override
    public List<Favorite> list(Long userId) {
        return favoriteMapper.selectByUserId(userId);
    }

    @Override
    public int count(Long userId) {
        return favoriteMapper.countByUserId(userId);
    }
}
