## Project Overview

This project is a standalone custom compiler designed to generate JVM assembly code from a simplified C-like language called **C-minus**. The compiler includes both a front end, which parses the source code and constructs an Abstract Syntax Tree (AST), and a back end, which translates the AST into JVM-compatible assembly code. The generated JVM assembly can then be assembled into executable Java class files.

This compiler supports basic programming constructs, including:
- **Arithmetic operations**
- **Conditional statements** (if/else)
- **Loops** (while)
- **Variable declarations and assignments**
- **Function calls for built-in methods**
