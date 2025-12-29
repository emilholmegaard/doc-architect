package com.docarchitect.core.scanner.impl.python;

import com.docarchitect.core.model.Component;
import com.docarchitect.core.model.ComponentType;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.base.AbstractRegexScanner;
import com.docarchitect.core.util.IdGenerator;
import com.docarchitect.core.util.Technologies;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Scanner for Django applications defined in settings.py INSTALLED_APPS.
 *
 * <p>This scanner extracts Django apps from the INSTALLED_APPS configuration,
 * treating each app as an architectural component. Django apps are modular
 * components that encapsulate specific functionality (e.g., authentication,
 * blog, e-commerce).
 *
 * <p><b>Example settings.py:</b></p>
 * <pre>{@code
 * INSTALLED_APPS = [
 *     'django.contrib.admin',
 *     'django.contrib.auth',
 *     'myapp.users',
 *     'myapp.products',
 *     'myapp.orders',
 * ]
 * }</pre>
 *
 * <p><b>Component Extraction:</b>
 * <ul>
 *   <li>Built-in Django apps (django.contrib.*) are marked as EXTERNAL</li>
 *   <li>Third-party apps are marked as LIBRARY</li>
 *   <li>Local apps (detected by directory presence) are marked as MODULE</li>
 * </ul>
 *
 * <p><b>Detection Strategy:</b>
 * <ol>
 *   <li>Find settings.py or settings/*.py files</li>
 *   <li>Parse INSTALLED_APPS list (handles both list and tuple syntax)</li>
 *   <li>For each app, check if it's local (has corresponding directory)</li>
 *   <li>Create Component with appropriate type</li>
 * </ol>
 *
 * <p><b>Priority:</b> 15 (after dependency scanners, with other framework scanners)</p>
 *
 * @see Component
 * @since 1.0.0
 */
public class DjangoAppScanner extends AbstractRegexScanner {

    private static final String SCANNER_ID = "django-apps";
    private static final String SCANNER_DISPLAY_NAME = "Django App Scanner";
    private static final String SETTINGS_PATTERN = "**/settings.py";
    private static final String SETTINGS_DIR_PATTERN = "**/settings/*.py";
    private static final int SCANNER_PRIORITY = 15;

    // Regex to match INSTALLED_APPS = [...] or INSTALLED_APPS = (...)
    private static final Pattern INSTALLED_APPS_PATTERN = Pattern.compile(
        "INSTALLED_APPS\\s*=\\s*[\\[\\(]([^\\]\\)]+)[\\]\\)]",
        Pattern.DOTALL
    );

    // Regex to extract individual app strings from the list
    private static final Pattern APP_STRING_PATTERN = Pattern.compile(
        "['\"]([^'\"]+)['\"]"
    );

