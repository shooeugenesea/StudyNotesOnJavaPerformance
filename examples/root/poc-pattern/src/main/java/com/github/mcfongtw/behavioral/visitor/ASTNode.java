package com.github.mcfongtw.behavioral.visitor;

import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import lombok.*;

import java.util.Collections;
import java.util.List;
import java.util.Queue;


@Data
public class ASTNode extends Node {

    @ToString.Exclude
    @EqualsAndHashCode.Include
    @Singular
    private List<ASTNode> children = Lists.newArrayList();

    @ToString.Exclude
    @EqualsAndHashCode.Include
    private ASTNode parent;

    private NodeType type;

    @Builder(builderClassName = "ASTNodeBuilder")
    public ASTNode(int id, String image, NodeType type) {
        super(id, image);
        this.type = type;
    }


    /*
     * *************************
     * AST Manipulation
     * *************************
     */

    public void insert( ASTNode node) {
        this.children.add(node);
        
        node.setParent(this);
    }
    
    public void insert(List<ASTNode> nodes) {
        this.children.addAll(nodes);
        
        for(ASTNode node: nodes) {
            node.setParent(this);
        }
    }

    public ASTNode getChildNode(int index) {
    	return this.children.get(index);
    }
    
    public int getChildCount() {
        return this.children.size();
    }
    

    public int getLastChildIndex() {
    	return this.children.size() - 1;
    }
    
    public boolean isRoot() {
        return this.parent == null;
    }
    

    public List<ASTNode> getDescendants() {
    	List<ASTNode> descedents = Lists.newArrayList();
    	
    	for(int i = 0; i < this.getChildCount(); i++) {
    		//Add the grand-children of ith child
    		descedents.add(this.getChildNode(i));
    		descedents.addAll(this.getChildNode(i).getDescendants());
    	}
    	
    	return descedents;
    }
    

    public int getNumOfDescendants() {
    	int count = 0;
    	
    	for(int i = 0; i < this.getChildCount(); i++) {
    		//count the ith child
    		count++;
    		count += this.getChildNode(i).getNumOfDescendants();
    	}
    	
    	return count;
    }

    public int getNumOfSiblings() {
        if(parent == null) {
            return 0;
        } else {
            return parent.children.size() -1;
        }
    }
    

	public Object accept(Visitor visitor) {

        Object result = null;

		//move to children / siblings
		if(visitor.getStrategy() == TraverseStrategy.DEPTH_FIRST) {
            result = performDepthFirstTraversal(visitor);
		} else if (visitor.getStrategy() == TraverseStrategy.BREADTH_FIRST) {
            result = performBreadthFirstTraversal(visitor);
        }

        return result;
	}

	private Object performDepthFirstTraversal(Visitor visitor) {
        //IN
        visitor.visit(this, VisitAction.IN);

        this.acceptChildren(visitor);

        //OUT
        visitor.visit(this, VisitAction.OUT);

        return null;
    }
	
	/**
	 * accept all children node of this {@code ASTNode} 
	 * 
	 * @param visitor the way we visit the child node
	 */
	private void acceptChildren(Visitor visitor) {
        for (ASTNode child : this.children) {
            child.accept(visitor);
        }
	}

	/*
	 * [ITERATIVE] For breadth first traversal. While adding all children
	 * to the queue, then poll the head and visit on it.
	 */
	private Object performBreadthFirstTraversal(Visitor visitor) {
	    Object result = null;

	    Queue<ASTNode> queue = Lists.newLinkedList();

	    queue.add(this);

	    while( queue.isEmpty() == false) {

	        ASTNode candidate = queue.poll();

	        visitor.visit(candidate, VisitAction.IN);

	        for(int i = 0; i < candidate.getChildCount(); i++) {
	            ASTNode child = candidate.getChildNode(i);
	            queue.add(child);
            }
        }

	    return result;
    }


    private void breathFirstSearchRecursively(Visitor visitor, ASTNode node, int level) {
        //IN
        visitor.visit(node, VisitAction.IN);

        //OUT
        visitor.visit(node, VisitAction.OUT);

        if(level > 1) {
            for(int i = 0; i < node.getChildCount(); i++) {
                ASTNode child = node.getChildNode(i);
                breathFirstSearchRecursively(visitor, child, level -1);
            }
        } else /* level == 1 */  {

        }
    }

    public int getMaximumHeight(ASTNode node) {
	    if(node.getChildCount() == 0) {
	        //leaf node
	        return 1;
        } else {
            /* compute  height of each subtree */
            int[] childrenHeights = new int[node.getChildCount()];

            for(int i = 0; i < node.getChildCount(); i++) {
                childrenHeights[i] = this.getMaximumHeight(node.getChildNode(i));
            }

            return Collections.max(Ints.asList(childrenHeights)).intValue() + 1;
	    }
    }

    @Override
    public String toString() {
    	StringBuilder builder = new StringBuilder();

        builder.append("id : " + this.id);
    	builder.append("image : " + this.image);
    	builder.append("\t\t");
    	builder.append("type : " + this.type.name());
    	
    	return builder.toString();
    }
}

enum NodeType {        
    ADD,
    SUB,
    MUL,
    DIV,
    NEG,
    NUMBER
}
