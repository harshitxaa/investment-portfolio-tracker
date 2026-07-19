package com.portfoliotracker.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Basic performance metrics for a portfolio, computed from its current
 * holdings against a simulated daily price history (see MarketPriceService).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsResponse {
    private Long portfolioId;
    private int lookbackDays;
    private BigDecimal totalReturnPct;
    private BigDecimal unrealizedProfitLoss;
    private BigDecimal annualizedVolatilityPct;
    private BigDecimal sharpeRatio;
    private BigDecimal riskFreeRatePct;
}
