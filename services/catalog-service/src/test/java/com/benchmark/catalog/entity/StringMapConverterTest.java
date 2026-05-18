package com.benchmark.catalog.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StringMapConverterTest {

    private final StringMapConverter converter = new StringMapConverter();

    @Test
    void convertsNullAndBlankValuesToEmptyMetadata() {
        assertThat(converter.convertToDatabaseColumn(null)).isEqualTo("{}");
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
        assertThat(converter.convertToEntityAttribute("   ")).isEmpty();
    }

    @Test
    void roundTripsMetadataAsStringMap() {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("track_name", "Song One");
        metadata.put("track_genre", "pop");

        String dbValue = converter.convertToDatabaseColumn(metadata);
        Map<String, String> restored = converter.convertToEntityAttribute(dbValue);

        assertThat(restored).containsExactlyEntriesOf(metadata);
    }

    @Test
    void rejectsMalformedMetadataJson() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("{not json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to deserialize song metadata");
    }
}
