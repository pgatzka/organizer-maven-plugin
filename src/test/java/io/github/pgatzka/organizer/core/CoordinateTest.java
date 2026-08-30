package io.github.pgatzka.organizer.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CoordinateTest {

    @Test
    void parsesGroupAndArtifact() {
        Coordinate coordinate = Coordinate.parse("org.example:widget");

        assertThat(coordinate.getGroupId()).isEqualTo("org.example");
        assertThat(coordinate.getArtifactId()).isEqualTo("widget");
        assertThat(coordinate.getVersion()).isNull();
        assertThat(coordinate.hasVersion()).isFalse();
    }

    @Test
    void parsesAVersion() {
        assertThat(Coordinate.parse("org.example:widget:1.2.3").getVersion()).isEqualTo("1.2.3");
    }

    @Test
    void parsesAClassifierAndType() {
        Coordinate coordinate = Coordinate.parse("org.example:widget:1.2.3:tests:test-jar");

        assertThat(coordinate.getClassifier()).isEqualTo("tests");
        assertThat(coordinate.getType()).isEqualTo("test-jar");
    }

    @Test
    void treatsEmptySegmentsAsAbsent() {
        Coordinate coordinate = Coordinate.parse("org.example:widget::tests");

        assertThat(coordinate.getVersion()).isNull();
        assertThat(coordinate.getClassifier()).isEqualTo("tests");
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(Coordinate.parse("  org.example:widget:1.0  ").toString())
                .isEqualTo("org.example:widget:1.0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "onlyone", "a:b:c:d:e:f", ":widget", "org.example:"})
    void rejectsMalformedCoordinates(String text) {
        assertThatThrownBy(() -> Coordinate.parse(text)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANullCoordinate() {
        assertThatThrownBy(() -> Coordinate.parse(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing coordinate");
    }

    @Test
    void rendersTheGroupArtifactIdentity() {
        assertThat(Coordinate.parse("org.example:widget:1.0").toGA()).isEqualTo("org.example:widget");
    }

    @Test
    void addsAndRemovesTheVersion() {
        Coordinate base = Coordinate.parse("org.example:widget");

        assertThat(base.withVersion("2.0").getVersion()).isEqualTo("2.0");
        assertThat(base.withVersion("2.0").withoutVersion().hasVersion()).isFalse();
    }

    @Test
    void matchesOnGroupAndArtifact() {
        Coordinate coordinate = Coordinate.parse("org.example:widget");

        assertThat(coordinate.matchesGA("org.example", "widget")).isTrue();
        assertThat(coordinate.matchesGA("org.example", "other")).isFalse();
        assertThat(coordinate.matchesGA(null, "widget")).isFalse();
    }

    @Test
    void supportsWildcardsWhenMatching() {
        assertThat(Coordinate.parse("org.springframework.*:*").matchesGA("org.springframework.boot", "spring-boot"))
                .isTrue();
        assertThat(Coordinate.parse("*:*").matchesGA("anything", "at-all")).isTrue();
        assertThat(Coordinate.parse("org.*:widget*").matchesGA("org.example", "widget-core")).isTrue();
        assertThat(Coordinate.parse("org.*:widget*").matchesGA("com.example", "widget-core")).isFalse();
    }

    @Test
    void treatsDotsAsLiteralsWhenMatching() {
        assertThat(Coordinate.parse("org.example:widget").matchesGA("orgXexample", "widget")).isFalse();
    }

    @Test
    void rendersEveryPresentSegment() {
        assertThat(Coordinate.parse("g:a").toString()).isEqualTo("g:a");
        assertThat(Coordinate.parse("g:a:1:c:t").toString()).isEqualTo("g:a:1:c:t");
    }

    @Test
    void comparesByValue() {
        assertThat(Coordinate.of("g", "a", "1")).isEqualTo(Coordinate.of("g", "a", "1"));
        assertThat(Coordinate.of("g", "a", "1")).hasSameHashCodeAs(Coordinate.of("g", "a", "1"));
        assertThat(Coordinate.of("g", "a", "1")).isNotEqualTo(Coordinate.of("g", "a", "2"));
        assertThat(Coordinate.of("g", "a", "1")).isNotEqualTo("not a coordinate");
        assertThat(Coordinate.of("g", "a", "1")).isEqualTo(Coordinate.of("g", "a", "1"));
    }
}
