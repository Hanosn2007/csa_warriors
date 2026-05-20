public class Action {
    private final ActionType type;
    private final Direction direction;
    private final int targetId;

    private Action(ActionType type, Direction direction, int targetId) {
        this.type = type;
        this.direction = direction;
        this.targetId = targetId;
    }

    // 向一个方向移动一格。目标格如果是墙或已有单位，行动会失败。
    public static Action move(Direction direction) {
        return new Action(ActionType.MOVE, direction, -1);
    }

    // 攻击指定 id 的敌人。只有目标在攻击范围内时才会成功。
    public static Action attack(int targetId) {
        return new Action(ActionType.ATTACK, null, targetId);
    }

    // 防御会降低下一次受到的攻击伤害。
    public static Action defend() {
        return new Action(ActionType.DEFEND, null, -1);
    }

    // 原地等待。连续没有进展的等待会影响评分。
    public static Action stay() {
        return new Action(ActionType.STAY, null, -1);
    }

    public ActionType getType() {
        return type;
    }

    public Direction getDirection() {
        return direction;
    }

    public int getTargetId() {
        return targetId;
    }
}
