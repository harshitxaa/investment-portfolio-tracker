package com.portfoliotracker.service;

import com.portfoliotracker.dto.analytics.AnalyticsResponse;
import com.portfoliotracker.entity.MarketPrice;
import com.portfoliotracker.entity.Portfolio;
import com.portfoliotracker.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Computes basic portfolio performance metrics.
 *
 * SIMPLIFYING ASSUMPTION (documented deliberately, this is a backend-focused
 * project rather than a quant-finance one): the portfolio's current holding
 * quantities are treated as constant across the whole lookback window when
 * building the historical value series. A production system would instead
 * replay actual buy/sell events day-by-day. This keeps the calculation
 * logic easy to follow while still exercising real time-series math.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final PortfolioService portfolioService;
    private final MarketPriceService marketPriceService;

    private static final int TRADING_DAYS_PER_YEAR = 252;

    @Value("${app.market.price-history-days:90}")
    private int defaultLookbackDays;

    @Transactional
    public AnalyticsResponse computeAnalytics(Long userId, Long portfolioId, Integer lookbackDaysParam,
                                               Double riskFreeRatePctParam) {
        Portfolio portfolio = portfolioService.getOwnedPortfolioOrThrow(userId, portfolioId);
        int lookbackDays = lookbackDaysParam != null ? lookbackDaysParam : defaultLookbackDays;
        BigDecimal riskFreeRatePct = riskFreeRatePctParam != null
                ? BigDecimal.valueOf(riskFreeRatePctParam)
                : BigDecimal.valueOf(2.0); // 2% annual, a reasonable illustrative default

        Map<String, BigDecimal> quantities = portfolioService.getCurrentHoldingQuantities(portfolio);
        if (quantities.isEmpty()) {
            throw new BadRequestException("Portfolio has no current holdings to analyze");
        }

        // Build the aligned daily portfolio value series: for each date in the
        // lookback window, value = sum(quantity_held * closePrice on that date).
        Map<LocalDate, BigDecimal> portfolioValueByDate = new TreeMap<>();

        for (Map.Entry<String, BigDecimal> holding : quantities.entrySet()) {
            String symbol = holding.getKey();
            BigDecimal qty = holding.getValue();
            List<MarketPrice> history = marketPriceService.getPriceHistory(symbol, lookbackDays);

            for (MarketPrice mp : history) {
                portfolioValueByDate.merge(mp.getPriceDate(), qty.multiply(mp.getClosePrice()), BigDecimal::add);
            }
        }

        List<BigDecimal> values = portfolioValueByDate.values().stream().toList();
        if (values.size() < 2) {
            throw new BadRequestException("Not enough price history to compute analytics yet");
        }

        List<Double> dailyReturns = computeDailyReturns(values);

        double meanDailyReturn = mean(dailyReturns);
        double dailyStdDev = stdDev(dailyReturns, meanDailyReturn);

        double annualizedVolatility = dailyStdDev * Math.sqrt(TRADING_DAYS_PER_YEAR);
        double annualizedReturn = meanDailyReturn * TRADING_DAYS_PER_YEAR;

        double riskFreeDecimal = riskFreeRatePct.doubleValue() / 100.0;
        double sharpeRatio = annualizedVolatility == 0.0
                ? 0.0
                : (annualizedReturn - riskFreeDecimal) / annualizedVolatility;

        BigDecimal startValue = values.get(0);
        BigDecimal endValue = values.get(values.size() - 1);
        BigDecimal totalReturnPct = startValue.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : endValue.subtract(startValue).divide(startValue, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

        BigDecimal unrealizedPL = endValue.subtract(startValue);

        return AnalyticsResponse.builder()
                .portfolioId(portfolio.getId())
                .lookbackDays(lookbackDays)
                .totalReturnPct(totalReturnPct.setScale(2, RoundingMode.HALF_UP))
                .unrealizedProfitLoss(unrealizedPL.setScale(2, RoundingMode.HALF_UP))
                .annualizedVolatilityPct(BigDecimal.valueOf(annualizedVolatility * 100)
                        .setScale(2, RoundingMode.HALF_UP))
                .sharpeRatio(BigDecimal.valueOf(sharpeRatio).setScale(4, RoundingMode.HALF_UP))
                .riskFreeRatePct(riskFreeRatePct.setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    private List<Double> computeDailyReturns(List<BigDecimal> values) {
        return java.util.stream.IntStream.range(1, values.size())
                .mapToObj(i -> {
                    BigDecimal prev = values.get(i - 1);
                    BigDecimal curr = values.get(i);
                    if (prev.compareTo(BigDecimal.ZERO) == 0) {
                        return 0.0;
                    }
                    return curr.subtract(prev).divide(prev, 10, RoundingMode.HALF_UP).doubleValue();
                })
                .toList();
    }

    private double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private double stdDev(List<Double> values, double mean) {
        double sumSquaredDiffs = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .sum();
        // Sample standard deviation (n-1 denominator).
        return values.size() <= 1 ? 0.0 : Math.sqrt(sumSquaredDiffs / (values.size() - 1));
    }
}
