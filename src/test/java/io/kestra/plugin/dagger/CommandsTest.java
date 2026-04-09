package io.kestra.plugin.dagger;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.runner.Process;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class CommandsTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void runCommands() throws Exception {
        var runContext = runContextFactory.of();
        createFakeDagger(runContext);

        var task = Commands.builder()
            .id("dagger-commands")
            .type(Commands.class.getName())
            .taskRunner(Process.instance())
            .env(Property.ofValue(Map.of("PATH", runContext.workingDir().path().toAbsolutePath() + ":" + System.getenv("PATH"))))
            .commands(Property.ofValue(List.of("container --from alpine stdout")))
            .build();

        runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        var output = task.run(runContext);

        assertThat(output.getExitCode(), is(0));
    }

    @Test
    void runCommandsFailure() throws Exception {
        var runContext = runContextFactory.of();
        createFakeDagger(runContext);

        var task = Commands.builder()
            .id("dagger-commands-failed")
            .type(Commands.class.getName())
            .taskRunner(Process.instance())
            .env(Property.ofValue(Map.of("PATH", runContext.workingDir().path().toAbsolutePath() + ":" + System.getenv("PATH"))))
            .commands(Property.ofValue(List.of("fail")))
            .build();

        assertThrows(Exception.class, () -> task.run(runContext));
    }

    private static void createFakeDagger(RunContext runContext) throws Exception {
        var fakeDagger = runContext.workingDir().resolve(Path.of("dagger"));
        var script = """
            #!/bin/sh
            if [ "$1" = "shell" ] && [ "$2" = "-c" ]; then
              shift 2
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
        Files.setPosixFilePermissions(fakeDagger, PosixFilePermissions.fromString("rwxr-xr-x"));
    }
}
