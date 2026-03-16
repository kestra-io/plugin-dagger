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
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class ScriptTest {
    private static final String DAGGER_BINARY_PROPERTY = "kestra.plugin.dagger.binary";

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void runScript() throws Exception {
        RunContext runContext = runContextFactory.of();

        Path fakeDagger = createFakeDagger(runContext);
        String previous = System.getProperty(DAGGER_BINARY_PROPERTY);
        System.setProperty(DAGGER_BINARY_PROPERTY, fakeDagger.toAbsolutePath().toString());

        try {
            Script task = Script.builder()
                .id("dagger-script")
                .type(Script.class.getName())
                .script(Property.ofValue("""
                    container |
                    from alpine |
                    stdout
                    """))
                .build();

            Script.Output output = task.run(runContext);

            assertThat(output.getExitCode(), is(0));
            assertThat(output.getStdout(), containsString("container |"));
            assertThat(output.getStderr(), is(""));
        } finally {
            restoreProperty(previous);
        }
    }

    @Test
    void runScriptFailure() throws Exception {
        RunContext runContext = runContextFactory.of();

        Path fakeDagger = createFakeDagger(runContext);
        String previous = System.getProperty(DAGGER_BINARY_PROPERTY);
        System.setProperty(DAGGER_BINARY_PROPERTY, fakeDagger.toAbsolutePath().toString());

        try {
            Script task = Script.builder()
                .id("dagger-script-failed")
                .type(Script.class.getName())
                .script(Property.ofValue("FAIL_SCRIPT"))
                .build();

            RunnableTaskException exception = assertThrows(RunnableTaskException.class, () -> task.run(runContext));
            Script.Output output = (Script.Output) exception.getOutput();

            assertThat(output.getExitCode(), is(7));
            assertThat(output.getStderr(), containsString("simulated script failure"));
        } finally {
            restoreProperty(previous);
        }
    }

    private static Path createFakeDagger(RunContext runContext) throws Exception {
        Path fakeDagger = runContext.workingDir().resolve(Path.of("dagger"));
        String script = """
            #!/bin/sh
            if [ \"$1\" = \"run\" ]; then
              if grep -q \"FAIL_SCRIPT\" \"$2\"; then
                echo \"simulated script failure\" 1>&2
                exit 7
              fi
              cat \"$2\"
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
