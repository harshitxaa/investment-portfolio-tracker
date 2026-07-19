package com.portfoliotracker.dto.portfolio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioSummaryResponse {
    private Long portfolioId;
    private String portfolioName;
    private BigDecimal totalMarketValue;
    private BigDecimal totalCostBasis;
    private BigDecimal totalUnrealizedProfitLoss;
    private BigDecimal totalUnrealizedReturnPct;
    private List<HoldingResponse> holdings;
}
