package com.docarchitect.core.scanner.impl.javascript;

import com.docarchitect.core.model.ApiEndpoint;
import com.docarchitect.core.model.ApiType;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for {@link ExpressScanner}.
 */
class ExpressScannerTest extends ScannerTestBase {

    private ExpressScanner scanner;

    @BeforeEach
    void setUpScanner() {
        scanner = new ExpressScanner();
    }

    @Test
    void scan_withAppRoutes_extractsEndpoints() throws IOException {
        // Given: Express routes using app object
        createFile("routes/users.js", """
const express = require('express');
const app = express();

app.get('/api/users', (req, res) => {
    res.json({ users: [] });
});

app.post('/api/users', (req, res) => {
    res.status(201).json({ created: true });
});

app.delete('/api/users/:id', (req, res) => {
    res.status(204).send();
});
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract all 3 endpoints
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(3);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::method)
            .containsExactlyInAnyOrder("GET", "POST", "DELETE");

        ApiEndpoint getUsersEndpoint = result.apiEndpoints().stream()
            .filter(e -> "GET".equals(e.method()) && "/api/users".equals(e.path()))
            .findFirst()
            .orElseThrow();
        assertThat(getUsersEndpoint.type()).isEqualTo(ApiType.REST);
    }

    @Test
    void scan_withRouterRoutes_extractsEndpoints() throws IOException {
        // Given: Express routes using router object
        createFile("routes/products.js", """
const express = require('express');
const router = express.Router();

router.get('/products', (req, res) => {
    res.json({ products: [] });
});

router.put('/products/:id', (req, res) => {
    res.json({ updated: true });
});

router.patch('/products/:id/price', (req, res) => {
    res.json({ updated: true });
});

module.exports = router;
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract router endpoints
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(3);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::method)
            .containsExactlyInAnyOrder("GET", "PUT", "PATCH");
    }

    @Test
    void scan_withPathParameters_extractsCorrectly() throws IOException {
        // Given: Routes with path parameters
        createFile("routes/api.js", """
const app = require('express')();

app.get('/users/:userId', (req, res) => {});
app.get('/users/:userId/posts/:postId', (req, res) => {});
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract paths with parameters
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(2);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::path)
            .containsExactlyInAnyOrder("/users/:userId", "/users/:userId/posts/:postId");
    }

    @Test
    void scan_withTypeScriptFiles_parsesCorrectly() throws IOException {
        // Given: Express routes in TypeScript
        createFile("routes/orders.ts", """
import express from 'express';
const router = express.Router();

router.get('/orders', (req, res) => {
    res.json([]);
});

router.post('/orders', (req, res) => {
    res.status(201).json({});
});

export default router;
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should parse TypeScript files
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(2);
    }

    @Test
    void scan_withMultipleFiles_extractsAll() throws IOException {
        // Given: Multiple route files
        createFile("routes/auth.js", """
const app = require('express')();
app.post('/login', (req, res) => {});
app.post('/logout', (req, res) => {});
""");

        createFile("routes/profile.js", """
const router = require('express').Router();
router.get('/profile', (req, res) => {});
router.put('/profile', (req, res) => {});
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract from all files
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(4);
    }

    @Test
    void scan_withNoJsFiles_returnsEmpty() throws IOException {
        // Given: No JavaScript files in project
        createDirectory("routes");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).isEmpty();
    }

    @Test
    void appliesTo_withJsFiles_returnsTrue() throws IOException {
        // Given: Project with JavaScript files
        createFile("index.js", "console.log('hello');");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return true
        assertThat(applies).isTrue();
    }

    @Test
    void appliesTo_withoutJsFiles_returnsFalse() throws IOException {
        // Given: Project without JavaScript files
        createDirectory("src/main/java");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return false
        assertThat(applies).isFalse();
    }
}
