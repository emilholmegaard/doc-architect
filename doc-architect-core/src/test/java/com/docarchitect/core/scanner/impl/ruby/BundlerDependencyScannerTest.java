package com.docarchitect.core.scanner.impl.ruby;

import com.docarchitect.core.model.Component;
import com.docarchitect.core.model.ComponentType;
import com.docarchitect.core.model.Dependency;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for {@link BundlerDependencyScanner}.
 *
 * <p>These tests validate the scanner's ability to:
 * <ul>
 *   <li>Parse Gemfile files for gem dependencies</li>
 *   <li>Parse Gemfile.lock files for locked versions</li>
 *   <li>Extract version constraints from gem declarations</li>
 *   <li>Identify development vs production dependencies based on groups</li>
 *   <li>Handle various Gemfile formats and edge cases</li>
 * </ul>
 *
 * @see BundlerDependencyScanner
 * @since 1.0.0
 */
class BundlerDependencyScannerTest extends ScannerTestBase {

    private BundlerDependencyScanner scanner;

    @BeforeEach
    void setUpScanner() {
        scanner = new BundlerDependencyScanner();
    }

    @Test
    void getId_returnsCorrectId() {
        assertThat(scanner.getId()).isEqualTo("bundler-dependencies");
    }

    @Test
    void getDisplayName_returnsCorrectName() {
        assertThat(scanner.getDisplayName()).isEqualTo("Bundler Dependency Scanner");
    }

    @Test
    void getPriority_returnsCorrectPriority() {
        assertThat(scanner.getPriority()).isEqualTo(80);
    }

    @Test
    void getSupportedLanguages_includesRuby() {
        assertThat(scanner.getSupportedLanguages()).contains("ruby");
    }

    @Test
    void getSupportedFilePatterns_includesGemfilePatterns() {
        assertThat(scanner.getSupportedFilePatterns())
            .contains("**/Gemfile", "**/Gemfile.lock");
    }

    @Test
    void appliesTo_withGemfile_returnsTrue() throws IOException {
        // Given: Project with Gemfile
        createFile("Gemfile", "gem 'rails'");

        // When/Then
        assertThat(scanner.appliesTo(context)).isTrue();
    }

    @Test
    void appliesTo_withoutGemfile_returnsFalse() {
        // Given: Project without Gemfile (empty temp directory)
        // When/Then
        assertThat(scanner.appliesTo(context)).isFalse();
    }

