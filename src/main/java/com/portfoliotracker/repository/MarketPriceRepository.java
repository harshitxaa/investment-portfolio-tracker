package com.portfoliotracker.repository;

import com.portfoliotracker.entity.MarketPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MarketPriceRepository extends JpaRepository<MarketPrice, Long> {

    boolean existsBySymbol(String symbol);

    List<MarketPrice> findAllBySymbolOrderByPriceDateAsc(String symbol);

    Optional<MarketPrice> findFirstBySymbolOrderByPriceDateDesc(String symbol);

    List<MarketPrice> findAllBySymbolAndPriceDateGreaterThanEqualOrderByPriceDateAsc(
            String symbol, LocalDate fromDate);
}
