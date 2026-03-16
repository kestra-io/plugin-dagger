package io.kestra.plugin.dagger;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.RunnableTaskException;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Run a Dagger CLI script.",
    description = "Writes the inline script to a temporary file and executes it using `dagger run <file>` via the Process task runner."
)
@Plugin(
    examples = {
        @Example(
            title = "Run a Dagger script",
            full = true,
            code = """
                id: dagger_script
                namespace: company.team

                tasks:
                  - id: run_dagger_script
                    type: io.kestra.plugin.dagger.Script
                    script: |
                      container |
                      from alpine |
                      with-exec echo \"Hello\" |
                      stdout
                """
        )
    }
)
public class Script extends Task implements RunnableTask<Script.Output> {
    @Schema(
        title = "Inline Dagger script",
        description = "Multi-line script content to write to a temporary file and execute with `dagger run`."
    )
    @NotNull
    protected Property<String> script;

    @Schema(
        title = "Container image",
        description = "Optional container image hint kept for consistency with script plugins. When using the Process runner, this value is informational and does not change local CLI execution."
    )
    protected Property<String> containerImage;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String renderedScript = runContext.render(this.script).as(String.class).orElse(null);
        String renderedContainerImage = this.containerImage == null
            ? null
            : runContext.render(this.containerImage).as(String.class).orElse(null);

        if (renderedScript == null || renderedScript.isBlank()) {
            throw new RunnableTaskException("The `script` property must not be empty.");
        }

        if (renderedContainerImage != null && !renderedContainerImage.isBlank()) {
            runContext.logger().debug("Configured containerImage='{}' (informational with Process runner)", renderedContainerImage);
        }

        Path scriptFile = runContext.workingDir().createTempFile(".dagger");
        try {
            Files.writeString(scriptFile, renderedScript, StandardCharsets.UTF_8);
            runContext.logger().info("Executing Dagger script from {}", scriptFile);

            DaggerCliExecutor.ExecutionResult result = DaggerCliExecutor.execute(
                runContext,
                List.of(DaggerCliExecutor.daggerBinary(), "run", scriptFile.toAbsolutePath().toString()),
                renderedContainerImage
            );

            if (result.exitCode() != 0) {
                runContext.logger().error("Dagger script failed with exit code {}", result.exitCode());
                throw new RunnableTaskException(
                    "Dagger script failed with exit code " + result.exitCode(),
                    Output.builder()
                        .exitCode(result.exitCode())
                        .stdout(result.stdout())
                        .stderr(result.stderr())
                        .build()
                );
            }

            runContext.logger().info("Dagger script completed with exit code {}", result.exitCode());
            return Output.builder()
                .exitCode(result.exitCode())
                .stdout(result.stdout())
                .stderr(result.stderr())
                .build();
        } finally {
            Files.deleteIfExists(scriptFile);
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Process exit code"
        )
        private final Integer exitCode;

        @Schema(
            title = "Captured standard output"
        )
        private final String stdout;

        @Schema(
            title = "Captured standard error"
        )
        private final String stderr;
    }
}
