parser grammar RubyParser;

options {
    tokenVocab=RubyLexer;
    superClass=RubyParserBase;
}

// Root
program
    : (statement NEWLINE?)* EOF
    ;

statement
    : classDefinition
    | moduleDefinition
    | methodDefinition
    | expressionStatement
    | NEWLINE
    ;

// Class definitions
classDefinition
    : CLASS className (LT superclass)?
      classBody?
      END
    ;

className
    : IDENTIFIER (DOUBLE_COLON IDENTIFIER)*
    ;

superclass
    : IDENTIFIER (DOUBLE_COLON IDENTIFIER)*
    ;

classBody
    : (classBodyStatement)*
    ;

classBodyStatement
    : methodDefinition
    | beforeActionCall NEWLINE?
    | afterActionCall NEWLINE?
    | aroundActionCall NEWLINE?
    | skipBeforeActionCall NEWLINE?
    | expressionStatement
    | NEWLINE
    ;

// Module definitions
moduleDefinition
    : MODULE IDENTIFIER (DOUBLE_COLON IDENTIFIER)*
      moduleBody?
      END
    ;

moduleBody
    : (statement)*
    ;

// Method definitions
methodDefinition
    : DEF methodName methodParams?
      methodBody?
      END
    ;

methodName
    : IDENTIFIER
    | SELF DOT IDENTIFIER
    ;

methodParams
    : LPAREN (paramList)? RPAREN
    | paramList
    ;

paramList
    : param (COMMA param)*
    ;

param
    : IDENTIFIER (EQ primary)?
    | STAR IDENTIFIER?
    | AMPERSAND IDENTIFIER
    | STAR STAR IDENTIFIER
    ;

methodBody
    : (statement)*
    ;

// Rails-specific DSL calls
beforeActionCall
    : BEFORE_ACTION symbolOrMethodArgs
    ;

afterActionCall
    : AFTER_ACTION symbolOrMethodArgs
    ;

aroundActionCall
    : AROUND_ACTION symbolOrMethodArgs
    ;

skipBeforeActionCall
    : SKIP_BEFORE_ACTION symbolOrMethodArgs
    ;

symbolOrMethodArgs
    : SYMBOL (COMMA hashOptions)?
    | COLON IDENTIFIER (COMMA hashOptions)?
    ;

// Routes DSL (for routes.rb)
routesStatement
    : namespaceBlock
    | resourcesCall
    | resourceCall
    | httpVerbCall
    | rootCall
    | matchCall
    | NEWLINE
    ;

namespaceBlock
    : NAMESPACE (STRING | SYMBOL) DO
      routesBody
      END
    ;

routesBody
    : (routesStatement)*
    ;

resourcesCall
    : RESOURCES (SYMBOL | STRING) (COMMA resourceOptions)?
    ;

resourceCall
    : RESOURCE (SYMBOL | STRING) (COMMA resourceOptions)?
    ;

resourceOptions
    : hashPair (COMMA hashPair)*
    ;

httpVerbCall
    : (GET | POST | PUT | PATCH | DELETE) STRING (COMMA hashOptions)?
    ;

rootCall
    : ROOT (STRING | SYMBOL)
    ;

matchCall
    : MATCH STRING (COMMA hashOptions)?
    ;

// Expressions
expressionStatement
    : expression
    ;

expression
    : assignmentExpr
    ;

assignmentExpr
    : (IDENTIFIER | INSTANCE_VAR | CLASS_VAR | GLOBAL_VAR) EQ orExpr
    | orExpr
    ;

orExpr
    : andExpr (OR_OP andExpr)*
    ;

andExpr
    : equalityExpr (AND_OP equalityExpr)*
    ;

equalityExpr
    : comparisonExpr ((EQEQ | NEQ) comparisonExpr)*
    ;

comparisonExpr
    : additiveExpr ((LT | LE | GT | GE) additiveExpr)*
    ;

additiveExpr
    : multiplicativeExpr ((PLUS | MINUS) multiplicativeExpr)*
    ;

multiplicativeExpr
    : unaryExpr ((STAR | DIV | MOD) unaryExpr)*
    ;

unaryExpr
    : (PLUS | MINUS | BANG | TILDE) unaryExpr
    | postfixExpr
    ;

postfixExpr
    : primaryExpr (DOT IDENTIFIER methodCallArgs?)*
    ;

primaryExpr
    : IDENTIFIER methodCallArgs?
    | INSTANCE_VAR
    | CLASS_VAR
    | GLOBAL_VAR
    | STRING
    | SYMBOL
    | INTEGER
    | FLOAT
    | TRUE
    | FALSE
    | NIL
    | SELF
    | arrayLiteral
    | hashLiteral
    | renderCall
    | redirectToCall
    | LPAREN expression RPAREN
    ;

methodCallArgs
    : LPAREN argumentList? RPAREN
    | blockArg
    ;

argumentList
    : argument (COMMA argument)*
    ;

argument
    : expression
    | hashPair
    | AMPERSAND IDENTIFIER
    ;

blockArg
    : DO (PIPE blockParams PIPE)?
      blockBody
      END
    | LBRACE (PIPE blockParams PIPE)?
      blockBody
      RBRACE
    ;

blockParams
    : IDENTIFIER (COMMA IDENTIFIER)*
    ;

blockBody
    : (statement)*
    ;

// Rails-specific method calls
renderCall
    : RENDER renderArgs
    ;

renderArgs
    : hashOptions
    | SYMBOL (COMMA hashOptions)?
    | STRING (COMMA hashOptions)?
    ;

redirectToCall
    : REDIRECT_TO (STRING | IDENTIFIER) (COMMA hashOptions)?
    ;

// Literals
arrayLiteral
    : LBRACK (expression (COMMA expression)*)? RBRACK
    ;

hashLiteral
    : LBRACE (hashPair (COMMA hashPair)*)? RBRACE
    ;

hashPair
    : (SYMBOL | STRING | IDENTIFIER) (ARROW | COLON) expression
    ;

hashOptions
    : hashPair (COMMA hashPair)*
    | hashLiteral
    ;

// Primary types
primary
    : IDENTIFIER
    | INSTANCE_VAR
    | CLASS_VAR
    | GLOBAL_VAR
    | STRING
    | SYMBOL
    | INTEGER
    | FLOAT
    | TRUE
    | FALSE
    | NIL
    | SELF
    ;
