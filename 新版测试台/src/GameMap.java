public class GameMap {
    private final TileType[][] tiles;

    public GameMap(TileType[][] tiles) {
        this.tiles = copyTiles(tiles);
    }

    public static GameMap createDefault() {
        String[] lines = {
            "............",
            ".#....+...#.",
            ".#..##....#.",
            "...+........",
            "....###.....",
            "............",
            ".....###....",
            "........+...",
            ".#....##..#.",
            ".#...+....#.",
            "............",
            "............"
        };
        return fromText(lines);
    }

    public static GameMap fromText(String[] lines) {
        TileType[][] tiles = new TileType[lines.length][lines[0].length()];
        for (int row = 0; row < lines.length; row++) {
            for (int col = 0; col < lines[row].length(); col++) {
                char ch = lines[row].charAt(col);
                if (ch == '#') {
                    tiles[row][col] = TileType.WALL;
                } else if (ch == '+') {
                    tiles[row][col] = TileType.HEALING_POINT;
                } else {
                    tiles[row][col] = TileType.EMPTY;
                }
            }
        }
        return new GameMap(tiles);
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

    public TileType getTile(Position position) {
        if (!isInside(position)) {
            return TileType.WALL;
        }
        return tiles[position.getRow()][position.getCol()];
    }

    public boolean isInside(Position position) {
        if (position == null) {
            return false;
        }
        return position.getRow() >= 0
                && position.getRow() < getRows()
                && position.getCol() >= 0
                && position.getCol() < getCols();
    }

    public boolean isWall(Position position) {
        return !isInside(position) || getTile(position) == TileType.WALL;
    }

    public boolean isHealingPoint(Position position) {
        return isInside(position) && getTile(position) == TileType.HEALING_POINT;
    }

    public TileType[][] copyTiles() {
        return copyTiles(tiles);
    }

    private static TileType[][] copyTiles(TileType[][] original) {
        TileType[][] copy = new TileType[original.length][];
        for (int row = 0; row < original.length; row++) {
            copy[row] = new TileType[original[row].length];
            for (int col = 0; col < original[row].length; col++) {
                copy[row][col] = original[row][col];
            }
        }
        return copy;
    }
}
