package dev.erkut.searchservice.api;

import dev.erkut.searchservice.api.response.ProductSearchResponse;
import dev.erkut.searchservice.application.ProductSearchService;
import dev.erkut.searchservice.application.ProductSearchSort;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/search/products")
public class ProductSearchController {
    private final ProductSearchService productSearchService;

    public ProductSearchController(ProductSearchService productSearchService) {
        this.productSearchService = productSearchService;
    }

    @GetMapping
    public ResponseEntity<Page<ProductSearchResponse>> searchProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @PositiveOrZero BigDecimal minPrice,
            @RequestParam(required = false) @PositiveOrZero BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String sort
    ) {
        ProductSearchSort requestedSort = ProductSearchSort.fromParameter(sort);
        Page<ProductSearchResponse> response = productSearchService.search(
                q, minPrice, maxPrice, page, size, requestedSort);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
