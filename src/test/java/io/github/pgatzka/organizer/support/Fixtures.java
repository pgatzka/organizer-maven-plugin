package io.github.pgatzka.organizer.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads the POM fixtures under {@code src/test/resources/poms}. */
public final class Fixtures {

    private Fixtures() {}

    /** The raw text of a fixture, with LF line endings. */
    public static String text(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/poms/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("No fixture named " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Writes a fixture into {@code dir} as {@code pom.xml} and returns its path. */
    public static Path copyTo(Path dir, String name) {
        return write(dir.resolve("pom.xml"), text(name));
    }

    /** Writes arbitrary POM text to a path. */
    public static Path write(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Reads a file back as UTF-8 text. */
    public static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
