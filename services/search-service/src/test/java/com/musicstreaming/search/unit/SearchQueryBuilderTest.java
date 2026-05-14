package com.musicstreaming.search.unit;

import com.musicstreaming.search.service.SearchQueryBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.index.query.MultiMatchQueryBuilder;
import org.opensearch.index.query.RangeQueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class SearchQueryBuilderTest {

    private SearchQueryBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new SearchQueryBuilder();
    }

    @Test
    void build_noParams_usesMatchAll() {
        SearchSourceBuilder result = builder.build(null, null, null, null, null, 20);
        BoolQueryBuilder bool = (BoolQueryBuilder) result.query();
        assertThat(bool.must()).hasSize(1);
        assertThat(bool.must().get(0)).isInstanceOf(MatchAllQueryBuilder.class);
        assertThat(bool.filter()).isEmpty();
    }

    @Test
    void build_withTextQuery_addsMustMultiMatch() {
        SearchSourceBuilder result = builder.build("alpha", null, null, null, null, 20);
        BoolQueryBuilder bool = (BoolQueryBuilder) result.query();
        assertThat(bool.must()).hasSize(1);
        assertThat(bool.must().get(0)).isInstanceOf(MultiMatchQueryBuilder.class);
        MultiMatchQueryBuilder mm = (MultiMatchQueryBuilder) bool.must().get(0);
        assertThat(mm.value()).isEqualTo("alpha");
        assertThat(mm.fields()).containsKeys("title", "artist", "album");
    }

    @Test
    void build_withBlankQuery_usesMatchAll() {
        SearchSourceBuilder result = builder.build("   ", null, null, null, null, 20);
        BoolQueryBuilder bool = (BoolQueryBuilder) result.query();
        assertThat(bool.must().get(0)).isInstanceOf(MatchAllQueryBuilder.class);
    }

    @Test
    void build_withGenre_addsTermFilter() {
        SearchSourceBuilder result = builder.build(null, "Pop", null, null, null, 20);
        BoolQueryBuilder bool = (BoolQueryBuilder) result.query();
        assertThat(bool.filter()).hasSize(1);
        assertThat(bool.filter().get(0)).isInstanceOf(TermQueryBuilder.class);
        TermQueryBuilder term = (TermQueryBuilder) bool.filter().get(0);
        assertThat(term.fieldName()).isEqualTo("genre");
        assertThat(term.value().toString()).isEqualTo("Pop");
    }

    @Test
    void build_withBpmRange_addsRangeFilter() {
        SearchSourceBuilder result = builder.build(null, null, 100.0, 150.0, null, 20);
        BoolQueryBuilder bool = (BoolQueryBuilder) result.query();
        assertThat(bool.filter()).hasSize(1);
        assertThat(bool.filter().get(0)).isInstanceOf(RangeQueryBuilder.class);
        RangeQueryBuilder range = (RangeQueryBuilder) bool.filter().get(0);
        assertThat(range.fieldName()).isEqualTo("tempo");
        assertThat(range.from()).isEqualTo(100.0);
        assertThat(range.to()).isEqualTo(150.0);
    }

    @Test
    void build_withBpmMinOnly_addsOpenUpperRange() {
        SearchSourceBuilder result = builder.build(null, null, 120.0, null, null, 20);
        BoolQueryBuilder bool = (BoolQueryBuilder) result.query();
        RangeQueryBuilder range = (RangeQueryBuilder) bool.filter().get(0);
        assertThat(range.from()).isEqualTo(120.0);
        assertThat(range.to()).isNull();
    }

    @Test
    void build_withBpmMaxOnly_addsOpenLowerRange() {
        SearchSourceBuilder result = builder.build(null, null, null, 90.0, null, 20);
        BoolQueryBuilder bool = (BoolQueryBuilder) result.query();
        RangeQueryBuilder range = (RangeQueryBuilder) bool.filter().get(0);
        assertThat(range.from()).isNull();
        assertThat(range.to()).isEqualTo(90.0);
    }

    @Test
    void build_withYear_addsTermFilter() {
        SearchSourceBuilder result = builder.build(null, null, null, null, 2020, 20);
        BoolQueryBuilder bool = (BoolQueryBuilder) result.query();
        assertThat(bool.filter()).hasSize(1);
        TermQueryBuilder term = (TermQueryBuilder) bool.filter().get(0);
        assertThat(term.fieldName()).isEqualTo("year");
        assertThat(((Number) term.value()).intValue()).isEqualTo(2020);
    }

    @Test
    void build_allFilters_buildsCombinedQuery() {
        SearchSourceBuilder result = builder.build("rock", "Rock", 100.0, 180.0, 2019, 20);
        BoolQueryBuilder bool = (BoolQueryBuilder) result.query();
        assertThat(bool.must().get(0)).isInstanceOf(MultiMatchQueryBuilder.class);
        assertThat(bool.filter()).hasSize(3);
        long termCount = bool.filter().stream()
                .filter(q -> q instanceof TermQueryBuilder).count();
        long rangeCount = bool.filter().stream()
                .filter(q -> q instanceof RangeQueryBuilder).count();
        assertThat(termCount).isEqualTo(2);
        assertThat(rangeCount).isEqualTo(1);
    }

    @Test
    void build_setsMaxResultsAsSize() {
        SearchSourceBuilder result = builder.build(null, null, null, null, null, 42);
        assertThat(result.size()).isEqualTo(42);
    }
}
