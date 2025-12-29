lexer grammar RubyLexer;

// Keywords
CLASS       : 'class';
DEF         : 'def';
END         : 'end';
MODULE      : 'module';
DO          : 'do';
IF          : 'if';
ELSIF       : 'elsif';
ELSE        : 'else';
UNLESS      : 'unless';
WHILE       : 'while';
FOR         : 'for';
IN          : 'in';
RETURN      : 'return';
YIELD       : 'yield';
BREAK       : 'break';
NEXT        : 'next';
REDO        : 'redo';
RETRY       : 'retry';
RESCUE      : 'rescue';
ENSURE      : 'ensure';
RAISE       : 'raise';
WHEN        : 'when';
CASE        : 'case';
THEN        : 'then';
BEGIN       : 'begin';
SUPER       : 'super';
SELF        : 'self';
TRUE        : 'true';
FALSE       : 'false';
NIL         : 'nil';
AND         : 'and';
OR          : 'or';
NOT         : 'not';

// Rails/Ruby DSL keywords
BEFORE_ACTION   : 'before_action';
AFTER_ACTION    : 'after_action';
AROUND_ACTION   : 'around_action';
SKIP_BEFORE_ACTION : 'skip_before_action';
NAMESPACE       : 'namespace';
RESOURCES       : 'resources';
RESOURCE        : 'resource';
GET             : 'get';
POST            : 'post';
PUT             : 'put';
PATCH           : 'patch';
DELETE          : 'delete';
MATCH           : 'match';
ROOT            : 'root';
MEMBER          : 'member';
COLLECTION      : 'collection';
SCOPE           : 'scope';
RENDER          : 'render';
REDIRECT_TO     : 'redirect_to';
HEAD            : 'head';

// Separators
LPAREN      : '(';
RPAREN      : ')';
LBRACK      : '[';
RBRACK      : ']';
LBRACE      : '{';
RBRACE      : '}';
COMMA       : ',';
DOT         : '.';
SEMICOLON   : ';';
COLON       : ':';
DOUBLE_COLON : '::';
ARROW       : '=>';
PIPE        : '|';
QUESTION    : '?';

// Operators
PLUS        : '+';
MINUS       : '-';
STAR        : '*';
DIV         : '/';
MOD         : '%';
POW         : '**';
EQ          : '=';
PLUS_EQ     : '+=';
MINUS_EQ    : '-=';
STAR_EQ     : '*=';
DIV_EQ      : '/=';
MOD_EQ      : '%=';
EQEQ        : '==';
NEQ         : '!=';
LT          : '<';
LE          : '<=';
GT          : '>';
GE          : '>=';
SPACESHIP   : '<=>';
AND_OP      : '&&';
OR_OP       : '||';
AMPERSAND   : '&';
BANG        : '!';
TILDE       : '~';
LSHIFT      : '<<';
RSHIFT      : '>>';
CARET       : '^';
DOUBLE_DOT  : '..';
TRIPLE_DOT  : '...';

// Literals
SYMBOL      : ':' [a-zA-Z_][a-zA-Z0-9_?!]*;
INSTANCE_VAR : '@' [a-zA-Z_][a-zA-Z0-9_]*;
CLASS_VAR    : '@@' [a-zA-Z_][a-zA-Z0-9_]*;
GLOBAL_VAR   : '$' [a-zA-Z_][a-zA-Z0-9_]*;
IDENTIFIER  : [a-zA-Z_][a-zA-Z0-9_]*[?!]?;

// Numbers
INTEGER     : [0-9]+;
FLOAT       : [0-9]+ '.' [0-9]+;
HEXADECIMAL : '0' [xX] [0-9a-fA-F]+;
OCTAL       : '0' [oO] [0-7]+;
BINARY      : '0' [bB] [01]+;

// Strings
STRING      : '"' (~["\r\n] | '\\' .)* '"'
            | '\'' (~['\r\n] | '\\' .)* '\'';
REGEXP      : '/' (~[/\r\n] | '\\' .)* '/' [imxo]*;

// Whitespace and comments
NEWLINE     : ('\r'? '\n' | '\r') -> skip;
WS          : [ \t]+ -> skip;
COMMENT     : '#' ~[\r\n]* -> skip;

// Allow unrecognized characters
ERROR_CHAR  : . ;
