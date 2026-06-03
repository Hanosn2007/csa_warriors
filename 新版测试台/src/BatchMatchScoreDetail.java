public class BatchMatchScoreDetail {
    private final int matchNumber;
    private final int roundCount;
    private final String teamName;
    private final String winner;
    private final boolean unfinished;
    private final int totalScore;
    private final int damageScore;
    private final int damageDealt;
    private final int defeatScore;
    private final int defeats;
    private final int powerUpScore;
    private final int powerUps;
    private final int strongPowerUps;
    private final int healingScore;
    private final int healingDone;
    private final int survivalScore;
    private final int finalAliveScore;
    private final int winnerScore;
    private final int penaltyScore;
    private final int invalidActions;
    private final int noProgressPenalties;
    private final int engagedEnemyCount;

    public BatchMatchScoreDetail(
            int matchNumber,
            int roundCount,
            String winner,
            boolean unfinished,
            TeamScore score) {
        this(matchNumber, roundCount, score.getTeamName(), winner, unfinished, score);
    }

    public BatchMatchScoreDetail(
            int matchNumber,
            int roundCount,
            String teamName,
            String winner,
            boolean unfinished,
            TeamScore score) {
        this.matchNumber = matchNumber;
        this.roundCount = roundCount;
        this.teamName = teamName;
        this.winner = winner;
        this.unfinished = unfinished;
        this.totalScore = score.getTotalScore();
        this.damageScore = score.getDamageScore();
        this.damageDealt = score.getDamageDealt();
        this.defeatScore = score.getDefeatScore();
        this.defeats = score.getDefeats();
        this.powerUpScore = score.getPowerUpScore();
        this.powerUps = score.getPowerUps();
        this.strongPowerUps = score.getStrongPowerUps();
        this.healingScore = score.getHealingScore();
        this.healingDone = score.getHealingDone();
        this.survivalScore = score.getSurvivalScore();
        this.finalAliveScore = score.getFinalAliveScore();
        this.winnerScore = score.getWinnerScore();
        this.penaltyScore = score.getPenaltyScore();
        this.invalidActions = score.getInvalidActions();
        this.noProgressPenalties = score.getNoProgressPenalties();
        this.engagedEnemyCount = score.getEngagedEnemyCount();
    }

    public int getMatchNumber() {
        return matchNumber;
    }

    public int getRoundCount() {
        return roundCount;
    }

    public String getTeamName() {
        return teamName;
    }

    public String getResultText() {
        if (unfinished) {
            return "未结束";
        }
        if (winner == null) {
            return "无胜者";
        }
        if (winner.equals(teamName)) {
            return "胜";
        }
        return "负";
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
        return engagedEnemyCount;
    }
}
