package com.docarchitect.core.scanner.impl.java;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.docarchitect.core.model.DataEntity;
import com.docarchitect.core.model.Relationship;
import com.docarchitect.core.model.RelationshipType;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.base.AbstractJavaParserScanner;
import com.docarchitect.core.util.Technologies;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;

/**
 * Scanner for JPA entity declarations in Java source files.
 *
 * <p>Uses JavaParser to extract JPA entities, fields, and relationships from @Entity classes.
 *
 * @see Scanner
 * @see DataEntity
 * @since 1.0.0
 */
public class JpaEntityScanner extends AbstractJavaParserScanner {

    private static final String SCANNER_ID = "jpa-entities";
    private static final String SCANNER_DISPLAY_NAME = "JPA Entity Scanner";
    private static final String JAVA_FILE_GLOB = "**/*.java";
    private static final int SCANNER_PRIORITY = 60;

    private static final String ENTITY_ANNOTATION = "Entity";
    private static final String TABLE_ANNOTATION = "Table";
    private static final String TABLE_NAME_ATTRIBUTE = "name";
    private static final String COLUMN_ANNOTATION = "Column";
    private static final String NULLABLE_ATTRIBUTE = "nullable";
    private static final String FALSE_LITERAL = "false";
    private static final String ID_ANNOTATION = "Id";

    private static final String DATA_ENTITY_TYPE_TABLE = "table";
    private static final String ENTITY_DESCRIPTION_PREFIX = "JPA Entity: ";
    private static final String RELATIONSHIP_DESCRIPTION_SUFFIX = " relationship";
    private static final String RELATIONSHIP_TECHNOLOGY = "JPA";

    private static final String LIST_TYPE_PATTERN = "List<(.+)>";
    private static final String SET_TYPE_PATTERN = "Set<(.+)>";
    private static final String COLLECTION_TYPE_PATTERN = "Collection<(.+)>";

    private static final Set<String> RELATIONSHIP_ANNOTATIONS = Set.of(
        "OneToMany", "ManyToMany", "ManyToOne", "OneToOne"
    );

    @Override
    public String getId() {
        return SCANNER_ID;
    }

    @Override
    public String getDisplayName() {
        return SCANNER_DISPLAY_NAME;
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of(Technologies.JAVA);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(JAVA_FILE_GLOB);
    }

    @Override
    public int getPriority() {
        return SCANNER_PRIORITY;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, JAVA_FILE_GLOB);
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning JPA entities in: {}", context.rootPath());

        List<DataEntity> dataEntities = new ArrayList<>();
        List<Relationship> relationships = new ArrayList<>();

        List<Path> javaFiles = context.findFiles(JAVA_FILE_GLOB).toList();

        if (javaFiles.isEmpty()) {
            return emptyResult();
        }

        for (Path javaFile : javaFiles) {
            try {
                parseJpaEntities(javaFile, dataEntities, relationships);
            } catch (IOException e) {
                log.warn("Failed to parse Java file: {} - {}", javaFile, e.getMessage());
            }
        }

        log.info("Found {} JPA entities and {} relationships", dataEntities.size(), relationships.size());

        return buildSuccessResult(
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            dataEntities,
            relationships,
            List.of()
        );
    }

    private void parseJpaEntities(Path javaFile, List<DataEntity> dataEntities, List<Relationship> relationships) throws IOException {
        Optional<CompilationUnit> cuOpt = parseJavaFile(javaFile);
        if (cuOpt.isEmpty()) {
            return;
        }

        CompilationUnit cu = cuOpt.get();
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
            boolean isEntity = classDecl.getAnnotations().stream()
                .anyMatch(ann -> ENTITY_ANNOTATION.equals(ann.getNameAsString()));

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
                DATA_ENTITY_TYPE_TABLE,
                fields,
                primaryKey,
                ENTITY_DESCRIPTION_PREFIX + className
            );

            dataEntities.add(entity);
            log.debug("Found JPA entity: {} -> table: {}", fullyQualifiedName, tableName);
        });
    }

    private String extractTableName(ClassOrInterfaceDeclaration classDecl, String className) {
        return classDecl.getAnnotations().stream()
            .filter(ann -> TABLE_ANNOTATION.equals(ann.getNameAsString()))
            .filter(ann -> ann instanceof NormalAnnotationExpr)
            .findFirst()
            .flatMap(ann -> ((NormalAnnotationExpr) ann).getPairs().stream()
                .filter(pair -> TABLE_NAME_ATTRIBUTE.equals(pair.getNameAsString()))
                .findFirst()
                .map(pair -> pair.getValue().toString().replaceAll("\"", "")))
            .orElse(toSnakeCase(className));
    }

    private DataEntity.Field extractField(FieldDeclaration fieldDecl) {
        String fieldName = fieldDecl.getVariables().get(0).getNameAsString();
        String fieldType = fieldDecl.getElementType().asString();

        boolean isNullable = fieldDecl.getAnnotations().stream()
            .noneMatch(ann -> COLUMN_ANNOTATION.equals(ann.getNameAsString()) &&
                ann instanceof NormalAnnotationExpr &&
                ((NormalAnnotationExpr) ann).getPairs().stream()
                    .anyMatch(pair -> NULLABLE_ATTRIBUTE.equals(pair.getNameAsString()) &&
                        FALSE_LITERAL.equals(pair.getValue().toString())));

        return new DataEntity.Field(
            fieldName,
            fieldType,
            isNullable,
            null
        );
    }

    private boolean isIdField(FieldDeclaration fieldDecl) {
        return fieldDecl.getAnnotations().stream()
            .anyMatch(ann -> ID_ANNOTATION.equals(ann.getNameAsString()));
    }

    private void extractRelationship(FieldDeclaration fieldDecl, String sourceEntity,
                                     AnnotationExpr annotation, List<Relationship> relationships) {
        String fieldType = fieldDecl.getElementType().asString();
        String targetEntity = fieldType
            .replaceAll(LIST_TYPE_PATTERN, "$1")
            .replaceAll(SET_TYPE_PATTERN, "$1")
            .replaceAll(COLLECTION_TYPE_PATTERN, "$1");

        String annotationName = annotation.getNameAsString();
        String description = annotationName + RELATIONSHIP_DESCRIPTION_SUFFIX;

        Relationship relationship = new Relationship(
            sourceEntity,
            targetEntity,
            RelationshipType.DEPENDS_ON,  
            description,
            RELATIONSHIP_TECHNOLOGY
        );

        relationships.add(relationship);
        log.debug("Found relationship: {} --[{}]--> {}", sourceEntity, annotationName, targetEntity);
    }

    protected String toSnakeCase(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
