package com.docarchitect.core.scanner.impl.python;

import com.docarchitect.core.model.Component;
import com.docarchitect.core.model.ComponentType;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for {@link DjangoAppScanner}.
 *
 * <p>Tests the scanner's ability to extract Django applications
 * from settings.py INSTALLED_APPS configuration.
 */
class DjangoAppScannerTest extends ScannerTestBase {

    private final DjangoAppScanner scanner = new DjangoAppScanner();

    @Test
    void scan_withInstalledApps_extractsLocalApps() throws IOException {
        // Given: Django settings.py with local apps
        createFile("myproject/settings.py", """
            INSTALLED_APPS = [
                'django.contrib.admin',
                'django.contrib.auth',
                'myapp.users',
                'myapp.products',
                'myapp.orders',
            ]
            """);

        // Create app directories with __init__.py and models.py to mark them as Django apps
        createDirectory("myapp/users");
        createFile("myapp/users/__init__.py", "");
        createFile("myapp/users/models.py", "");

        createDirectory("myapp/products");
        createFile("myapp/products/__init__.py", "");
        createFile("myapp/products/models.py", "");

        createDirectory("myapp/orders");
        createFile("myapp/orders/__init__.py", "");
        createFile("myapp/orders/models.py", "");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract local apps (excluding django.contrib.*)
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(3);

        assertThat(result.components())
            .extracting(Component::name)
            .containsExactlyInAnyOrder("users", "products", "orders");

        Component usersApp = result.components().get(0);
        assertThat(usersApp.type()).isEqualTo(ComponentType.MODULE);
        assertThat(usersApp.technology()).isEqualTo("Django");
        assertThat(usersApp.metadata().get("appName")).isEqualTo("myapp.users");
    }

    @Test
    void scan_withTupleSyntax_extractsApps() throws IOException {
        // Given: Django settings.py using tuple syntax
        createFile("myproject/settings.py", """
            INSTALLED_APPS = (
                'django.contrib.admin',
                'myapp.users',
                'myapp.products',
            )
            """);

        createDirectory("myapp/users");
        createFile("myapp/users/__init__.py", "");
        createFile("myapp/users/models.py", "");

        createDirectory("myapp/products");
        createFile("myapp/products/__init__.py", "");
        createFile("myapp/products/models.py", "");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should handle tuple syntax
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(2);

        assertThat(result.components())
            .extracting(Component::name)
            .containsExactlyInAnyOrder("users", "products");
    }

    @Test
    void scan_withThirdPartyApps_classifiesAsLibrary() throws IOException {
        // Given: Django settings.py with third-party apps (no local directory)
        createFile("myproject/settings.py", """
            INSTALLED_APPS = [
                'django.contrib.admin',
                'rest_framework',
                'corsheaders',
            ]
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should classify as LIBRARY (no local directory)
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(2);

        assertThat(result.components())
            .allMatch(c -> c.type() == ComponentType.LIBRARY);

        assertThat(result.components())
            .extracting(Component::name)
            .containsExactlyInAnyOrder("rest_framework", "corsheaders");
    }

    @Test
    void scan_withNestedSettings_extractsApps() throws IOException {
        // Given: Django settings in nested settings directory
        createFile("myproject/settings/base.py", """
            INSTALLED_APPS = [
                'myapp.users',
                'myapp.products',
            ]
            """);

        createDirectory("myapp/users");
        createFile("myapp/users/__init__.py", "");
        createFile("myapp/users/models.py", "");

        createDirectory("myapp/products");
        createFile("myapp/products/__init__.py", "");
        createFile("myapp/products/models.py", "");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should find settings in nested directory
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(2);
    }

    @Test
    void scan_withSimpleAppName_extractsApp() throws IOException {
        // Given: Django settings.py with simple app names (no dot notation)
        createFile("myproject/settings.py", """
            INSTALLED_APPS = [
                'users',
                'products',
            ]
            """);

        createDirectory("users");
        createFile("users/__init__.py", "");
        createFile("users/models.py", "");

        createDirectory("products");
        createFile("products/__init__.py", "");
        createFile("products/models.py", "");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract apps with simple names
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(2);

        assertThat(result.components())
            .extracting(Component::name)
            .containsExactlyInAnyOrder("users", "products");
    }

    @Test
    void scan_withoutInitPy_classifiesAsLibrary() throws IOException {
        // Given: App directory exists but no __init__.py
        createFile("myproject/settings.py", """
            INSTALLED_APPS = [
                'myapp.users',
            ]
            """);

        createDirectory("myapp/users");
        // No __init__.py created

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should classify as library (not a local Python package)
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(1);
        assertThat(result.components().get(0).type()).isEqualTo(ComponentType.LIBRARY);
    }

    @Test
    void scan_withDuplicateApps_deduplicates() throws IOException {
        // Given: Multiple settings files with same app
        createFile("myproject/settings/base.py", """
            INSTALLED_APPS = [
                'myapp.users',
            ]
            """);

        createFile("myproject/settings/production.py", """
            INSTALLED_APPS = [
                'myapp.users',
            ]
            """);

        createDirectory("myapp/users");
        createFile("myapp/users/__init__.py", "");
        createFile("myapp/users/models.py", "");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should only include users once
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(1);
        assertThat(result.components().get(0).name()).isEqualTo("users");
    }

    @Test
    void scan_withMultilineInstalledApps_extractsAll() throws IOException {
        // Given: Django settings.py with multiline INSTALLED_APPS
        createFile("myproject/settings.py", """
            INSTALLED_APPS = [
                'django.contrib.admin',
                'django.contrib.auth',
                'django.contrib.contenttypes',

                # Local apps
                'myapp.users',
                'myapp.products',
                'myapp.orders',

                # Third party
                'rest_framework',
            ]
            """);

        createDirectory("myapp/users");
        createFile("myapp/users/__init__.py", "");
        createFile("myapp/users/models.py", "");

        createDirectory("myapp/products");
        createFile("myapp/products/__init__.py", "");
        createFile("myapp/products/models.py", "");

        createDirectory("myapp/orders");
        createFile("myapp/orders/__init__.py", "");
        createFile("myapp/orders/models.py", "");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract all apps (excluding built-in django.contrib)
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(4); // 3 local + 1 third-party

        assertThat(result.components())
            .extracting(Component::name)
            .containsExactlyInAnyOrder("users", "products", "orders", "rest_framework");
    }

    @Test
    void scan_withNoSettingsFile_returnsEmptyResult() {
        // Given: No settings.py file

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.components()).isEmpty();
    }

    @Test
    void scan_withNoInstalledApps_returnsEmptyResult() throws IOException {
        // Given: settings.py without INSTALLED_APPS
        createFile("myproject/settings.py", """
            DEBUG = True
            DATABASES = {}
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.components()).isEmpty();
    }

