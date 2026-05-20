public class CarefulWarrior extends Warrior {
    public CarefulWarrior() {
        super("Careful Warrior", 120, 10, 1);
    }

    @Override
    public Action chooseAction(GameState state, UnitInfo self) {
        if (self.getHealth() < 40) {
            Position healingPoint = state.findNearestHealingPoint(self);
            if (healingPoint != null && !self.getPosition().equals(healingPoint)) {
                return state.moveToward(self, healingPoint);
            }
            return Action.defend();
        }

        UnitInfo enemy = state.findNearestEnemy(self);
        if (enemy == null) {
            return Action.stay();
        }

        if (self.distanceTo(enemy) <= self.getRange()) {
            return Action.attack(enemy.getId());
        }

        return state.moveToward(self, enemy.getPosition());
    }
}
