package com.portfoliotracker.dto.watchlist;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistRequest {

    @NotBlank(message = "Symbol is required")
    @Size(max = 15, message = "Symbol must be at most 15 characters")
    private String symbol;

    @Size(max = 300, message = "Notes must be at most 300 characters")
    private String notes;
}
