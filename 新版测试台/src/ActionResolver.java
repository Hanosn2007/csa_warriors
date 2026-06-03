public class ActionResolver {
    public static boolean resolve(GameEngine engine, Unit unit, Action action) {
        if (unit == null || !unit.isAlive()) {
            return false;
        }
        if (action == null) {
            action = Action.stay();
        }

        if (action.getType() == ActionType.MOVE) {
            return move(engine, unit, action.getDirection());
        } else if (action.getType() == ActionType.ATTACK) {
            return attack(engine, unit, action.getTargetId());
        } else if (action.getType() == ActionType.DEFEND) {
            unit.setDefending(true);
            engine.log(unitLabel(unit) + " defends.");
            return true;
        } else {
            engine.log(unitLabel(unit) + " stays.");
            return true;
        }
    }

    private static boolean move(GameEngine engine, Unit unit, Direction direction) {
        if (direction == null) {
            engine.log(unitLabel(unit) + " tried to move, but no direction was given.");
            engine.recordInvalidAction(unit);
            return false;
        }

        Position next = unit.getPosition().move(direction);
        if (!engine.isOpen(next)) {
            engine.log(unitLabel(unit) + " cannot move to " + next + ".");
            engine.recordInvalidAction(unit);
            return false;
        }

        unit.moveTo(next);
        engine.log(unitLabel(unit) + " moves " + direction + " to " + next + ".");
        return true;
    }

    private static boolean attack(GameEngine engine, Unit attacker, int targetId) {
        Unit target = engine.findUnitById(targetId);
        if (target == null || !target.isAlive()) {
            engine.log(unitLabel(attacker) + " attacks, but the target is invalid.");
            engine.recordInvalidAction(attacker);
            return false;
        }
        if (attacker.getTeamName().equals(target.getTeamName())) {
            engine.log(unitLabel(attacker) + " cannot attack a teammate.");
            engine.recordInvalidAction(attacker);
            return false;
        }

        int distance = attacker.getPosition().distanceTo(target.getPosition());
        if (distance > attacker.getRange()) {
            engine.log(unitLabel(attacker) + " attacks, but " + unitLabel(target) + " is out of range.");
            engine.recordInvalidAction(attacker);
            return false;
        }

        int damage = attacker.getAttackPower();
        if (target.isDefending()) {
            damage = Math.max(1, damage / 2);
            target.setDefending(false);
        }

        int beforeHealth = target.getHealth();
        target.takeDamage(damage);
        int actualDamage = beforeHealth - target.getHealth();
        engine.recordDamage(attacker, target, actualDamage);
        engine.log(unitLabel(attacker)
                + " hits "
                + unitLabel(target)
                + " for "
                + actualDamage
                + " damage.");

        if (!target.isAlive()) {
            engine.recordDefeat(attacker);
            engine.log(unitLabel(target) + " is defeated.");
        }
        return true;
    }

    private static String unitLabel(Unit unit) {
        return unit.getTeamName() + "#" + unit.getId() + " " + unit.getName();
    }
}
