package com.fashion.service;

import com.fashion.entity.Favorite;

import java.util.List;

public interface FavoriteService {
    boolean add(Long userId, Long productId);
    boolean remove(Long userId, Long productId);
    boolean isFavorited(Long userId, Long productId);
    List<Favorite> list(Long userId);
    int count(Long userId);
}
