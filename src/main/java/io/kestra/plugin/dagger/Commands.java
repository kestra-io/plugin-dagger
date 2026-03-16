package io.kestra.plugin.dagger;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Run Dagger CLI pipelines from inline commands.",
    description = "Executes each pipeline using `dagger call <pipeline>` via the configured task runner. " +
        "Each pipeline string is passed as a single shell-quoted argument to prevent unintended shell interpretation."
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
                      - container --from alpine with-exec --args echo,Hello stdout
                """
        )
    }
)
public class Commands extends AbstractExecScript implements RunnableTask<ScriptOutput> {

    static final String DAGGER_BINARY_PROPERTY = "kestra.plugin.dagger.binary";

    @Schema(
        title = "Dagger pipeline commands",
        description = "List of pipeline expressions passed to `dagger call`. " +
            "Each entry is shell-quoted and executed as `dagger call '<pipeline>'`."
    )
    @NotNull
    private Property<List<String>> commands;

    @Schema(
        title = "Container image",
        description = "Container image used when the task runner is Docker-based. " +
            "Must include the Dagger CLI when using a Docker task runner. " +
            "Ignored when using the Process task runner."
    )
    @Builder.Default
    private Property<String> containerImage = Property.ofValue("docker.io/library/alpine:latest");

    @Override
    public Property<String> getContainerImage() {
        return this.containerImage;
    }

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        List<String> renderedCommands = runContext.render(this.commands).asList(String.class);

        String binary = daggerBinary();
        List<String> daggerCommands = new ArrayList<>();
        for (String pipeline : renderedCommands) {
            // Shell-quote the pipeline to prevent interpretation of special characters (e.g. |)
            daggerCommands.add(binary + " call " + shellQuote(pipeline));
        }

        return this.commands(runContext)
            .withInterpreter(this.getInterpreter())
            .withBeforeCommands(this.getBeforeCommands())
            .withBeforeCommandsWithOptions(true)
            .withCommands(Property.ofValue(daggerCommands))
            .run();
    }

    static String daggerBinary() {
        return System.getProperty(DAGGER_BINARY_PROPERTY, "dagger");
    }

    /**
     * Single-quote a string for safe shell interpolation.
     * Handles embedded single quotes by ending the quoted segment,
     * inserting an escaped quote, and restarting.
     */
    static String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
