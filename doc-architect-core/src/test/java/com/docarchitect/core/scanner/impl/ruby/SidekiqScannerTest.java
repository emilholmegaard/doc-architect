package com.docarchitect.core.scanner.impl.ruby;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.docarchitect.core.model.MessageFlow;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import com.docarchitect.core.util.Technologies;

/**
 * Unit tests for {@link SidekiqScanner}.
 */
class SidekiqScannerTest extends ScannerTestBase {

    private final SidekiqScanner scanner = new SidekiqScanner();

    @Test
    void getId_returnsCorrectId() {
        assertThat(scanner.getId()).isEqualTo("sidekiq-jobs");
    }

    @Test
    void getDisplayName_returnsCorrectName() {
        assertThat(scanner.getDisplayName()).isEqualTo("Sidekiq Job Scanner");
    }

    @Test
    void getSupportedLanguages_returnsRuby() {
        assertThat(scanner.getSupportedLanguages()).containsExactly(Technologies.RUBY);
    }

    @Test
    void getSupportedFilePatterns_returnsRubyFiles() {
        assertThat(scanner.getSupportedFilePatterns()).containsExactly("**/*.rb");
    }

    @Test
    void getPriority_returns50() {
        assertThat(scanner.getPriority()).isEqualTo(50);
    }

    @Test
    void appliesTo_withRubyFiles_returnsTrue() throws IOException {
        createFile("app/workers/test_worker.rb", "# test");

        assertThat(scanner.appliesTo(context)).isTrue();
    }

    @Test
    void appliesTo_withoutRubyFiles_returnsFalse() {
        assertThat(scanner.appliesTo(context)).isFalse();
    }

    @Test
    void scan_withSidekiqWorker_findsWorker() throws IOException {
        // Create worker with Sidekiq::Worker
        String workerContent = """
            class EmailWorker
              include Sidekiq::Worker
              sidekiq_options queue: :notifications

              def perform(user_id)
                # Send email
              end
            end
            """;
        createFile("app/workers/email_worker.rb", workerContent);

        // Create service that invokes the worker
        String serviceContent = """
            class UserService
              def register_user(user_id)
                EmailWorker.perform_async(user_id)
              end
            end
            """;
        createFile("app/services/user_service.rb", serviceContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.publisherComponentId()).isEqualTo("user_service");
        assertThat(flow.subscriberComponentId()).isEqualTo("email_worker");
        assertThat(flow.topic()).isEqualTo("notifications");
        assertThat(flow.messageType()).isEqualTo("EmailWorker");
        assertThat(flow.broker()).isEqualTo("sidekiq");
    }

    @Test
    void scan_withApplicationWorker_findsWorker() throws IOException {
        // Create worker with ApplicationWorker (Rails pattern)
        String workerContent = """
            class NotificationWorker
              include ApplicationWorker
              sidekiq_options queue: :mailers

              def perform(notification_id)
                # Send notification
              end
            end
            """;
        createFile("app/workers/notification_worker.rb", workerContent);

        String serviceContent = """
            class NotificationService
              def send_notification(notification_id)
                NotificationWorker.perform_async(notification_id)
              end
            end
            """;
        createFile("app/services/notification_service.rb", serviceContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.topic()).isEqualTo("mailers");
        assertThat(flow.messageType()).isEqualTo("NotificationWorker");
    }

    @Test
    void scan_withDefaultQueue_usesDefaultQueue() throws IOException {
        // Worker without explicit queue specification
        String workerContent = """
            class DataProcessingWorker
              include Sidekiq::Worker

              def perform(data_id)
                # Process data
              end
            end
            """;
        createFile("app/workers/data_processing_worker.rb", workerContent);

        String serviceContent = """
            class DataService
              def process_data(data_id)
                DataProcessingWorker.perform_async(data_id)
              end
            end
            """;
        createFile("app/services/data_service.rb", serviceContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.topic()).isEqualTo("default");  // Sidekiq default queue
    }

    @Test
    void scan_withPerformIn_findsInvocation() throws IOException {
        String workerContent = """
            class ReminderWorker
              include Sidekiq::Worker
              sidekiq_options queue: :scheduled

              def perform(user_id)
                # Send reminder
              end
            end
            """;
        createFile("app/workers/reminder_worker.rb", workerContent);

        String serviceContent = """
            class ReminderService
              def schedule_reminder(user_id)
                ReminderWorker.perform_in(1.hour, user_id)
              end
            end
            """;
        createFile("app/services/reminder_service.rb", serviceContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.messageType()).isEqualTo("ReminderWorker");
        assertThat(flow.topic()).isEqualTo("scheduled");
    }

