package com.docarchitect.core.scanner.impl.python.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.docarchitect.core.scanner.ast.PythonAst;

/**
 * Unit tests for {@link PythonFunctionParser}.
 */
class PythonFunctionParserTest {

    @Test
    void parseFunctions_withSimpleTask_returnsFunction(@TempDir Path tempDir) throws IOException {
        String content = """
            from celery import shared_task

            @shared_task
            def send_email(to, subject):
                pass
            """;

        Path file = tempDir.resolve("tasks.py");
        Files.writeString(file, content);

        List<PythonAst.Function> functions = PythonFunctionParser.parseFunctions(file);

        assertThat(functions).hasSize(1);
        PythonAst.Function func = functions.get(0);
        assertThat(func.name()).isEqualTo("send_email");
        assertThat(func.decorators()).containsExactly("shared_task");
        assertThat(func.parameters()).containsExactly("to", "subject");
        assertThat(func.isAsync()).isFalse();
    }

    @Test
    void parseFunctionCalls_withDelayInvocation_returnsCall(@TempDir Path tempDir) throws IOException {
        String content = """
            from tasks import send_email

            send_email.delay('user@example.com', 'Welcome')
            """;

        Path file = tempDir.resolve("views.py");
        Files.writeString(file, content);

        List<PythonAst.FunctionCall> calls = PythonFunctionParser.parseFunctionCalls(file);

        assertThat(calls).hasSize(1);
        PythonAst.FunctionCall call = calls.get(0);
        assertThat(call.functionName()).isEqualTo("send_email");
        assertThat(call.method()).isEqualTo("delay");
    }

    @Test
    void extractParameter_withQueueParam_extractsValue() {
        String result = PythonFunctionParser.extractParameter("queue='emails', name='task'", "queue");
        assertThat(result).isEqualTo("emails");
    }
}
