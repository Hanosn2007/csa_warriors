public class Position {
    private final int row;
    private final int col;

    public Position(int row, int col) {
        this.row = row;
        this.col = col;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    // 根据方向得到移动一格后的新位置。Position 本身不会被修改。
    public Position move(Direction direction) {
        if (direction == null) {
            return this;
        }
        return new Position(row + direction.getRowChange(), col + direction.getColChange());
    }

    // 使用网格步数距离，不计算斜线距离。
    // 例如从 (0, 0) 到 (2, 3) 的距离是 5。
    public int distanceTo(Position other) {
        if (other == null) {
            return 9999;
        }
        int rowDistance = Math.abs(row - other.row);
        int colDistance = Math.abs(col - other.col);
        return rowDistance + colDistance;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Position)) {
            return false;
        }
        Position position = (Position) other;
        return row == position.row && col == position.col;
    }

    @Override
    public int hashCode() {
        return row * 31 + col;
    }

    @Override
    public String toString() {
        return "(" + row + ", " + col + ")";
    }
}
