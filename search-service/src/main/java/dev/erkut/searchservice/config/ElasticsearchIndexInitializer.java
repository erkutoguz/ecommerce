package dev.erkut.searchservice.config;

import dev.erkut.searchservice.document.ProductSearchDocument;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.index.Settings;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class ElasticsearchIndexInitializer implements ApplicationRunner {

    private static final String SETTINGS_PATH =
            "elasticsearch/products/settings.json";

    private static final String MAPPINGS_PATH =
            "elasticsearch/products/mappings.json";

    private final ElasticsearchOperations elasticsearchOperations;

    public ElasticsearchIndexInitializer(
            ElasticsearchOperations elasticsearchOperations
    ) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        IndexOperations indexOperations =
                elasticsearchOperations.indexOps(ProductSearchDocument.class);

        Document mapping = Document.parse(readResource(MAPPINGS_PATH));
        if (!indexOperations.exists()) {
            Settings settings = Settings.parse(readResource(SETTINGS_PATH));
            boolean created = indexOperations.create(settings, mapping);
            if (!created) {
                throw new IllegalStateException("Failed to create Elasticsearch products index");
            }
        }
        // Additive mapping update is required for existing indexes when versioned events roll out.
        indexOperations.putMapping(mapping);
    }

    private String readResource(String path) throws IOException {
        return new ClassPathResource(path)
                .getContentAsString(StandardCharsets.UTF_8);
    }
}
