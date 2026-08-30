package io.github.pgatzka.organizer.support;

import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.logging.Log;

/** A Maven {@link Log} that keeps everything it was told, so tests can assert on goal output. */
public final class RecordingLog implements Log {

    private final List<String> messages = new ArrayList<>();

    /** Every message logged so far, at any level. */
    public List<String> messages() {
        return List.copyOf(messages);
    }

    /** All messages joined by newlines, for substring assertions. */
    public String text() {
        return String.join("\n", messages);
    }

    private void record(CharSequence content) {
        messages.add(String.valueOf(content));
    }

    @Override
    public boolean isDebugEnabled() {
        return true;
    }

    @Override
    public void debug(CharSequence content) {
        record(content);
    }

    @Override
    public void debug(CharSequence content, Throwable error) {
        record(content);
    }

    @Override
    public void debug(Throwable error) {
        record(error.getMessage());
    }

    @Override
    public boolean isInfoEnabled() {
        return true;
    }

    @Override
    public void info(CharSequence content) {
        record(content);
    }

    @Override
    public void info(CharSequence content, Throwable error) {
        record(content);
    }

    @Override
    public void info(Throwable error) {
        record(error.getMessage());
    }

    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    @Override
    public void warn(CharSequence content) {
        record(content);
    }

    @Override
    public void warn(CharSequence content, Throwable error) {
        record(content);
    }

    @Override
    public void warn(Throwable error) {
        record(error.getMessage());
    }

    @Override
    public boolean isErrorEnabled() {
        return true;
    }

    @Override
    public void error(CharSequence content) {
        record(content);
    }

    @Override
    public void error(CharSequence content, Throwable error) {
        record(content);
    }

    @Override
    public void error(Throwable error) {
        record(error.getMessage());
    }
}
