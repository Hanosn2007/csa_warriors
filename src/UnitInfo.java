public class UnitInfo {
    private final int id;
    private final String name;
    private final String teamName;
    private final int health;
    private final int maxHealth;
    private final int attackPower;
    private final int range;
    private final int row;
    private final int col;
    private final boolean alive;
    private final boolean defending;

    public UnitInfo(
            int id,
            String name,
            String teamName,
            int health,
            int maxHealth,
            int attackPower,
            int range,
            int row,
            int col,
            boolean alive,
            boolean defending) {
        this.id = id;
        this.name = name;
        this.teamName = teamName;
        this.health = health;
        this.maxHealth = maxHealth;
        this.attackPower = attackPower;
        this.range = range;
        this.row = row;
        this.col = col;
        this.alive = alive;
        this.defending = defending;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTeamName() {
        return teamName;
    }

    public int getHealth() {
        return health;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public int getAttackPower() {
        return attackPower;
    }

    public int getRange() {
        return range;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public boolean isAlive() {
        return alive;
    }

    public boolean isDefending() {
        return defending;
    }

    public Position getPosition() {
        return new Position(row, col);
    }

    // 计算自己到另一个单位的网格步数距离。
    public int distanceTo(UnitInfo other) {
        if (other == null) {
            return 9999;
        }
        return distanceTo(other.getPosition());
    }

    // 计算自己到某个地图位置的网格步数距离。
    public int distanceTo(Position position) {
        return getPosition().distanceTo(position);
    }

    // 队伍名不同就视为敌人。
    public boolean isEnemyOf(UnitInfo other) {
        if (other == null) {
            return false;
        }
        return !teamName.equals(other.teamName);
    }
}
