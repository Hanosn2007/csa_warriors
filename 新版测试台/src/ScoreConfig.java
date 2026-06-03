public class ScoreConfig {
    // Combat rewards
    public int damagePoint = 1;
    public int defeatBonus = 25;

    // Resource rewards
    public int normalPowerUpBonus = 8;
    public int strongPowerUpBonus = 15;

    // Healing rewards. Repeated healing on the same point quickly loses score value.
    public int healingChunkSize = 5;
    public int healingChunkPoint = 1;
    public int healingScoreCapPerUnit = 25;
    public int secondConsecutiveHealingPercent = 50;
    public int thirdConsecutiveHealingPercent = 0;

    // Survival and result rewards
    public int survivalPointPerRound = 1;
    public int survivalScoreCapPerUnit = 40;
    public int finalAliveBonus = 20;
    public int winnerBonus = 50;

    // Penalties
    public int invalidActionPenalty = 2;
    public int noProgressPenalty = 5;
    public int noProgressTurnsBeforePenalty = 3;

    public static ScoreConfig defaults() {
        return new ScoreConfig();
    }
}
