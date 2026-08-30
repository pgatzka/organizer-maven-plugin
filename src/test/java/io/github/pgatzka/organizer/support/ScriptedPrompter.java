package io.github.pgatzka.organizer.support;

import io.github.pgatzka.organizer.core.Prompter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** A {@link Prompter} that replays scripted answers and records the questions it was asked. */
public final class ScriptedPrompter implements Prompter {

    private final Deque<String> answers = new ArrayDeque<>();
    private final List<String> questions = new ArrayList<>();
    private final boolean interactive;

    public ScriptedPrompter(String... answers) {
        this(true, answers);
    }

    public ScriptedPrompter(boolean interactive, String... answers) {
        this.interactive = interactive;
        this.answers.addAll(List.of(answers));
    }

    /** A prompter that refuses to prompt, as under {@code mvn -B}. */
    public static ScriptedPrompter batchMode() {
        return new ScriptedPrompter(false);
    }

    /** The questions asked so far, in order. */
    public List<String> questions() {
        return List.copyOf(questions);
    }

    /** Whether every scripted answer was used. */
    public boolean isExhausted() {
        return answers.isEmpty();
    }

    @Override
    public boolean isInteractive() {
        return interactive;
    }

    @Override
    public String prompt(String message, String defaultValue) {
        questions.add(message);
        String answer = next(message);
        return answer.isEmpty() && defaultValue != null ? defaultValue : answer;
    }

    @Override
    public boolean confirm(String message, boolean defaultValue) {
        questions.add(message);
        String answer = next(message);
        return answer.isEmpty() ? defaultValue : answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes");
    }

    @Override
    public int select(String message, List<String> labels, int defaultIndex) {
        questions.add(message + " " + labels);
        String answer = next(message);
        return Integer.parseInt(answer) - 1;
    }

    private String next(String message) {
        if (!interactive) {
            throw new IllegalStateException("Not interactive, but was asked: " + message);
        }
        String answer = answers.poll();
        if (answer == null) {
            throw new IllegalStateException("No scripted answer for: " + message);
        }
        return answer;
    }
}
