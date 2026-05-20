public class AggressiveWarrior extends Warrior {
    public AggressiveWarrior() {
        super("Aggressive Warrior", 110, 12, 1);
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

        return state.moveToward(self, enemy.getPosition());
    }
}
