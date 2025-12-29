package com.docarchitect.core.scanner.impl.ruby.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RubyAstParser}.
 */
class RubyAstParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parseFile_withSimpleClass_extractsClass() throws IOException {
        // Given: A simple Ruby class
        Path rubyFile = tempDir.resolve("user.rb");
        Files.writeString(rubyFile, """
            class User
              def initialize(name)
                @name = name
              end
            end
            """);

        // When: Parse the file
        List<RubyAstParser.RubyClass> classes = RubyAstParser.parseFile(rubyFile);

        // Then: Should extract the class
        assertThat(classes).hasSize(1);
        assertThat(classes.get(0).name).isEqualTo("User");
        assertThat(classes.get(0).methods).hasSize(1);
        assertThat(classes.get(0).methods.get(0).name).isEqualTo("initialize");
    }

    @Test
    void parseFile_withControllerClass_extractsClassAndMethods() throws IOException {
        // Given: A Rails controller
        Path controllerFile = tempDir.resolve("users_controller.rb");
        Files.writeString(controllerFile, """
            class UsersController < ApplicationController
              def index
                @users = User.all
              end

              def show
                @user = User.find(params[:id])
              end
            end
            """);

        // When: Parse the file
        List<RubyAstParser.RubyClass> classes = RubyAstParser.parseFile(controllerFile);

        // Then: Should extract controller and methods
        assertThat(classes).hasSize(1);

        RubyAstParser.RubyClass controller = classes.get(0);
        assertThat(controller.name).isEqualTo("UsersController");
        assertThat(controller.superclass).isEqualTo("ApplicationController");
        assertThat(controller.methods).hasSize(2);
        assertThat(controller.methods).extracting(m -> m.name)
            .containsExactlyInAnyOrder("index", "show");
    }

    @Test
    void parseFile_withBeforeAction_extractsCallback() throws IOException {
        // Given: Controller with before_action
        Path controllerFile = tempDir.resolve("admin_controller.rb");
        Files.writeString(controllerFile, """
            class AdminController < ApplicationController
              before_action :authenticate_admin

              def dashboard
              end
            end
            """);

        // When: Parse the file
        List<RubyAstParser.RubyClass> classes = RubyAstParser.parseFile(controllerFile);

        // Then: Should extract before_action
        assertThat(classes).hasSize(1);

        RubyAstParser.RubyClass controller = classes.get(0);
        assertThat(controller.beforeActions).hasSize(1);
        assertThat(controller.beforeActions).contains("authenticate_admin");
    }

    @Test
    void parseContent_withValidRuby_extractsClass() {
        // Given: Ruby source code as string
        String rubyCode = """
            class Product
              def price
                @price
              end
            end
            """;

        // When: Parse the content
        List<RubyAstParser.RubyClass> classes = RubyAstParser.parseContent(rubyCode);

        // Then: Should extract the class
        assertThat(classes).hasSize(1);
        assertThat(classes.get(0).name).isEqualTo("Product");
    }

    @Test
    void parseFile_withMultipleClasses_extractsAll() throws IOException {
        // Given: File with multiple classes
        Path rubyFile = tempDir.resolve("models.rb");
        Files.writeString(rubyFile, """
            class User
              def name
              end
            end

            class Post
              def title
              end
            end
            """);

        // When: Parse the file
        List<RubyAstParser.RubyClass> classes = RubyAstParser.parseFile(rubyFile);

        // Then: Should extract both classes
        assertThat(classes).hasSize(2);
        assertThat(classes).extracting(c -> c.name)
            .containsExactlyInAnyOrder("User", "Post");
    }

    @Test
    void parseFile_withEmptyFile_returnsEmpty() throws IOException {
        // Given: Empty file
        Path emptyFile = tempDir.resolve("empty.rb");
        Files.writeString(emptyFile, "");

        // When: Parse the file
        List<RubyAstParser.RubyClass> classes = RubyAstParser.parseFile(emptyFile);

        // Then: Should return empty list
        assertThat(classes).isEmpty();
    }
}
