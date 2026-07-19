package com.portfoliotracker.dto.transaction;

import com.portfoliotracker.entity.TransactionType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRequest {

    @NotBlank(message = "Symbol is required")
    @Size(max = 15, message = "Symbol must be at most 15 characters")
    private String symbol;

    @NotNull(message = "Transaction type is required")
    private TransactionType type;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.0001", message = "Quantity must be greater than zero")
    private BigDecimal quantity;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0001", message = "Price must be greater than zero")
    private BigDecimal price;

    @NotNull(message = "Transaction date is required")
    @PastOrPresent(message = "Transaction date cannot be in the future")
    private LocalDate transactionDate;
}
