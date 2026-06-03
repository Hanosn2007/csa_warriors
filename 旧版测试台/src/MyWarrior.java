public class MyWarrior extends Warrior {
    public MyWarrior() {
        // 这里是战士在界面和日志中显示的名字。
        super("My Warrior");
    }

    @Override
    public Action chooseAction(GameState state, UnitInfo self) {
        // 每次轮到这个战士行动时，游戏都会调用一次 chooseAction。
        // state 表示当前地图和所有单位的信息，self 表示这个战士自己。
        UnitInfo enemy = state.findNearestEnemy(self);

        // 如果场上已经没有敌人，就原地等待。
        if (enemy == null) {
            return Action.stay();
        }

        // distanceTo 使用地图步数距离。敌人在攻击范围内时，直接攻击。
        if (self.distanceTo(enemy) <= self.getRange()) {
            return Action.attack(enemy.getId());
        }

        // 敌人不在范围内时，向敌人的位置移动一步。
        return state.moveToward(self, enemy.getPosition());
    }
}
