package com.portfoliotracker.dto.watchlist;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistResponse {
    private Long id;
    private String symbol;
    private String notes;
    private BigDecimal currentPrice;
    private LocalDateTime addedAt;
}
