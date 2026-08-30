package io.github.pgatzka.organizer.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.List;

/** A {@link Prompter} that talks to the terminal Maven was started from. */
public final class ConsolePrompter implements Prompter {

    private final BufferedReader in;
    private final PrintStream out;
    private final boolean interactive;

    public ConsolePrompter(Reader in, PrintStream out, boolean interactive) {
        this.in = new BufferedReader(in);
        this.out = out;
        this.interactive = interactive;
    }

    @Override
    public boolean isInteractive() {
        return interactive;
    }

    @Override
    public String prompt(String message, String defaultValue) {
        requireInteractive();
        while (true) {
            out.print(defaultValue == null || defaultValue.isEmpty()
                    ? message + ": "
                    : message + " [" + defaultValue + "]: ");
            out.flush();
            String answer = readLine();
            if (answer == null) {
                throw new IllegalStateException(
                        "Reached end of input while asking for '" + message
                                + "'. Pass the parameter on the command line, or run with -B.");
            }
            answer = answer.trim();
            if (!answer.isEmpty()) {
                return answer;
            }
            if (defaultValue != null) {
                return defaultValue;
            }
            out.println("  A value is required.");
        }
    }

    @Override
    public boolean confirm(String message, boolean defaultValue) {
        requireInteractive();
        while (true) {
            out.print(message + (defaultValue ? " [Y/n]: " : " [y/N]: "));
            out.flush();
            String answer = readLine();
            if (answer == null) {
                return defaultValue;
            }
            answer = answer.trim().toLowerCase(java.util.Locale.ROOT);
            if (answer.isEmpty()) {
                return defaultValue;
            }
            if (answer.equals("y") || answer.equals("yes")) {
                return true;
            }
            if (answer.equals("n") || answer.equals("no")) {
                return false;
            }
            out.println("  Please answer 'y' or 'n'.");
        }
    }

    @Override
    public int select(String message, List<String> labels, int defaultIndex) {
        requireInteractive();
        if (labels.isEmpty()) {
            throw new IllegalArgumentException("Nothing to choose from for '" + message + "'");
        }
        out.println(message + ":");
        for (int i = 0; i < labels.size(); i++) {
            out.println("  " + (i + 1) + ") " + labels.get(i));
        }
        String fallback = defaultIndex >= 0 ? String.valueOf(defaultIndex + 1) : null;
        while (true) {
            String answer = prompt("Choose 1-" + labels.size(), fallback);
            try {
                int choice = Integer.parseInt(answer.trim());
                if (choice >= 1 && choice <= labels.size()) {
                    return choice - 1;
                }
            } catch (NumberFormatException e) {
                // fall through to the retry message
            }
            out.println("  Enter a number between 1 and " + labels.size() + ".");
        }
    }

    private void requireInteractive() {
        if (!interactive) {
            throw new IllegalStateException("Cannot prompt: this Maven session is not interactive");
        }
    }

    private String readLine() {
        try {
            return in.readLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
