import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

// Main class to run the code on the provided file
public class Main {
    public static void main(String[] args) throws Exception {

        CharStream input = CharStreams.fromFileName(args[0]);
        cminusLexer lexer = new cminusLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        cminusParser parser = new cminusParser(tokens);
        ParseTree tree = parser.program();
        CMinusASTBuilder astBuilder = new CMinusASTBuilder();
        ASTNode astRoot = astBuilder.visit(tree);
        Path path = Paths.get(args[0]);
        String fileName = path.getFileName().toString();
        String className = fileName.replaceAll("\\.(cminus|c-)$", "");
        System.out.println(astRoot.toString());
        StringBuilder output = new StringBuilder();
        output.append(".class ").append(className).append("\n\n");
        output.append(".main\n");
        // Format the assembly with proper indentation
        String assembly = astRoot.toJVMAssembly();
        String formattedAssembly = formatAssembly(assembly);
        output.append(formattedAssembly).append("\n");
        output.append("return\n");
        Files.write(Paths.get(className + ".jasm"), output.toString().getBytes());
    }
    private static String formatAssembly(String assembly) {
        StringBuilder formatted = new StringBuilder();
        for (String line : assembly.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.endsWith(":")) {
                formatted.append(line).append("\n");
            } else {
                formatted.append("    ").append(line).append("\n");
            }
        }
        return formatted.toString();
    }
}

// AST Node (parent class)
abstract class ASTNode {
    protected static int labelCounter = 0;
    public abstract String toString();
    public abstract String toJVMAssembly();
    protected String indent(String str) {
        return str.replaceAll("(?m)^", "  ");
    }
    protected int getNextLabel() {
        return labelCounter++;
    }
}

class ProgramNode extends ASTNode {
    public final List<BlockItemNode> blockItems = new ArrayList<>();

    public void addBlockItem(BlockItemNode blockItem) {
        blockItems.add(blockItem);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[\n");
        for (BlockItemNode blockItem : blockItems) {
            sb.append(indent(blockItem.toString())).append("\n");
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public String toJVMAssembly() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < blockItems.size(); i++) {
            String code = blockItems.get(i).toJVMAssembly();
            if (code != null && !code.trim().isEmpty()) {
                sb.append(code);
                // Add newline if not the last item and if doesn't end with newline
                if (i < blockItems.size() - 1 && !code.endsWith("\n")) {
                    sb.append("\n");
                }
            }
        }
        return sb.toString();
    }
}

class BlockItemNode extends ASTNode {
    private final ASTNode content;

    public BlockItemNode(ASTNode content) {
        this.content = content;
    }

    @Override
    public String toString() {
        return content.toString();
    }

    @Override
    public String toJVMAssembly() {
        if (content == null) {
            return "";
        }
        String assembly = content.toJVMAssembly();
        return assembly != null ? assembly.trim() : "";
    }
}

class DeclarationNode extends ASTNode {
    private final String identifier;
    private final String type;

    public DeclarationNode(String identifier, String type) {
        this.identifier = identifier;
        this.type = type;
    }

    @Override
    public String toString() {
        return String.format("(declare \"%s\" %s)", identifier, type);
    }
    @Override
    public String toJVMAssembly() {
        return "";
    }
}

class AssignmentNode extends ASTNode {
    private final String identifier;
    private final ASTNode expr;

    public AssignmentNode(String identifier, ASTNode expr) {
        this.identifier = identifier;
        this.expr = expr;
    }

    @Override
    public String toString() {
        return String.format("(assign \"%s\" %s)", identifier, expr.toString());
    }
    @Override
    public String toJVMAssembly() {
        return expr.toJVMAssembly() + "\nistore " + identifier;
    }
}

class FuncallNode extends ASTNode {
    private final String funcName;
    private final List<ASTNode> arguments;

