package com.piotrbednarski.logicgatesplugin.model;

import org.bukkit.block.BlockFace;

public class GateData {
    private BlockFace facing;
    private final GateType type;
    private boolean state;
    private long lastToggleTime;
    private long interval = 1000L;

    public GateData(BlockFace facing, GateType type) {
        this.facing = facing;
        this.type = type;
        this.lastToggleTime = System.currentTimeMillis();
    }

    public BlockFace getFacing() {
        return facing;
    }

    public void setFacing(BlockFace facing) {
        this.facing = facing;
    }

    public GateType getType() {
        return type;
    }

    public boolean isState() {
        return state;
    }

    public void setState(boolean state) {
        this.state = state;
    }

    public long getLastToggleTime() {
        return lastToggleTime;
    }

    public void setLastToggleTime(long lastToggleTime) {
        this.lastToggleTime = lastToggleTime;
    }

    public void setInterval(long interval) {
        this.interval = interval;
    }

    public long getInterval() {
        return interval;
    }
}
