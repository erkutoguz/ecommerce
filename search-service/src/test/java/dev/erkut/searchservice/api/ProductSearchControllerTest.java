package dev.erkut.searchservice.api;

import dev.erkut.searchservice.api.error.GlobalExceptionHandler;
import dev.erkut.searchservice.api.response.ProductSearchResponse;
import dev.erkut.searchservice.application.ProductSearchService;
import dev.erkut.searchservice.application.ProductSearchSort;
import dev.erkut.searchservice.document.ProductStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.elasticsearch.UncategorizedElasticsearchException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductSearchController.class)
@Import(GlobalExceptionHandler.class)
class ProductSearchControllerTest {
    private static final UUID PRODUCT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductSearchService productSearchService;

    @Test
    void defaultSearchUsesRepositoryPaginationDefaultsAndReturnsPageResponse() throws Exception {
        when(productSearchService.search(null, null, null, 0, 10, null))
                .thenReturn(new PageImpl<>(List.of(response()), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/search/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].productId").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.content[0].name").value("Apple iPhone 16 Pro"))
                .andExpect(jsonPath("$.content[0].price").value(65000))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(10));

        verify(productSearchService).search(null, null, null, 0, 10, null);
    }

    @Test
    void searchForwardsQueryPriceBoundsPaginationAndControlledSort() throws Exception {
        when(productSearchService.search(
                eq("iphone"), eq(new BigDecimal("1000")), eq(new BigDecimal("50000")),
                eq(1), eq(5), eq(ProductSearchSort.PRICE_ASC)))
                .thenReturn(new PageImpl<>(List.of(response())));

        mockMvc.perform(get("/search/products")
                        .param("q", "iphone")
                        .param("minPrice", "1000")
                        .param("maxPrice", "50000")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sort", "price_asc"))
                .andExpect(status().isOk());

        verify(productSearchService).search(
                "iphone", new BigDecimal("1000"), new BigDecimal("50000"),
                1, 5, ProductSearchSort.PRICE_ASC);
    }

    @Test
    void invalidPageSizePricesAndMalformedParametersReturnBadRequest() throws Exception {
        mockMvc.perform(get("/search/products").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Request validation failed"));
        mockMvc.perform(get("/search/products").param("size", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/search/products").param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/search/products").param("minPrice", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/search/products").param("maxPrice", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/search/products").param("minPrice", "not-a-number"))
                .andExpect(status().isBadRequest());
        verify(productSearchService, never()).search(any(), any(), any(), any(Integer.class),
                any(Integer.class), any());
    }

    @Test
    void unsupportedSortAndDeepPaginationReturnBadRequest() throws Exception {
        mockMvc.perform(get("/search/products").param("sort", "name"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unsupported sort value: name"));

        when(productSearchService.search(null, null, null, 100, 100, null))
                .thenThrow(new IllegalArgumentException("Requested page exceeds the maximum result window"));
        mockMvc.perform(get("/search/products").param("page", "100").param("size", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Requested page exceeds the maximum result window"));

        verify(productSearchService, never()).search(null, null, null, 0, 10, null);
    }

    @Test
    void invalidPriceRangeAndRelevanceWithoutQueryReturnBadRequest() throws Exception {
        when(productSearchService.search(null, new BigDecimal("20"), new BigDecimal("10"), 0, 10, null))
                .thenThrow(new IllegalArgumentException("minPrice must be less than or equal to maxPrice"));
        mockMvc.perform(get("/search/products")
                        .param("minPrice", "20")
                        .param("maxPrice", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("minPrice must be less than or equal to maxPrice"));

        when(productSearchService.search("  ", null, null, 0, 10, ProductSearchSort.RELEVANCE))
                .thenThrow(new IllegalArgumentException("relevance sort requires a nonblank q parameter"));
        mockMvc.perform(get("/search/products").param("q", "  ").param("sort", "relevance"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elasticsearchInfrastructureFailureReturnsServiceUnavailable() throws Exception {
        when(productSearchService.search(null, null, null, 0, 10, null))
                .thenThrow(new DataAccessResourceFailureException("connection refused"));

        mockMvc.perform(get("/search/products"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Search service is temporarily unavailable"));
    }

    @Test
    void unexpectedElasticsearchQueryFailureReturnsInternalServerError() throws Exception {
        when(productSearchService.search(null, null, null, 0, 10, null))
                .thenThrow(new UncategorizedElasticsearchException("invalid query", 400, "query error", null));

        mockMvc.perform(get("/search/products"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Search request failed"));
    }

    private ProductSearchResponse response() {
        return new ProductSearchResponse(
                PRODUCT_ID,
                "Apple iPhone 16 Pro",
                new BigDecimal("65000"),
                ProductStatus.ACTIVE,
                CREATED_AT,
                CREATED_AT
        );
    }
}
