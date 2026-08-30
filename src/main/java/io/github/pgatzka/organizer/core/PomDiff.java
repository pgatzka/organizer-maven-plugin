package io.github.pgatzka.organizer.core;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import java.util.List;

/** Renders the change a goal would make as a unified diff, for {@code -Dorganizer.dryRun}. */
public final class PomDiff {

    private static final int CONTEXT_LINES = 3;

    private PomDiff() {}

    /** A unified diff between two texts, or an empty list when they are identical. */
    public static List<String> unified(String original, String revised, String fileName) {
        List<String> originalLines = lines(original);
        List<String> revisedLines = lines(revised);
        Patch<String> patch = DiffUtils.diff(originalLines, revisedLines);
        if (patch.getDeltas().isEmpty()) {
            return List.of();
        }
        return UnifiedDiffUtils.generateUnifiedDiff(
                fileName, fileName, originalLines, patch, CONTEXT_LINES);
    }

    /** The unified diff of a POM against its on-disk state. */
    public static List<String> of(PomDocument pom) {
        return unified(pom.getOriginalText(), pom.render(), fileName(pom));
    }

    /** The name to put in the diff header; not every path has a file name. */
    private static String fileName(PomDocument pom) {
        if (pom.getPath() == null) {
            return "pom.xml";
        }
        java.nio.file.Path name = pom.getPath().getFileName();
        return name == null ? "pom.xml" : name.toString();
    }

    private static List<String> lines(String text) {
        return List.of(text.split("\n", -1));
    }
}
