public class PowerUp {
    private final PowerUpType type;
    private final Position position;

    public PowerUp(PowerUpType type, Position position) {
        this.type = type;
        this.position = position;
    }

    public PowerUpType getType() {
        return type;
    }

    public Position getPosition() {
        return position;
    }

    public String getDisplayName() {
        if (type == PowerUpType.HEALTH) {
            return "Health Pack";
        }
        if (type == PowerUpType.ATTACK) {
            return "Attack Gem";
        }
        if (type == PowerUpType.RANGE) {
            return "Range Lens";
        }
        if (type == PowerUpType.MEGA_HEALTH) {
            return "Mega Health";
        }
        if (type == PowerUpType.POWER_CORE) {
            return "Power Core";
        }
        return "Battle Core";
    }

    public String getShortName() {
        if (type == PowerUpType.HEALTH) {
            return "HP";
        }
        if (type == PowerUpType.ATTACK) {
            return "ATK";
        }
        if (type == PowerUpType.RANGE) {
            return "RNG";
        }
        if (type == PowerUpType.MEGA_HEALTH) {
            return "MHP";
        }
        if (type == PowerUpType.POWER_CORE) {
            return "PWR";
        }
        return "CORE";
    }
}
