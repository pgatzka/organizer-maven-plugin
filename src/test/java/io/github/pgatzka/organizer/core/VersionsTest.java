package io.github.pgatzka.organizer.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.pgatzka.organizer.core.Versions.Segment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class VersionsTest {

    @Test
    void recognisesSnapshots() {
        assertThat(Versions.isSnapshot("1.0.0-SNAPSHOT")).isTrue();
        assertThat(Versions.isSnapshot("1.0.0")).isFalse();
        assertThat(Versions.isSnapshot(null)).isFalse();
    }

    @Test
    void stripsAndAddsTheSnapshotSuffix() {
        assertThat(Versions.release("1.0.0-SNAPSHOT")).isEqualTo("1.0.0");
        assertThat(Versions.release("1.0.0")).isEqualTo("1.0.0");
        assertThat(Versions.snapshot("1.0.0")).isEqualTo("1.0.0-SNAPSHOT");
        assertThat(Versions.snapshot("1.0.0-SNAPSHOT")).isEqualTo("1.0.0-SNAPSHOT");
    }

    @ParameterizedTest
    @CsvSource({
        "1.2.3, MAJOR, 2.0.0",
        "1.2.3, MINOR, 1.3.0",
        "1.2.3, PATCH, 1.2.4",
        "1.2.3-SNAPSHOT, MINOR, 1.3.0-SNAPSHOT",
        "0.9.9, MAJOR, 1.0.0",
        "1.2, PATCH, 1.2.1",
        "1.2, MINOR, 1.3.0",
        "1, MAJOR, 2.0.0",
        "1.2.3-RC1, PATCH, 1.2.4-RC1",
        "1.2.3.4, PATCH, 1.2.4.4"
    })
    void bumpsTheRequestedSegment(String version, Segment segment, String expected) {
        assertThat(Versions.bump(version, segment)).isEqualTo(expected);
    }

    @Test
    void producesTheNextDevelopmentVersion() {
        assertThat(Versions.nextSnapshot("1.2.3")).isEqualTo("1.2.4-SNAPSHOT");
        assertThat(Versions.nextSnapshot("1.2.3-SNAPSHOT")).isEqualTo("1.2.4-SNAPSHOT");
    }

    @Test
    void parsesSegmentNames() {
        assertThat(Segment.parse("major")).isEqualTo(Segment.MAJOR);
        assertThat(Segment.parse(" PATCH ")).isEqualTo(Segment.PATCH);
    }

    @Test
    void rejectsAnUnknownSegment() {
        assertThatThrownBy(() -> Segment.parse("build"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("major, minor or patch");
        assertThatThrownBy(() -> Segment.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RELEASE", "not-a-version", "v1.2.3"})
    void refusesToBumpANonNumericVersion(String version) {
        assertThatThrownBy(() -> Versions.bump(version, Segment.PATCH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not start with a number");
    }

    @Test
    void refusesToWorkWithoutAVersion() {
        assertThatThrownBy(() -> Versions.bump(null, Segment.PATCH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no version");
        assertThatThrownBy(() -> Versions.release("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Versions.snapshot(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
