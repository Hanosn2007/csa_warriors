import java.util.ArrayList;

public class GameState {
    private final TileType[][] tiles;
    private final ArrayList<UnitInfo> units;
    private final ArrayList<Position> powerUpPositions;

    public GameState(TileType[][] tiles, ArrayList<UnitInfo> units) {
        this(tiles, units, new ArrayList<Position>());
    }

    public GameState(TileType[][] tiles, ArrayList<UnitInfo> units, ArrayList<Position> powerUpPositions) {
        this.tiles = copyTiles(tiles);
        this.units = new ArrayList<UnitInfo>();
        for (int i = 0; i < units.size(); i++) {
            this.units.add(units.get(i));
        }
        this.powerUpPositions = new ArrayList<Position>();
        for (int i = 0; i < powerUpPositions.size(); i++) {
            this.powerUpPositions.add(powerUpPositions.get(i));
        }
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

    public ArrayList<UnitInfo> getLivingUnits() {
        // 返回所有仍然存活的单位。
        ArrayList<UnitInfo> living = new ArrayList<UnitInfo>();
        for (int i = 0; i < units.size(); i++) {
            UnitInfo unit = units.get(i);
            if (unit.isAlive()) {
                living.add(unit);
            }
        }
        return living;
    }

    public ArrayList<UnitInfo> getLivingEnemies(UnitInfo self) {
        // 返回所有与 self 不同队伍的存活单位。
        ArrayList<UnitInfo> enemies = new ArrayList<UnitInfo>();
        if (self == null) {
            return enemies;
        }
        for (int i = 0; i < units.size(); i++) {
            UnitInfo unit = units.get(i);
            if (unit.isAlive() && unit.isEnemyOf(self)) {
                enemies.add(unit);
            }
        }
        return enemies;
    }

    public ArrayList<UnitInfo> getLivingTeammates(UnitInfo self) {
        // 返回与 self 同队伍的其他存活单位，不包含 self 自己。
        ArrayList<UnitInfo> teammates = new ArrayList<UnitInfo>();
        if (self == null) {
            return teammates;
        }
        for (int i = 0; i < units.size(); i++) {
            UnitInfo unit = units.get(i);
            if (unit.isAlive()
                    && unit.getId() != self.getId()
                    && unit.getTeamName().equals(self.getTeamName())) {
                teammates.add(unit);
            }
        }
        return teammates;
    }

    public UnitInfo findNearestEnemy(UnitInfo self) {
        // 找到距离最近的敌人。没有敌人时返回 null。
        ArrayList<UnitInfo> enemies = getLivingEnemies(self);
        UnitInfo nearest = null;
        int nearestDistance = 9999;
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo enemy = enemies.get(i);
            int distance = self.distanceTo(enemy);
            if (distance < nearestDistance) {
                nearest = enemy;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    public UnitInfo findWeakestEnemy(UnitInfo self) {
        // 找到当前血量最低的敌人。没有敌人时返回 null。
        ArrayList<UnitInfo> enemies = getLivingEnemies(self);
        UnitInfo weakest = null;
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo enemy = enemies.get(i);
            if (weakest == null || enemy.getHealth() < weakest.getHealth()) {
                weakest = enemy;
            }
        }
        return weakest;
    }

    public UnitInfo findLowestHealthEnemyInRange(UnitInfo self) {
        // 只在攻击范围内寻找血量最低的敌人。没有目标时返回 null。
        ArrayList<UnitInfo> enemies = getLivingEnemies(self);
        UnitInfo best = null;
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo enemy = enemies.get(i);
            if (self.distanceTo(enemy) <= self.getRange()) {
                if (best == null || enemy.getHealth() < best.getHealth()) {
                    best = enemy;
                }
            }
        }
        return best;
    }

    public Position findNearestHealingPoint(UnitInfo self) {
        // 找到最近的治疗点。治疗点会在战士行动结束后自动回血。
        if (self == null) {
            return null;
        }
        Position best = null;
        int bestDistance = 9999;
        for (int row = 0; row < getRows(); row++) {
            for (int col = 0; col < getCols(); col++) {
                if (tiles[row][col] == TileType.HEALING_POINT) {
                    Position position = new Position(row, col);
                    int distance = self.distanceTo(position);
                    if (distance < bestDistance) {
                        best = position;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    public ArrayList<Position> getPowerUpPositions() {
        // 返回所有当前存在的道具位置。
        ArrayList<Position> copy = new ArrayList<Position>();
        for (int i = 0; i < powerUpPositions.size(); i++) {
            copy.add(powerUpPositions.get(i));
        }
        return copy;
    }

    public Position findNearestPowerUp(UnitInfo self) {
        // 找到最近的道具。道具会在战士走到该格子后自动拾取。
        if (self == null) {
            return null;
        }
        Position best = null;
        int bestDistance = 9999;
        for (int i = 0; i < powerUpPositions.size(); i++) {
            Position position = powerUpPositions.get(i);
            int distance = self.distanceTo(position);
            if (distance < bestDistance) {
                best = position;
                bestDistance = distance;
            }
        }
        return best;
    }

    public Action moveToward(UnitInfo self, Position target) {
        // 朝目标位置移动一步。如果没有可走方向，就等待。
        Direction direction = findStepToward(self, target);
        if (direction == null) {
            return Action.stay();
        }
        return Action.move(direction);
    }

    public Action moveAwayFrom(UnitInfo self, Position danger) {
        // 尝试远离危险位置。如果周围没有更远的可走格子，就防御。
        if (self == null || danger == null) {
            return Action.stay();
        }

        Direction bestDirection = null;
        int bestDistance = self.distanceTo(danger);
        Direction[] directions = Direction.values();
        for (int i = 0; i < directions.length; i++) {
            Position next = self.getPosition().move(directions[i]);
            if (isOpen(next)) {
                int distance = next.distanceTo(danger);
                if (distance > bestDistance) {
                    bestDistance = distance;
                    bestDirection = directions[i];
                }
            }
        }

        if (bestDirection == null) {
            return Action.defend();
        }
        return Action.move(bestDirection);
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
        if (!isInside(position)) {
            return true;
        }
        return tiles[position.getRow()][position.getCol()] == TileType.WALL;
    }

    public boolean isHealingPoint(Position position) {
        if (!isInside(position)) {
            return false;
        }
        return tiles[position.getRow()][position.getCol()] == TileType.HEALING_POINT;
    }

    public boolean isOccupied(Position position) {
        if (position == null) {
            return false;
        }
        for (int i = 0; i < units.size(); i++) {
            UnitInfo unit = units.get(i);
            if (unit.isAlive() && unit.getPosition().equals(position)) {
                return true;
            }
        }
        return false;
    }

    public boolean isOpen(Position position) {
        // open 表示在地图内、不是墙、也没有活着的单位占据。
        return isInside(position) && !isWall(position) && !isOccupied(position);
    }

    private Direction findStepToward(UnitInfo self, Position target) {
        if (self == null || target == null) {
            return null;
        }

        Direction bestDirection = null;
        Direction fallbackDirection = null;
        int bestDistance = self.distanceTo(target);
        int fallbackDistance = 9999;
        Direction[] directions = preferredDirections(self.getPosition(), target);
        for (int i = 0; i < directions.length; i++) {
            Position next = self.getPosition().move(directions[i]);
            if (isOpen(next)) {
                int distance = next.distanceTo(target);
                if (fallbackDirection == null || distance < fallbackDistance) {
                    fallbackDirection = directions[i];
                    fallbackDistance = distance;
                }
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestDirection = directions[i];
                }
            }
        }
        if (bestDirection != null) {
            return bestDirection;
        }
        return fallbackDirection;
    }

    private Direction[] preferredDirections(Position start, Position target) {
        Direction vertical = Direction.DOWN;
        Direction horizontal = Direction.RIGHT;

        if (target.getRow() < start.getRow()) {
            vertical = Direction.UP;
        }
        if (target.getCol() < start.getCol()) {
            horizontal = Direction.LEFT;
        }

        int rowDistance = Math.abs(target.getRow() - start.getRow());
        int colDistance = Math.abs(target.getCol() - start.getCol());

        Direction oppositeVertical = vertical == Direction.UP ? Direction.DOWN : Direction.UP;
        Direction oppositeHorizontal = horizontal == Direction.LEFT ? Direction.RIGHT : Direction.LEFT;

        if (rowDistance >= colDistance) {
            return new Direction[] {vertical, horizontal, oppositeHorizontal, oppositeVertical};
        }
        return new Direction[] {horizontal, vertical, oppositeVertical, oppositeHorizontal};
    }

    private TileType[][] copyTiles(TileType[][] original) {
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
