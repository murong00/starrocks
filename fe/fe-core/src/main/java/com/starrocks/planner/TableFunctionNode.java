// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Inc.
package com.starrocks.planner;

import com.starrocks.analysis.TupleDescriptor;
import com.starrocks.catalog.TableFunction;
import com.starrocks.thrift.TExpr;
import com.starrocks.thrift.TExprNode;
import com.starrocks.thrift.TExprNodeType;
import com.starrocks.thrift.TPlanNode;
import com.starrocks.thrift.TPlanNodeType;
import com.starrocks.thrift.TTableFunctionNode;
import com.starrocks.thrift.TTypeDesc;

import java.util.List;

public class TableFunctionNode extends PlanNode {
    private final TableFunction tableFunction;
    //Slots of output by table function
    private final List<Integer> fnResultSlots;
    //External column slots of the join logic generated by the table function
    private final List<Integer> outerSlots;
    //Slots of table function input parameters
    private final List<Integer> paramSlots;

    public TableFunctionNode(PlanNodeId id, PlanNode child, TupleDescriptor outputTupleDesc,
                             TableFunction tableFunction,
                             List<Integer> paramSlots,
                             List<Integer> outerSlots,
                             List<Integer> fnResultSlots) {
        super(id, "TableValueFunction");
        this.children.add(child);
        this.tableFunction = tableFunction;

        this.paramSlots = paramSlots;
        this.outerSlots = outerSlots;
        this.fnResultSlots = fnResultSlots;
        this.tupleIds.add(outputTupleDesc.getId());
    }

    @Override
    protected void toThrift(TPlanNode msg) {
        msg.node_type = TPlanNodeType.TABLE_FUNCTION_NODE;
        msg.table_function_node = new TTableFunctionNode();

        TExprNode tExprNode = new TExprNode(TExprNodeType.TABLE_FUNCTION_EXPR,
                new TTypeDesc(), tableFunction.getNumArgs(), 0);
        tExprNode.setFn(tableFunction.toThrift());

        TExpr texpr = new TExpr();
        texpr.addToNodes(tExprNode);

        msg.table_function_node.setTable_function(texpr);
        msg.table_function_node.setParam_columns(paramSlots);
        msg.table_function_node.setOuter_columns(outerSlots);
        msg.table_function_node.setFn_result_columns(fnResultSlots);
    }

    @Override
    public boolean canUsePipeLine() {
        return getChildren().stream().allMatch(PlanNode::canUsePipeLine);
    }
}
