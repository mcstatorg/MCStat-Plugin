package org.mcstat.common.platform;

/** One reading of server performance. Null fields were not measurable. */
public final class ServerSample {

    public final Double tps;
    public final Double mspt;
    public final Integer chunks;
    public final Integer entities;
    public final Integer tiles;

    public ServerSample(Double tps, Double mspt, Integer chunks, Integer entities, Integer tiles) {
        this.tps = tps;
        this.mspt = mspt;
        this.chunks = chunks;
        this.entities = entities;
        this.tiles = tiles;
    }

    public static final ServerSample EMPTY = new ServerSample(null, null, null, null, null);
}
