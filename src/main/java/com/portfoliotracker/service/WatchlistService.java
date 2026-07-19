package com.portfoliotracker.service;

import com.portfoliotracker.dto.watchlist.WatchlistRequest;
import com.portfoliotracker.dto.watchlist.WatchlistResponse;
import com.portfoliotracker.entity.User;
import com.portfoliotracker.entity.WatchlistItem;
import com.portfoliotracker.exception.DuplicateResourceException;
import com.portfoliotracker.exception.ResourceNotFoundException;
import com.portfoliotracker.repository.UserRepository;
import com.portfoliotracker.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final UserRepository userRepository;
    private final MarketPriceService marketPriceService;

    @Transactional
    public WatchlistResponse addToWatchlist(Long userId, WatchlistRequest request) {
        String symbol = request.getSymbol().trim().toUpperCase();

        if (watchlistRepository.existsByUserIdAndSymbol(userId, symbol)) {
            throw new DuplicateResourceException(symbol + " is already on your watchlist");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + userId));

        WatchlistItem item = WatchlistItem.builder()
                .user(user)
                .symbol(symbol)
                .notes(request.getNotes())
                .build();

        return toResponse(watchlistRepository.save(item));
    }

    public List<WatchlistResponse> getWatchlist(Long userId) {
        return watchlistRepository.findAllByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void removeFromWatchlist(Long userId, Long watchlistItemId) {
        WatchlistItem item = watchlistRepository.findByIdAndUserId(watchlistItemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Watchlist item not found with id " + watchlistItemId));
        watchlistRepository.delete(item);
    }

    private WatchlistResponse toResponse(WatchlistItem item) {
        return WatchlistResponse.builder()
                .id(item.getId())
                .symbol(item.getSymbol())
                .notes(item.getNotes())
                .currentPrice(marketPriceService.getCurrentPrice(item.getSymbol()))
                .addedAt(item.getAddedAt())
                .build();
    }
}
