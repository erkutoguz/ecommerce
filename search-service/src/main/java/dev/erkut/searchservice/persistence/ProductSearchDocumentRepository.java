package dev.erkut.searchservice.persistence;

import dev.erkut.searchservice.document.ProductSearchDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProductSearchDocumentRepository extends ElasticsearchRepository<ProductSearchDocument, UUID> {
}
