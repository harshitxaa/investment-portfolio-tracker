package com.portfoliotracker.repository;

import com.portfoliotracker.entity.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchlistRepository extends JpaRepository<WatchlistItem, Long> {
    List<WatchlistItem> findAllByUserId(Long userId);
    Optional<WatchlistItem> findByIdAndUserId(Long id, Long userId);
    boolean existsByUserIdAndSymbol(Long userId, String symbol);
}
