import java.util.ArrayList;

public class ReplayMatch {
    private final int matchNumber;
    private final TileType[][] tiles;
    private final ArrayList<ReplaySnapshot> snapshots;
    private String winner;
    private boolean unfinished;
    private int finalRound;

    public ReplayMatch(int matchNumber, GameEngine engine) {
        this.matchNumber = matchNumber;
        this.tiles = engine.getMap().copyTiles();
        this.snapshots = new ArrayList<ReplaySnapshot>();
        this.winner = null;
        this.unfinished = false;
        this.finalRound = engine.getCurrentRound();
        addSnapshot(engine);
    }

    public int getMatchNumber() {
        return matchNumber;
    }

    public TileType[][] getTiles() {
        TileType[][] copy = new TileType[tiles.length][];
        for (int row = 0; row < tiles.length; row++) {
            copy[row] = new TileType[tiles[row].length];
            for (int col = 0; col < tiles[row].length; col++) {
                copy[row][col] = tiles[row][col];
            }
        }
        return copy;
    }

    public int getRows() {
        return tiles.length;
    }

    public int getCols() {
        if (tiles.length == 0) {
            return 0;
        }
        return tiles[0].length;
    }

    public TileType getTile(int row, int col) {
        if (row < 0 || row >= getRows() || col < 0 || col >= getCols()) {
            return TileType.WALL;
        }
        return tiles[row][col];
    }

    public void addSnapshot(GameEngine engine) {
        snapshots.add(new ReplaySnapshot(snapshots.size(), engine));
    }

    public void finish(String winner, boolean unfinished, int finalRound) {
        this.winner = winner;
        this.unfinished = unfinished;
        this.finalRound = finalRound;
    }

    public String getResultText() {
        if (unfinished) {
            return "超过保护上限未结束";
        }
        if (winner == null) {
            return "无胜者";
        }
        return winner + " 胜";
    }

    public int getFinalRound() {
        return finalRound;
    }

    public int getSnapshotCount() {
        return snapshots.size();
    }

    public ReplaySnapshot getSnapshot(int index) {
        if (snapshots.size() == 0) {
            return null;
        }
        int safeIndex = Math.max(0, Math.min(index, snapshots.size() - 1));
        return snapshots.get(safeIndex);
    }
}
