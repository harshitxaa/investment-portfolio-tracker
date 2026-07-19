package com.portfoliotracker.controller;

import com.portfoliotracker.dto.watchlist.WatchlistRequest;
import com.portfoliotracker.dto.watchlist.WatchlistResponse;
import com.portfoliotracker.security.SecurityUtils;
import com.portfoliotracker.service.WatchlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/watchlist")
@RequiredArgsConstructor
@Tag(name = "Watchlist", description = "Track symbols of interest without owning them")
public class WatchlistController {

    private final WatchlistService watchlistService;

    @PostMapping
    @Operation(summary = "Add a symbol to the current user's watchlist")
    public ResponseEntity<WatchlistResponse> add(@Valid @RequestBody WatchlistRequest request) {
        WatchlistResponse response = watchlistService.addToWatchlist(SecurityUtils.getCurrentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List the current user's watchlist with live simulated prices")
    public ResponseEntity<List<WatchlistResponse>> getAll() {
        return ResponseEntity.ok(watchlistService.getWatchlist(SecurityUtils.getCurrentUserId()));
    }

    @DeleteMapping("/{watchlistItemId}")
    @Operation(summary = "Remove a symbol from the watchlist")
    public ResponseEntity<Void> remove(@PathVariable Long watchlistItemId) {
        watchlistService.removeFromWatchlist(SecurityUtils.getCurrentUserId(), watchlistItemId);
        return ResponseEntity.noContent().build();
    }
}