    public FuncallNode(String funcName, List<ASTNode> arguments) {
        this.funcName = funcName;
        this.arguments = arguments;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("(").append(funcName);
        for (ASTNode arg : arguments) {
            sb.append(" ").append(arg.toString());
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public String toJVMAssembly() {
        StringBuilder sb = new StringBuilder();
        
        switch (funcName) {
            case "putint":
                if (!arguments.isEmpty()) {
                    sb.append(arguments.get(0).toJVMAssembly()).append("\n");
                    sb.append("call putint");
                }
                break;
                
            case "getint":
                sb.append("call getint");
                break;

        }
        
        return sb.toString();
    }
}

class WhileNode extends ASTNode {
    private final ASTNode condition;
    private final ProgramNode body;

    public WhileNode(ASTNode condition, ProgramNode body) {
        this.condition = condition;
        this.body = body;
    }

    @Override
    public String toString() {
        return String.format("(while %s\n%s\n)", condition.toString(), indent(body.toString()));
    }
    @Override
    public String toJVMAssembly() {
        int labelNum = getNextLabel();
        String startLabel = "while_start_" + labelNum;
        String endLabel = "while_end_" + labelNum;
        
        StringBuilder sb = new StringBuilder();
        
        sb.append(startLabel).append(":\n");
        
        if (condition instanceof BinaryOperationNode) {
            BinaryOperationNode binaryOp = (BinaryOperationNode) condition;
            sb.append(binaryOp.toJVMAssemblyForBranch(endLabel));
        }
        
        for (BlockItemNode item : body.blockItems) {
            String itemCode = item.toJVMAssembly();
            if (!itemCode.trim().isEmpty()) {
                sb.append(itemCode).append("\n");
            }
        }
        
        sb.append("goto ").append(startLabel).append("\n");
        
        sb.append(endLabel).append(":\n");
        
        return sb.toString();
    }
}

class IfNode extends ASTNode {
    private final ASTNode condition;
    private final ProgramNode thenBody;
    private final ProgramNode elseBody;

    public IfNode(ASTNode condition, ProgramNode thenBody, ProgramNode elseBody) {
        this.condition = condition;
        this.thenBody = thenBody;
        this.elseBody = elseBody;
    }

    @Override
    public String toString() {
        if (elseBody != null) {
            return String.format("(if %s\n%s\n%s\n)", condition.toString(), 
                                 indent(thenBody.toString()), 
                                 indent(elseBody.toString()));
        } else {
            return String.format("(if %s\n%s\n)", condition.toString(), 
                                 indent(thenBody.toString()));
        }
    }
    @Override
    public String toJVMAssembly() {
        StringBuilder sb = new StringBuilder();
        
        if (elseBody != null) {
            // get the label number once and use it for both labels
            int labelNum = labelCounter++;
            String elseLabel = "else_" + labelNum;
            String endLabel = "endif_" + labelNum;
            
            if (condition instanceof BinaryOperationNode) {
                BinaryOperationNode binaryOp = (BinaryOperationNode) condition;
                sb.append(binaryOp.toJVMAssemblyForBranch(elseLabel));
            }
            
            // then block
            for (BlockItemNode item : thenBody.blockItems) {
                sb.append(item.toJVMAssembly()).append("\n");
            }
            
            sb.append("goto ").append(endLabel).append("\n");
            sb.append(elseLabel).append(":\n");
            
            // else block
            for (BlockItemNode item : elseBody.blockItems) {
                sb.append(item.toJVMAssembly()).append("\n");
            }
            
            sb.append(endLabel).append(":\n");
        } else {
            // for single if without else
            String skipLabel = "skip_" + getNextLabel();
            
            if (condition instanceof BinaryOperationNode) {
                BinaryOperationNode binaryOp = (BinaryOperationNode) condition;
                sb.append(binaryOp.toJVMAssemblyForBranch(skipLabel));
            }
            
            for (BlockItemNode item : thenBody.blockItems) {
                sb.append(item.toJVMAssembly()).append("\n");
            }
            
            sb.append(skipLabel).append(":\n");
        }
        
        return sb.toString();
    }
}

class BinaryOperationNode extends ASTNode {
    private final String operator;
    private final ASTNode left;
    private final ASTNode right;

    public BinaryOperationNode(String operator, ASTNode left, ASTNode right) {
        this.operator = operator;
        this.left = left;
        this.right = right;
    }

    @Override
    public String toString() {
        return String.format("(%s %s %s)", operator, left.toString(), right.toString());
    }
    @Override
    public String toJVMAssembly() {
        StringBuilder sb = new StringBuilder();
        
        sb.append(left.toJVMAssembly()).append("\n");
        sb.append(right.toJVMAssembly()).append("\n");
        switch (operator) {
            case "+": sb.append("iadd"); break;
            case "-": sb.append("isub"); break;
            case "*": sb.append("imul"); break;
            case "/": sb.append("idiv"); break;
        }
        
        return sb.toString();
    }
    
    public String toJVMAssemblyForBranch(String label) {
        StringBuilder sb = new StringBuilder();
        
        sb.append(left.toJVMAssembly()).append("\n");
        sb.append(right.toJVMAssembly()).append("\n");
        
        switch (operator) {
            case "==": sb.append("if_icmpne "); break;
            case "!=": sb.append("if_icmpeq "); break;
            case "<":  sb.append("if_icmpge "); break;
            case "<=": sb.append("if_icmpgt "); break;
            case ">":  sb.append("if_icmple "); break;
            case ">=": sb.append("if_icmplt "); break;
        }
        
        sb.append(label).append("\n");
        return sb.toString();
    }
}

class IdentifierNode extends ASTNode {
    private final String name;

    public IdentifierNode(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return String.format("\"%s\"", name);
    }
    @Override
    public String toJVMAssembly() {
        return "iload " + name;
    }
}

class NumberNode extends ASTNode {
    private final String value;
    

    public NumberNode(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
    @Override
    public String toJVMAssembly() {
        try {
            int intValue = Integer.parseInt(value);
            if (intValue == -1) {
                return "iconst_m1";
            } else if (intValue >= 0 && intValue <= 5) {
                return "iconst_" + intValue;
            } else {
                throw new RuntimeException("Number " + intValue + " is outside the supported range [-1, 5]");
            }
        } catch (NumberFormatException e) {
            return "iconst_0";
        }
    }
}

class DeclarationWithInitNode extends ASTNode {
    private final DeclarationNode declaration;
    private final AssignmentNode assignment;

    public DeclarationWithInitNode(DeclarationNode declaration, AssignmentNode assignment) {
        this.declaration = declaration;
        this.assignment = assignment;
    }

    @Override
    public String toString() {
        return declaration.toString() + "\n" + assignment.toString();
    }

    @Override
    public String toJVMAssembly() {
        return assignment.toJVMAssembly();
    }
}

// AST Builder class
class CMinusASTBuilder extends cminusBaseVisitor<ASTNode> {

    @Override
    public ASTNode visitProgram(cminusParser.ProgramContext ctx) {
        ProgramNode programNode = new ProgramNode();
        for (cminusParser.Block_itemContext item : ctx.block_item()) {
            ASTNode blockItemContent = visit(item);
            if (blockItemContent != null) {
                programNode.addBlockItem(new BlockItemNode(blockItemContent));
            }
        }
        return programNode;
    }

    @Override
    public ASTNode visitDeclaration(cminusParser.DeclarationContext ctx) {
        String type = ctx.type_specifier().getText();
        String identifier = ctx.declarator().Identifier().getText();
        DeclarationNode declNode = new DeclarationNode(identifier, type);
        if (ctx.declarator().expr() != null) {
            ASTNode expr = visit(ctx.declarator().expr());
            return new DeclarationWithInitNode(declNode, new AssignmentNode(identifier, expr));
        }
        return declNode;
    }

    @Override
    public ASTNode visitSimple_statement(cminusParser.Simple_statementContext ctx) {
        if (ctx.assignment_statement() != null) {
            return visit(ctx.assignment_statement());
        } else if (ctx.funcall() != null) {
            return visit(ctx.funcall());
        }
        return null;
    }

    @Override
    public ASTNode visitAssignment_statement(cminusParser.Assignment_statementContext ctx) {
        String identifier = ctx.Identifier().getText();
        ASTNode expr = visit(ctx.expr());
        return new AssignmentNode(identifier, expr);
    }

    @Override
    public ASTNode visitFuncall(cminusParser.FuncallContext ctx) {
        String funcName = ctx.Identifier().getText();
        List<ASTNode> arguments = new ArrayList<>();
        if (ctx.argument_expr_list() != null) {
            for (cminusParser.ExprContext expr : ctx.argument_expr_list().expr()) {
                arguments.add(visit(expr));
            }
        }
        return new FuncallNode(funcName, arguments);
    }
    @Override
    public ASTNode visitStatement(cminusParser.StatementContext ctx) {
        if (ctx.simple_statement() != null) {
            return visit(ctx.simple_statement());
        } else if (ctx.selection_statement() != null) {
            return visit(ctx.selection_statement());
        } else if (ctx.iteration_statement() != null) {
            return visit(ctx.iteration_statement());
        } else if (ctx.compound_statement() != null) {
            return visit(ctx.compound_statement());
        }
        return null;
    }
    @Override
    public ASTNode visitIteration_statement(cminusParser.Iteration_statementContext ctx) {
        ASTNode condition = visit(ctx.condition());
        ASTNode bodyContent = visit(ctx.statement());
        ProgramNode body = new ProgramNode();
        if (bodyContent instanceof ProgramNode) {
            body = (ProgramNode) bodyContent;
        } else {
            body.addBlockItem(new BlockItemNode(bodyContent));
        }
        return new WhileNode(condition, body);
    }

    @Override
    public ASTNode visitSelection_statement(cminusParser.Selection_statementContext ctx) {
        ASTNode condition = visit(ctx.condition());
        ASTNode thenBodyContent = visit(ctx.statement(0));
        ProgramNode thenBody = new ProgramNode();
        if (thenBodyContent instanceof ProgramNode) {
            thenBody = (ProgramNode) thenBodyContent;
        } else {
            thenBody.addBlockItem(new BlockItemNode(thenBodyContent));
        }
        
        ProgramNode elseBody = null;
        if (ctx.statement().size() > 1) {
            ASTNode elseBodyContent = visit(ctx.statement(1));
            elseBody = new ProgramNode();
            if (elseBodyContent instanceof ProgramNode) {
                elseBody = (ProgramNode) elseBodyContent;
            } else {
                elseBody.addBlockItem(new BlockItemNode(elseBodyContent));
            }
        }
        
        return new IfNode(condition, thenBody, elseBody);
    }


    @Override
    public ASTNode visitCondition(cminusParser.ConditionContext ctx) {
        ASTNode left = visit(ctx.expr(0));
        ASTNode right = visit(ctx.expr(1));
        String operator = ctx.cmp_op().getText();
        return new BinaryOperationNode(operator, left, right);
    }

    @Override
    public ASTNode visitCompound_statement(cminusParser.Compound_statementContext ctx) {
        ProgramNode blockNode = new ProgramNode();
        for (cminusParser.Block_itemContext item : ctx.block_item()) {
            ASTNode blockItemContent = visit(item);
            if (blockItemContent != null) {
                blockNode.addBlockItem(new BlockItemNode(blockItemContent));
            }
        }
        return blockNode;
    }
    
    @Override
    public ASTNode visitExpr(cminusParser.ExprContext ctx) {
        if (ctx.atomic_expr() != null) {
            return visit(ctx.atomic_expr());
        } else {
            ASTNode left = visit(ctx.expr(0));
            ASTNode right = visit(ctx.expr(1));
            String operator = ctx.arith_op().getText();
            return new BinaryOperationNode(operator, left, right);
        }
    }

    @Override
    public ASTNode visitAtomic_expr(cminusParser.Atomic_exprContext ctx) {
        if (ctx.Identifier() != null) {
            return new IdentifierNode(ctx.Identifier().getText());
        } else if (ctx.Number() != null) {
            if (ctx.getChild(0).getText().equals("-")) {
                // Handle negative number
                return new NumberNode("-" + ctx.Number().getText());
            } else {
                return new NumberNode(ctx.Number().getText());
            }
        } else if (ctx.expr() != null) {
            return visit(ctx.expr());
        } else if (ctx.funcall() != null) {
            return visit(ctx.funcall());
        }
        return null;
    }
}
