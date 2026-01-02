package com.docarchitect.core.scanner.impl.python;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.docarchitect.core.model.MessageFlow;
import com.docarchitect.core.scanner.ApplicabilityStrategies;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerApplicabilityStrategy;
import com.docarchitect.core.scanner.base.AbstractRegexScanner;
import com.docarchitect.core.util.Technologies;

/**
 * Scanner for Celery task definitions and invocations in Python source files.
 *
 * <p>Celery is a distributed task queue system for Python. This scanner detects:
 * <ul>
 *   <li>Task definitions using {@code @task}, {@code @shared_task}, {@code @app.task} decorators</li>
 *   <li>Task invocations using {@code .delay()}, {@code .apply_async()} methods</li>
 *   <li>Queue names from task configurations</li>
 * </ul>
 *
 * <p><b>Supported Patterns</b></p>
 * <ul>
 *   <li>{@code @task} - Simple task decorator</li>
 *   <li>{@code @shared_task} - Shared task decorator (from celery import shared_task)</li>
 *   <li>{@code @app.task} - App-specific task decorator</li>
 *   <li>{@code @celery.task} - Celery instance task decorator</li>
 *   <li>{@code @task(name='task.name')} - Task with custom name</li>
 *   <li>{@code @shared_task(queue='queue_name')} - Task with queue specification</li>
 *   <li>{@code send_email.delay(args)} - Asynchronous task invocation</li>
 *   <li>{@code send_email.apply_async(args, queue='emails')} - Task with queue override</li>
 * </ul>
 *
 * <p><b>Queue Name Extraction</b></p>
 * <ul>
 *   <li>From decorator: {@code @shared_task(queue='emails')}</li>
 *   <li>From apply_async: {@code task.apply_async(args, queue='notifications')}</li>
 *   <li>Default queue: "celery" (Celery's default)</li>
 * </ul>
 *
 * <p><b>Message Flow Model</b></p>
 * <ul>
 *   <li>Producer: Component that calls .delay() or .apply_async()</li>
 *   <li>Consumer: Component that defines the task</li>
 *   <li>Topic/Queue: Celery queue name</li>
 *   <li>Type: "celery"</li>
 * </ul>
 *
 * <p><b>Regex Patterns</b></p>
 * <ul>
 *   <li>{@code TASK_DECORATOR_PATTERN}: {@code @(?:shared_task|task|[a-zA-Z_][a-zA-Z0-9_]*\.task)\s*(?:\((.*?)\))?}</li>
 *   <li>{@code TASK_INVOCATION_PATTERN}: {@code ([a-zA-Z_][a-zA-Z0-9_]*)\.(delay|apply_async)\s*\(}</li>
 *   <li>{@code QUEUE_NAME_PATTERN}: {@code queue\s*=\s*['"]([^'"]+)['"]}</li>
 *   <li>{@code TASK_NAME_PATTERN}: {@code name\s*=\s*['"]([^'"]+)['"]}</li>
 * </ul>
 *
 * <p><b>Example Usage</b></p>
 * <pre>{@code
 * // Task definition
 * @shared_task(queue='emails')
 * def send_email(to, subject, body):
 *     pass
 *
 * // Task invocation
 * send_email.delay('user@example.com', 'Welcome', 'Hello!')
 * }</pre>
 *
 * @see MessageFlow
 * @see AbstractRegexScanner
 * @since 1.0.0
 */
public class CeleryScanner extends AbstractRegexScanner {

    private static final String SCANNER_ID = "celery-tasks";
    private static final String SCANNER_DISPLAY_NAME = "Celery Task Scanner";
    private static final String PATTERN_PYTHON_FILES = "**/*.py";
    private static final String MESSAGE_TYPE_CELERY = "celery";
    private static final String DEFAULT_QUEUE = "celery";
    private static final String PYTHON_FILE_EXTENSION = "\\.py$";

    private static final int MAX_FUNCTION_SEARCH_LINES = 10;

