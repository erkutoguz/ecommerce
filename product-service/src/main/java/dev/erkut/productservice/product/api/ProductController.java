package dev.erkut.productservice.product.api;

import dev.erkut.productservice.product.api.request.ProductBulkRequest;
import dev.erkut.productservice.product.api.request.ProductCreateRequest;
import dev.erkut.productservice.product.api.response.ProductResponse;
import dev.erkut.productservice.product.api.request.ProductUpdateRequest;
import dev.erkut.productservice.product.application.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductCreateRequest req) {
        ProductResponse response = productService.createProduct(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponse> getProductWithId(@PathVariable("productId") UUID productId) {
        ProductResponse response = productService.getProductWithId(productId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getProducts(
            @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(value = "size", defaultValue = "10") @Min(1) @Max(100) int size
    ) {
        Page<ProductResponse> response = productService.getProducts(page, size);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/bulk-lookup")
    public ResponseEntity<List<ProductResponse>> bulkLookup(@Valid @RequestBody ProductBulkRequest req) {
        List<ProductResponse> response = productService.bulkLookup(req);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/{productId}/deactivate")
    public ResponseEntity<ProductResponse> deactivateProduct(@PathVariable("productId") UUID productId) {
        ProductResponse response = productService.deactivateProduct(productId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> deleteProductWithId(@PathVariable("productId") UUID productId) {
        productService.deleteProduct(productId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{productId}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody ProductUpdateRequest req) {
        ProductResponse response = productService.updateProduct(productId, req);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

}
