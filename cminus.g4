grammar cminus;

program : block_item+;

block_item : declaration | statement;

declaration : type_specifier declarator ';';

type_specifier : 'int' | 'float';

declarator : Identifier | Identifier '=' expr;

statement
    : simple_statement ';'
    | selection_statement
    | iteration_statement
    | compound_statement
    ;

simple_statement : assignment_statement | funcall;

assignment_statement : Identifier ass_op expr;

ass_op : '=';

funcall : Identifier '(' argument_expr_list? ')';

argument_expr_list : expr (',' expr)*;

selection_statement
    : 'if' '(' condition ')' statement
    | 'if' '(' condition ')' statement 'else' statement
    ;

iteration_statement : 'while' '(' condition ')' statement;

condition : expr cmp_op expr;

cmp_op : '==' | '!=' | '<' | '<=' | '>' | '>=';

compound_statement : '{' block_item* '}';

expr : expr arith_op expr | atomic_expr;


arith_op : '+' | '-' | '*' | '/';

atomic_expr
    : Identifier
    | Number
    | '-' Number
    | '(' expr ')'
    | funcall
    ;

Identifier : NonDigit (NonDigit | Digit)*;

Number : Integer | Float;

Integer : Digit+;

Float
    : Digit+ '.' Digit* ExponentPart?
    | Digit* '.' Digit+ ExponentPart?
    ;

fragment ExponentPart : 'e' [+-]? Digit+;

fragment Digit : [0-9];
fragment NonDigit : [a-zA-Z_];

WS : [ \t\r\n]+ -> skip;