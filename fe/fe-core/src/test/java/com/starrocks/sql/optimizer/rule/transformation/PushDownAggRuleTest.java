// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Inc.

package com.starrocks.sql.optimizer.rule.transformation;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.starrocks.catalog.Column;
import com.starrocks.catalog.Type;
import com.starrocks.sql.optimizer.Memo;
import com.starrocks.sql.optimizer.OptExpression;
import com.starrocks.sql.optimizer.OptimizerContext;
import com.starrocks.sql.optimizer.base.ColumnRefFactory;
import com.starrocks.sql.optimizer.operator.AggType;
import com.starrocks.sql.optimizer.operator.OperatorType;
import com.starrocks.sql.optimizer.operator.logical.LogicalAggregationOperator;
import com.starrocks.sql.optimizer.operator.logical.LogicalFilterOperator;
import com.starrocks.sql.optimizer.operator.logical.LogicalOlapScanOperator;
import com.starrocks.sql.optimizer.operator.scalar.BinaryPredicateOperator;
import com.starrocks.sql.optimizer.operator.scalar.ColumnRefOperator;
import com.starrocks.sql.optimizer.operator.scalar.CompoundPredicateOperator;
import com.starrocks.sql.optimizer.operator.scalar.ConstantOperator;
import mockit.Expectations;
import mockit.Mocked;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class PushDownAggRuleTest {

    @Test
    public void transform(@Mocked LogicalOlapScanOperator scanOp) {
        Map<ColumnRefOperator, Column> aggMap = Maps.newHashMap();
        aggMap.put(new ColumnRefOperator(1, Type.INT, "id", true), null);

        Map<ColumnRefOperator, Column> scanColumnMap = Maps.newHashMap();
        scanColumnMap.put(new ColumnRefOperator(1, Type.INT, "id", true), null);
        scanColumnMap.put(new ColumnRefOperator(2, Type.INT, "name", true), null);

        new Expectations(scanOp) {{
            scanOp.getColRefToColumnMetaMap();
            minTimes = 0;
            result = scanColumnMap;
        }};

        OptExpression filter = new OptExpression(new LogicalFilterOperator(
                new CompoundPredicateOperator(CompoundPredicateOperator.CompoundType.AND,
                        new BinaryPredicateOperator(BinaryPredicateOperator.BinaryType.EQ,
                                new ColumnRefOperator(1, Type.INT, "id", true),
                                ConstantOperator.createInt(1)),
                        new CompoundPredicateOperator(CompoundPredicateOperator.CompoundType.NOT,
                                new ColumnRefOperator(2, Type.INT, "name", true)))));

        OptExpression scan =
                new OptExpression(scanOp);
        OptExpression agg = new OptExpression(
                new LogicalAggregationOperator(AggType.GLOBAL, Lists.newArrayList(aggMap.keySet()), Maps.newHashMap()));

        filter.getInputs().add(agg);
        agg.getInputs().add(scan);

        PushDownPredicateAggRule rule = new PushDownPredicateAggRule();

        List<OptExpression> list = rule.transform(filter, new OptimizerContext(new Memo(), new ColumnRefFactory()));

        assertEquals(OperatorType.LOGICAL_AGGR, list.get(0).getOp().getOpType());
        assertEquals(OperatorType.COMPOUND,
                ((LogicalAggregationOperator) list.get(0).getOp()).getPredicate().getOpType());
        assertEquals(1, ((LogicalAggregationOperator) list.get(0).getOp()).getPredicate().getChildren().size());

        assertEquals(OperatorType.LOGICAL_FILTER, list.get(0).getInputs().get(0).getOp().getOpType());

        assertEquals(BinaryPredicateOperator.BinaryType.EQ,
                ((BinaryPredicateOperator) ((LogicalFilterOperator) list.get(0).getInputs().get(0).getOp())
                        .getPredicate()).getBinaryType());
    }
}