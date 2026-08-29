package dev.erkut.productservice.service;

import dev.erkut.productservice.dto.ProductBulkRequest;
import dev.erkut.productservice.dto.ProductCreateRequest;
import dev.erkut.productservice.dto.ProductResponse;
import dev.erkut.productservice.dto.ProductUpdateRequest;
import dev.erkut.productservice.exception.ProductNotFoundException;
import dev.erkut.productservice.mapper.ProductMapper;
import dev.erkut.productservice.model.Product;
import dev.erkut.productservice.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public ProductResponse createProduct(ProductCreateRequest req) {
        Product product = Product.create(req.name(), req.price(), Instant.now());
        Product savedProduct = productRepository.save(product);
        return ProductMapper.toResponse(savedProduct);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductWithId(UUID productId) {
        Product product = findProductWithId(productId);
        return ProductMapper.toResponse(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> getProducts(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending()
                .and(Sort.by(Sort.Direction.DESC, "id")));
        Page<Product> products = productRepository.findAll(pageable);
        return products.map(ProductMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> bulkLookup(ProductBulkRequest req) {
        Set<UUID> requestedIds = new HashSet<>(req.requestedProductIds());

        List<Product> products = productRepository.findAllById(requestedIds);

        Set<UUID> foundIds = products.stream()
                .map(Product::getId)
                .collect(Collectors.toSet());

        Set<UUID> missingIds = new HashSet<>(requestedIds);
        missingIds.removeAll(foundIds);

        if (!missingIds.isEmpty()) {
            throw new ProductNotFoundException("Product(s) not found with id(s): " + missingIds);
        }

        return products.stream()
                .map(ProductMapper::toResponse)
                .toList();
    }

    @Transactional
    public ProductResponse deactivateProduct(UUID productId) {
        Product product = findProductWithId(productId);
        product.deactivateProduct(Instant.now());
        return ProductMapper.toResponse(product);
    }

    @Transactional
    public void deleteProduct(UUID productId) {
        Product product = findProductWithId(productId);
        productRepository.delete(product);
    }

    @Transactional
    public ProductResponse updateProduct(UUID productId, ProductUpdateRequest req) {
        if (req.name() == null && req.price() == null) {
            throw new IllegalArgumentException("Product name and/or price must be provided");
        }

        Product product = findProductWithId(productId);
        Instant now = Instant.now();

        if(req.name() != null) {
            product.changeProductName(req.name(), now);
        }

        if(req.price() != null) {
            product.changeProductPrice(req.price(), now);
        }

        return ProductMapper.toResponse(product);
    }

    private Product findProductWithId(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + productId));
    }
}
