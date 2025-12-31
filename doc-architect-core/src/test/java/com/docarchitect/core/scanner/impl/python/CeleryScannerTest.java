package com.docarchitect.core.scanner.impl.python;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.docarchitect.core.model.MessageFlow;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import com.docarchitect.core.util.Technologies;

/**
 * Unit tests for {@link CeleryScanner}.
 */
class CeleryScannerTest extends ScannerTestBase {

    private final CeleryScanner scanner = new CeleryScanner();

    @Test
    void getId_returnsCorrectId() {
        assertThat(scanner.getId()).isEqualTo("celery-tasks");
    }

    @Test
    void getDisplayName_returnsCorrectName() {
        assertThat(scanner.getDisplayName()).isEqualTo("Celery Task Scanner");
    }

    @Test
    void getSupportedLanguages_returnsPython() {
        assertThat(scanner.getSupportedLanguages()).containsExactly(Technologies.PYTHON);
    }

    @Test
    void getSupportedFilePatterns_returnsPythonFiles() {
        assertThat(scanner.getSupportedFilePatterns()).containsExactly("**/*.py");
    }

    @Test
    void getPriority_returns50() {
        assertThat(scanner.getPriority()).isEqualTo(50);
    }

    @Test
    void appliesTo_withPythonFiles_returnsTrue() throws IOException {
        // Given: Project with Python files and Celery dependency
        createFile("app/test.py", "# test");

        // Create mock dependency scan result with Celery
        ScanResult depResult = new ScanResult(
            "pip-dependencies",
            true,
            List.of(),  // components
            List.of(new com.docarchitect.core.model.Dependency("test-component", "celery", "celery", "5.3.0", "runtime", true)),  // dependencies
            List.of(),  // apiEndpoints
            List.of(),  // messageFlows
            List.of(),  // dataEntities
            List.of(),  // relationships
            List.of(),  // warnings
            List.of(),  // errors
            com.docarchitect.core.scanner.ScanStatistics.empty()  // statistics
        );

        Map<String, ScanResult> previousResults = Map.of("pip-dependencies", depResult);
        ScanContext contextWithDeps = new ScanContext(tempDir, List.of(tempDir), Map.of(), Map.of(), previousResults);

        // When/Then: appliesTo should return true
        assertThat(scanner.appliesTo(contextWithDeps)).isTrue();
    }

    @Test
    void appliesTo_withoutPythonFiles_returnsFalse() {
        assertThat(scanner.appliesTo(context)).isFalse();
    }

    @Test
    void scan_withSharedTaskDecorator_findsTask() throws IOException {
        // Create tasks.py with task definition
        String tasksContent = """
            from celery import shared_task

            @shared_task
            def send_email(to, subject, body):
                pass
            """;
        createFile("app/tasks.py", tasksContent);

        // Create views.py with task invocation
        String viewsContent = """
            from tasks import send_email

            def register_user(email):
                send_email.delay(email, 'Welcome', 'Thank you for registering')
            """;
        createFile("app/views.py", viewsContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.publisherComponentId()).isEqualTo("views");
        assertThat(flow.subscriberComponentId()).isEqualTo("tasks");
        assertThat(flow.broker()).isEqualTo("celery");
        assertThat(flow.topic()).isEqualTo("celery");  // Default queue
        assertThat(flow.messageType()).isEqualTo("send_email");
    }

    @Test
    void scan_withTaskDecorator_findsTask() throws IOException {
        String tasksContent = """
            from celery import task

            @task
            def process_data(data_id):
                pass
            """;
        createFile("app/tasks.py", tasksContent);

        String serviceContent = """
            from tasks import process_data

            process_data.delay(123)
            """;
        createFile("app/service.py", serviceContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.messageType()).isEqualTo("process_data");
        assertThat(flow.topic()).isEqualTo("celery");
    }

    @Test
    void scan_withAppTaskDecorator_findsTask() throws IOException {
        String tasksContent = """
            from celery import Celery

            app = Celery('myapp')

            @app.task
            def send_notification(user_id):
                pass
            """;
        createFile("app/tasks.py", tasksContent);

        String serviceContent = """
            from tasks import send_notification

            send_notification.delay(456)
            """;
        createFile("app/service.py", serviceContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.messageType()).isEqualTo("send_notification");
    }

    @Test
    void scan_withCeleryInstanceTask_findsTask() throws IOException {
        String tasksContent = """
            import celery

            celery = celery.Celery('myapp')

            @celery.task
            def background_job():
                pass
            """;
        createFile("app/tasks.py", tasksContent);

        String serviceContent = """
            from tasks import background_job

            background_job.delay()
            """;
        createFile("app/service.py", serviceContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.messageType()).isEqualTo("background_job");
    }

    @Test
    void scan_withQueueParameter_extractsQueueName() throws IOException {
        String tasksContent = """
            from celery import shared_task

            @shared_task(queue='emails')
            def send_email(to):
                pass
            """;
        createFile("app/tasks.py", tasksContent);

        String serviceContent = """
            from tasks import send_email

            send_email.delay('user@example.com')
            """;
        createFile("app/service.py", serviceContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.topic()).isEqualTo("emails");
    }

    @Test
    void scan_withCustomTaskName_usesCustomName() throws IOException {
        String tasksContent = """
            from celery import shared_task

            @shared_task(name='notifications.send_email')
            def send_email_task(to):
                pass
            """;
        createFile("app/tasks.py", tasksContent);

        String serviceContent = """
            from tasks import send_email_task

            send_email_task.delay('user@example.com')
            """;
        createFile("app/service.py", serviceContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.messageType()).isEqualTo("notifications.send_email");
    }

