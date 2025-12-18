package com.docarchitect.core.renderer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the OutputRenderer SPI (Service Provider Interface).
 *
 * <p>These tests verify:
 * <ul>
 *   <li>All renderers are discoverable via ServiceLoader</li>
 *   <li>Each renderer has valid metadata (ID)</li>
 *   <li>Renderer IDs are unique across all implementations</li>
 *   <li>SPI registration files are correctly configured</li>
 * </ul>
 *
 * <p><b>Critical for runtime behavior:</b> If these tests fail, renderers won't be
 * discovered by the CLI application, resulting in silent failures during execution.
 */
class RendererIntegrationTest {

    /**
     * Expected renderer count for Phase 8:
     * - FileSystemRenderer (writes to filesystem)
     * - ConsoleRenderer (writes to console)
     * Total: 2 renderers
     */
    private static final int EXPECTED_RENDERER_COUNT = 2;

    /**
     * Verifies all renderers are discoverable via ServiceLoader.
     * This is the core SPI mechanism used by the CLI to find available renderers.
     */
    @Test
    void serviceLoader_shouldDiscoverAllRenderers() {
        ServiceLoader<OutputRenderer> loader = ServiceLoader.load(OutputRenderer.class);
        List<OutputRenderer> renderers = new ArrayList<>();
        loader.forEach(renderers::add);

        assertThat(renderers)
            .as("ServiceLoader should discover exactly %d renderers", EXPECTED_RENDERER_COUNT)
            .hasSize(EXPECTED_RENDERER_COUNT);
    }

    /**
     * Verifies each renderer has a valid, non-empty ID.
     * IDs are used for configuration, logging, and selecting renderers.
     */
    @Test
    void allRenderers_shouldHaveValidId() {
        ServiceLoader<OutputRenderer> loader = ServiceLoader.load(OutputRenderer.class);

        loader.forEach(renderer -> {
            assertThat(renderer.getId())
                .as("Renderer %s should have non-null ID", renderer.getClass().getSimpleName())
                .isNotNull()
                .isNotBlank();
        });
    }

    /**
     * Verifies all renderer IDs are unique.
     * Duplicate IDs would cause configuration conflicts and ambiguous logging.
     */
    @Test
    void allRenderers_shouldHaveUniqueIds() {
        ServiceLoader<OutputRenderer> loader = ServiceLoader.load(OutputRenderer.class);
        Set<String> ids = new HashSet<>();
        List<String> duplicates = new ArrayList<>();

        loader.forEach(renderer -> {
            String id = renderer.getId();
            if (!ids.add(id)) {
                duplicates.add(id);
            }
        });

        assertThat(duplicates)
            .as("Renderer IDs should be unique, but found duplicates: %s", duplicates)
            .isEmpty();
    }

    /**
     * Verifies renderers can be instantiated and basic methods called.
     * This ensures no runtime initialization errors occur.
     */
    @Test
    void allRenderers_shouldBeInstantiable() {
        ServiceLoader<OutputRenderer> loader = ServiceLoader.load(OutputRenderer.class);

        loader.forEach(renderer -> {
            assertThat(renderer)
                .as("Renderer should be instantiable")
                .isNotNull();

            // Verify getId() doesn't throw exceptions
            assertThat(renderer.getId()).isNotNull();
        });
    }

    /**
     * Verifies expected renderer types are present.
     * This ensures all planned renderer categories are implemented.
     */
    @Test
    void expectedRenderers_shouldBePresent() {
        ServiceLoader<OutputRenderer> loader = ServiceLoader.load(OutputRenderer.class);
        Set<String> rendererIds = new HashSet<>();

        loader.forEach(renderer -> rendererIds.add(renderer.getId()));

        assertThat(rendererIds)
            .as("Should have both filesystem and console renderers")
            .contains("filesystem", "console");
    }

    /**
     * Verifies renderer IDs follow naming conventions.
     * IDs should be lowercase for consistency.
     */
    @Test
    void allRenderers_shouldFollowNamingConventions() {
        ServiceLoader<OutputRenderer> loader = ServiceLoader.load(OutputRenderer.class);

        loader.forEach(renderer -> {
            String id = renderer.getId();
            assertThat(id)
                .as("Renderer ID should be lowercase: %s", id)
                .isEqualTo(id.toLowerCase());
        });
    }
}
