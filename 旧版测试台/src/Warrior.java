public abstract class Warrior {
    private final String name;
    private final int maxHealth;
    private final int attackPower;
    private final int range;

    public Warrior(String name) {
        this(name, 110, 11, 1);
    }

    public Warrior(String name, int maxHealth, int attackPower, int range) {
        if (name == null || name.length() == 0) {
            this.name = "Unnamed Warrior";
        } else {
            this.name = name;
        }
        this.maxHealth = Math.max(1, maxHealth);
        this.attackPower = Math.max(1, attackPower);
        this.range = Math.max(1, range);
    }

    public String getName() {
        return name;
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

    // 策略的核心方法。
    // 每次行动必须返回一个 Action，例如移动、攻击、防御或等待。
    // 只能通过 state 和 self 读取公开信息，不能直接修改游戏内部状态。
    public abstract Action chooseAction(GameState state, UnitInfo self) throws InterruptedException;
}
