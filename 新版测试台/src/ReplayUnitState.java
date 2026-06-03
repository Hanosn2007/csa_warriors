public class ReplayUnitState {
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

    public ReplayUnitState(Unit unit) {
        id = unit.getId();
        name = unit.getName();
        teamName = unit.getTeamName();
        health = unit.getHealth();
        maxHealth = unit.getMaxHealth();
        attackPower = unit.getAttackPower();
        range = unit.getRange();
        row = unit.getRow();
        col = unit.getCol();
        alive = unit.isAlive();
        defending = unit.isDefending();
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
}
