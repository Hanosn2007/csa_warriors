public class SmartWarrior extends Warrior {
    public SmartWarrior() {
        super("Smart Warrior", 105, 11, 2);
    }

    @Override
    public Action chooseAction(GameState state, UnitInfo self) {
        if (self.getHealth() < 30) {
            UnitInfo nearestEnemy = state.findNearestEnemy(self);
            if (nearestEnemy != null) {
                return state.moveAwayFrom(self, nearestEnemy.getPosition());
            }
            return Action.defend();
        }

        UnitInfo targetInRange = state.findLowestHealthEnemyInRange(self);
        if (targetInRange != null) {
            return Action.attack(targetInRange.getId());
        }

        UnitInfo weakest = state.findWeakestEnemy(self);
        if (weakest == null) {
            return Action.stay();
        }

        return state.moveToward(self, weakest.getPosition());
    }
}