    private static final String DJANGO_PREFIX = "django.contrib.";
    private static final String TECHNOLOGY = "Django";

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
        return Set.of(SETTINGS_PATTERN, SETTINGS_DIR_PATTERN);
    }

    @Override
    public int getPriority() {
        return SCANNER_PRIORITY;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, SETTINGS_PATTERN, SETTINGS_DIR_PATTERN);
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Django applications in: {}", context.rootPath());

        List<Component> components = new ArrayList<>();
        Set<String> processedApps = new HashSet<>();

        // Find settings.py files
        List<Path> settingsFiles = Stream.concat(
            context.findFiles(SETTINGS_PATTERN),
            context.findFiles(SETTINGS_DIR_PATTERN)
        ).toList();

        if (settingsFiles.isEmpty()) {
            log.debug("No Django settings.py files found");
            return emptyResult();
        }

        for (Path settingsFile : settingsFiles) {
            try {
                parseSettingsFile(settingsFile, context, components, processedApps);
            } catch (IOException e) {
                log.warn("Failed to parse settings file: {} - {}", settingsFile, e.getMessage());
            }
        }

        log.info("Found {} Django apps", components.size());

        return buildSuccessResult(
            components,
            List.of(), // No dependencies
            List.of(), // No API endpoints
            List.of(), // No message flows
            List.of(), // No data entities
            List.of(), // No relationships
            List.of()  // No warnings
        );
    }

    /**
     * Parses a Django settings.py file to extract INSTALLED_APPS.
     *
     * @param settingsFile path to settings.py
     * @param context scan context
     * @param components list to add discovered components
     * @param processedApps set of already processed app names
     * @throws IOException if file cannot be read
     */
    private void parseSettingsFile(Path settingsFile, ScanContext context,
                                   List<Component> components, Set<String> processedApps) throws IOException {
        String content = readFileContent(settingsFile);

        Matcher appsMatcher = INSTALLED_APPS_PATTERN.matcher(content);

        if (!appsMatcher.find()) {
            log.debug("No INSTALLED_APPS found in {}", settingsFile);
            return;
        }

        String appsBlock = appsMatcher.group(1);
        Matcher appMatcher = APP_STRING_PATTERN.matcher(appsBlock);

        while (appMatcher.find()) {
            String appName = appMatcher.group(1);

            // Skip if already processed
            if (processedApps.contains(appName)) {
                continue;
            }

            // Determine component type and create component
            ComponentType componentType = determineComponentType(appName, context);

            // Skip built-in Django apps to reduce noise
            if (componentType == ComponentType.EXTERNAL && appName.startsWith(DJANGO_PREFIX)) {
                log.debug("Skipping built-in Django app: {}", appName);
                continue;
            }

            String componentId = IdGenerator.generate("django-app", appName);
            String displayName = extractAppDisplayName(appName);

            Map<String, String> metadata = new HashMap<>();
            metadata.put("appName", appName);
            metadata.put("settingsFile", context.rootPath().relativize(settingsFile).toString());

            // Try to find the app directory
            String appPath = findAppDirectory(appName, context);
            if (appPath != null) {
                metadata.put("appDirectory", appPath);
            }

            Component component = new Component(
                componentId,
                displayName,
                componentType,
                "Django app: " + appName,
                TECHNOLOGY,
                appPath != null ? context.rootPath().resolve(appPath).toString() : null,
                metadata
            );

            components.add(component);
            processedApps.add(appName);

            log.debug("Found Django app: {} (type={})", appName, componentType);
        }
    }

    /**
     * Determines component type based on app name and presence in project.
     *
     * @param appName Django app name
     * @param context scan context
     * @return component type
     */
    private ComponentType determineComponentType(String appName, ScanContext context) {
        // Built-in Django apps
        if (appName.startsWith(DJANGO_PREFIX)) {
            return ComponentType.EXTERNAL;
        }

        // Check if it's a local app (has directory in project)
        if (findAppDirectory(appName, context) != null) {
            return ComponentType.MODULE;
        }

        // Third-party app
        return ComponentType.LIBRARY;
    }

    /**
     * Finds the directory for a Django app.
     *
     * @param appName app name (e.g., "myapp.users")
     * @param context scan context
     * @return relative path to app directory, or null if not found
     */
    private String findAppDirectory(String appName, ScanContext context) {
        // Convert app name to possible directory paths
        // e.g., "myapp.users" -> ["myapp/users", "users"]
        List<String> possiblePaths = new ArrayList<>();

        if (appName.contains(".")) {
            possiblePaths.add(appName.replace(".", "/"));
            String[] parts = appName.split("\\.");
            possiblePaths.add(parts[parts.length - 1]); // Last segment
        } else {
            possiblePaths.add(appName);
        }

        for (String possiblePath : possiblePaths) {
            Path appDir = context.rootPath().resolve(possiblePath);
            if (Files.exists(appDir) && Files.isDirectory(appDir)) {
                // Verify it's a Python package (has __init__.py)
                Path initFile = appDir.resolve("__init__.py");
                if (Files.exists(initFile)) {
                    return context.rootPath().relativize(appDir).toString();
                }
            }
        }

        return null;
    }

    /**
     * Extracts display name from app name.
     *
     * @param appName full app name (e.g., "myapp.users")
     * @return display name (e.g., "users")
     */
    private String extractAppDisplayName(String appName) {
        if (appName.contains(".")) {
            String[] parts = appName.split("\\.");
            return parts[parts.length - 1];
        }
        return appName;
    }
}