    @Test
    void scan_withSimpleGemfile_extractsDependencies() throws IOException {
        // Given: A simple Gemfile
        createFile("Gemfile", """
            source 'https://rubygems.org'

            gem 'rails', '~> 7.0.0'
            gem 'pg', '~> 1.4'
            gem 'redis', '~> 4.0'
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract dependencies
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(1);
        assertThat(result.dependencies()).hasSize(3);

        Component component = result.components().get(0);
        assertThat(component.type()).isEqualTo(ComponentType.SERVICE);
        assertThat(component.technology()).isEqualTo("ruby");

        Dependency railsDep = result.dependencies().stream()
            .filter(d -> "rails".equals(d.artifactId()))
            .findFirst()
            .orElseThrow();
        assertThat(railsDep.groupId()).isEqualTo("rubygems");
        assertThat(railsDep.version()).isEqualTo("~> 7.0.0");
        assertThat(railsDep.scope()).isEqualTo("compile");
    }

    @Test
    void scan_withGemfileLock_usesLockedVersions() throws IOException {
        // Given: Gemfile and Gemfile.lock
        createFile("Gemfile", """
            source 'https://rubygems.org'

            gem 'rails', '~> 7.0.0'
            gem 'pg', '~> 1.4'
            """);

        createFile("Gemfile.lock", """
            GEM
              remote: https://rubygems.org/
              specs:
                rails (7.0.8)
                pg (1.4.5)

            PLATFORMS
              ruby

            DEPENDENCIES
              rails (~> 7.0.0)
              pg (~> 1.4)
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should use locked versions from Gemfile.lock
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSize(2);

        Dependency railsDep = result.dependencies().stream()
            .filter(d -> "rails".equals(d.artifactId()))
            .findFirst()
            .orElseThrow();
        assertThat(railsDep.version()).isEqualTo("7.0.8");

        Dependency pgDep = result.dependencies().stream()
            .filter(d -> "pg".equals(d.artifactId()))
            .findFirst()
            .orElseThrow();
        assertThat(pgDep.version()).isEqualTo("1.4.5");
    }

    @Test
    void scan_withDevelopmentGroup_identifiesScope() throws IOException {
        // Given: Gemfile with development and test groups
        createFile("Gemfile", """
            source 'https://rubygems.org'

            gem 'rails', '~> 7.0'

            group :development, :test do
              gem 'rspec-rails', '~> 6.0'
              gem 'factory_bot_rails', '~> 6.2'
            end

            group :development do
              gem 'rubocop', '~> 1.50'
            end
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should identify correct scopes
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSize(4);

        Dependency railsDep = result.dependencies().stream()
            .filter(d -> "rails".equals(d.artifactId()))
            .findFirst()
            .orElseThrow();
        assertThat(railsDep.scope()).isEqualTo("compile");

        Dependency rspecDep = result.dependencies().stream()
            .filter(d -> "rspec-rails".equals(d.artifactId()))
            .findFirst()
            .orElseThrow();
        assertThat(rspecDep.scope()).isEqualTo("test");

        Dependency rubocopDep = result.dependencies().stream()
            .filter(d -> "rubocop".equals(d.artifactId()))
            .findFirst()
            .orElseThrow();
        assertThat(rubocopDep.scope()).isEqualTo("development");
    }

    @Test
    void scan_withMultipleVersionConstraints_extractsFirst() throws IOException {
        // Given: Gemfile with multiple version constraints
        createFile("Gemfile", """
            source 'https://rubygems.org'

            gem 'bcrypt', '~> 3.1', '>= 3.1.14'
            gem 'doorkeeper', '~> 5.8', '>= 5.8.1'
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract first version constraint
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSize(2);

        Dependency bcryptDep = result.dependencies().stream()
            .filter(d -> "bcrypt".equals(d.artifactId()))
            .findFirst()
            .orElseThrow();
        assertThat(bcryptDep.version()).isEqualTo("~> 3.1");
    }

    @Test
    void scan_withGemsWithoutVersions_defaultsToWildcard() throws IOException {
        // Given: Gemfile with gems without version constraints
        createFile("Gemfile", """
            source 'https://rubygems.org'

            gem 'rails'
            gem 'pg'
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should default to wildcard version
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSize(2);

        Dependency railsDep = result.dependencies().stream()
            .filter(d -> "rails".equals(d.artifactId()))
            .findFirst()
            .orElseThrow();
        assertThat(railsDep.version()).isEqualTo("*");
    }

    @Test
    void scan_withComments_ignoresCommentLines() throws IOException {
        // Given: Gemfile with comments
        createFile("Gemfile", """
            source 'https://rubygems.org'

            # Core framework
            gem 'rails', '~> 7.0'

            # Database
            # gem 'mysql2', '~> 0.5'  # Commented out
            gem 'pg', '~> 1.4'
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should ignore commented gems
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSize(2);
        assertThat(result.dependencies())
            .extracting(Dependency::artifactId)
            .containsExactlyInAnyOrder("rails", "pg")
            .doesNotContain("mysql2");
    }

    @Test
    void scan_withNestedGroups_handlesCorrectly() throws IOException {
        // Given: Gemfile with nested groups
        createFile("Gemfile", """
            source 'https://rubygems.org'

            gem 'rails', '~> 7.0'

            group :test do
              gem 'rspec-rails', '~> 6.0'
            end

            gem 'pg', '~> 1.4'
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should handle group nesting
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSize(3);

        // Gems outside groups should be compile scope
        Dependency railsDep = result.dependencies().stream()
            .filter(d -> "rails".equals(d.artifactId()))
            .findFirst()
            .orElseThrow();
        assertThat(railsDep.scope()).isEqualTo("compile");

        Dependency pgDep = result.dependencies().stream()
            .filter(d -> "pg".equals(d.artifactId()))
            .findFirst()
            .orElseThrow();
        assertThat(pgDep.scope()).isEqualTo("compile");

        // Gem inside test group should be test scope
        Dependency rspecDep = result.dependencies().stream()
            .filter(d -> "rspec-rails".equals(d.artifactId()))
            .findFirst()
            .orElseThrow();
        assertThat(rspecDep.scope()).isEqualTo("test");
    }

    @Test
    void scan_withComplexGemfile_parsesCorrectly() throws IOException {
        // Given: Complex Gemfile similar to GitLab
        createFile("Gemfile", """
            source 'https://rubygems.org'

            gem 'rails', '~> 7.2.3'
            gem 'pg', '~> 1.6.1'
            gem 'redis', '~> 4.0'
            gem 'sidekiq', '~> 7.0'

            # Authentication
            gem 'devise', '~> 4.9.3'
            gem 'bcrypt', '~> 3.1', '>= 3.1.14'
            gem 'doorkeeper', '~> 5.8', '>= 5.8.1'

            group :development, :test do
              gem 'rspec-rails', '~> 6.0'
              gem 'factory_bot_rails', '~> 6.2'
            end

            group :development do
              gem 'rubocop', '~> 1.50'
            end
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should parse all dependencies correctly
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(1);
        assertThat(result.dependencies()).hasSize(10);

        // Verify some key dependencies
        assertThat(result.dependencies())
            .extracting(Dependency::artifactId)
            .contains("rails", "pg", "redis", "sidekiq", "devise", "bcrypt",
                "doorkeeper", "rspec-rails", "factory_bot_rails", "rubocop");
    }

    @Test
    void scan_withNoGemfile_returnsEmptyResult() {
        // Given: No Gemfile in project
        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.components()).isEmpty();
        assertThat(result.dependencies()).isEmpty();
    }

    @Test
    void scan_withMultipleGemfiles_scansAll() throws IOException {
        // Given: Multiple Gemfiles (monorepo scenario)
        createFile("api/Gemfile", """
            source 'https://rubygems.org'
            gem 'rails', '~> 7.0'
            """);

        createFile("worker/Gemfile", """
            source 'https://rubygems.org'
            gem 'sidekiq', '~> 7.0'
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should scan all Gemfiles
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(2);
        assertThat(result.dependencies()).hasSize(2);

        assertThat(result.dependencies())
            .extracting(Dependency::artifactId)
            .containsExactlyInAnyOrder("rails", "sidekiq");
    }

    @Test
    void scan_withGitLabStyle_handlesSpecialSyntax() throws IOException {
        // Given: GitLab-style Gemfile with feature_category and path options
        createFile("Gemfile", """
            source 'https://rubygems.org'

            gem 'rails', '~> 7.2.3', feature_category: :shared
            gem 'pg', '~> 1.6.1', feature_category: :database
            gem 'bundler-checksum', '~> 0.1.0', path: 'gems/bundler-checksum', require: false
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract gem names and versions, ignoring extra options
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSize(3);

        assertThat(result.dependencies())
            .extracting(Dependency::artifactId)
            .containsExactlyInAnyOrder("rails", "pg", "bundler-checksum");
    }
}
