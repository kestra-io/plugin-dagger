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

import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Run Dagger CLI pipelines from inline commands.",
    description = "Executes each pipeline in `commands` using `dagger call <pipeline>` via the Process task runner. Stdout and stderr are captured and returned."
)
@Plugin(
    examples = {
        @Example(
            title = "Run a single Dagger pipeline command",
            full = true,
            code = """
                id: dagger_commands
                namespace: company.team

                tasks:
                  - id: run_dagger_pipeline
                    type: io.kestra.plugin.dagger.Commands
                    commands:
                      - container | from alpine | with-exec echo \"Hello\" | stdout
                """
        )
    }
)
public class Commands extends Task implements RunnableTask<Commands.Output> {
    @Schema(
        title = "Dagger pipeline commands",
        description = "List of pipeline expressions executed one by one using `dagger call <pipeline>`."
    )
    @NotNull
    protected Property<List<String>> commands;

    @Schema(
        title = "Container image",
        description = "Optional container image hint kept for consistency with script plugins. When using the Process runner, this value is informational and does not change local CLI execution."
    )
    protected Property<String> containerImage;

    @Override
    public Output run(RunContext runContext) throws Exception {
        List<String> renderedCommands = runContext.render(this.commands).asList(String.class);
        String renderedContainerImage = this.containerImage == null
            ? null
            : runContext.render(this.containerImage).as(String.class).orElse(null);

        if (renderedCommands == null || renderedCommands.isEmpty()) {
            throw new RunnableTaskException("The `commands` property must contain at least one pipeline command.");
        }

        if (renderedContainerImage != null && !renderedContainerImage.isBlank()) {
            runContext.logger().debug("Configured containerImage='{}' (informational with Process runner)", renderedContainerImage);
        }

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        for (String pipeline : renderedCommands) {
            runContext.logger().info("Executing Dagger command: dagger call {}", pipeline);
            DaggerCliExecutor.ExecutionResult result = DaggerCliExecutor.execute(
                runContext,
                List.of(DaggerCliExecutor.daggerBinary(), "call", pipeline),
                renderedContainerImage
            );

            DaggerCliExecutor.append(stdout, result.stdout());
            DaggerCliExecutor.append(stderr, result.stderr());

            if (result.exitCode() != 0) {
                runContext.logger().error("Dagger command failed with exit code {}", result.exitCode());
                throw new RunnableTaskException(
                    "Dagger command failed with exit code " + result.exitCode(),
                    Output.builder()
                        .exitCode(result.exitCode())
                        .stdout(stdout.toString())
                        .stderr(stderr.toString())
                        .build()
                );
            }
        }

        runContext.logger().info("All Dagger commands completed successfully");
        return Output.builder()
            .exitCode(0)
            .stdout(stdout.toString())
            .stderr(stderr.toString())
            .build();
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
