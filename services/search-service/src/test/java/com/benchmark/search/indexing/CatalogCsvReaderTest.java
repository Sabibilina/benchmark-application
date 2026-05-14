package com.benchmark.search.indexing;

import static org.assertj.core.api.Assertions.assertThat;

import com.benchmark.search.support.TestDataFiles;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogCsvReaderTest {

    @Test
    void mapsCatalogCsvFieldsToSearchDocuments() {
        List<SearchDocument> documents = new CatalogCsvReader().read(TestDataFiles.catalogCsv());

        assertThat(documents).hasSize(2);
        assertThat(documents.getFirst().id()).isEqualTo("song-1");
        assertThat(documents.getFirst().title()).isEqualTo("Ocean Drive");
        assertThat(documents.getFirst().artist()).isEqualTo("Duke Dumont");
        assertThat(documents.getFirst().album()).isEqualTo("Blase Boys Club");
        assertThat(documents.getFirst().genre()).isEqualTo("dance");
        assertThat(documents.getFirst().bpm()).isEqualByComparingTo(new BigDecimal("115.5"));
        assertThat(documents.getFirst().year()).isEqualTo(2015);
    }
}
