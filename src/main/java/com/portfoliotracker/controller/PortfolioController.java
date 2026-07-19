package com.portfoliotracker.controller;

import com.portfoliotracker.dto.portfolio.PortfolioRequest;
import com.portfoliotracker.dto.portfolio.PortfolioResponse;
import com.portfoliotracker.dto.portfolio.PortfolioSummaryResponse;
import com.portfoliotracker.security.SecurityUtils;
import com.portfoliotracker.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/portfolios")
@RequiredArgsConstructor
@Tag(name = "Portfolios", description = "Create and manage investment portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;

    @PostMapping
    @Operation(summary = "Create a new portfolio for the current user")
    public ResponseEntity<PortfolioResponse> create(@Valid @RequestBody PortfolioRequest request) {
        PortfolioResponse response = portfolioService.createPortfolio(SecurityUtils.getCurrentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List all portfolios owned by the current user")
    public ResponseEntity<List<PortfolioResponse>> getAll() {
        return ResponseEntity.ok(portfolioService.getPortfolios(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/{portfolioId}")
    @Operation(summary = "Get a single portfolio by id")
    public ResponseEntity<PortfolioResponse> getOne(@PathVariable Long portfolioId) {
        return ResponseEntity.ok(portfolioService.getPortfolio(SecurityUtils.getCurrentUserId(), portfolioId));
    }

    @PutMapping("/{portfolioId}")
    @Operation(summary = "Update a portfolio's name/description")
    public ResponseEntity<PortfolioResponse> update(@PathVariable Long portfolioId,
                                                      @Valid @RequestBody PortfolioRequest request) {
        return ResponseEntity.ok(
                portfolioService.updatePortfolio(SecurityUtils.getCurrentUserId(), portfolioId, request));
    }

    @DeleteMapping("/{portfolioId}")
    @Operation(summary = "Delete a portfolio and all of its transactions")
    public ResponseEntity<Void> delete(@PathVariable Long portfolioId) {
        portfolioService.deletePortfolio(SecurityUtils.getCurrentUserId(), portfolioId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{portfolioId}/summary")
    @Operation(summary = "Get computed holdings, market value and P/L for a portfolio")
    public ResponseEntity<PortfolioSummaryResponse> getSummary(@PathVariable Long portfolioId) {
        return ResponseEntity.ok(portfolioService.getSummary(SecurityUtils.getCurrentUserId(), portfolioId));
    }
}
