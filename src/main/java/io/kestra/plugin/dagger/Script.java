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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Run a Dagger CLI script.",
    description = "Writes the inline script to a temporary file and executes it using `dagger run <file>` " +
        "via the configured task runner."
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
                      with-exec echo "Hello" |
                      stdout
                """
        )
    }
)
public class Script extends AbstractExecScript implements RunnableTask<ScriptOutput> {

    @Schema(
        title = "Inline Dagger script",
        description = "Script content written to a temporary file and executed with `dagger run`."
    )
    @NotNull
    private Property<String> script;

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
        String renderedScript = runContext.render(this.script).as(String.class).orElse(null);
        if (renderedScript == null || renderedScript.isBlank()) {
            throw new IllegalArgumentException("The `script` property must not be empty.");
        }

        Path scriptFile = runContext.workingDir().createTempFile(".dagger");
        Files.writeString(scriptFile, renderedScript, StandardCharsets.UTF_8);

        String binary = Commands.daggerBinary();
        return this.commands(runContext)
            .withInterpreter(this.getInterpreter())
            .withBeforeCommands(this.getBeforeCommands())
            .withBeforeCommandsWithOptions(true)
            .withCommands(Property.ofValue(List.of(
                binary + " run " + scriptFile.toAbsolutePath()
            )))
            .run();
    }
}
