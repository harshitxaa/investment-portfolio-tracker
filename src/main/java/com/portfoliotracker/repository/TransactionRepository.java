package com.portfoliotracker.repository;

import com.portfoliotracker.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long>,
        JpaSpecificationExecutor<Transaction> {

    List<Transaction> findAllByPortfolioIdOrderByTransactionDateAsc(Long portfolioId);

    List<Transaction> findAllByPortfolioId(Long portfolioId);
}
