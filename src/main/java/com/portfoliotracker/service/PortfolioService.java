package com.portfoliotracker.service;

import com.portfoliotracker.dto.portfolio.HoldingResponse;
import com.portfoliotracker.dto.portfolio.PortfolioRequest;
import com.portfoliotracker.dto.portfolio.PortfolioResponse;
import com.portfoliotracker.dto.portfolio.PortfolioSummaryResponse;
import com.portfoliotracker.entity.Portfolio;
import com.portfoliotracker.entity.Transaction;
import com.portfoliotracker.entity.TransactionType;
import com.portfoliotracker.entity.User;
import com.portfoliotracker.exception.BadRequestException;
import com.portfoliotracker.exception.ResourceNotFoundException;
import com.portfoliotracker.repository.PortfolioRepository;
import com.portfoliotracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final UserRepository userRepository;
    private final MarketPriceService marketPriceService;

    private static final int SCALE = 4;

    @Transactional
    public PortfolioResponse createPortfolio(Long userId, PortfolioRequest request) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + userId));

        Portfolio portfolio = Portfolio.builder()
                .name(request.getName())
                .description(request.getDescription())
                .owner(owner)
                .build();

        return toResponse(portfolioRepository.save(portfolio));
    }

    public List<PortfolioResponse> getPortfolios(Long userId) {
        return portfolioRepository.findAllByOwnerId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public PortfolioResponse getPortfolio(Long userId, Long portfolioId) {
        return toResponse(getOwnedPortfolioOrThrow(userId, portfolioId));
    }

    @Transactional
    public PortfolioResponse updatePortfolio(Long userId, Long portfolioId, PortfolioRequest request) {
        Portfolio portfolio = getOwnedPortfolioOrThrow(userId, portfolioId);
        portfolio.setName(request.getName());
        portfolio.setDescription(request.getDescription());
        return toResponse(portfolioRepository.save(portfolio));
    }

    @Transactional
    public void deletePortfolio(Long userId, Long portfolioId) {
        Portfolio portfolio = getOwnedPortfolioOrThrow(userId, portfolioId);
        portfolioRepository.delete(portfolio);
    }

    /**
     * Computes current holdings for a portfolio using the weighted-average
     * cost method: buys update the running average cost, sells reduce
     * quantity while leaving the average cost basis unchanged. This mirrors
     * how brokerages commonly report "average cost" for tax/reporting
     * purposes.
     */
    @Transactional(readOnly = true)
    public PortfolioSummaryResponse getSummary(Long userId, Long portfolioId) {
        Portfolio portfolio = getOwnedPortfolioOrThrow(userId, portfolioId);

        Map<String, HoldingAccumulator> holdingsBySymbol = new LinkedHashMap<>();

        List<Transaction> transactions = new ArrayList<>(portfolio.getTransactions());
        transactions.sort(Comparator.comparing(Transaction::getTransactionDate)
                .thenComparing(Transaction::getId));

        for (Transaction tx : transactions) {
            HoldingAccumulator acc = holdingsBySymbol.computeIfAbsent(tx.getSymbol(), s -> new HoldingAccumulator());
            if (tx.getType() == TransactionType.BUY) {
                acc.applyBuy(tx.getQuantity(), tx.getPrice());
            } else {
                acc.applySell(tx.getSymbol(), tx.getQuantity());
            }
        }

        List<HoldingResponse> holdings = new ArrayList<>();
        BigDecimal totalMarketValue = BigDecimal.ZERO;
        BigDecimal totalCostBasis = BigDecimal.ZERO;

        for (Map.Entry<String, HoldingAccumulator> entry : holdingsBySymbol.entrySet()) {
            HoldingAccumulator acc = entry.getValue();
            if (acc.quantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue; // fully sold out of this symbol
            }

            String symbol = entry.getKey();
            BigDecimal currentPrice = marketPriceService.getCurrentPrice(symbol);
            BigDecimal marketValue = acc.quantity.multiply(currentPrice).setScale(SCALE, RoundingMode.HALF_UP);
            BigDecimal costBasis = acc.quantity.multiply(acc.averageCost).setScale(SCALE, RoundingMode.HALF_UP);
            BigDecimal unrealizedPL = marketValue.subtract(costBasis);
            BigDecimal returnPct = costBasis.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : unrealizedPL.divide(costBasis, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);

            holdings.add(HoldingResponse.builder()
                    .symbol(symbol)
                    .quantity(acc.quantity.setScale(SCALE, RoundingMode.HALF_UP))
                    .averageCost(acc.averageCost.setScale(SCALE, RoundingMode.HALF_UP))
                    .currentPrice(currentPrice)
                    .marketValue(marketValue)
                    .costBasis(costBasis)
                    .unrealizedProfitLoss(unrealizedPL)
                    .unrealizedReturnPct(returnPct)
                    .build());

            totalMarketValue = totalMarketValue.add(marketValue);
            totalCostBasis = totalCostBasis.add(costBasis);
        }

        BigDecimal totalPL = totalMarketValue.subtract(totalCostBasis);
        BigDecimal totalReturnPct = totalCostBasis.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : totalPL.divide(totalCostBasis, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);

        return PortfolioSummaryResponse.builder()
                .portfolioId(portfolio.getId())
                .portfolioName(portfolio.getName())
                .totalMarketValue(totalMarketValue.setScale(2, RoundingMode.HALF_UP))
                .totalCostBasis(totalCostBasis.setScale(2, RoundingMode.HALF_UP))
                .totalUnrealizedProfitLoss(totalPL.setScale(2, RoundingMode.HALF_UP))
                .totalUnrealizedReturnPct(totalReturnPct)
                .holdings(holdings)
                .build();
    }

    /**
     * Returns just the current quantity held per symbol (no pricing),
     * used by AnalyticsService to build a historical portfolio value series.
     */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> getCurrentHoldingQuantities(Portfolio portfolio) {
        Map<String, HoldingAccumulator> holdingsBySymbol = new LinkedHashMap<>();

        List<Transaction> transactions = new ArrayList<>(portfolio.getTransactions());
        transactions.sort(Comparator.comparing(Transaction::getTransactionDate)
                .thenComparing(Transaction::getId));

        for (Transaction tx : transactions) {
            HoldingAccumulator acc = holdingsBySymbol.computeIfAbsent(tx.getSymbol(), s -> new HoldingAccumulator());
            if (tx.getType() == TransactionType.BUY) {
                acc.applyBuy(tx.getQuantity(), tx.getPrice());
            } else {
                acc.applySell(tx.getSymbol(), tx.getQuantity());
            }
        }

        Map<String, BigDecimal> quantities = new LinkedHashMap<>();
        for (Map.Entry<String, HoldingAccumulator> entry : holdingsBySymbol.entrySet()) {
            if (entry.getValue().quantity.compareTo(BigDecimal.ZERO) > 0) {
                quantities.put(entry.getKey(), entry.getValue().quantity);
            }
        }

        return quantities;
    }

    public Portfolio getOwnedPortfolioOrThrow(Long userId, Long portfolioId) {
        return portfolioRepository.findByIdAndOwnerId(portfolioId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Portfolio not found with id " + portfolioId + " for the current user"));
    }

    private PortfolioResponse toResponse(Portfolio portfolio) {
        return PortfolioResponse.builder()
                .id(portfolio.getId())
                .name(portfolio.getName())
                .description(portfolio.getDescription())
                .createdAt(portfolio.getCreatedAt())
                .updatedAt(portfolio.getUpdatedAt())
                .build();
    }

    /** Tracks running quantity + weighted-average cost for a single symbol. */
    private static class HoldingAccumulator {
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal averageCost = BigDecimal.ZERO;

        void applyBuy(BigDecimal qty, BigDecimal price) {
            BigDecimal newQuantity = quantity.add(qty);
            BigDecimal existingCost = quantity.multiply(averageCost);
            BigDecimal newCost = qty.multiply(price);
            averageCost = newQuantity.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : existingCost.add(newCost).divide(newQuantity, 8, RoundingMode.HALF_UP);
            quantity = newQuantity;
        }

        void applySell(String symbol, BigDecimal qty) {
            if (qty.compareTo(quantity) > 0) {
                throw new BadRequestException(
                        "Cannot sell " + qty + " units of " + symbol + "; only " + quantity + " held at that point in time");
            }
            quantity = quantity.subtract(qty);
        }
    }
}