    /**
     * Regex to match Celery task decorators.
     * Matches: @task, @shared_task, @app.task, @celery.task
     * With optional parameters: @shared_task(queue='emails', name='send_email')
     * Captures: (1) decorator parameters (optional)
     */
    private static final Pattern TASK_DECORATOR_PATTERN = Pattern.compile(
        "@(?:shared_task|task|[a-zA-Z_][a-zA-Z0-9_]*\\.task)\\s*(?:\\(([^)]*)\\))?"
    );

    /**
     * Regex to match task invocations.
     * Matches: send_email.delay(...) or send_email.apply_async(...)
     * Captures: (1) task name, (2) invocation method (delay or apply_async)
     */
    private static final Pattern TASK_INVOCATION_PATTERN = Pattern.compile(
        "([a-zA-Z_][a-zA-Z0-9_]*)\\.(?:delay|apply_async)\\s*\\("
    );

    /**
     * Regex to extract queue name from decorator or apply_async parameters.
     * Matches: queue='emails' or queue="emails"
     * Captures: (1) queue name
     */
    private static final Pattern QUEUE_NAME_PATTERN = Pattern.compile(
        "queue\\s*=\\s*['\"]([^'\"]+)['\"]"
    );

    /**
     * Regex to extract task name from decorator parameters.
     * Matches: name='task.send_email' or name="task.send_email"
     * Captures: (1) task name
     */
    private static final Pattern TASK_NAME_PATTERN = Pattern.compile(
        "name\\s*=\\s*['\"]([^'\"]+)['\"]"
    );

    /**
     * Regex to match function definition.
     * Matches: def send_email(...) or async def send_email(...)
     * Captures: (1) function name
     */
    private static final Pattern FUNCTION_PATTERN = Pattern.compile(
        "(?:async\\s+)?def\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\("
    );

    @Override
    public String getId() {
        return SCANNER_ID;
    }

    @Override
    public String getDisplayName() {
        return SCANNER_DISPLAY_NAME;
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of(Technologies.PYTHON);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(PATTERN_PYTHON_FILES);
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public ScannerApplicabilityStrategy getApplicabilityStrategy() {
        return ApplicabilityStrategies.hasPythonFiles()
            .and(ApplicabilityStrategies.hasCelery()
                .or(ApplicabilityStrategies.hasFileContaining("from celery", "import Celery", "@task", "@shared_task")));
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Celery tasks in: {}", context.rootPath());

        List<MessageFlow> messageFlows = new ArrayList<>();
        List<Path> pythonFiles = context.findFiles(PATTERN_PYTHON_FILES).toList();

        if (pythonFiles.isEmpty()) {
            return emptyResult();
        }

        // First pass: collect all task definitions
        Map<String, TaskDefinition> taskDefinitions = new HashMap<>();
        for (Path pythonFile : pythonFiles) {
            try {
                collectTaskDefinitions(pythonFile, taskDefinitions);
            } catch (Exception e) {
                log.warn("Failed to parse Python file for task definitions: {} - {}", pythonFile, e.getMessage());
            }
        }

        // Second pass: find task invocations and create message flows
        for (Path pythonFile : pythonFiles) {
            try {
                findTaskInvocations(pythonFile, taskDefinitions, messageFlows);
            } catch (Exception e) {
                log.warn("Failed to parse Python file for task invocations: {} - {}", pythonFile, e.getMessage());
            }
        }

        log.info("Found {} Celery message flows", messageFlows.size());

        return buildSuccessResult(
            List.of(),           // No components
            List.of(),           // No dependencies
            List.of(),           // No API endpoints
            messageFlows,        // Message flows
            List.of(),           // No data entities
            List.of(),           // No relationships
            List.of()            // No warnings
        );
    }

    /**
     * Collects all task definitions from a Python file.
     */
    private void collectTaskDefinitions(Path file, Map<String, TaskDefinition> taskDefinitions) throws IOException {
        List<String> lines = readFileLines(file);
        String componentId = extractModuleName(file);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            Matcher decoratorMatcher = TASK_DECORATOR_PATTERN.matcher(line);
            if (decoratorMatcher.find()) {
                String decoratorParams = decoratorMatcher.group(1);

                // Find the function definition
                String functionName = findFunctionNameAfterDecorator(lines, i);
                if (functionName == null) {
                    continue;
                }

                // Extract queue name and task name from decorator
                String queueName = extractQueueName(decoratorParams);
                String taskName = extractTaskName(decoratorParams);

                // Use explicit task name if provided, otherwise use function name
                String effectiveTaskName = (taskName != null) ? taskName : functionName;

                TaskDefinition taskDef = new TaskDefinition(
                    effectiveTaskName,
                    functionName,
                    componentId,
                    queueName != null ? queueName : DEFAULT_QUEUE
                );

                taskDefinitions.put(functionName, taskDef);
                log.debug("Found Celery task: {} (function: {}) in queue: {}",
                    effectiveTaskName, functionName, taskDef.queueName);
            }
        }
    }

