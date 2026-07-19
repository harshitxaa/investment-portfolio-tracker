package com.portfoliotracker.controller;

import com.portfoliotracker.dto.analytics.AnalyticsResponse;
import com.portfoliotracker.security.SecurityUtils;
import com.portfoliotracker.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/portfolios/{portfolioId}/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Basic performance metrics: returns, P/L, volatility, Sharpe ratio")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping
    @Operation(summary = "Compute returns, P/L, annualized volatility and Sharpe ratio for a portfolio",
            description = "Uses a simulated daily price feed over the lookback window. " +
                    "See MarketPriceService / AnalyticsService for the documented assumptions.")
    public ResponseEntity<AnalyticsResponse> getAnalytics(
            @PathVariable Long portfolioId,
            @Parameter(description = "Number of days of history to analyze, default 90")
            @RequestParam(required = false) Integer lookbackDays,
            @Parameter(description = "Annual risk-free rate as a percentage, default 2.0")
            @RequestParam(required = false) Double riskFreeRatePct) {

        return ResponseEntity.ok(analyticsService.computeAnalytics(
                SecurityUtils.getCurrentUserId(), portfolioId, lookbackDays, riskFreeRatePct));
    }
}
