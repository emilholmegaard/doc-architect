package com.docarchitect.core.scanner.impl.php;

import com.docarchitect.core.model.Component;
import com.docarchitect.core.model.ComponentType;
import com.docarchitect.core.model.Relationship;
import com.docarchitect.core.model.RelationshipType;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for {@link PhpInternalDependencyScanner}.
 *
 * <p>Tests the scanner's ability to detect internal dependencies between PHP classes
 * including use statements, inheritance, interfaces, traits, and dependency injection.
 */
class PhpInternalDependencyScannerTest extends ScannerTestBase {

    private final PhpInternalDependencyScanner scanner = new PhpInternalDependencyScanner();

    @Test
    void scan_withUseStatements_extractsImportDependencies() throws IOException {
        // Given: A PHP file with use statements
        createFile("src/Controller/UserController.php", """
            <?php

            namespace App\\Controller;

            use App\\Service\\UserService;
            use App\\Repository\\UserRepository;

            class UserController
            {
                public function __construct(
                    private UserService $userService,
                    private UserRepository $userRepository
                ) {}
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract import dependencies
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(1);

        // Verify import relationships exist
        assertThat(result.relationships())
            .anyMatch(r -> r.targetId().equals("App\\Service\\UserService") &&
                         r.description().contains("import"));
        assertThat(result.relationships())
            .anyMatch(r -> r.targetId().equals("App\\Repository\\UserRepository") &&
                         r.description().contains("import"));
    }

    @Test
    void scan_withConstructorInjection_extractsInjectionDependencies() throws IOException {
        // Given: A PHP file with constructor injection
        createFile("src/Service/OrderService.php", """
            <?php

            namespace App\\Service;

            use App\\Repository\\OrderRepository;
            use App\\Service\\PaymentService;

            class OrderService
            {
                public function __construct(
                    private OrderRepository $orderRepository,
                    private PaymentService $paymentService
                ) {}
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract injection dependencies
        assertThat(result.success()).isTrue();

        assertThat(result.relationships())
            .anyMatch(r -> r.targetId().equals("App\\Repository\\OrderRepository") &&
                         r.description().contains("injection"));
        assertThat(result.relationships())
            .anyMatch(r -> r.targetId().equals("App\\Service\\PaymentService") &&
                         r.description().contains("injection"));
    }

    @Test
    void scan_withInheritance_extractsInheritanceDependency() throws IOException {
        // Given: A PHP file with class inheritance
        createFile("src/Controller/ApiController.php", """
            <?php

            namespace App\\Controller;

            use App\\Controller\\Base\\AbstractController;

            class ApiController extends AbstractController
            {
                public function index()
                {
                    return [];
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract inheritance dependency
        assertThat(result.success()).isTrue();

        assertThat(result.relationships())
            .anyMatch(r -> r.targetId().equals("App\\Controller\\Base\\AbstractController") &&
                         r.description().contains("inheritance") &&
                         r.type().equals(RelationshipType.USES));
    }

    @Test
    void scan_withInterfaceImplementation_extractsInterfaceDependencies() throws IOException {
        // Given: A PHP file implementing interfaces
        createFile("src/Service/CacheService.php", """
            <?php

            namespace App\\Service;

            use Psr\\SimpleCache\\CacheInterface;
            use Countable;

            class CacheService implements CacheInterface, Countable
            {
                public function get($key, $default = null) {}
                public function set($key, $value, $ttl = null) {}
                public function count() { return 0; }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract interface dependencies
        assertThat(result.success()).isTrue();

        assertThat(result.relationships())
            .anyMatch(r -> r.targetId().equals("Psr\\SimpleCache\\CacheInterface") &&
                         r.description().contains("interface"));
        assertThat(result.relationships())
            .anyMatch(r -> r.description().contains("Countable") &&
                         r.description().contains("interface"));
    }

    @Test
    void scan_withTraitUsage_extractsTraitDependencies() throws IOException {
        // Given: A PHP file using traits
        createFile("src/Model/User.php", """
            <?php

            namespace App\\Model;

            use App\\Traits\\HasTimestamps;
            use App\\Traits\\SoftDeletes;

            class User
            {
                use HasTimestamps, SoftDeletes;

                private string $name;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract trait dependencies
        assertThat(result.success()).isTrue();

        assertThat(result.relationships())
            .anyMatch(r -> r.targetId().equals("App\\Traits\\HasTimestamps") &&
                         r.description().contains("trait"));
        assertThat(result.relationships())
            .anyMatch(r -> r.targetId().equals("App\\Traits\\SoftDeletes") &&
                         r.description().contains("trait"));
    }

    @Test
    void scan_withMethodInjection_extractsSetterDependencies() throws IOException {
        // Given: A PHP file with setter injection
        createFile("src/Service/NotificationService.php", """
            <?php

            namespace App\\Service;

            use App\\Service\\EmailService;
            use App\\Service\\SmsService;

            class NotificationService
            {
                private ?EmailService $emailService = null;
                private ?SmsService $smsService = null;

                public function setEmailService(EmailService $emailService): void
                {
                    $this->emailService = $emailService;
                }

                public function setSmsService(SmsService $smsService): void
                {
                    $this->smsService = $smsService;
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract method injection dependencies
        assertThat(result.success()).isTrue();

        assertThat(result.relationships())
            .anyMatch(r -> r.targetId().equals("App\\Service\\EmailService") &&
                         r.description().contains("injection"));
        assertThat(result.relationships())
            .anyMatch(r -> r.targetId().equals("App\\Service\\SmsService") &&
                         r.description().contains("injection"));
    }

    @Test
    void scan_withLaravelContainerBindings_extractsBindingDependencies() throws IOException {
        // Given: A Laravel service provider with container bindings
        createFile("app/Providers/AppServiceProvider.php", """
            <?php

            namespace App\\Providers;

            use Illuminate\\Support\\ServiceProvider;
            use App\\Contracts\\PaymentGatewayInterface;
            use App\\Services\\StripePaymentGateway;

            class AppServiceProvider extends ServiceProvider
            {
                public function register()
                {
                    $this->app->bind(PaymentGatewayInterface::class, StripePaymentGateway::class);
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract container binding dependencies
        assertThat(result.success()).isTrue();

        assertThat(result.relationships())
            .anyMatch(r -> r.sourceId().equals("App\\Services\\StripePaymentGateway") &&
                         r.targetId().equals("App\\Contracts\\PaymentGatewayInterface") &&
                         r.description().contains("binding"));
    }

    @Test
    void scan_withSingletonBinding_extractsBindingDependency() throws IOException {
        // Given: A Laravel service provider with singleton binding
        createFile("app/Providers/CacheServiceProvider.php", """
            <?php

            namespace App\\Providers;

            use Illuminate\\Support\\ServiceProvider;
            use App\\Contracts\\CacheInterface;
            use App\\Services\\RedisCache;

            class CacheServiceProvider extends ServiceProvider
            {
                public function register()
                {
                    $this->app->singleton(CacheInterface::class, RedisCache::class);
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract singleton binding dependency
        assertThat(result.success()).isTrue();

        assertThat(result.relationships())
            .anyMatch(r -> r.sourceId().equals("App\\Services\\RedisCache") &&
                         r.targetId().equals("App\\Contracts\\CacheInterface") &&
                         r.description().contains("binding"));
    }

    @Test
    void scan_withImportAlias_resolvesAliasedClass() throws IOException {
        // Given: A PHP file with use statement alias
        createFile("src/Service/DataService.php", """
            <?php

            namespace App\\Service;

            use App\\Repository\\DataRepository as Repository;

            class DataService
            {
                public function __construct(private Repository $repository) {}
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should resolve alias to fully qualified name
        assertThat(result.success()).isTrue();

        assertThat(result.relationships())
            .anyMatch(r -> r.targetId().equals("App\\Repository\\DataRepository"));
    }

    @Test
    void scan_withBuiltinTypes_skipsBuiltinTypes() throws IOException {
        // Given: A PHP file with built-in type parameters
        createFile("src/Service/StringService.php", """
            <?php

            namespace App\\Service;

            class StringService
            {
                public function __construct(
                    private string $prefix,
                    private int $maxLength,
                    private ?array $options
                ) {}
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should not create relationships for built-in types
        assertThat(result.success()).isTrue();
        assertThat(result.relationships())
            .noneMatch(r -> r.targetId().equalsIgnoreCase("string"))
            .noneMatch(r -> r.targetId().equalsIgnoreCase("int"))
            .noneMatch(r -> r.targetId().equalsIgnoreCase("array"));
    }

    @Test
    void scan_withVendorFiles_excludesVendorDirectory() throws IOException {
        // Given: PHP files in vendor directory
        createFile("vendor/some-package/src/SomeClass.php", """
            <?php

            namespace Vendor\\SomePackage;

            use App\\Service\\MyService;

            class SomeClass
            {
                public function __construct(private MyService $service) {}
            }
            """);

        // And: A non-vendor file
        createFile("src/Service/MyService.php", """
            <?php

            namespace App\\Service;

            class MyService {}
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should only find the non-vendor class
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(1);
        assertThat(result.components().get(0).name()).isEqualTo("MyService");
    }

    @Test
    void scan_withInterface_createsLibraryComponent() throws IOException {
        // Given: A PHP interface file
        createFile("src/Contracts/UserRepositoryInterface.php", """
            <?php

            namespace App\\Contracts;

            interface UserRepositoryInterface
            {
                public function find(int $id);
                public function save(object $user);
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should create a LIBRARY component
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(1);

        Component component = result.components().get(0);
        assertThat(component.type()).isEqualTo(ComponentType.LIBRARY);
        assertThat(component.metadata().get("classType")).isEqualTo("interface");
    }

    @Test
    void scan_withTrait_createsLibraryComponent() throws IOException {
        // Given: A PHP trait file
        createFile("src/Traits/Loggable.php", """
            <?php

            namespace App\\Traits;

            trait Loggable
            {
                protected function log(string $message): void
                {
                    echo $message;
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should create a LIBRARY component
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(1);

        Component component = result.components().get(0);
        assertThat(component.type()).isEqualTo(ComponentType.LIBRARY);
        assertThat(component.metadata().get("classType")).isEqualTo("trait");
    }

    @Test
    void scan_withNoNamespace_handlesGlobalNamespace() throws IOException {
        // Given: A PHP file without namespace
        createFile("legacy/Helper.php", """
            <?php

            class Helper
            {
                public static function format(string $value): string
                {
                    return trim($value);
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should create component with class name only
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(1);

        Component component = result.components().get(0);
        assertThat(component.name()).isEqualTo("Helper");
        assertThat(component.metadata().get("fullyQualifiedName")).isEqualTo("Helper");
    }

    @Test
    void scan_withFullyQualifiedTypehint_extractsDependency() throws IOException {
        // Given: A PHP file with fully qualified type hints
        createFile("src/Service/LegacyService.php", """
            <?php

            namespace App\\Service;

            class LegacyService
            {
                public function __construct(
                    private \\App\\Repository\\LegacyRepository $repository
                ) {}
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract the dependency
        assertThat(result.success()).isTrue();

        assertThat(result.relationships())
            .anyMatch(r -> r.targetId().equals("App\\Repository\\LegacyRepository"));
    }

    @Test
    void scan_withMultipleClassesInFile_onlyExtractsFirstClass() throws IOException {
        // Given: A PHP file with multiple classes (unusual but valid)
        createFile("src/Multiple.php", """
            <?php

            namespace App;

            class FirstClass
            {
                public function first() {}
            }

            class SecondClass
            {
                public function second() {}
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract only the first class (PHP convention)
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(1);
        assertThat(result.components().get(0).name()).isEqualTo("FirstClass");
    }

    @Test
    void scan_withAbstractClass_extractsClass() throws IOException {
        // Given: An abstract PHP class
        createFile("src/Controller/AbstractController.php", """
            <?php

            namespace App\\Controller;

            use Psr\\Http\\Message\\ResponseInterface;

            abstract class AbstractController
            {
                public function __construct(protected ResponseInterface $response) {}

                abstract public function handle();
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract the abstract class
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(1);
        assertThat(result.components().get(0).name()).isEqualTo("AbstractController");
    }

    @Test
    void scan_withFinalClass_extractsClass() throws IOException {
        // Given: A final PHP class
        createFile("src/Value/Money.php", """
            <?php

            namespace App\\Value;

            final class Money
            {
                public function __construct(
                    private float $amount,
                    private string $currency
                ) {}
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract the final class
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(1);
        assertThat(result.components().get(0).name()).isEqualTo("Money");
    }

    @Test
    void scan_withNoPhpFiles_returnsEmptyResult() {
        // Given: No PHP files

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.components()).isEmpty();
        assertThat(result.relationships()).isEmpty();
    }

    @Test
    void getId_returnsCorrectId() {
        assertThat(scanner.getId()).isEqualTo("php-internal-dependencies");
    }

    @Test
    void getDisplayName_returnsHumanReadableName() {
        assertThat(scanner.getDisplayName()).isEqualTo("PHP Internal Dependency Scanner");
    }

    @Test
    void getSupportedLanguages_includesPhp() {
        assertThat(scanner.getSupportedLanguages()).containsExactly("php");
    }

    @Test
    void getPriority_returnsExpectedPriority() {
        // Internal dependency scanner should run after ORM scanners
        assertThat(scanner.getPriority()).isEqualTo(70);
    }

    @Test
    void appliesTo_withPhpFiles_returnsTrue() throws IOException {
        // Given: A PHP file exists
        createFile("src/Test.php", """
            <?php
            class Test {}
            """);

        // When: Check if scanner applies
        boolean applies = scanner.appliesTo(context);

        // Then: Should return true
        assertThat(applies).isTrue();
    }

    @Test
    void appliesTo_withoutPhpFiles_returnsFalse() {
        // Given: No PHP files

        // When: Check if scanner applies
        boolean applies = scanner.appliesTo(context);

        // Then: Should return false
        assertThat(applies).isFalse();
    }
}
