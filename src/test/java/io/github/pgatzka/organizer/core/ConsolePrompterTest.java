package io.github.pgatzka.organizer.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConsolePrompterTest {

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    private ConsolePrompter prompter(String input) {
        return new ConsolePrompter(
                new StringReader(input), new PrintStream(output, true, StandardCharsets.UTF_8), true);
    }

    private String written() {
        return output.toString(StandardCharsets.UTF_8);
    }

    @Test
    void returnsTheTypedAnswer() {
        assertThat(prompter("org.example\n").prompt("groupId", null)).isEqualTo("org.example");
        assertThat(written()).isEqualTo("groupId: ");
    }

    @Test
    void trimsTheTypedAnswer() {
        assertThat(prompter("  spaced  \n").prompt("groupId", null)).isEqualTo("spaced");
    }

    @Test
    void acceptsTheDefaultOnAnEmptyAnswer() {
        assertThat(prompter("\n").prompt("scope", "compile")).isEqualTo("compile");
        assertThat(written()).isEqualTo("scope [compile]: ");
    }

    @Test
    void reAsksWhenNoValueIsGivenAndThereIsNoDefault() {
        assertThat(prompter("\nfinally\n").prompt("groupId", null)).isEqualTo("finally");
        assertThat(written()).contains("A value is required.");
    }

    @Test
    void failsAtEndOfInput() {
        assertThatThrownBy(() -> prompter("").prompt("groupId", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("end of input");
    }

    @Test
    void readsYesAndNo() {
        assertThat(prompter("y\n").confirm("Remove it?", false)).isTrue();
        assertThat(prompter("yes\n").confirm("Remove it?", false)).isTrue();
        assertThat(prompter("n\n").confirm("Remove it?", true)).isFalse();
        assertThat(prompter("no\n").confirm("Remove it?", true)).isFalse();
    }

    @Test
    void confirmationFallsBackToTheDefault() {
        assertThat(prompter("\n").confirm("Remove it?", true)).isTrue();
        assertThat(prompter("").confirm("Remove it?", false)).isFalse();
        assertThat(written()).contains("[y/N]");
    }

    @Test
    void reAsksOnAnUnrecognisedConfirmation() {
        assertThat(prompter("maybe\ny\n").confirm("Remove it?", false)).isTrue();
        assertThat(written()).contains("Please answer 'y' or 'n'.");
    }

    @Test
    void selectsByNumber() {
        assertThat(prompter("2\n").select("Pick one", List.of("first", "second"), -1)).isEqualTo(1);
        assertThat(written()).contains("1) first").contains("2) second");
    }

    @Test
    void selectionUsesTheDefaultOnAnEmptyAnswer() {
        assertThat(prompter("\n").select("Pick one", List.of("first", "second"), 1)).isEqualTo(1);
    }

    @Test
    void reAsksOnAnOutOfRangeOrNonNumericSelection() {
        assertThat(prompter("9\nx\n1\n").select("Pick one", List.of("first", "second"), -1)).isZero();
        assertThat(written()).contains("Enter a number between 1 and 2.");
    }

    @Test
    void rejectsAnEmptySelection() {
        assertThatThrownBy(() -> prompter("").select("Pick one", List.of(), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesToPromptWhenNotInteractive() {
        ConsolePrompter prompter = new ConsolePrompter(
                new StringReader("y\n"), new PrintStream(output, true, StandardCharsets.UTF_8), false);

        assertThat(prompter.isInteractive()).isFalse();
        assertThatThrownBy(() -> prompter.prompt("groupId", null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> prompter.confirm("ok?", true)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> prompter.select("pick", List.of("a"), 0)).isInstanceOf(IllegalStateException.class);
    }
}
