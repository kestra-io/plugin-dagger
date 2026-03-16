package io.kestra.plugin.dagger;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTaskException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class CommandsTest {
    private static final String DAGGER_BINARY_PROPERTY = "kestra.plugin.dagger.binary";

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void runCommands() throws Exception {
        RunContext runContext = runContextFactory.of();

        Path fakeDagger = createFakeDagger(runContext);
        String previous = System.getProperty(DAGGER_BINARY_PROPERTY);
        System.setProperty(DAGGER_BINARY_PROPERTY, fakeDagger.toAbsolutePath().toString());

        try {
            Commands task = Commands.builder()
                .id("dagger-commands")
                .type(Commands.class.getName())
                .commands(Property.ofValue(List.of("container | from alpine | stdout")))
                .build();

            Commands.Output output = task.run(runContext);

            assertThat(output.getExitCode(), is(0));
            assertThat(output.getStdout(), containsString("called:container | from alpine | stdout"));
            assertThat(output.getStderr(), is(""));
        } finally {
            restoreProperty(previous);
        }
    }

    @Test
    void runCommandsFailure() throws Exception {
        RunContext runContext = runContextFactory.of();

        Path fakeDagger = createFakeDagger(runContext);
        String previous = System.getProperty(DAGGER_BINARY_PROPERTY);
        System.setProperty(DAGGER_BINARY_PROPERTY, fakeDagger.toAbsolutePath().toString());

        try {
            Commands task = Commands.builder()
                .id("dagger-commands-failed")
                .type(Commands.class.getName())
                .commands(Property.ofValue(List.of("fail")))
                .build();

            RunnableTaskException exception = assertThrows(RunnableTaskException.class, () -> task.run(runContext));
            Commands.Output output = (Commands.Output) exception.getOutput();

            assertThat(output.getExitCode(), is(42));
            assertThat(output.getStderr(), containsString("simulated command failure"));
        } finally {
            restoreProperty(previous);
        }
    }

    private static Path createFakeDagger(RunContext runContext) throws Exception {
        Path fakeDagger = runContext.workingDir().resolve(Path.of("dagger"));
        String script = """
            #!/bin/sh
            if [ \"$1\" = \"call\" ]; then
              if [ \"$2\" = \"fail\" ]; then
                echo \"simulated command failure\" 1>&2
                exit 42
              fi
              echo \"called:$2\"
              exit 0
            fi

            echo \"unsupported invocation\" 1>&2
            exit 1
            """;

        Files.writeString(fakeDagger, script, StandardCharsets.UTF_8);
        Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxr-xr-x");
        Files.setPosixFilePermissions(fakeDagger, perms);
        return fakeDagger;
    }

    private static void restoreProperty(String previous) {
        if (previous == null) {
            System.clearProperty(DAGGER_BINARY_PROPERTY);
        } else {
            System.setProperty(DAGGER_BINARY_PROPERTY, previous);
        }
    }
}
