public class ReplayPowerUpState {
    private final PowerUpType type;
    private final int row;
    private final int col;

    public ReplayPowerUpState(PowerUp powerUp) {
        type = powerUp.getType();
        row = powerUp.getPosition().getRow();
        col = powerUp.getPosition().getCol();
    }

    public PowerUpType getType() {
        return type;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }
}
