public class UnitScoreState {
    private final int unitId;
    private final String teamName;
    private Position lastHealingPosition;
    private int consecutiveHealingTurns;
    private int healingScore;
    private int survivalScore;
    private int noProgressTurns;

    public UnitScoreState(int unitId, String teamName) {
        this.unitId = unitId;
        this.teamName = teamName;
        this.lastHealingPosition = null;
        this.consecutiveHealingTurns = 0;
        this.healingScore = 0;
        this.survivalScore = 0;
        this.noProgressTurns = 0;
    }

    public int getUnitId() {
        return unitId;
    }

    public String getTeamName() {
        return teamName;
    }

    public Position getLastHealingPosition() {
        return lastHealingPosition;
    }

    public void setLastHealingPosition(Position lastHealingPosition) {
        this.lastHealingPosition = lastHealingPosition;
    }

    public int getConsecutiveHealingTurns() {
        return consecutiveHealingTurns;
    }

    public void setConsecutiveHealingTurns(int consecutiveHealingTurns) {
        this.consecutiveHealingTurns = consecutiveHealingTurns;
    }

    public int getHealingScore() {
        return healingScore;
    }

    public void addHealingScore(int amount) {
        healingScore = healingScore + amount;
    }

    public int getSurvivalScore() {
        return survivalScore;
    }

    public void addSurvivalScore(int amount) {
        survivalScore = survivalScore + amount;
    }

    public int getNoProgressTurns() {
        return noProgressTurns;
    }

    public void addNoProgressTurn() {
        noProgressTurns++;
    }

    public void resetNoProgressTurns() {
        noProgressTurns = 0;
    }
}