    @Test
    void scan_withApplyAsync_findsInvocation() throws IOException {
        String tasksContent = """
            from celery import shared_task

            @shared_task
            def process_image(image_id):
                pass
            """;
        createFile("app/tasks.py", tasksContent);

        String serviceContent = """
            from tasks import process_image

            process_image.apply_async(args=[123])
            """;
        createFile("app/service.py", serviceContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.messageType()).isEqualTo("process_image");
    }

    @Test
    void scan_withApplyAsyncQueueOverride_usesOverriddenQueue() throws IOException {
        String tasksContent = """
            from celery import shared_task

            @shared_task(queue='default')
            def send_email(to):
                pass
            """;
        createFile("app/tasks.py", tasksContent);

        String serviceContent = """
            from tasks import send_email

            send_email.apply_async(args=['user@example.com'], queue='priority')
            """;
        createFile("app/service.py", serviceContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.topic()).isEqualTo("priority");  // Should use overridden queue
    }

    @Test
    void scan_withAsyncDef_findsTask() throws IOException {
        String tasksContent = """
            from celery import shared_task

            @shared_task
            async def async_task(data):
                pass
            """;
        createFile("app/tasks.py", tasksContent);

        String serviceContent = """
            from tasks import async_task

            async_task.delay({'key': 'value'})
            """;
        createFile("app/service.py", serviceContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.messageType()).isEqualTo("async_task");
    }

    @Test
    void scan_withMultipleTasks_findsAllTasks() throws IOException {
        String tasksContent = """
            from celery import shared_task

            @shared_task(queue='emails')
            def send_email(to):
                pass

            @shared_task(queue='notifications')
            def send_sms(phone):
                pass

            @shared_task
            def process_payment(payment_id):
                pass
            """;
        createFile("app/tasks.py", tasksContent);

        String serviceContent = """
            from tasks import send_email, send_sms, process_payment

            send_email.delay('user@example.com')
            send_sms.delay('+1234567890')
            process_payment.apply_async(args=[123])
            """;
        createFile("app/service.py", serviceContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(3);

        List<String> messages = result.messageFlows().stream()
            .map(MessageFlow::messageType)
            .toList();
        assertThat(messages).containsExactlyInAnyOrder("send_email", "send_sms", "process_payment");

        List<String> queues = result.messageFlows().stream()
            .map(MessageFlow::topic)
            .toList();
        assertThat(queues).containsExactlyInAnyOrder("emails", "notifications", "celery");
    }

    @Test
    void scan_withTaskInSameFile_findsFlow() throws IOException {
        String content = """
            from celery import shared_task

            @shared_task
            def send_email(to):
                pass

            def register_user(email):
                send_email.delay(email)
            """;
        createFile("app/service.py", content);

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.publisherComponentId()).isEqualTo("service");
        assertThat(flow.subscriberComponentId()).isEqualTo("service");
        assertThat(flow.messageType()).isEqualTo("send_email");
    }

    @Test
    void scan_withMultipleInvocations_findsAllFlows() throws IOException {
        String tasksContent = """
            from celery import shared_task

            @shared_task
            def send_email(to):
                pass
            """;
        createFile("app/tasks.py", tasksContent);

        String service1Content = """
            from tasks import send_email

            send_email.delay('user1@example.com')
            """;
        createFile("app/service1.py", service1Content);

        String service2Content = """
            from tasks import send_email

            send_email.delay('user2@example.com')
            """;
        createFile("app/service2.py", service2Content);

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(2);
        assertThat(result.messageFlows())
            .extracting(MessageFlow::publisherComponentId)
            .containsExactlyInAnyOrder("service1", "service2");
    }

    @Test
    void scan_withNoTaskInvocations_returnsEmpty() throws IOException {
        String tasksContent = """
            from celery import shared_task

            @shared_task
            def send_email(to):
                pass
            """;
        createFile("app/tasks.py", tasksContent);

        ScanResult result = scanner.scan(context);

        // Task is defined but never invoked
        assertThat(result.messageFlows()).isEmpty();
    }

    @Test
    void scan_withNonTaskFile_returnsEmpty() throws IOException {
        String content = """
            def regular_function():
                pass
            """;
        createFile("app/utils.py", content);

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).isEmpty();
    }

    @Test
    void scan_withEmptyFile_returnsEmpty() throws IOException {
        createFile("app/empty.py", "");

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).isEmpty();
    }

    @Test
    void scan_withComplexQueueParameter_extractsQueue() throws IOException {
        String tasksContent = """
            from celery import shared_task

            @shared_task(queue='high-priority', name='tasks.send_urgent_email', bind=True, max_retries=3)
            def send_urgent_email(self, to):
                pass
            """;
        createFile("app/tasks.py", tasksContent);

        String serviceContent = """
            from tasks import send_urgent_email

            send_urgent_email.delay('admin@example.com')
            """;
        createFile("app/service.py", serviceContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.topic()).isEqualTo("high-priority");
        assertThat(flow.messageType()).isEqualTo("tasks.send_urgent_email");
    }

    @Test
    void scan_withSingleQuotesAndDoubleQuotes_extractsCorrectly() throws IOException {
        String tasksContent = """
            from celery import shared_task

            @shared_task(queue="emails", name='tasks.send')
            def send_email(to):
                pass
            """;
        createFile("app/tasks.py", tasksContent);

        String serviceContent = """
            from tasks import send_email

            send_email.delay('user@example.com')
            """;
        createFile("app/service.py", serviceContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.topic()).isEqualTo("emails");
        assertThat(flow.messageType()).isEqualTo("tasks.send");
    }
}
