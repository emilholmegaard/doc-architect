package com.docarchitect.core.scanner.impl.ruby;

import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RailsApiScannerDebugTest extends ScannerTestBase {

    private RailsApiScanner scanner;

    @BeforeEach
    void setUpScanner() {
        scanner = new RailsApiScanner();
    }

    @Test
    void debug_fileDiscovery() throws IOException {
        // Create a controller file
        Path controllerFile = createFile("app/controllers/users_controller.rb", """
            class UsersController < ApplicationController
              def index
              end
            end
            """);

        System.out.println("Created file: " + controllerFile);
        System.out.println("File exists: " + Files.exists(controllerFile));
        System.out.println("TempDir: " + tempDir);

        // Check if scanner detects it
        List<Path> foundFiles = context.findFiles("**/app/controllers/**/*_controller.rb").toList();
        System.out.println("Found files: " + foundFiles.size());
        foundFiles.forEach(f -> System.out.println("  - " + f));

        // Try scanning
        ScanResult result = scanner.scan(context);
        System.out.println("Scan result success: " + result.success());
        System.out.println("Components: " + result.components().size());
        System.out.println("Endpoints: " + result.apiEndpoints().size());
    }
}
