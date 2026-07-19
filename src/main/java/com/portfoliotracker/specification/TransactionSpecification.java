package com.portfoliotracker.specification;

import com.portfoliotracker.entity.Transaction;
import com.portfoliotracker.entity.TransactionType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * Builds a dynamic WHERE clause for transaction search/filter based on
 * whichever optional criteria were supplied. Each method returns null when
 * its criterion is absent so Specification.where(...).and(...) simply skips it.
 */
public final class TransactionSpecification {

    private TransactionSpecification() {
    }

    public static Specification<Transaction> belongsToPortfolio(Long portfolioId) {
        return (root, query, cb) -> cb.equal(root.get("portfolio").get("id"), portfolioId);
    }

    public static Specification<Transaction> hasSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(cb.upper(root.get("symbol")), symbol.toUpperCase());
    }

    public static Specification<Transaction> hasType(TransactionType type) {
        if (type == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<Transaction> fromDate(LocalDate from) {
        if (from == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("transactionDate"), from);
    }

    public static Specification<Transaction> toDate(LocalDate to) {
        if (to == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("transactionDate"), to);
    }

    public static Specification<Transaction> build(Long portfolioId, String symbol, TransactionType type,
                                                     LocalDate from, LocalDate to) {
        return Specification.where(belongsToPortfolio(portfolioId))
                .and(hasSymbol(symbol))
                .and(hasType(type))
                .and(fromDate(from))
                .and(toDate(to));
    }
}
