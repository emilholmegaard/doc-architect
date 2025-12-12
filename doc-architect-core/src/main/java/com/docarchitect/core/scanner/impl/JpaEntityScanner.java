package com.docarchitect.core.scanner.impl;

import com.docarchitect.core.model.DataEntity;
import com.docarchitect.core.model.Relationship;
import com.docarchitect.core.model.RelationshipType;
import com.docarchitect.core.scanner.Scanner;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.util.IdGenerator;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Scanner for JPA entity declarations in Java source files.
 *
 * <p>Uses JavaParser to extract JPA entities, fields, and relationships from @Entity classes.
 *
 * @see Scanner
 * @see DataEntity
 * @since 1.0.0
 */
public class JpaEntityScanner implements Scanner {

    private static final Logger log = LoggerFactory.getLogger(JpaEntityScanner.class);

    private static final Set<String> RELATIONSHIP_ANNOTATIONS = Set.of(
        "OneToMany", "ManyToMany", "ManyToOne", "OneToOne"
    );

    @Override
    public String getId() {
        return "jpa-entities";
    }

    @Override
    public String getDisplayName() {
        return "JPA Entity Scanner";
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of("java");
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of("**/*.java");
    }

    @Override
    public int getPriority() {
        return 60;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return context.findFiles("**/*.java").findAny().isPresent();
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning JPA entities in: {}", context.rootPath());

        List<DataEntity> dataEntities = new ArrayList<>();
        List<Relationship> relationships = new ArrayList<>();

        List<Path> javaFiles = context.findFiles("**/*.java").toList();

        if (javaFiles.isEmpty()) {
            return ScanResult.empty(getId());
        }

        for (Path javaFile : javaFiles) {
            try {
                parseJavaFile(javaFile, dataEntities, relationships);
            } catch (Exception e) {
                log.warn("Failed to parse Java file: {} - {}", javaFile, e.getMessage());
            }
        }

        log.info("Found {} JPA entities and {} relationships", dataEntities.size(), relationships.size());

        return new ScanResult(
            getId(),
            true,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            dataEntities,
            relationships,
            List.of(),
            List.of()
        );
    }

    private void parseJavaFile(Path javaFile, List<DataEntity> dataEntities, List<Relationship> relationships) throws IOException {
        String content = Files.readString(javaFile);
        CompilationUnit cu = StaticJavaParser.parse(content);

        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
            boolean isEntity = classDecl.getAnnotations().stream()
                .anyMatch(ann -> "Entity".equals(ann.getNameAsString()));

            if (!isEntity) {
                return;
            }

            String className = classDecl.getNameAsString();
            String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

            String fullyQualifiedName = packageName.isEmpty() ? className : packageName + "." + className;
            String tableName = extractTableName(classDecl, className);

            List<DataEntity.Field> fields = new ArrayList<>();
            String primaryKey = null;

            for (FieldDeclaration fieldDecl : classDecl.getFields()) {
                Optional<AnnotationExpr> relationshipAnnotation = fieldDecl.getAnnotations().stream()
                    .filter(ann -> RELATIONSHIP_ANNOTATIONS.contains(ann.getNameAsString()))
                    .findFirst();

                if (relationshipAnnotation.isPresent()) {
                    extractRelationship(fieldDecl, fullyQualifiedName, relationshipAnnotation.get(), relationships);
                } else {
                    DataEntity.Field field = extractField(fieldDecl);
                    if (field != null) {
                        fields.add(field);
                        if (isIdField(fieldDecl) && primaryKey == null) {
                            primaryKey = field.name();
                        }
                    }
                }
            }

            DataEntity entity = new DataEntity(
                fullyQualifiedName,
                tableName,
                "table",
                fields,
                primaryKey,
                "JPA Entity: " + className
            );

            dataEntities.add(entity);
            log.debug("Found JPA entity: {} -> table: {}", fullyQualifiedName, tableName);
        });
    }

    private String extractTableName(ClassOrInterfaceDeclaration classDecl, String className) {
        return classDecl.getAnnotations().stream()
            .filter(ann -> "Table".equals(ann.getNameAsString()))
            .filter(ann -> ann instanceof NormalAnnotationExpr)
            .findFirst()
            .flatMap(ann -> ((NormalAnnotationExpr) ann).getPairs().stream()
                .filter(pair -> "name".equals(pair.getNameAsString()))
                .findFirst()
                .map(pair -> pair.getValue().toString().replaceAll("\"", "")))
            .orElse(toSnakeCase(className));
    }

    private DataEntity.Field extractField(FieldDeclaration fieldDecl) {
        String fieldName = fieldDecl.getVariables().get(0).getNameAsString();
        String fieldType = fieldDecl.getElementType().asString();

        boolean isNullable = fieldDecl.getAnnotations().stream()
            .noneMatch(ann -> "Column".equals(ann.getNameAsString()) &&
                ann instanceof NormalAnnotationExpr &&
                ((NormalAnnotationExpr) ann).getPairs().stream()
                    .anyMatch(pair -> "nullable".equals(pair.getNameAsString()) &&
                        "false".equals(pair.getValue().toString())));

        return new DataEntity.Field(
            fieldName,
            fieldType,
            isNullable,
            null
        );
    }

    private boolean isIdField(FieldDeclaration fieldDecl) {
        return fieldDecl.getAnnotations().stream()
            .anyMatch(ann -> "Id".equals(ann.getNameAsString()));
    }

    private void extractRelationship(FieldDeclaration fieldDecl, String sourceEntity,
                                     AnnotationExpr annotation, List<Relationship> relationships) {
        String fieldType = fieldDecl.getElementType().asString();
        String targetEntity = fieldType
            .replaceAll("List<(.+)>", "$1")
            .replaceAll("Set<(.+)>", "$1")
            .replaceAll("Collection<(.+)>", "$1");

        String annotationName = annotation.getNameAsString();
        RelationshipType type = switch (annotationName) {
            case "OneToMany", "ManyToMany", "ManyToOne", "OneToOne" -> RelationshipType.DEPENDS_ON;
            default -> RelationshipType.DEPENDS_ON;
        };

        String description = annotationName + " relationship";

        Relationship relationship = new Relationship(
            sourceEntity,
            targetEntity,
            type,
            description,
            "JPA"
        );

        relationships.add(relationship);
        log.debug("Found relationship: {} --[{}]--> {}", sourceEntity, annotationName, targetEntity);
    }

    private String toSnakeCase(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