    @Test
    void scan_withPerformAt_findsInvocation() throws IOException {
        String workerContent = """
            class ScheduledWorker
              include Sidekiq::Worker

              def perform(task_id)
                # Execute scheduled task
              end
            end
            """;
        createFile("app/workers/scheduled_worker.rb", workerContent);

        String serviceContent = """
            class TaskScheduler
              def schedule_at(task_id, time)
                ScheduledWorker.perform_at(time, task_id)
              end
            end
            """;
        createFile("app/services/task_scheduler.rb", serviceContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.messageType()).isEqualTo("ScheduledWorker");
    }

    @Test
    void scan_withQueueAsString_extractsQueue() throws IOException {
        String workerContent = """
            class EmailWorker
              include Sidekiq::Worker
              sidekiq_options queue: 'emails'

              def perform(user_id)
                # Send email
              end
            end
            """;
        createFile("app/workers/email_worker.rb", workerContent);

        String serviceContent = """
            class UserService
              def send_email(user_id)
                EmailWorker.perform_async(user_id)
              end
            end
            """;
        createFile("app/services/user_service.rb", serviceContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.topic()).isEqualTo("emails");
    }

    @Test
    void scan_withNamespacedWorker_findsWorker() throws IOException {
        // Worker with namespace
        String workerContent = """
            module Notifications
              class EmailWorker
                include Sidekiq::Worker
                sidekiq_options queue: :notifications

                def perform(user_id)
                  # Send email
                end
              end
            end
            """;
        createFile("app/workers/notifications/email_worker.rb", workerContent);

        String serviceContent = """
            class UserService
              def register_user(user_id)
                Notifications::EmailWorker.perform_async(user_id)
              end
            end
            """;
        createFile("app/services/user_service.rb", serviceContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.messageType()).isEqualTo("Notifications::EmailWorker");
    }

    @Test
    void scan_withMultipleWorkers_findsAllWorkers() throws IOException {
        String worker1Content = """
            class EmailWorker
              include Sidekiq::Worker
              sidekiq_options queue: :emails

              def perform(user_id)
              end
            end
            """;
        createFile("app/workers/email_worker.rb", worker1Content);

        String worker2Content = """
            class SmsWorker
              include Sidekiq::Worker
              sidekiq_options queue: :sms

              def perform(phone_number)
              end
            end
            """;
        createFile("app/workers/sms_worker.rb", worker2Content);

        String serviceContent = """
            class NotificationService
              def notify_user(user_id, phone)
                EmailWorker.perform_async(user_id)
                SmsWorker.perform_async(phone)
              end
            end
            """;
        createFile("app/services/notification_service.rb", serviceContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(2);

        List<String> workers = result.messageFlows().stream()
            .map(MessageFlow::messageType)
            .toList();
        assertThat(workers).containsExactlyInAnyOrder("EmailWorker", "SmsWorker");

        List<String> queues = result.messageFlows().stream()
            .map(MessageFlow::topic)
            .toList();
        assertThat(queues).containsExactlyInAnyOrder("emails", "sms");
    }

    @Test
    void scan_withWorkerInSameFile_findsFlow() throws IOException {
        String content = """
            class ProcessingWorker
              include Sidekiq::Worker

              def perform(data_id)
                # Process data
              end
            end

            class DataProcessor
              def process(data_id)
                ProcessingWorker.perform_async(data_id)
              end
            end
            """;
        createFile("app/lib/processor.rb", content);

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.publisherComponentId()).isEqualTo("processor");
        assertThat(flow.subscriberComponentId()).isEqualTo("processor");
    }

    @Test
    void scan_withMultipleInvocations_findsAllFlows() throws IOException {
        String workerContent = """
            class EmailWorker
              include Sidekiq::Worker

              def perform(user_id)
              end
            end
            """;
        createFile("app/workers/email_worker.rb", workerContent);

        String service1Content = """
            class UserRegistration
              def register(user_id)
                EmailWorker.perform_async(user_id)
              end
            end
            """;
        createFile("app/services/user_registration.rb", service1Content);

        String service2Content = """
            class UserService
              def notify(user_id)
                EmailWorker.perform_async(user_id)
              end
            end
            """;
        createFile("app/services/user_service.rb", service2Content);

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(2);
        assertThat(result.messageFlows())
            .extracting(MessageFlow::publisherComponentId)
            .containsExactlyInAnyOrder("user_registration", "user_service");
    }

