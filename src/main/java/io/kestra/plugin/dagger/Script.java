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
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import io.kestra.core.models.annotations.PluginProperty;

@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Run a Dagger CLI script.",
    description = """
        Writes the inline script to a temporary file and pipes it as stdin to `dagger shell` \
        via the configured task runner."""
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
                    taskRunner:
                      type: io.kestra.plugin.core.runner.Process
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
        description = "Script content written to a temporary file and piped as stdin to `dagger shell`."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> script;

    @Schema(
        title = "Container image",
        description = """
            Container image used when the task runner is Docker-based. \
            Must include the Dagger CLI when using a Docker task runner. \
            Ignored when using the Process task runner."""
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    private Property<String> containerImage = Property.ofValue("curlimages/curl:latest");

    @Override
    public Property<String> getContainerImage() {
        return this.containerImage;
    }

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        var rScript = runContext.render(this.script).as(String.class).orElse(null);
        if (rScript == null || rScript.isBlank()) {
            throw new IllegalArgumentException("The `script` property must not be empty.");
        }

        var scriptFile = runContext.workingDir().createTempFile(".dagger");
        Files.writeString(scriptFile, rScript, StandardCharsets.UTF_8);

        return this.commands(runContext)
            .withInterpreter(this.getInterpreter())
            .withBeforeCommands(Property.ofValue(Commands.mergedBeforeCommands(this.getBeforeCommands(), runContext)))
            .withBeforeCommandsWithOptions(true)
            .withCommands(Property.ofValue(List.of(
                "dagger shell < " + scriptFile.toAbsolutePath()
            )))
            .run();
    }
}