    @Test
    void appliesTo_withSettingsFile_returnsTrue() throws IOException {
        // Given: A settings.py file exists
        createFile("myproject/settings.py", "INSTALLED_APPS = []");

        // When: Check if scanner applies
        boolean applies = scanner.appliesTo(context);

        // Then: Should return true
        assertThat(applies).isTrue();
    }

    @Test
    void appliesTo_withoutSettingsFile_returnsFalse() {
        // Given: No settings.py file

        // When: Check if scanner applies
        boolean applies = scanner.appliesTo(context);

        // Then: Should return false
        assertThat(applies).isFalse();
    }

    @Test
    void scan_withAppsFile_detectsAsLocalApp() throws IOException {
        // Given: Django settings.py with app that has apps.py (but no models.py)
        createFile("myproject/settings.py", """
            INSTALLED_APPS = [
                'myapp.users',
            ]
            """);

        createDirectory("myapp/users");
        createFile("myapp/users/__init__.py", "");
        createFile("myapp/users/apps.py", """
            from django.apps import AppConfig

            class UsersConfig(AppConfig):
                name = 'myapp.users'
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should detect as local MODULE (has apps.py)
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(1);
        assertThat(result.components().get(0).type()).isEqualTo(ComponentType.MODULE);
    }

    @Test
    void scan_withVerboseName_extractsDisplayName() throws IOException {
        // Given: Django settings.py with app that has verbose_name in apps.py
        createFile("myproject/settings.py", """
            INSTALLED_APPS = [
                'saleor.checkout',
                'saleor.payment',
            ]
            """);

        createDirectory("saleor/checkout");
        createFile("saleor/checkout/__init__.py", "");
        createFile("saleor/checkout/apps.py", """
            from django.apps import AppConfig

            class CheckoutConfig(AppConfig):
                name = 'saleor.checkout'
                verbose_name = 'Checkout'
            """);

        createDirectory("saleor/payment");
        createFile("saleor/payment/__init__.py", "");
        createFile("saleor/payment/apps.py", """
            from django.apps import AppConfig

            class PaymentConfig(AppConfig):
                name = 'saleor.payment'
                verbose_name = "Payment Processing"
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract verbose_name as display name
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(2);

        assertThat(result.components())
            .extracting(Component::name)
            .containsExactlyInAnyOrder("Checkout", "Payment Processing");
    }

    @Test
    void scan_withoutVerboseName_usesAppName() throws IOException {
        // Given: Django app with apps.py but no verbose_name
        createFile("myproject/settings.py", """
            INSTALLED_APPS = [
                'myapp.users',
            ]
            """);

        createDirectory("myapp/users");
        createFile("myapp/users/__init__.py", "");
        createFile("myapp/users/apps.py", """
            from django.apps import AppConfig

            class UsersConfig(AppConfig):
                name = 'myapp.users'
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should use last segment of app name as display name
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(1);
        assertThat(result.components().get(0).name()).isEqualTo("users");
    }

    @Test
    void scan_withModelsFile_detectsAsLocalApp() throws IOException {
        // Given: Django settings.py with app that has models.py (but no apps.py)
        createFile("myproject/settings.py", """
            INSTALLED_APPS = [
                'myapp.products',
            ]
            """);

        createDirectory("myapp/products");
        createFile("myapp/products/__init__.py", "");
        createFile("myapp/products/models.py", """
            from django.db import models

            class Product(models.Model):
                name = models.CharField(max_length=100)
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should detect as local MODULE (has models.py)
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(1);
        assertThat(result.components().get(0).type()).isEqualTo(ComponentType.MODULE);
        assertThat(result.components().get(0).name()).isEqualTo("products");
    }

    @Test
    void scan_withOnlyInitPy_classifiesAsLibrary() throws IOException {
        // Given: App directory with only __init__.py (no apps.py or models.py)
        createFile("myproject/settings.py", """
            INSTALLED_APPS = [
                'myapp.utils',
            ]
            """);

        createDirectory("myapp/utils");
        createFile("myapp/utils/__init__.py", "");
        // No apps.py or models.py

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should classify as LIBRARY (not a Django app, just a Python package)
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(1);
        assertThat(result.components().get(0).type()).isEqualTo(ComponentType.LIBRARY);
    }
}
