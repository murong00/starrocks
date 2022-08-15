// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Inc.
package com.starrocks.sql.ast;

import com.starrocks.sql.optimizer.base.SetQualifier;

import java.util.List;

public class IntersectRelation extends SetOperationRelation {
    public IntersectRelation(List<QueryRelation> relations, SetQualifier qualifier) {
        super(relations, qualifier);
    }

    public <R, C> R accept(AstVisitor<R, C> visitor, C context) {
        return visitor.visitIntersect(this, context);
    }
}
