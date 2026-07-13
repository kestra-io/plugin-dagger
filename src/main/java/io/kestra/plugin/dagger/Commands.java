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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import io.kestra.core.models.annotations.PluginProperty;

@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Run Dagger CLI pipelines from inline commands",
    description = """
        Executes each pipeline using `dagger shell -c '<pipeline>'` via the configured task runner. \
        Each pipeline string is passed as a single shell-quoted argument to prevent unintended shell interpretation."""
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
                    taskRunner:
                      type: io.kestra.plugin.core.runner.Process
                    commands:
                      - container | from alpine | with-exec echo Hello | stdout
                """
        )
    }
)
public class Commands extends AbstractExecScript implements RunnableTask<ScriptOutput> {

    /**
     * The pinned Dagger CLI version to install when not already present on PATH.
     * Update this constant when a new Dagger release is required.
     * Using a pinned version prevents supply-chain attacks from unversioned install scripts.
     */
    static final String DAGGER_VERSION = "0.18.6";

    /**
     * Commands that install the Dagger CLI if it is not already on PATH.
     * Uses a pinned version and verifies the downloaded binary via the official checksums file
     * instead of piping an unversioned install script directly to sh.
     * Expects {@code curl} and {@code sha256sum} to be available in the container image.
     */
    static final List<String> DAGGER_INSTALL_COMMANDS = List.of(
        "command -v dagger > /dev/null 2>&1 || (" +
            "DAGGER_VERSION=" + DAGGER_VERSION + " && " +
            "ARCH=$(uname -m | sed 's/x86_64/amd64/;s/aarch64/arm64/') && " +
            "DAGGER_ARCHIVE=\"dagger_v${DAGGER_VERSION}_linux_${ARCH}.tar.gz\" && " +
            "DAGGER_BASE_URL=\"https://dl.dagger.io/dagger/releases/${DAGGER_VERSION}\" && " +
            "curl -fsSL \"${DAGGER_BASE_URL}/${DAGGER_ARCHIVE}\" -o /tmp/dagger.tar.gz && " +
            "curl -fsSL \"${DAGGER_BASE_URL}/checksums.txt\" -o /tmp/dagger_checksums.txt && " +
            "grep \"${DAGGER_ARCHIVE}\" /tmp/dagger_checksums.txt | sha256sum -c - && " +
            "tar -xzf /tmp/dagger.tar.gz -C /usr/local/bin dagger && " +
            "rm -f /tmp/dagger.tar.gz /tmp/dagger_checksums.txt" +
        ")"
    );

    @Schema(
        title = "Dagger pipeline commands",
        description = """
            List of pipeline expressions passed to `dagger shell -c`. \
            Each entry is shell-quoted to prevent interpretation of special characters \
            (e.g., `|` is passed literally to the Dagger CLI, not interpreted as a shell pipe)."""
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<List<String>> commands;

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
        var rCommands = runContext.render(this.commands).asList(String.class);

        var daggerCommands = new ArrayList<String>();
        for (var pipeline : rCommands) {
            // Shell-quote the pipeline to prevent interpretation of special characters (e.g. |)
            daggerCommands.add("dagger shell -c " + shellQuote(pipeline));
        }

        return this.commands(runContext)
            .withInterpreter(this.getInterpreter())
            .withBeforeCommands(Property.ofValue(mergedBeforeCommands(this.getBeforeCommands(), runContext)))
            .withBeforeCommandsWithOptions(true)
            .withCommands(Property.ofValue(daggerCommands))
            .run();
    }

    /**
     * Merges the Dagger install commands with any user-provided beforeCommands.
     * Install commands run first to ensure the Dagger CLI is available.
     */
    static List<String> mergedBeforeCommands(Property<List<String>> userBeforeCommands, RunContext runContext) throws Exception {
        var merged = new ArrayList<>(DAGGER_INSTALL_COMMANDS);
        var userBefore = runContext.render(userBeforeCommands).asList(String.class);
        merged.addAll(userBefore);
        return Collections.unmodifiableList(merged);
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
