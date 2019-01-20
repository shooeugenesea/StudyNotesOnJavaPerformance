package com.github.mcfongtw.behavioral.visitor;

import java.io.PrintStream;

/*
 * Visitor pattern is used when we have to perform an operation on a group of similar kind of
 * Objects. With the help of visitor pattern, we can move the operational logic from the objects to
 * another class.
 */
public class Application {

    public static void main(String[] args) {
        ASTNode num1 = new ASTNode("1", NodeType.NUMBER);
        ASTNode num2 = new ASTNode("2", NodeType.NUMBER);
        ASTNode op = new ASTNode("+", NodeType.ADD);

        op.insert(num1);
        op.insert(num2);


        Visitor visitor = new PrettyTreePrinter(new PrintStream(System.out));

        op.accept(visitor);

    }

}
