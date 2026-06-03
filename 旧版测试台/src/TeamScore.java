import java.util.ArrayList;

public class TeamScore {
    private final String teamName;
    private final ArrayList<Integer> engagedEnemyIds;
    private int totalScore;
    private int damageScore;
    private int damageDealt;
    private int defeatScore;
    private int defeats;
    private int powerUpScore;
    private int powerUps;
    private int strongPowerUps;
    private int healingScore;
    private int healingDone;
    private int survivalScore;
    private int finalAliveScore;
    private int winnerScore;
    private int penaltyScore;
    private int invalidActions;
    private int noProgressPenalties;

    public TeamScore(String teamName) {
        this.teamName = teamName;
        this.engagedEnemyIds = new ArrayList<Integer>();
    }

    public String getTeamName() {
        return teamName;
    }

    public int getTotalScore() {
        return totalScore;
    }

    public int getDamageScore() {
        return damageScore;
    }

    public int getDamageDealt() {
        return damageDealt;
    }

    public int getDefeatScore() {
        return defeatScore;
    }

    public int getDefeats() {
        return defeats;
    }

    public int getPowerUpScore() {
        return powerUpScore;
    }

    public int getPowerUps() {
        return powerUps;
    }

    public int getStrongPowerUps() {
        return strongPowerUps;
    }

    public int getHealingScore() {
        return healingScore;
    }

    public int getHealingDone() {
        return healingDone;
    }

    public int getSurvivalScore() {
        return survivalScore;
    }

    public int getFinalAliveScore() {
        return finalAliveScore;
    }

    public int getWinnerScore() {
        return winnerScore;
    }

    public int getPenaltyScore() {
        return penaltyScore;
    }

    public int getInvalidActions() {
        return invalidActions;
    }

    public int getNoProgressPenalties() {
        return noProgressPenalties;
    }

    public int getEngagedEnemyCount() {
        return engagedEnemyIds.size();
    }

    public void addDamage(int damage, int points) {
        damageDealt = damageDealt + damage;
        damageScore = damageScore + points;
        totalScore = totalScore + points;
    }

    public void addEngagedEnemy(int enemyId) {
        if (!engagedEnemyIds.contains(enemyId)) {
            engagedEnemyIds.add(enemyId);
        }
    }

    public void addDefeat(int points) {
        defeats++;
        defeatScore = defeatScore + points;
        totalScore = totalScore + points;
    }

    public void addPowerUp(boolean strong, int points) {
        powerUps++;
        if (strong) {
            strongPowerUps++;
        }
        powerUpScore = powerUpScore + points;
        totalScore = totalScore + points;
    }

    public void addHealing(int healing, int points) {
        healingDone = healingDone + healing;
        healingScore = healingScore + points;
        totalScore = totalScore + points;
    }

    public void addSurvival(int points) {
        survivalScore = survivalScore + points;
        totalScore = totalScore + points;
    }

    public void addFinalAlive(int points) {
        finalAliveScore = finalAliveScore + points;
        totalScore = totalScore + points;
    }

    public void addWinnerBonus(int points) {
        winnerScore = winnerScore + points;
        totalScore = totalScore + points;
    }

    public void addInvalidActionPenalty(int points) {
        invalidActions++;
        penaltyScore = penaltyScore - points;
        totalScore = totalScore - points;
    }

    public void addNoProgressPenalty(int points) {
        noProgressPenalties++;
        penaltyScore = penaltyScore - points;
        totalScore = totalScore - points;
    }

    public String summaryBlock() {
        return teamName
                + " total="
                + totalScore
                + "\n    damage: "
                + damageScore
                + " pts / "
                + damageDealt
                + " HP"
                + ", engaged enemies: "
                + getEngagedEnemyCount()
                + ", defeats: "
                + defeatScore
                + " pts / "
                + defeats
                + "\n    power-ups: "
                + powerUpScore
                + " pts / "
                + powerUps
                + " picked, strong "
                + strongPowerUps
                + ", healing: "
                + healingScore
                + " pts / "
                + healingDone
                + " HP"
                + "\n    survival: "
                + survivalScore
                + ", final alive: "
                + finalAliveScore
                + ", win: "
                + winnerScore
                + ", penalties: "
                + penaltyScore
                + " (invalid "
                + invalidActions
                + ", idle "
                + noProgressPenalties
                + ")";
    }
}
