/* Generated By:JavaCC: Do not edit this line. BExGrammarConstants.java */
package org.openl.grammar.bexgrammar;

public interface BExGrammarConstants {

  int EOF = 0;
  int OR = 6;
  int AND = 7;
  int NOT = 8;
  int ABSTRACT = 9;
  int BREAK = 10;
  int CALCULATE = 11;
  int CASE = 12;
  int CATCH = 13;
  int CONST = 14;
  int CONTINUE = 15;
  int _DEFAULT = 16;
  int DO = 17;
  int ELSE = 18;
  int EXTENDS = 19;
  int FALSE = 20;
  int FINAL = 21;
  int FINALLY = 22;
  int FOR = 23;
  int GOTO = 24;
  int IF = 25;
  int PLUSSTR = 26;
  int IMPLEMENTS = 27;
  int IMPORT = 28;
  int INSTANCEOF = 29;
  int INTERFACE = 30;
  int NATIVE = 31;
  int NEW = 32;
  int NULL = 33;
  int PACKAGE = 34;
  int PRIVATE = 35;
  int PROTECTED = 36;
  int PUBLIC = 37;
  int RETURN = 38;
  int STATIC = 39;
  int SUPER = 40;
  int SWITCH = 41;
  int SYNCHRONIZED = 42;
  int THROW = 43;
  int THROWS = 44;
  int TRANSIENT = 45;
  int TRUE = 46;
  int TRY = 47;
  int VOID = 48;
  int VOLATILE = 49;
  int WHILE = 50;
  int OF = 51;
  int THE = 52;
  int WHERE = 53;
  int IS = 54;
  int LESS = 55;
  int THAN = 56;
  int LPAREN = 57;
  int RPAREN = 58;
  int LBRACE = 59;
  int RBRACE = 60;
  int LBRACKET = 61;
  int RBRACKET = 62;
  int SEMICOLON = 63;
  int COMMA = 64;
  int DOT = 65;
  int DDOT = 66;
  int TDOT = 67;
  int ASSIGN = 68;
  int GT = 69;
  int LT = 70;
  int BANG = 71;
  int TILDE = 72;
  int HOOK = 73;
  int COLON = 74;
  int EQ = 75;
  int LE = 76;
  int GE = 77;
  int NE = 78;
  int BOOL_OR = 79;
  int BOOL_AND = 80;
  int INCR = 81;
  int DECR = 82;
  int PLUS = 83;
  int MINUS = 84;
  int STAR = 85;
  int SLASH = 86;
  int BIT_AND = 87;
  int BIT_OR = 88;
  int BIT_XOR = 89;
  int REM = 90;
  int LSHIFT = 91;
  int RSIGNEDSHIFT = 92;
  int RUNSIGNEDSHIFT = 93;
  int PLUSASSIGN = 94;
  int MINUSASSIGN = 95;
  int STARASSIGN = 96;
  int SLASHASSIGN = 97;
  int ANDASSIGN = 98;
  int ORASSIGN = 99;
  int XORASSIGN = 100;
  int REMASSIGN = 101;
  int LSHIFTASSIGN = 102;
  int RSIGNEDSHIFTASSIGN = 103;
  int RUNSIGNEDSHIFTASSIGN = 104;
  int EXP = 105;
  int IMPL = 106;
  int INTEGER_LITERAL = 107;
  int DECIMAL_LITERAL = 108;
  int HEX_LITERAL = 109;
  int OCTAL_LITERAL = 110;
  int FP_LITERAL1 = 111;
  int FP_LITERAL2 = 112;
  int FLOATING_POINT_LITERAL = 113;
  int BUSINESS_INTEGER_LITERAL = 114;
  int PERCENT_LITERAL = 115;
  int EXPONENT = 116;
  int CHARACTER_LITERAL = 117;
  int STRING_LITERAL = 118;
  int IDENTIFIER = 119;
  int LETTER = 120;
  int DIGIT = 121;
  int LABEL = 122;
  int SINGLE_LINE_COMMENT = 125;
  int FORMAL_COMMENT = 126;
  int MULTI_LINE_COMMENT = 127;

