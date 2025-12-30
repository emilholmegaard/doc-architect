package com.docarchitect.core.scanner.impl.dotnet;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.docarchitect.core.model.MessageFlow;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.base.AbstractRegexScanner;
import com.docarchitect.core.util.Technologies;

/**
 * Scanner for Streamiz.Kafka.Net stream processing applications in C#.
 *
 * <p>Streamiz.Kafka.Net is a .NET stream processing library for Apache Kafka,
 * inspired by Kafka Streams. This scanner detects stream topologies built with
 * the Streamiz API.
 *
 * <p><b>Supported Patterns</b></p>
 * <ul>
 *   <li>{@code builder.Stream<K,V>("topic")} - Stream source from topic</li>
 *   <li>{@code builder.Table<K,V>("topic")} - Table source from topic</li>
 *   <li>{@code builder.GlobalTable<K,V>("topic")} - Global table source</li>
 *   <li>{@code stream.To("topic")} - Sink to topic</li>
 *   <li>{@code stream.Through("topic")} - Through operator (consume + produce)</li>
 *   <li>{@code stream.Repartition()} - Repartition operation</li>
 * </ul>
 *
 * <p><b>Example Detection</b></p>
 * <pre>{@code
 * using Streamiz.Kafka.Net;
 * using Streamiz.Kafka.Net.Stream;
 *
 * var builder = new StreamBuilder();
 * var orders = builder.Stream<string, Order>("orders");
 * var products = builder.Table<string, Product>("products");
 *
 * orders
 *     .Join(products, ...)
 *     .To("enriched-orders");
 * }</pre>
 *
 * <p>Generates:
 * <ul>
 *   <li>MessageFlow: null → ClassName (topic: orders, type: IKStream)</li>
 *   <li>MessageFlow: null → ClassName (topic: products, type: IKTable)</li>
 *   <li>MessageFlow: ClassName → null (topic: enriched-orders)</li>
 * </ul>
 *
 * @see MessageFlow
 * @since 1.0.0
 */
public class StreamizKafkaScanner extends AbstractRegexScanner {

    private static final String SCANNER_ID = "streamiz-kafka";
    private static final String SCANNER_DISPLAY_NAME = "Streamiz.Kafka.Net Stream Processing Scanner";
    private static final String FILE_PATTERN_NESTED = "**/*.cs";
    private static final String FILE_PATTERN_ROOT = "*.cs";
    private static final String TECHNOLOGY = "streamiz-kafka";

    private static final String DEFAULT_MESSAGE_TYPE = "IKStream<?,?>";
    private static final String KTABLE_TYPE = "IKTable<?,?>";
    private static final int SCANNER_PRIORITY = 75;

    // Regex patterns for Streamiz API detection
    private static final Pattern STREAM_PATTERN =
        Pattern.compile("builder\\.Stream<[^>]+>\\s*\\(\\s*\"([^\"]+)\"");

    private static final Pattern TABLE_PATTERN =
        Pattern.compile("builder\\.(Table|GlobalTable)<[^>]+>\\s*\\(\\s*\"([^\"]+)\"");

    private static final Pattern TO_PATTERN =
        Pattern.compile("\\.To\\s*\\(\\s*\"([^\"]+)\"");

    private static final Pattern THROUGH_PATTERN =
        Pattern.compile("\\.Through\\s*\\(\\s*\"([^\"]+)\"");

