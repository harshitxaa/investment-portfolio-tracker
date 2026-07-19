package com.portfoliotracker.service;

import com.portfoliotracker.dto.common.PagedResponse;
import com.portfoliotracker.dto.transaction.TransactionRequest;
import com.portfoliotracker.dto.transaction.TransactionResponse;
import com.portfoliotracker.entity.Portfolio;
import com.portfoliotracker.entity.Transaction;
import com.portfoliotracker.entity.TransactionType;
import com.portfoliotracker.exception.ResourceNotFoundException;
import com.portfoliotracker.repository.TransactionRepository;
import com.portfoliotracker.specification.TransactionSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final PortfolioService portfolioService;

    @Transactional
    public TransactionResponse recordTransaction(Long userId, Long portfolioId, TransactionRequest request) {
        Portfolio portfolio = portfolioService.getOwnedPortfolioOrThrow(userId, portfolioId);

        Transaction transaction = Transaction.builder()
                .portfolio(portfolio)
                .symbol(request.getSymbol().trim().toUpperCase())
                .type(request.getType())
                .quantity(request.getQuantity())
                .price(request.getPrice())
                .transactionDate(request.getTransactionDate())
                .build();

        return toResponse(transactionRepository.save(transaction));
    }

    public PagedResponse<TransactionResponse> search(Long userId, Long portfolioId, String symbol,
                                                       TransactionType type, LocalDate from, LocalDate to,
                                                       Pageable pageable) {
        // Ownership check - throws 404 if the portfolio doesn't belong to this user.
        portfolioService.getOwnedPortfolioOrThrow(userId, portfolioId);

        var spec = TransactionSpecification.build(portfolioId, symbol, type, from, to);
        Page<Transaction> page = transactionRepository.findAll(spec, pageable);
        return PagedResponse.fromPage(page.map(this::toResponse));
    }

    public TransactionResponse getOne(Long userId, Long portfolioId, Long transactionId) {
        portfolioService.getOwnedPortfolioOrThrow(userId, portfolioId);
        Transaction transaction = transactionRepository.findById(transactionId)
                .filter(tx -> tx.getPortfolio().getId().equals(portfolioId))
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id " + transactionId));
        return toResponse(transaction);
    }

    @Transactional
    public void delete(Long userId, Long portfolioId, Long transactionId) {
        portfolioService.getOwnedPortfolioOrThrow(userId, portfolioId);
        Transaction transaction = transactionRepository.findById(transactionId)
                .filter(tx -> tx.getPortfolio().getId().equals(portfolioId))
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id " + transactionId));
        transactionRepository.delete(transaction);
    }

    private TransactionResponse toResponse(Transaction tx) {
        return TransactionResponse.builder()
                .id(tx.getId())
                .portfolioId(tx.getPortfolio().getId())
                .symbol(tx.getSymbol())
                .type(tx.getType())
                .quantity(tx.getQuantity())
                .price(tx.getPrice())
                .totalAmount(tx.getQuantity().multiply(tx.getPrice()))
                .transactionDate(tx.getTransactionDate())
                .createdAt(tx.getCreatedAt())
                .build();
    }
}