    /**
     * Finds task invocations and creates message flows.
     */
    private void findTaskInvocations(
            Path file,
            Map<String, TaskDefinition> taskDefinitions,
            List<MessageFlow> messageFlows) throws IOException {

        List<String> lines = readFileLines(file);
        String producerComponentId = extractModuleName(file);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            Matcher invocationMatcher = TASK_INVOCATION_PATTERN.matcher(line);
            while (invocationMatcher.find()) {
                String taskFunctionName = invocationMatcher.group(1);

                TaskDefinition taskDef = taskDefinitions.get(taskFunctionName);
                if (taskDef == null) {
                    // Task not found - might be imported or external
                    log.debug("Task invocation found but definition not found: {}", taskFunctionName);
                    continue;
                }

                // Check for queue override in apply_async
                String queueOverride = extractQueueNameFromInvocation(line);
                String effectiveQueue = (queueOverride != null) ? queueOverride : taskDef.queueName;

                MessageFlow messageFlow = new MessageFlow(
                    producerComponentId,
                    taskDef.componentId,
                    effectiveQueue,
                    taskDef.taskName,
                    null,  // No schema for Celery tasks
                    MESSAGE_TYPE_CELERY
                );

                messageFlows.add(messageFlow);
                log.debug("Found Celery invocation: {} -> {} (queue: {})",
                    producerComponentId, taskDef.taskName, effectiveQueue);
            }
        }
    }

    /**
     * Finds the function name after a task decorator.
     */
    private String findFunctionNameAfterDecorator(List<String> lines, int decoratorIndex) {
        for (int i = decoratorIndex + 1; i < Math.min(decoratorIndex + MAX_FUNCTION_SEARCH_LINES, lines.size()); i++) {
            String line = lines.get(i).trim();

            Matcher functionMatcher = FUNCTION_PATTERN.matcher(line);
            if (functionMatcher.find()) {
                return functionMatcher.group(1);
            }
        }
        return null;
    }

    /**
     * Extracts queue name from decorator parameters.
     */
    private String extractQueueName(String decoratorParams) {
        if (decoratorParams == null || decoratorParams.isEmpty()) {
            return null;
        }

        Matcher matcher = QUEUE_NAME_PATTERN.matcher(decoratorParams);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Extracts task name from decorator parameters.
     */
    private String extractTaskName(String decoratorParams) {
        if (decoratorParams == null || decoratorParams.isEmpty()) {
            return null;
        }

        Matcher matcher = TASK_NAME_PATTERN.matcher(decoratorParams);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Extracts queue name from task invocation line (for apply_async queue override).
     */
    private String extractQueueNameFromInvocation(String line) {
        Matcher matcher = QUEUE_NAME_PATTERN.matcher(line);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Extracts module name from file path.
     */
    private String extractModuleName(Path file) {
        String fileName = file.getFileName().toString();
        return fileName.replaceAll(PYTHON_FILE_EXTENSION, "");
    }

    /**
     * Internal class to hold task definition information.
     */
    private static class TaskDefinition {
        final String taskName;
        final String functionName;
        final String componentId;
        final String queueName;

        TaskDefinition(String taskName, String functionName, String componentId, String queueName) {
            this.taskName = taskName;
            this.functionName = functionName;
            this.componentId = componentId;
            this.queueName = queueName;
        }
    }
}