  int DEFAULT = 0;
  int IN_FORMAL_COMMENT = 1;
  int IN_MULTI_LINE_COMMENT = 2;

  String[] tokenImage = {
    "<EOF>",
    "\" \"",
    "\"\\r\"",
    "\"\\t\"",
    "\"\\n\"",
    "\"\\f\"",
    "\"or\"",
    "\"and\"",
    "\"not\"",
    "\"abstract\"",
    "\"break\"",
    "\"Calculate\"",
    "\"case\"",
    "\"catch\"",
    "\"const\"",
    "\"continue\"",
    "\"default\"",
    "\"do\"",
    "\"else\"",
    "\"extends\"",
    "\"false\"",
    "\"final\"",
    "\"finally\"",
    "\"for\"",
    "\"goto\"",
    "\"if\"",
    "\"plus\"",
    "\"implements\"",
    "\"import\"",
    "\"instanceof\"",
    "\"interface\"",
    "\"native\"",
    "\"new\"",
    "\"null\"",
    "\"package\"",
    "\"private\"",
    "\"protected\"",
    "\"public\"",
    "\"return\"",
    "\"static\"",
    "\"super\"",
    "\"switch\"",
    "\"synchronized\"",
    "\"throw\"",
    "\"throws\"",
    "\"transient\"",
    "\"true\"",
    "\"try\"",
    "\"void\"",
    "\"volatile\"",
    "\"while\"",
    "\"of\"",
    "\"the\"",
    "\"where\"",
    "\"is\"",
    "\"less\"",
    "\"than\"",
    "\"(\"",
    "\")\"",
    "\"{\"",
    "\"}\"",
    "\"[\"",
    "\"]\"",
    "\";\"",
    "\",\"",
    "\".\"",
    "\"..\"",
    "\"...\"",
    "\"=\"",
    "\">\"",
    "\"<\"",
    "\"!\"",
    "\"~\"",
    "\"?\"",
    "\":\"",
    "\"==\"",
    "\"<=\"",
    "\">=\"",
    "\"!=\"",
    "\"||\"",
    "\"&&\"",
    "\"++\"",
    "\"--\"",
    "\"+\"",
    "\"-\"",
    "\"*\"",
    "\"/\"",
    "\"&\"",
    "\"|\"",
    "\"^\"",
    "\"%\"",
    "\"<<\"",
    "\">>\"",
    "\">>>\"",
    "\"+=\"",
    "\"-=\"",
    "\"*=\"",
    "\"/=\"",
    "\"&=\"",
    "\"|=\"",
    "\"^=\"",
    "\"%=\"",
    "\"<<=\"",
    "\">>=\"",
    "\">>>=\"",
    "\"**\"",
    "\"->\"",
    "<INTEGER_LITERAL>",
    "<DECIMAL_LITERAL>",
    "<HEX_LITERAL>",
    "<OCTAL_LITERAL>",
    "<FP_LITERAL1>",
    "<FP_LITERAL2>",
    "<FLOATING_POINT_LITERAL>",
    "<BUSINESS_INTEGER_LITERAL>",
    "<PERCENT_LITERAL>",
    "<EXPONENT>",
    "<CHARACTER_LITERAL>",
    "<STRING_LITERAL>",
    "<IDENTIFIER>",
    "<LETTER>",
    "<DIGIT>",
    "<LABEL>",
    "<token of kind 123>",
    "\"/*\"",
    "<SINGLE_LINE_COMMENT>",
    "\"*/\"",
    "\"*/\"",
    "<token of kind 128>",
    "\"equals to\"",
    "\"is same as\"",
    "\"does not equal to\"",
    "\"is different from\"",
    "\"is less than\"",
    "\"is more than\"",
    "\"is less or equal\"",
    "\"is no more than\"",
    "\"is in\"",
    "\"is more or equal\"",
    "\"is no less than\"",
    "\"less than\"",
    "\"more than\"",
    "\"or less\"",
    "\"and more\"",
  };

}
