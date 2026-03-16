package io.kestra.plugin.dagger;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.core.runner.Process;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
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
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class CommandsTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void runCommands() throws Exception {
        RunContext runContext = runContextFactory.of();

        Path fakeDagger = createFakeDagger(runContext);
        String previous = System.getProperty(Commands.DAGGER_BINARY_PROPERTY);
        System.setProperty(Commands.DAGGER_BINARY_PROPERTY, fakeDagger.toAbsolutePath().toString());

        try {
            Commands task = Commands.builder()
                .id("dagger-commands")
                .type(Commands.class.getName())
                .taskRunner(Process.instance())
                .commands(Property.ofValue(List.of("container --from alpine stdout")))
                .build();

            ScriptOutput output = task.run(runContext);

            assertThat(output.getExitCode(), is(0));
        } finally {
            restoreProperty(previous);
        }
    }

    @Test
    void runCommandsFailure() throws Exception {
        RunContext runContext = runContextFactory.of();

        Path fakeDagger = createFakeDagger(runContext);
        String previous = System.getProperty(Commands.DAGGER_BINARY_PROPERTY);
        System.setProperty(Commands.DAGGER_BINARY_PROPERTY, fakeDagger.toAbsolutePath().toString());

        try {
            Commands task = Commands.builder()
                .id("dagger-commands-failed")
                .type(Commands.class.getName())
                .taskRunner(Process.instance())
                .commands(Property.ofValue(List.of("fail")))
                .build();

            assertThrows(Exception.class, () -> task.run(runContext));
        } finally {
            restoreProperty(previous);
        }
    }

    private static Path createFakeDagger(RunContext runContext) throws Exception {
        Path fakeDagger = runContext.workingDir().resolve(Path.of("dagger"));
        String script = """
            #!/bin/sh
            if [ "$1" = "call" ]; then
              shift
              if echo "$@" | grep -q "fail"; then
                echo "simulated command failure" 1>&2
                exit 42
              fi
              echo "called:$@"
              exit 0
            fi

            echo "unsupported invocation" 1>&2
            exit 1
            """;

        Files.writeString(fakeDagger, script, StandardCharsets.UTF_8);
        Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxr-xr-x");
        Files.setPosixFilePermissions(fakeDagger, perms);
        return fakeDagger;
    }

    private static void restoreProperty(String previous) {
        if (previous == null) {
            System.clearProperty(Commands.DAGGER_BINARY_PROPERTY);
        } else {
            System.setProperty(Commands.DAGGER_BINARY_PROPERTY, previous);
        }
    }
}
