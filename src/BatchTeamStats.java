public class BatchTeamStats {
    private final String teamName;
    private int wins;
    private int totalScore;
    private int bestScore;
    private int worstScore;
    private int lastScore;
    private int damageDealt;
    private int defeats;
    private int powerUps;
    private int strongPowerUps;
    private int healingDone;
    private int survivalScore;
    private int penaltyScore;
    private int invalidActions;
    private int noProgressPenalties;
    private int engagedEnemyTotal;

    public BatchTeamStats(String teamName) {
        this.teamName = teamName;
        this.wins = 0;
        this.totalScore = 0;
        this.bestScore = Integer.MIN_VALUE;
        this.worstScore = Integer.MAX_VALUE;
        this.lastScore = 0;
        this.damageDealt = 0;
        this.defeats = 0;
        this.powerUps = 0;
        this.strongPowerUps = 0;
        this.healingDone = 0;
        this.survivalScore = 0;
        this.penaltyScore = 0;
        this.invalidActions = 0;
        this.noProgressPenalties = 0;
        this.engagedEnemyTotal = 0;
    }

    public String getTeamName() {
        return teamName;
    }

    public int getWins() {
        return wins;
    }

    public int getTotalScore() {
        return totalScore;
    }

    public int getBestScore() {
        if (bestScore == Integer.MIN_VALUE) {
            return 0;
        }
        return bestScore;
    }

    public int getWorstScore() {
        if (worstScore == Integer.MAX_VALUE) {
            return 0;
        }
        return worstScore;
    }

    public int getLastScore() {
        return lastScore;
    }

    public int getDamageDealt() {
        return damageDealt;
    }

    public int getDefeats() {
        return defeats;
    }

    public int getPowerUps() {
        return powerUps;
    }

    public int getStrongPowerUps() {
        return strongPowerUps;
    }

    public int getHealingDone() {
        return healingDone;
    }

    public int getSurvivalScore() {
        return survivalScore;
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

    public int getEngagedEnemyTotal() {
        return engagedEnemyTotal;
    }

    public void addWin() {
        wins++;
    }

    public void addScore(int score) {
        lastScore = score;
        totalScore = totalScore + score;
        bestScore = Math.max(bestScore, score);
        worstScore = Math.min(worstScore, score);
    }

    public void addTeamScore(TeamScore score) {
        addScore(score.getTotalScore());
        damageDealt = damageDealt + score.getDamageDealt();
        defeats = defeats + score.getDefeats();
        powerUps = powerUps + score.getPowerUps();
        strongPowerUps = strongPowerUps + score.getStrongPowerUps();
        healingDone = healingDone + score.getHealingDone();
        survivalScore = survivalScore + score.getSurvivalScore();
        penaltyScore = penaltyScore + score.getPenaltyScore();
        invalidActions = invalidActions + score.getInvalidActions();
        noProgressPenalties = noProgressPenalties + score.getNoProgressPenalties();
        engagedEnemyTotal = engagedEnemyTotal + score.getEngagedEnemyCount();
    }

    public double getAverageScore(int matches) {
        if (matches <= 0) {
            return 0.0;
        }
        return totalScore * 1.0 / matches;
    }

    public double getAverageDamage(int matches) {
        if (matches <= 0) {
            return 0.0;
        }
        return damageDealt * 1.0 / matches;
    }

    public double getAverageHealing(int matches) {
        if (matches <= 0) {
            return 0.0;
        }
        return healingDone * 1.0 / matches;
    }

    public double getAverageEngagedEnemies(int matches) {
        if (matches <= 0) {
            return 0.0;
        }
        return engagedEnemyTotal * 1.0 / matches;
    }
}
