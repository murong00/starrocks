// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Inc.

package com.starrocks.execution;

import com.starrocks.analysis.InstallPluginStmt;
import com.starrocks.analysis.StatementBase;
import com.starrocks.qe.ConnectContext;
import com.starrocks.qe.ShowResultSet;

public class InstallPluginExecutor implements DataDefinitionExecutor {

    public ShowResultSet execute(StatementBase stmt, ConnectContext context) throws Exception {
        context.getGlobalStateMgr().installPlugin((InstallPluginStmt) stmt);
        return null;
    }
}

