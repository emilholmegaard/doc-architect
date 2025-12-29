package com.docarchitect.core.scanner.impl.java;

import com.docarchitect.core.model.Relationship;
import com.docarchitect.core.model.RelationshipType;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for {@link JavaHttpClientScanner}.
 *
 * <p>These tests validate the scanner's ability to:
 * <ul>
 *   <li>Detect Feign client declarations with @FeignClient annotations</li>
 *   <li>Extract service names from Feign client configurations</li>
 *   <li>Detect RestTemplate method calls with HTTP URLs</li>
 *   <li>Detect WebClient method calls with URIs</li>
 *   <li>Create Relationship records with type CALLS</li>
 *   <li>Filter out non-service URLs (localhost, IPs, etc.)</li>
 *   <li>Deduplicate relationships from multiple call sites</li>
 * </ul>
 *
 * @see JavaHttpClientScanner
 * @since 1.0.0
 */
class JavaHttpClientScannerTest extends ScannerTestBase {

    private JavaHttpClientScanner scanner;

    @BeforeEach
    void setUpScanner() {
        scanner = new JavaHttpClientScanner();
    }

    @Test
    void scan_withFeignClient_extractsRelationship() throws IOException {
        // Given: A Feign client interface with @FeignClient annotation
        createFile("src/main/java/com/example/UserClient.java", """
            package com.example;

            import org.springframework.cloud.openfeign.FeignClient;
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.PathVariable;

            @FeignClient(name = "user-service")
            public interface UserClient {

                @GetMapping("/api/users/{id}")
                User getUser(@PathVariable Long id);
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract one relationship
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).hasSize(1);

        Relationship relationship = result.relationships().get(0);
        assertThat(relationship.type()).isEqualTo(RelationshipType.CALLS);
        assertThat(relationship.description()).contains("user-service");
        assertThat(relationship.technology()).isEqualTo("HTTP/Feign");
    }

    @Test
    void scan_withFeignClientValueAttribute_extractsRelationship() throws IOException {
        // Given: A Feign client with value attribute instead of name
        createFile("src/main/java/com/example/OrderClient.java", """
            package com.example;

            import org.springframework.cloud.openfeign.FeignClient;
            import org.springframework.web.bind.annotation.PostMapping;

            @FeignClient(value = "order-service")
            public interface OrderClient {

                @PostMapping("/api/orders")
                Order createOrder(Order order);
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract relationship using value attribute
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).hasSize(1);

        Relationship relationship = result.relationships().get(0);
        assertThat(relationship.description()).contains("order-service");
        assertThat(relationship.technology()).isEqualTo("HTTP/Feign");
    }

    @Test
    void scan_withFeignClientUrlAttribute_extractsRelationship() throws IOException {
        // Given: A Feign client with url attribute
        createFile("src/main/java/com/example/PaymentClient.java", """
            package com.example;

            import org.springframework.cloud.openfeign.FeignClient;
            import org.springframework.web.bind.annotation.PostMapping;

            @FeignClient(url = "http://payment-service:8080")
            public interface PaymentClient {

                @PostMapping("/api/payments")
                Payment processPayment(Payment payment);
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract relationship from URL
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).hasSize(1);

        Relationship relationship = result.relationships().get(0);
        assertThat(relationship.description()).contains("payment-service");
        assertThat(relationship.technology()).isEqualTo("HTTP/Feign");
    }

    @Test
    void scan_withRestTemplateGetForObject_extractsRelationship() throws IOException {
        // Given: Service using RestTemplate.getForObject
        createFile("src/main/java/com/example/UserService.java", """
            package com.example;

            import org.springframework.web.client.RestTemplate;

            public class UserService {

                private RestTemplate restTemplate;

                public User getUser(Long id) {
                    return restTemplate.getForObject("http://user-service/api/users/" + id, User.class);
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract relationship
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).hasSize(1);

        Relationship relationship = result.relationships().get(0);
        assertThat(relationship.type()).isEqualTo(RelationshipType.CALLS);
        assertThat(relationship.description()).contains("user-service");
        assertThat(relationship.technology()).isEqualTo("HTTP/RestTemplate");
    }

    @Test
    void scan_withRestTemplatePostForObject_extractsRelationship() throws IOException {
        // Given: Service using RestTemplate.postForObject
        createFile("src/main/java/com/example/OrderService.java", """
            package com.example;

            import org.springframework.web.client.RestTemplate;

            public class OrderService {

                private RestTemplate restTemplate;

                public Order createOrder(Order order) {
                    return restTemplate.postForObject("http://order-service/api/orders", order, Order.class);
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract relationship
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).hasSize(1);

        Relationship relationship = result.relationships().get(0);
        assertThat(relationship.description()).contains("order-service");
        assertThat(relationship.technology()).isEqualTo("HTTP/RestTemplate");
    }

    @Test
    void scan_withRestTemplateExchange_extractsRelationship() throws IOException {
        // Given: Service using RestTemplate.exchange
        createFile("src/main/java/com/example/ProductService.java", """
            package com.example;

            import org.springframework.web.client.RestTemplate;
            import org.springframework.http.HttpMethod;
            import org.springframework.http.ResponseEntity;

            public class ProductService {

                private RestTemplate restTemplate;

                public Product updateProduct(Long id, Product product) {
                    ResponseEntity<Product> response = restTemplate.exchange(
                        "http://catalog-service/api/products/" + id,
                        HttpMethod.PUT,
                        null,
                        Product.class
                    );
                    return response.getBody();
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract relationship
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).hasSize(1);

        Relationship relationship = result.relationships().get(0);
        assertThat(relationship.description()).contains("catalog-service");
        assertThat(relationship.technology()).isEqualTo("HTTP/RestTemplate");
    }

    @Test
    void scan_withWebClientGet_extractsRelationship() throws IOException {
        // Given: Service using WebClient.get()
        createFile("src/main/java/com/example/NotificationService.java", """
            package com.example;

            import org.springframework.web.reactive.function.client.WebClient;

            public class NotificationService {

                private WebClient webClient;

                public String getTemplate(String templateId) {
                    return webClient.get()
                        .uri("http://template-service/api/templates/" + templateId)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract relationship
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).hasSize(1);

        Relationship relationship = result.relationships().get(0);
        assertThat(relationship.type()).isEqualTo(RelationshipType.CALLS);
        assertThat(relationship.description()).contains("template-service");
        assertThat(relationship.technology()).isEqualTo("HTTP/WebClient");
    }

    @Test
    void scan_withWebClientPost_extractsRelationship() throws IOException {
        // Given: Service using WebClient.post()
        createFile("src/main/java/com/example/EmailService.java", """
            package com.example;

            import org.springframework.web.reactive.function.client.WebClient;

            public class EmailService {

                private WebClient webClient;

                public void sendEmail(Email email) {
                    webClient.post()
                        .uri("http://mail-service/api/send")
                        .bodyValue(email)
                        .retrieve()
                        .toBodilessEntity()
                        .block();
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract relationship
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).hasSize(1);

        Relationship relationship = result.relationships().get(0);
        assertThat(relationship.description()).contains("mail-service");
        assertThat(relationship.technology()).isEqualTo("HTTP/WebClient");
    }

    @Test
    void scan_withMultipleHttpClients_extractsAllRelationships() throws IOException {
        // Given: Service using multiple HTTP client types
        createFile("src/main/java/com/example/MultiClientService.java", """
            package com.example;

            import org.springframework.web.client.RestTemplate;
            import org.springframework.web.reactive.function.client.WebClient;

            public class MultiClientService {

                private RestTemplate restTemplate;
                private WebClient webClient;

                public User getUser(Long id) {
                    return restTemplate.getForObject("http://user-service/api/users/" + id, User.class);
                }

                public Order getOrder(Long id) {
                    return webClient.get()
                        .uri("http://order-service/api/orders/" + id)
                        .retrieve()
                        .bodyToMono(Order.class)
                        .block();
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract both relationships
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).hasSize(2);

        assertThat(result.relationships())
            .extracting(Relationship::description)
            .anyMatch(desc -> desc.contains("user-service"))
            .anyMatch(desc -> desc.contains("order-service"));
    }

    @Test
    void scan_withLocalhostUrl_filtersOut() throws IOException {
        // Given: Service calling localhost
        createFile("src/main/java/com/example/LocalService.java", """
            package com.example;

            import org.springframework.web.client.RestTemplate;

            public class LocalService {

                private RestTemplate restTemplate;

                public String test() {
                    return restTemplate.getForObject("http://localhost:8080/api/test", String.class);
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should not extract relationship (localhost filtered out)
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).isEmpty();
    }

    @Test
    void scan_withIpAddress_filtersOut() throws IOException {
        // Given: Service calling IP address
        createFile("src/main/java/com/example/IpService.java", """
            package com.example;

            import org.springframework.web.client.RestTemplate;

            public class IpService {

                private RestTemplate restTemplate;

                public String test() {
                    return restTemplate.getForObject("http://127.0.0.1:8080/api/test", String.class);
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should not extract relationship (IP filtered out)
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).isEmpty();
    }

    @Test
    void scan_withPrivateIpAddress_filtersOut() throws IOException {
        // Given: Service calling private IP
        createFile("src/main/java/com/example/PrivateIpService.java", """
            package com.example;

            import org.springframework.web.client.RestTemplate;

            public class PrivateIpService {

                private RestTemplate restTemplate;

                public String test() {
                    return restTemplate.getForObject("http://192.168.1.100:8080/api/test", String.class);
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should not extract relationship (private IP filtered out)
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).isEmpty();
    }

    @Test
    void scan_withHttpsUrl_extractsRelationship() throws IOException {
        // Given: Service using HTTPS
        createFile("src/main/java/com/example/SecureService.java", """
            package com.example;

            import org.springframework.web.client.RestTemplate;

            public class SecureService {

                private RestTemplate restTemplate;

                public String getData() {
                    return restTemplate.getForObject("https://secure-service/api/data", String.class);
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract relationship (HTTPS supported)
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).hasSize(1);

        Relationship relationship = result.relationships().get(0);
        assertThat(relationship.description()).contains("secure-service");
    }

    @Test
    void scan_withServiceNameWithHyphens_extractsRelationship() throws IOException {
        // Given: Service with hyphens in name
        createFile("src/main/java/com/example/HyphenService.java", """
            package com.example;

            import org.springframework.web.client.RestTemplate;

            public class HyphenService {

                private RestTemplate restTemplate;

                public String getData() {
                    return restTemplate.getForObject("http://my-service-name/api/data", String.class);
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract relationship
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).hasSize(1);

        Relationship relationship = result.relationships().get(0);
        assertThat(relationship.description()).contains("my-service-name");
    }

    @Test
    void scan_withServiceNameWithPort_extractsRelationship() throws IOException {
        // Given: Service URL with port number
        createFile("src/main/java/com/example/PortService.java", """
            package com.example;

            import org.springframework.web.client.RestTemplate;

            public class PortService {

                private RestTemplate restTemplate;

                public String getData() {
                    return restTemplate.getForObject("http://api-gateway:8080/api/data", String.class);
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract relationship (port stripped)
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).hasSize(1);

        Relationship relationship = result.relationships().get(0);
        assertThat(relationship.description()).contains("api-gateway");
    }

    @Test
    void scan_withDuplicateRelationships_deduplicates() throws IOException {
        // Given: Service with multiple calls to same service
        createFile("src/main/java/com/example/DuplicateService.java", """
            package com.example;

            import org.springframework.web.client.RestTemplate;

            public class DuplicateService {

                private RestTemplate restTemplate;

                public User getUser(Long id) {
                    return restTemplate.getForObject("http://user-service/api/users/" + id, User.class);
                }

                public User updateUser(Long id, User user) {
                    return restTemplate.postForObject("http://user-service/api/users/" + id, user, User.class);
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should only create one relationship (deduplicated)
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).hasSize(1);

        Relationship relationship = result.relationships().get(0);
        assertThat(relationship.description()).contains("user-service");
    }

    @Test
    void scan_withNoHttpClientCode_returnsEmpty() throws IOException {
        // Given: Regular Java class without HTTP client code
        createFile("src/main/java/com/example/RegularService.java", """
            package com.example;

            public class RegularService {
                public String doSomething() {
                    return "nothing";
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).isEmpty();
    }

    @Test
    void scan_withNoJavaFiles_returnsEmpty() throws IOException {
        // Given: No Java files in project
        createDirectory("src/main/resources");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).isEmpty();
    }

    @Test
    void appliesTo_withJavaFiles_returnsTrue() throws IOException {
        // Given: Project with Java files
        createFile("src/main/java/com/example/Test.java", "public class Test {}");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return true
        assertThat(applies).isTrue();
    }

    @Test
    void appliesTo_withoutJavaFiles_returnsFalse() throws IOException {
        // Given: Project without Java files
        createDirectory("src/main/resources");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return false
        assertThat(applies).isFalse();
    }

    @Test
    void scan_withComplexMicroserviceScenario_extractsAllRelationships() throws IOException {
        // Given: Complex scenario with multiple services and client types
        createFile("src/main/java/com/example/clients/UserClient.java", """
            package com.example.clients;

            import org.springframework.cloud.openfeign.FeignClient;
            import org.springframework.web.bind.annotation.GetMapping;

            @FeignClient(name = "user-service")
            public interface UserClient {
                @GetMapping("/api/users/{id}")
                User getUser(Long id);
            }
            """);

        createFile("src/main/java/com/example/service/OrderService.java", """
            package com.example.service;

            import org.springframework.web.client.RestTemplate;

            public class OrderService {
                private RestTemplate restTemplate;

                public Order getOrder(Long id) {
                    return restTemplate.getForObject("http://order-service/api/orders/" + id, Order.class);
                }
            }
            """);

        createFile("src/main/java/com/example/service/NotificationService.java", """
            package com.example.service;

            import org.springframework.web.reactive.function.client.WebClient;

            public class NotificationService {
                private WebClient webClient;

                public void notify(String message) {
                    webClient.post()
                        .uri("http://notification-service/api/notify")
                        .bodyValue(message)
                        .retrieve()
                        .toBodilessEntity()
                        .block();
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract all 3 relationships
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).hasSize(3);

        assertThat(result.relationships())
            .extracting(Relationship::description)
            .anyMatch(desc -> desc.contains("user-service"))
            .anyMatch(desc -> desc.contains("order-service"))
            .anyMatch(desc -> desc.contains("notification-service"));

        assertThat(result.relationships())
            .extracting(Relationship::type)
            .containsOnly(RelationshipType.CALLS);
    }
}
