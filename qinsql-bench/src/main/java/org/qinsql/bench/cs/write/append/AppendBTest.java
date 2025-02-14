/*
 * Copyright Lealone Database Group.
 * Licensed under the Server Side Public License, v 1.
 * Initial Developer: zhh
 */
package org.qinsql.bench.cs.write.append;

import java.sql.Connection;
import java.sql.Statement;

import org.qinsql.bench.cs.write.ClientServerWriteBTest;

public abstract class AppendBTest extends ClientServerWriteBTest {

    public AppendBTest() {
        outerLoop = 15;
        threadCount = 48;
        sqlCountPerInnerLoop = 20;
        innerLoop = 10;
    }

    @Override
    protected void init() throws Exception {
        Connection conn = getConnection();
        Statement statement = conn.createStatement();
        statement.executeUpdate("drop table if exists AppendBTest");
        String sql = "create table if not exists AppendBTest(name varchar(20), f1 int, f2 int)";
        statement.executeUpdate(sql);
        close(statement, conn);
    }

    @Override
    protected UpdateThreadBase createUpdateThread(int id, Connection conn) {
        return new UpdateThread(id, conn);
    }

    private class UpdateThread extends UpdateThreadBase {

        UpdateThread(int id, Connection conn) {
            super(id, conn);
        }

        @Override
        protected String nextSql() {
            int i = id.incrementAndGet();
            return "insert into AppendBTest values('n" + i + "'," + i + "," + (i * 10) + ")";
        }
    }
}
