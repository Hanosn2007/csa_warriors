public class Unit {
    private final int id;
    private final Warrior warrior;
    private final String teamName;
    private int health;
    private int row;
    private int col;
    private boolean alive;
    private boolean defending;
    private int attackBonus;
    private int rangeBonus;

    public Unit(int id, Warrior warrior, String teamName, int row, int col) {
        this.id = id;
        this.warrior = warrior;
        this.teamName = teamName;
        this.health = warrior.getMaxHealth();
        this.row = row;
        this.col = col;
        this.alive = true;
        this.defending = false;
        this.attackBonus = 0;
        this.rangeBonus = 0;
    }

    public int getId() {
        return id;
    }

    public Warrior getWarrior() {
        return warrior;
    }

    public String getName() {
        return warrior.getName();
    }

    public String getTeamName() {
        return teamName;
    }

    public int getHealth() {
        return health;
    }

    public int getMaxHealth() {
        return warrior.getMaxHealth();
    }

    public int getAttackPower() {
        return warrior.getAttackPower() + attackBonus;
    }

    public int getRange() {
        return warrior.getRange() + rangeBonus;
    }

    public int getAttackBonus() {
        return attackBonus;
    }

    public int getRangeBonus() {
        return rangeBonus;
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

    public void setDefending(boolean defending) {
        this.defending = defending;
    }

    public Position getPosition() {
        return new Position(row, col);
    }

    public void moveTo(Position position) {
        row = position.getRow();
        col = position.getCol();
    }

    public void takeDamage(int damage) {
        if (!alive) {
            return;
        }
        health = health - Math.max(0, damage);
        if (health <= 0) {
            health = 0;
            alive = false;
            defending = false;
        }
    }

    public void heal(int amount) {
        if (!alive) {
            return;
        }
        health = Math.min(getMaxHealth(), health + Math.max(0, amount));
    }

    public void addAttackBonus(int amount) {
        attackBonus = Math.min(6, attackBonus + Math.max(0, amount));
    }

    public void addRangeBonus(int amount) {
        rangeBonus = Math.min(2, rangeBonus + Math.max(0, amount));
    }

    public UnitInfo toInfo() {
        return new UnitInfo(
                id,
                getName(),
                teamName,
                health,
                getMaxHealth(),
                getAttackPower(),
                getRange(),
                row,
                col,
                alive,
                defending);
    }
}
