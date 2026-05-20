public class DefaultWarrior extends Warrior {
    public DefaultWarrior() {
        super("Default Demo", 115, 11, 1);
    }

    @Override
    public Action chooseAction(GameState state, UnitInfo self) {
        UnitInfo enemy = state.findNearestEnemy(self);
        if (enemy == null) {
            return Action.stay();
        }

        if (self.distanceTo(enemy) <= self.getRange()) {
            return Action.attack(enemy.getId());
        }

        if (self.getHealth() < 35) {
            Position healingPoint = state.findNearestHealingPoint(self);
            if (healingPoint != null) {
                return state.moveToward(self, healingPoint);
            }
            return Action.defend();
        }

        return state.moveToward(self, enemy.getPosition());
    }
}
