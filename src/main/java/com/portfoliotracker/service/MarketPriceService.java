package com.portfoliotracker.service;

import com.portfoliotracker.entity.MarketPrice;
import com.portfoliotracker.repository.MarketPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;

/**
 * Provides daily close prices for a symbol.
 *
 * There is no dependency on a paid market-data API here: the first time a
 * symbol is requested, a starting price is picked and a short history is
 * generated using a simple random walk (each day's price = previous day's
 * price * (1 + small random % change)). The history is persisted so that
 * later analytics calls (Sharpe ratio, volatility, returns) work off a
 * consistent, reproducible series for that symbol.
 *
 * Swapping this for a real integration (e.g. Alpha Vantage / Twelve Data)
 * would only require changing this one class - callers only depend on
 * getCurrentPrice()/getPriceHistory().
 */
@Service
@RequiredArgsConstructor
public class MarketPriceService {

    private final MarketPriceRepository marketPriceRepository;

    @Value("${app.market.price-history-days:90}")
    private int priceHistoryDays;

    private static final double DAILY_VOLATILITY = 0.015; // 1.5% simulated daily std dev

    public BigDecimal getCurrentPrice(String symbol) {
        ensureHistoryExists(symbol);
        return marketPriceRepository.findFirstBySymbolOrderByPriceDateDesc(symbol)
                .map(MarketPrice::getClosePrice)
                .orElseThrow(() -> new IllegalStateException("Unable to resolve a price for symbol " + symbol));
    }

    public List<MarketPrice> getPriceHistory(String symbol, int days) {
        ensureHistoryExists(symbol);
        LocalDate from = LocalDate.now().minusDays(days);
        return marketPriceRepository.findAllBySymbolAndPriceDateGreaterThanEqualOrderByPriceDateAsc(symbol, from);
    }

    private synchronized void ensureHistoryExists(String symbol) {
        String normalized = symbol.trim().toUpperCase();
        if (marketPriceRepository.existsBySymbol(normalized)) {
            return;
        }
        seedHistory(normalized);
    }

    private void seedHistory(String symbol) {
        // Deterministic-ish seed per symbol so re-running is not wildly different,
        // while still giving each symbol its own price trajectory.
        Random random = new Random(symbol.hashCode());
        double startingPrice = 20 + (Math.abs(symbol.hashCode()) % 480); // between 20 and 500

        double price = startingPrice;
        LocalDate date = LocalDate.now().minusDays(priceHistoryDays);

        for (int i = 0; i <= priceHistoryDays; i++) {
            double changePct = random.nextGaussian() * DAILY_VOLATILITY;
            price = Math.max(1.0, price * (1 + changePct));

            MarketPrice marketPrice = MarketPrice.builder()
                    .symbol(symbol)
                    .closePrice(BigDecimal.valueOf(price).setScale(4, RoundingMode.HALF_UP))
                    .priceDate(date)
                    .build();
            marketPriceRepository.save(marketPrice);
            date = date.plusDays(1);
        }
    }
}