    @Test
    void scan_withNoInvocations_returnsEmpty() throws IOException {
        String workerContent = """
            class UnusedWorker
              include Sidekiq::Worker

              def perform(data)
              end
            end
            """;
        createFile("app/workers/unused_worker.rb", workerContent);

        ScanResult result = scanner.scan(context);

        // Worker is defined but never invoked
        assertThat(result.messageFlows()).isEmpty();
    }

    @Test
    void scan_withNonWorkerClass_returnsEmpty() throws IOException {
        String content = """
            class RegularClass
              def regular_method
              end
            end
            """;
        createFile("app/lib/regular_class.rb", content);

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).isEmpty();
    }

    @Test
    void scan_withEmptyFile_returnsEmpty() throws IOException {
        createFile("app/workers/empty.rb", "");

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).isEmpty();
    }

    @Test
    void scan_withComplexSidekiqOptions_extractsQueue() throws IOException {
        String workerContent = """
            class ComplexWorker
              include Sidekiq::Worker
              sidekiq_options queue: :high_priority, retry: 5, backtrace: true

              def perform(data)
              end
            end
            """;
        createFile("app/workers/complex_worker.rb", workerContent);

        String serviceContent = """
            class Service
              def process(data)
                ComplexWorker.perform_async(data)
              end
            end
            """;
        createFile("app/services/service.rb", serviceContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.topic()).isEqualTo("high_priority");
    }

    @Test
    void scan_withRealWorldGitLabWorker_findsWorker() throws IOException {
        // Example based on actual GitLab worker pattern
        String workerContent = """
            # frozen_string_literal: true

            class RepositoryImportWorker
              include ApplicationWorker

              data_consistency :always
              feature_category :importers
              sidekiq_options retry: false, dead: false

              def perform(project_id)
                # Import repository
              end
            end
            """;
        createFile("app/workers/repository_import_worker.rb", workerContent);

        String controllerContent = """
            class ProjectsController < ApplicationController
              def import
                RepositoryImportWorker.perform_async(project.id)
              end
            end
            """;
        createFile("app/controllers/projects_controller.rb", controllerContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.messageType()).isEqualTo("RepositoryImportWorker");
        assertThat(flow.topic()).isEqualTo("default");  // No queue specified
    }

    @Test
    void scan_withMultiplePerformMethods_findsAllInvocations() throws IOException {
        String workerContent = """
            class FlexibleWorker
              include Sidekiq::Worker
              sidekiq_options queue: :flexible

              def perform(data)
              end
            end
            """;
        createFile("app/workers/flexible_worker.rb", workerContent);

        String serviceContent = """
            class Scheduler
              def schedule_tasks
                FlexibleWorker.perform_async('immediate')
                FlexibleWorker.perform_in(5.minutes, 'delayed')
                FlexibleWorker.perform_at(Time.now + 1.hour, 'scheduled')
              end
            end
            """;
        createFile("app/services/scheduler.rb", serviceContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(3);
        assertThat(result.messageFlows())
            .extracting(MessageFlow::messageType)
            .containsOnly("FlexibleWorker");
        assertThat(result.messageFlows())
            .extracting(MessageFlow::topic)
            .containsOnly("flexible");
    }

    @Test
    void scan_withCommentsAndWhitespace_handlesCorrectly() throws IOException {
        String workerContent = """
            # This is a comment
            class EmailWorker
              # Include Sidekiq worker
              include Sidekiq::Worker

              # Set queue options
              sidekiq_options queue: :emails

              # Perform email sending
              def perform(user_id)
                # Send email logic
              end
            end
            """;
        createFile("app/workers/email_worker.rb", workerContent);

        String serviceContent = """
            class UserService
              def send_welcome_email(user_id)
                # Send welcome email asynchronously
                EmailWorker.perform_async(user_id)
              end
            end
            """;
        createFile("app/services/user_service.rb", serviceContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.messageType()).isEqualTo("EmailWorker");
        assertThat(flow.topic()).isEqualTo("emails");
    }
}
