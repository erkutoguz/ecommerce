package dev.erkut.searchservice.application;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;
import dev.erkut.searchservice.api.response.ProductSearchResponse;
import dev.erkut.searchservice.document.ProductSearchDocument;
import dev.erkut.searchservice.document.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ProductSearchService {
    private static final long MAX_RESULT_WINDOW = 10_000;

    private final ElasticsearchOperations elasticsearchOperations;

    public ProductSearchService(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    public Page<ProductSearchResponse> search(
            String queryText,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            int page,
            int size,
            ProductSearchSort requestedSort
    ) {
        validateSearchParameters(minPrice, maxPrice, page, size);

        String normalizedQuery = normalizeQuery(queryText);
        ProductSearchSort sort = resolveSort(requestedSort, normalizedQuery);

        BoolQuery.Builder boolQuery = new BoolQuery.Builder();
        if (normalizedQuery != null) {
            boolQuery.must(query -> query.match(match -> match
                    .field("name")
                    .query(normalizedQuery)));
        }

        boolQuery.filter(query -> query.term(term -> term
                .field("status")
                .value(FieldValue.of(ProductStatus.ACTIVE.name()))));
        boolQuery.mustNot(query -> query.term(term -> term
                .field("deleted")
                .value(FieldValue.of(true))));

        if (minPrice != null || maxPrice != null) {
            boolQuery.filter(query -> query.range(range -> range.untyped(untyped -> {
                untyped.field("price");
                if (minPrice != null) {
                    untyped.gte(JsonData.of(minPrice));
                }
                if (maxPrice != null) {
                    untyped.lte(JsonData.of(maxPrice));
                }
                return untyped;
            })));
        }

        Pageable pageable = PageRequest.of(page, size);
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(query -> query.bool(boolQuery.build()))
                .withSort(sortOptions(sort))
                .withPageable(pageable)
                .withTrackTotalHits(true)
                .build();

        SearchHits<ProductSearchDocument> searchHits =
                elasticsearchOperations.search(nativeQuery, ProductSearchDocument.class);

        List<ProductSearchResponse> content = searchHits.getSearchHits().stream()
                .map(hit -> toResponse(hit.getContent()))
                .toList();

        return new PageImpl<>(content, pageable, searchHits.getTotalHits());
    }

    private void validateSearchParameters(BigDecimal minPrice, BigDecimal maxPrice, int page, int size) {
        if (minPrice != null && minPrice.signum() < 0) {
            throw new IllegalArgumentException("minPrice must be greater than or equal to zero");
        }
        if (maxPrice != null && maxPrice.signum() < 0) {
            throw new IllegalArgumentException("maxPrice must be greater than or equal to zero");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException("minPrice must be less than or equal to maxPrice");
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to zero");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }

        long resultWindowEnd = ((long) page * size) + size;
        if (resultWindowEnd > MAX_RESULT_WINDOW) {
            throw new IllegalArgumentException("Requested page exceeds the maximum result window");
        }
    }

    private String normalizeQuery(String queryText) {
        if (queryText == null || queryText.isBlank()) {
            return null;
        }
        return queryText.trim();
    }

    private ProductSearchSort resolveSort(ProductSearchSort requestedSort, String normalizedQuery) {
        if (requestedSort == ProductSearchSort.RELEVANCE && normalizedQuery == null) {
            throw new IllegalArgumentException("relevance sort requires a nonblank q parameter");
        }
        if (requestedSort != null) {
            return requestedSort;
        }
        return normalizedQuery == null ? ProductSearchSort.NEWEST : ProductSearchSort.RELEVANCE;
    }

    private List<SortOptions> sortOptions(ProductSearchSort sort) {
        return switch (sort) {
            case RELEVANCE -> List.of(SortOptions.of(option -> option.score(score ->
                    score.order(SortOrder.Desc))));
            case PRICE_ASC -> List.of(SortOptions.of(option -> option.field(field -> field
                    .field("price")
                    .order(SortOrder.Asc))));
            case PRICE_DESC -> List.of(SortOptions.of(option -> option.field(field -> field
                    .field("price")
                    .order(SortOrder.Desc))));
            case NEWEST -> List.of(SortOptions.of(option -> option.field(field -> field
                    .field("createdAt")
                    .order(SortOrder.Desc))));
        };
    }

    private ProductSearchResponse toResponse(ProductSearchDocument document) {
        return new ProductSearchResponse(
                document.getProductId(),
                document.getName(),
                document.getPrice(),
                document.getStatus(),
                document.getUpdatedAt(),
                document.getCreatedAt()
        );
    }
}
