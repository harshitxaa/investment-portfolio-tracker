package com.portfoliotracker.controller;

import com.portfoliotracker.dto.common.PagedResponse;
import com.portfoliotracker.dto.transaction.TransactionRequest;
import com.portfoliotracker.dto.transaction.TransactionResponse;
import com.portfoliotracker.entity.TransactionType;
import com.portfoliotracker.security.SecurityUtils;
import com.portfoliotracker.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/portfolios/{portfolioId}/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Record and search buy/sell transactions within a portfolio")
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    @Operation(summary = "Record a BUY or SELL transaction")
    public ResponseEntity<TransactionResponse> create(@PathVariable Long portfolioId,
                                                        @Valid @RequestBody TransactionRequest request) {
        TransactionResponse response = transactionService.recordTransaction(
                SecurityUtils.getCurrentUserId(), portfolioId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Search transaction history with optional filters, sorting and pagination",
            description = "Sort with e.g. ?sort=transactionDate,desc  Filter with symbol, type, from, to query params")
    public ResponseEntity<PagedResponse<TransactionResponse>> search(
            @PathVariable Long portfolioId,
            @Parameter(description = "Filter by ticker symbol, e.g. AAPL") @RequestParam(required = false) String symbol,
            @Parameter(description = "Filter by BUY or SELL") @RequestParam(required = false) TransactionType type,
            @Parameter(description = "Only include transactions on/after this date") @RequestParam(required = false) LocalDate from,
            @Parameter(description = "Only include transactions on/before this date") @RequestParam(required = false) LocalDate to,
            Pageable pageable) {

        return ResponseEntity.ok(transactionService.search(
                SecurityUtils.getCurrentUserId(), portfolioId, symbol, type, from, to, pageable));
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Get a single transaction")
    public ResponseEntity<TransactionResponse> getOne(@PathVariable Long portfolioId,
                                                        @PathVariable Long transactionId) {
        return ResponseEntity.ok(transactionService.getOne(
                SecurityUtils.getCurrentUserId(), portfolioId, transactionId));
    }

    @DeleteMapping("/{transactionId}")
    @Operation(summary = "Delete a transaction")
    public ResponseEntity<Void> delete(@PathVariable Long portfolioId, @PathVariable Long transactionId) {
        transactionService.delete(SecurityUtils.getCurrentUserId(), portfolioId, transactionId);
        return ResponseEntity.noContent().build();
    }
}