    private static final Pattern REPARTITION_PATTERN =
        Pattern.compile("\\.Repartition\\s*\\(");

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
        return Set.of(Technologies.CSHARP);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(FILE_PATTERN_NESTED, FILE_PATTERN_ROOT);
    }

    @Override
    public int getPriority() {
        return SCANNER_PRIORITY;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, FILE_PATTERN_NESTED, FILE_PATTERN_ROOT);
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Streamiz.Kafka.Net topologies in: {}", context.rootPath());

        List<MessageFlow> messageFlows = new ArrayList<>();

        // Find C# files at both root and nested levels
        List<Path> csFiles = new ArrayList<>();
        context.findFiles(FILE_PATTERN_ROOT).forEach(csFiles::add);
        context.findFiles(FILE_PATTERN_NESTED).forEach(csFiles::add);

        if (csFiles.isEmpty()) {
            return emptyResult();
        }

        int parsedFiles = 0;
        int skippedFiles = 0;

        for (Path csFile : csFiles) {
            try {
                if (!shouldScanFile(csFile)) {
                    skippedFiles++;
                    continue;
                }
                parseStreamizFile(csFile, messageFlows);
                parsedFiles++;
            } catch (Exception e) {
                log.warn("Failed to parse C# file: {} - {}", csFile, e.getMessage());
            }
        }

        log.info("Found {} Streamiz.Kafka.Net message flows (parsed {}/{} files, skipped {} files)",
                 messageFlows.size(), parsedFiles, csFiles.size(), skippedFiles);

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
     * Pre-filters files to only scan those containing Streamiz imports or usage.
     *
     * @param file path to C# file
     * @return true if file likely contains Streamiz code
     */
    private boolean shouldScanFile(Path file) {
        try {
            String content = readFileContent(file);
            return content.contains("using Streamiz.Kafka.Net") ||
                   content.contains("StreamBuilder") ||
                   content.contains("IKStream") ||
                   content.contains("IKTable") ||
                   content.contains(".Stream<") ||
                   content.contains(".Table<");
        } catch (IOException e) {
            log.debug("Failed to read file for pre-filtering: {}", file);
            return false;
        }
    }

    /**
     * Parses a C# file for Streamiz.Kafka.Net stream processing patterns.
     *
     * @param file C# file to parse
     * @param messageFlows list to populate with discovered message flows
     */
    private void parseStreamizFile(Path file, List<MessageFlow> messageFlows) throws IOException {
        String content = readFileContent(file);
        String className = extractClassName(file, content);

        // Extract stream sources (builder.Stream<K,V>("topic"))
        Matcher streamMatcher = STREAM_PATTERN.matcher(content);
        while (streamMatcher.find()) {
            String topic = streamMatcher.group(1);
            messageFlows.add(new MessageFlow(null, className, topic,
                DEFAULT_MESSAGE_TYPE, null, TECHNOLOGY));
            log.debug("Found Streamiz stream source: {} consuming from topic: {}", className, topic);
        }

        // Extract table sources (builder.Table<K,V>("topic") or builder.GlobalTable<K,V>("topic"))
        Matcher tableMatcher = TABLE_PATTERN.matcher(content);
        while (tableMatcher.find()) {
            String topic = tableMatcher.group(2);
            messageFlows.add(new MessageFlow(null, className, topic,
                KTABLE_TYPE, null, TECHNOLOGY));
            log.debug("Found Streamiz table source: {} consuming from topic: {}", className, topic);
        }

        // Extract sinks (stream.To("topic"))
        Matcher toMatcher = TO_PATTERN.matcher(content);
        while (toMatcher.find()) {
            String topic = toMatcher.group(1);
            messageFlows.add(new MessageFlow(className, null, topic,
                DEFAULT_MESSAGE_TYPE, null, TECHNOLOGY));
            log.debug("Found Streamiz sink: {} producing to topic: {}", className, topic);
        }

        // Extract through operations (stream.Through("topic"))
        Matcher throughMatcher = THROUGH_PATTERN.matcher(content);
        while (throughMatcher.find()) {
            String topic = throughMatcher.group(1);
            // through() is both a consumer and producer
            messageFlows.add(new MessageFlow(null, className, topic,
                DEFAULT_MESSAGE_TYPE, null, TECHNOLOGY));
            messageFlows.add(new MessageFlow(className, null, topic,
                DEFAULT_MESSAGE_TYPE, null, TECHNOLOGY));
            log.debug("Found Streamiz through: {} both consuming and producing to topic: {}", className, topic);
        }
    }

    /**
     * Extracts class name from file path and content.
     *
     * @param file C# file
     * @param content file content
     * @return class name
     */
    private String extractClassName(Path file, String content) {
        // Try to extract from namespace and class declaration
        Pattern namespacePattern = Pattern.compile("namespace\\s+([\\w.]+)");
        Pattern classPattern = Pattern.compile("(?:public|internal|private)?\\s*(?:static)?\\s*(?:partial)?\\s*class\\s+(\\w+)");

        Matcher namespaceMatcher = namespacePattern.matcher(content);
        Matcher classMatcher = classPattern.matcher(content);

        String namespace = namespaceMatcher.find() ? namespaceMatcher.group(1) : "";
        String className = classMatcher.find() ? classMatcher.group(1) : file.getFileName().toString().replace(".cs", "");

        return namespace.isEmpty() ? className : namespace + "." + className;
    }
}
