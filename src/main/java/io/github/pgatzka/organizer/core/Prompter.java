package io.github.pgatzka.organizer.core;

import java.util.List;

/**
 * Asks the user for the parameters a goal was invoked without.
 *
 * <p>Kept as a small interface of our own rather than a Plexus component: the Maven runtime does
 * not ship {@code plexus-interactivity-api}, and an interface this size is trivial to fake in
 * tests.
 */
public interface Prompter {

    /** Whether prompting is possible at all. Batch mode and non-interactive sessions answer false. */
    boolean isInteractive();

    /**
     * Asks a free-text question.
     *
     * @param message the question, without a trailing colon
     * @param defaultValue offered as the answer for an empty reply; may be {@code null}
     * @return the answer, or the default; never blank unless the default is blank
     */
    String prompt(String message, String defaultValue);

    /** Asks a yes/no question. */
    boolean confirm(String message, boolean defaultValue);

    /**
     * Asks the user to pick one of {@code options}.
     *
     * @param labels what to show for each option, in order
     * @param defaultIndex pre-selected entry, or {@code -1} for none
     * @return the index of the chosen option
     */
    int select(String message, List<String> labels, int defaultIndex);
}
