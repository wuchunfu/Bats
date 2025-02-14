/*
 * Copyright Lealone Database Group.
 * Licensed under the Server Side Public License, v 1.
 * Initial Developer: zhh
 */
package org.qinsql.mysql.server.protocol;

public class InitDbPacket extends RequestPacket {

    public String database;

    @Override
    public String getPacketInfo() {
        return "MySQL Init Database Packet";
    }

    @Override
    public void read(PacketInput in) {
        super.read(in);
        in.position(5);
        database = in.readStringWithNull();
    }
}
