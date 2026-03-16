package io.kestra.plugin.dagger;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.runners.AbstractLogConsumer;
import io.kestra.core.models.tasks.runners.DefaultLogConsumer;
import io.kestra.core.models.tasks.runners.TargetOS;
import io.kestra.core.models.tasks.runners.TaskCommands;
import io.kestra.core.models.tasks.runners.TaskException;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.core.runner.Process;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class DaggerCliExecutor {
    static final String DAGGER_BINARY_PROPERTY = "kestra.plugin.dagger.binary";

    private DaggerCliExecutor() {
    }

    static String daggerBinary() {
        return System.getProperty(DAGGER_BINARY_PROPERTY, "dagger");
    }

    static ExecutionResult execute(RunContext runContext, List<String> command, String containerImage) throws Exception {
        CapturingLogConsumer logConsumer = new CapturingLogConsumer(runContext);
        TaskCommands taskCommands = new ProcessTaskCommands(
            runContext.workingDir().path(),
            logConsumer,
            Property.ofValue(command),
            containerImage
        );

        try {
            int exitCode = Process.instance().run(runContext, taskCommands, Collections.emptyList()).getExitCode();
            return new ExecutionResult(exitCode, logConsumer.stdout(), logConsumer.stderr());
        } catch (TaskException e) {
            return new ExecutionResult(e.getExitCode(), logConsumer.stdout(), logConsumer.stderr());
        }
    }

    static void append(StringBuilder target, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }

        if (target.length() > 0) {
            target.append(System.lineSeparator());
        }

        target.append(value);
    }

    record ExecutionResult(Integer exitCode, String stdout, String stderr) {
    }

    private static final class ProcessTaskCommands implements TaskCommands {
        private final Path workingDirectory;
        private final Path outputDirectory;
        private final AbstractLogConsumer logConsumer;
        private final Property<List<String>> commands;
        private final String containerImage;

        private ProcessTaskCommands(
            Path workingDirectory,
            AbstractLogConsumer logConsumer,
            Property<List<String>> commands,
            String containerImage
        ) {
            this.workingDirectory = workingDirectory;
            this.outputDirectory = workingDirectory.resolve("output");
            this.logConsumer = logConsumer;
            this.commands = commands;
            this.containerImage = containerImage;
        }

        @Override
        public String getContainerImage() {
            return containerImage;
        }

        @Override
        public AbstractLogConsumer getLogConsumer() {
            return logConsumer;
        }

        @Override
        public Property<List<String>> getInterpreter() {
            return null;
        }

        @Override
        public Property<List<String>> getBeforeCommands() {
            return null;
        }

        @Override
        public Property<List<String>> getCommands() {
            return commands;
        }

        @Override
        public Map<String, Object> getAdditionalVars() {
            return Collections.emptyMap();
        }

        @Override
        public Path getWorkingDirectory() {
            return workingDirectory;
        }

        @Override
        public Path getOutputDirectory() {
            return outputDirectory;
        }

        @Override
        public Map<String, String> getEnv() {
            return Collections.emptyMap();
        }

        @Override
        public Boolean getEnableOutputDirectory() {
            return false;
        }

        @Override
        public Duration getTimeout() {
            return null;
        }

        @Override
        public TargetOS getTargetOS() {
            return TargetOS.AUTO;
        }
    }

    private static final class CapturingLogConsumer extends AbstractLogConsumer {
        private final DefaultLogConsumer delegate;
        private final StringBuilder stdout = new StringBuilder();
        private final StringBuilder stderr = new StringBuilder();

        private CapturingLogConsumer(RunContext runContext) {
            this.delegate = new DefaultLogConsumer(runContext);
        }

        @Override
        public void accept(String line, Boolean isStdErr) {
            this.accept(line, isStdErr, null);
        }

        @Override
        public void accept(String line, Boolean isStdErr, Instant instant) {
            delegate.accept(line, isStdErr, instant);
            this.outputs.putAll(delegate.getOutputs());

            if (Boolean.TRUE.equals(isStdErr)) {
                this.stdErrCount.incrementAndGet();
                append(stderr, line);
            } else {
                this.stdOutCount.incrementAndGet();
                append(stdout, line);
            }
        }

        private String stdout() {
            return stdout.toString();
        }

        private String stderr() {
            return stderr.toString();
        }
    }
}
