package com.docarchitect.core.scanner.impl.ruby;

import com.docarchitect.core.model.MessageFlow;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.base.AbstractRegexScanner;
import com.docarchitect.core.util.Technologies;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scanner for Sidekiq worker definitions and job invocations in Ruby source files.
 *
 * <p>Sidekiq is a background job processing system for Ruby. This scanner detects:
 * <ul>
 *   <li>Worker definitions using {@code include Sidekiq::Worker}</li>
 *   <li>Job invocations using {@code .perform_async()}, {@code .perform_in()}, {@code .perform_at()} methods</li>
 *   <li>Queue names from {@code sidekiq_options} configurations</li>
 * </ul>
 *
 * <p><b>Supported Patterns</b></p>
 * <ul>
 *   <li>{@code include Sidekiq::Worker} - Worker definition</li>
 *   <li>{@code include ApplicationWorker} - Rails application worker (wraps Sidekiq::Worker)</li>
 *   <li>{@code sidekiq_options queue: :notifications} - Queue specification with symbol</li>
 *   <li>{@code sidekiq_options queue: 'emails'} - Queue specification with string</li>
 *   <li>{@code EmailWorker.perform_async(args)} - Asynchronous job invocation</li>
 *   <li>{@code EmailWorker.perform_in(1.hour, args)} - Delayed job invocation</li>
 *   <li>{@code EmailWorker.perform_at(time, args)} - Scheduled job invocation</li>
 * </ul>
 *
 * <p><b>Queue Name Extraction</b></p>
 * <ul>
 *   <li>From sidekiq_options: {@code sidekiq_options queue: :notifications}</li>
 *   <li>Default queue: "default" (Sidekiq's default)</li>
 * </ul>
 *
 * <p><b>Message Flow Model</b></p>
 * <ul>
 *   <li>Producer: Component that calls .perform_async(), .perform_in(), or .perform_at()</li>
 *   <li>Consumer: Component that includes Sidekiq::Worker or ApplicationWorker</li>
 *   <li>Topic/Queue: Sidekiq queue name</li>
 *   <li>Type: "sidekiq"</li>
 * </ul>
 *
 * <p><b>Regex Patterns</b></p>
 * <ul>
 *   <li>{@code WORKER_INCLUDE_PATTERN}: {@code include\s+(?:Sidekiq::Worker|ApplicationWorker)}</li>
 *   <li>{@code CLASS_DEFINITION_PATTERN}: {@code class\s+([A-Z][a-zA-Z0-9_]*(?:::[A-Z][a-zA-Z0-9_]*)*)}</li>
 *   <li>{@code SIDEKIQ_OPTIONS_PATTERN}: {@code sidekiq_options\s+.*queue:\s*[:']([^'",\s}]+)}</li>
 *   <li>{@code PERFORM_INVOCATION_PATTERN}: {@code ([A-Z][a-zA-Z0-9_]*(?:::[A-Z][a-zA-Z0-9_]*)*)\\.perform_(?:async|in|at)\s*\(}</li>
 * </ul>
 *
 * <p><b>Example Usage</b></p>
 * <pre>{@code
 * // Worker definition
 * class EmailWorker
 *   include Sidekiq::Worker
 *   sidekiq_options queue: :notifications
 *
 *   def perform(user_id)
 *     # Send email
 *   end
 * end
 *
 * // Job invocation
 * EmailWorker.perform_async(123)
 * EmailWorker.perform_in(1.hour, 123)
 * }</pre>
 *
 * @see MessageFlow
 * @see AbstractRegexScanner
 * @since 1.0.0
 */
public class SidekiqScanner extends AbstractRegexScanner {

    private static final String SCANNER_ID = "sidekiq-jobs";
    private static final String SCANNER_DISPLAY_NAME = "Sidekiq Job Scanner";
    private static final String PATTERN_RUBY_FILES = "**/*.rb";
    private static final String MESSAGE_TYPE_SIDEKIQ = "sidekiq";
    private static final String DEFAULT_QUEUE = "default";
    private static final String RUBY_FILE_EXTENSION = "\\.rb$";

    private static final int MAX_CLASS_SEARCH_LINES = 200;

    /**
     * Regex to match Sidekiq worker includes.
     * Matches: include Sidekiq::Worker or include ApplicationWorker
     */
    private static final Pattern WORKER_INCLUDE_PATTERN = Pattern.compile(
        "include\\s+(?:Sidekiq::Worker|ApplicationWorker)"
    );

    /**
     * Regex to match module definitions.
     * Matches: module Notifications
     * Captures: (1) module name
     */
    private static final Pattern MODULE_DEFINITION_PATTERN = Pattern.compile(
        "^\\s*module\\s+([A-Z][a-zA-Z0-9_]*)"
    );

    /**
     * Regex to match class definitions.
     * Matches: class EmailWorker or class MyModule::EmailWorker
     * Captures: (1) class name (including namespace)
     */
    private static final Pattern CLASS_DEFINITION_PATTERN = Pattern.compile(
        "^\\s*class\\s+([A-Z][a-zA-Z0-9_]*(?:::[A-Z][a-zA-Z0-9_]*)*)"
    );

    /**
     * Regex to extract queue name from sidekiq_options.
     * Matches: sidekiq_options queue: :notifications or queue: 'emails'
     * Captures: (1) queue name
     */
    private static final Pattern SIDEKIQ_OPTIONS_PATTERN = Pattern.compile(
        "sidekiq_options\\s+.*?queue:\\s*[:'\"]([^'\"\\s,}]+)"
    );

    /**
     * Regex to match job invocations.
     * Matches: EmailWorker.perform_async(...) or MyModule::EmailWorker.perform_in(...)
     * Captures: (1) worker class name (including namespace)
     */
    private static final Pattern PERFORM_INVOCATION_PATTERN = Pattern.compile(
        "([A-Z][a-zA-Z0-9_]*(?:::[A-Z][a-zA-Z0-9_]*)*)\\.perform_(?:async|in|at)\\s*\\("
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
        return Set.of(Technologies.RUBY);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(PATTERN_RUBY_FILES);
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, PATTERN_RUBY_FILES);
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Sidekiq jobs in: {}", context.rootPath());

        List<MessageFlow> messageFlows = new ArrayList<>();
        List<Path> rubyFiles = context.findFiles(PATTERN_RUBY_FILES).toList();

        if (rubyFiles.isEmpty()) {
            return emptyResult();
        }

        // First pass: collect all worker definitions
        Map<String, WorkerDefinition> workerDefinitions = new HashMap<>();
        for (Path rubyFile : rubyFiles) {
            try {
                collectWorkerDefinitions(rubyFile, workerDefinitions);
            } catch (Exception e) {
                log.warn("Failed to parse Ruby file for worker definitions: {} - {}", rubyFile, e.getMessage());
            }
        }

        // Second pass: find job invocations and create message flows
        for (Path rubyFile : rubyFiles) {
            try {
                findJobInvocations(rubyFile, workerDefinitions, messageFlows);
            } catch (Exception e) {
                log.warn("Failed to parse Ruby file for job invocations: {} - {}", rubyFile, e.getMessage());
            }
        }

        log.info("Found {} Sidekiq message flows", messageFlows.size());

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
     * Collects all worker definitions from a Ruby file.
     */
    private void collectWorkerDefinitions(Path file, Map<String, WorkerDefinition> workerDefinitions) throws IOException {
        List<String> lines = readFileLines(file);
        String componentId = extractModuleName(file);

        List<String> moduleStack = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            // Track module nesting
            Matcher moduleMatcher = MODULE_DEFINITION_PATTERN.matcher(line);
            if (moduleMatcher.find()) {
                moduleStack.add(moduleMatcher.group(1));
                continue;
            }

            // Check for class definition
            Matcher classMatcher = CLASS_DEFINITION_PATTERN.matcher(line);
            if (classMatcher.find()) {
                String className = classMatcher.group(1);

                // Build fully qualified class name with module namespace
                String fullyQualifiedName = className;
                if (!moduleStack.isEmpty() && !className.contains("::")) {
                    // Only prepend module if class name doesn't already have namespace
                    fullyQualifiedName = String.join("::", moduleStack) + "::" + className;
                }

                // Check if this class includes Sidekiq::Worker or ApplicationWorker
                boolean isSidekiqWorker = false;
                String queueName = null;

                // Look ahead for include Sidekiq::Worker and sidekiq_options
                for (int j = i + 1; j < Math.min(i + MAX_CLASS_SEARCH_LINES, lines.size()); j++) {
                    String lookAheadLine = lines.get(j);

                    // Stop at next class definition
                    if (CLASS_DEFINITION_PATTERN.matcher(lookAheadLine).find()) {
                        break;
                    }

                    // Check for Sidekiq worker include
                    if (WORKER_INCLUDE_PATTERN.matcher(lookAheadLine).find()) {
                        isSidekiqWorker = true;
                    }

                    // Check for sidekiq_options queue
                    Matcher optionsMatcher = SIDEKIQ_OPTIONS_PATTERN.matcher(lookAheadLine);
                    if (optionsMatcher.find()) {
                        queueName = optionsMatcher.group(1);
                    }

                    // Stop at 'end' that likely closes the class (simplified check)
                    if (lookAheadLine.trim().equals("end")) {
                        break;
                    }
                }

                if (isSidekiqWorker) {
                    WorkerDefinition workerDef = new WorkerDefinition(
                        fullyQualifiedName,
                        componentId,
                        queueName != null ? queueName : DEFAULT_QUEUE
                    );

                    workerDefinitions.put(fullyQualifiedName, workerDef);
                    log.debug("Found Sidekiq worker: {} in queue: {}", fullyQualifiedName, workerDef.queueName);
                }
            }

            // Handle 'end' statements - pop module stack when we see 'end'
            // This is simplified - a proper implementation would track nesting depth
            if (line.trim().equals("end") && !moduleStack.isEmpty()) {
                moduleStack.remove(moduleStack.size() - 1);
            }
        }
    }

    /**
     * Finds job invocations and creates message flows.
     */
    private void findJobInvocations(
            Path file,
            Map<String, WorkerDefinition> workerDefinitions,
            List<MessageFlow> messageFlows) throws IOException {

        List<String> lines = readFileLines(file);
        String producerComponentId = extractModuleName(file);

        for (String line : lines) {
            Matcher invocationMatcher = PERFORM_INVOCATION_PATTERN.matcher(line);
            while (invocationMatcher.find()) {
                String workerClassName = invocationMatcher.group(1);

                WorkerDefinition workerDef = workerDefinitions.get(workerClassName);
                if (workerDef == null) {
                    // Worker not found - might be external or imported
                    log.debug("Job invocation found but worker definition not found: {}", workerClassName);
                    continue;
                }

                MessageFlow messageFlow = new MessageFlow(
                    producerComponentId,
                    workerDef.componentId,
                    workerDef.queueName,
                    workerDef.workerClassName,
                    null,  // No schema for Sidekiq jobs
                    MESSAGE_TYPE_SIDEKIQ
                );

                messageFlows.add(messageFlow);
                log.debug("Found Sidekiq invocation: {} -> {} (queue: {})",
                    producerComponentId, workerDef.workerClassName, workerDef.queueName);
            }
        }
    }

    /**
     * Extracts module name from file path.
     * Converts app/workers/email_worker.rb -> email_worker
     */
    private String extractModuleName(Path file) {
        String fileName = file.getFileName().toString();
        return fileName.replaceAll(RUBY_FILE_EXTENSION, "");
    }

    /**
     * Internal class to hold worker definition information.
     */
    private static class WorkerDefinition {
        final String workerClassName;
        final String componentId;
        final String queueName;

        WorkerDefinition(String workerClassName, String componentId, String queueName) {
            this.workerClassName = workerClassName;
            this.componentId = componentId;
            this.queueName = queueName;
        }
    }
}
