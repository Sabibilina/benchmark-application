package com.benchmark.catalog.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Converter
public class StringMapConverter implements AttributeConverter<Map<String, String>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, String>> MAP_TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(Map<String, String> attribute) {
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute == null ? Map.of() : attribute);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize song metadata", ex);
        }
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return OBJECT_MAPPER.readValue(dbData, MAP_TYPE);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to deserialize song metadata", ex);
        }
    }
}
