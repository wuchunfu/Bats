/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lealone.bats.engine.server;

import java.io.IOException;
import java.util.List;

import org.apache.calcite.schema.CalciteSchema;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.drill.common.exceptions.ExecutionSetupException;
import org.apache.drill.exec.proto.UserProtos;
import org.apache.drill.exec.server.Drillbit;
import org.apache.drill.exec.work.user.UserWorker;
import org.lealone.bats.engine.storage.LealoneStoragePlugin;
import org.lealone.bats.engine.storage.LealoneStoragePluginConfig;
import org.lealone.common.exceptions.DbException;
import org.lealone.db.Constants;
import org.lealone.db.Session;
import org.lealone.db.result.Result;
import org.lealone.net.Transfer;
import org.lealone.net.WritableChannel;
import org.lealone.server.TcpServerConnection;
import org.lealone.sql.PreparedStatement;
import org.lealone.storage.PageKey;

public class BatsServerConnection extends TcpServerConnection {

    // private static final Logger logger = LoggerFactory.getLogger(BatsServerConnection.class);
    private final boolean useBatsEngineForQuery = Boolean
            .parseBoolean(System.getProperty("use.bats.engine.for.query", "true"));

    private final BatsServer server;

    protected BatsServerConnection(BatsServer server, WritableChannel writableChannel, boolean isServer) {
        super(writableChannel, isServer);
        this.server = server;
    }

    @SuppressWarnings("unused")
    @Override
    protected void executeQueryAsync(Transfer transfer, Session session, int sessionId, int id, int operation,
            boolean prepared) throws IOException {
        int resultId = transfer.readInt();
        int maxRows = transfer.readInt();
        int fetchSize = transfer.readInt();
        boolean scrollable = transfer.readBoolean();

        if (operation == Session.COMMAND_DISTRIBUTED_TRANSACTION_QUERY
                || operation == Session.COMMAND_DISTRIBUTED_TRANSACTION_PREPARED_QUERY) {
            session.setAutoCommit(false);
            session.setRoot(false);
        }

        PreparedStatement command;
        if (prepared) {
            command = (PreparedStatement) getCache().getObject(id, false);
            setParameters(transfer, command);
        } else {
            String sql = transfer.readString();
            command = session.prepareStatement(sql, fetchSize);
            getCache().addObject(id, command);
        }
        command.setFetchSize(fetchSize);

        List<PageKey> pageKeys = readPageKeys(transfer);
        if (useBatsEngineForQuery) {
            Drillbit drillbit = server.getDrillbit();
            // TODO 支持drill的插件名前缀
            // String sql = "SELECT * FROM lealone.lealone.PUBLIC.MY_TABLE";
            String sql = command.getSQL();
            UserProtos.RunQuery runQuery = UserProtos.RunQuery.newBuilder().setPlan(sql)
                    .setType(org.apache.drill.exec.proto.UserBitShared.QueryType.SQL).build();
            UserWorker userWorker = drillbit.getWorkManager().getUserWorker();
            LealoneStoragePlugin lsp;
            try {
                lsp = (LealoneStoragePlugin) drillbit.getStoragePluginRegistry()
                        .getPlugin(LealoneStoragePluginConfig.NAME);
            } catch (ExecutionSetupException e) {
                throw DbException.throwInternalError();
            }

            SchemaPlus defaultSchema = CalciteSchema.createRootSchema(false, true, Constants.SCHEMA_MAIN).plus();
            String dbName = session.getDatabase().getShortName();
            SchemaPlus schema = CalciteSchema.createRootSchema(defaultSchema, false, true, dbName).plus();
            lsp.registerSchema(schema, dbName, defaultSchema);
            BatsClientConnection clientConnection = new BatsClientConnection(schema, session.getUserName(), userWorker,
                    getWritableChannel().getSocketChannel().getRemoteAddress(), res -> {
                        if (res.isSucceeded()) {
                            Result result = res.getResult();
                            sendResult(transfer, session, sessionId, id, operation, result, resultId, fetchSize);
                        }
                    });
            userWorker.submitWork(clientConnection, runQuery);
        } else {
            super.executeQueryAsync(transfer, session, sessionId, id, operation, prepared);
        }
    }
}
