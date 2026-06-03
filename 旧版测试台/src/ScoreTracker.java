import java.util.ArrayList;

public class ScoreTracker {
    private final ScoreConfig config;
    private final ArrayList<TeamScore> teamScores;
    private final ArrayList<UnitScoreState> unitStates;

    public ScoreTracker(ScoreConfig config) {
        this.config = config;
        this.teamScores = new ArrayList<TeamScore>();
        this.unitStates = new ArrayList<UnitScoreState>();
    }

    public void addTeam(String teamName) {
        if (findTeamScore(teamName) == null) {
            teamScores.add(new TeamScore(teamName));
        }
    }

    public void recordDamage(Unit attacker, Unit target, int damage) {
        if (damage <= 0) {
            return;
        }
        TeamScore score = getTeamScore(attacker.getTeamName());
        score.addDamage(damage, damage * config.damagePoint);
        if (target != null) {
            score.addEngagedEnemy(target.getId());
            getTeamScore(target.getTeamName()).addEngagedEnemy(attacker.getId());
        }
    }

    public void recordDefeat(Unit attacker) {
        TeamScore score = getTeamScore(attacker.getTeamName());
        score.addDefeat(config.defeatBonus);
    }

    public void recordPowerUp(Unit unit, PowerUp powerUp) {
        boolean strong = isStrongPowerUp(powerUp.getType());
        int points = config.normalPowerUpBonus;
        if (strong) {
            points = config.strongPowerUpBonus;
        }
        TeamScore score = getTeamScore(unit.getTeamName());
        score.addPowerUp(strong, points);
    }

    public int recordHealing(Unit unit, int amount, Position position) {
        if (amount <= 0) {
            return 0;
        }

        UnitScoreState state = getUnitState(unit);
        if (position.equals(state.getLastHealingPosition())) {
            state.setConsecutiveHealingTurns(state.getConsecutiveHealingTurns() + 1);
        } else {
            state.setLastHealingPosition(position);
            state.setConsecutiveHealingTurns(1);
        }

        int rawPoints = (amount / config.healingChunkSize) * config.healingChunkPoint;
        if (amount % config.healingChunkSize != 0) {
            rawPoints = rawPoints + config.healingChunkPoint;
        }

        if (state.getConsecutiveHealingTurns() == 2) {
            rawPoints = rawPoints * config.secondConsecutiveHealingPercent / 100;
        } else if (state.getConsecutiveHealingTurns() >= 3) {
            rawPoints = rawPoints * config.thirdConsecutiveHealingPercent / 100;
        }

        int remaining = config.healingScoreCapPerUnit - state.getHealingScore();
        int points = Math.max(0, Math.min(rawPoints, remaining));
        state.addHealingScore(points);

        TeamScore score = getTeamScore(unit.getTeamName());
        score.addHealing(amount, points);
        return points;
    }

    public void recordInvalidAction(Unit unit) {
        TeamScore score = getTeamScore(unit.getTeamName());
        score.addInvalidActionPenalty(config.invalidActionPenalty);
    }

    public void recordTurnEnd(Unit unit, boolean moved, Action action, boolean scoredThisTurn) {
        UnitScoreState state = getUnitState(unit);
        if (state.getLastHealingPosition() != null
                && !unit.getPosition().equals(state.getLastHealingPosition())) {
            state.setLastHealingPosition(null);
            state.setConsecutiveHealingTurns(0);
        }

        boolean noProgress = !moved && !scoredThisTurn;

        if (noProgress) {
            state.addNoProgressTurn();
            if (state.getNoProgressTurns() >= config.noProgressTurnsBeforePenalty) {
                getTeamScore(unit.getTeamName()).addNoProgressPenalty(config.noProgressPenalty);
                state.resetNoProgressTurns();
            }
        } else {
            state.resetNoProgressTurns();
        }
    }

    public void recordRoundSurvival(ArrayList<Team> teams) {
        for (int i = 0; i < teams.size(); i++) {
            Team team = teams.get(i);
            ArrayList<Unit> units = team.getUnits();
            for (int j = 0; j < units.size(); j++) {
                Unit unit = units.get(j);
                if (unit.isAlive()) {
                    UnitScoreState state = getUnitState(unit);
                    if (state.getSurvivalScore() < config.survivalScoreCapPerUnit) {
                        int points = Math.min(
                                config.survivalPointPerRound,
                                config.survivalScoreCapPerUnit - state.getSurvivalScore());
                        state.addSurvivalScore(points);
                        getTeamScore(unit.getTeamName()).addSurvival(points);
                    }
                }
            }
        }
    }

    public void recordFinalBonuses(ArrayList<Team> teams, String winner) {
        for (int i = 0; i < teams.size(); i++) {
            Team team = teams.get(i);
            ArrayList<Unit> units = team.getUnits();
            for (int j = 0; j < units.size(); j++) {
                Unit unit = units.get(j);
                if (unit.isAlive()) {
                    getTeamScore(unit.getTeamName()).addFinalAlive(config.finalAliveBonus);
                }
            }
        }

        if (winner != null) {
            getTeamScore(winner).addWinnerBonus(config.winnerBonus);
        }
    }

    public String buildReport(ArrayList<Team> teams) {
        String report = "Score Report:\n";
        for (int i = 0; i < teams.size(); i++) {
            TeamScore score = getTeamScore(teams.get(i).getName());
            report = report + "  " + score.summaryBlock() + "\n";
        }
        return report;
    }

    public ArrayList<TeamScore> getTeamScores() {
        return new ArrayList<TeamScore>(teamScores);
    }

    private TeamScore getTeamScore(String teamName) {
        TeamScore score = findTeamScore(teamName);
        if (score == null) {
            score = new TeamScore(teamName);
            teamScores.add(score);
        }
        return score;
    }

    private TeamScore findTeamScore(String teamName) {
        for (int i = 0; i < teamScores.size(); i++) {
            TeamScore score = teamScores.get(i);
            if (score.getTeamName().equals(teamName)) {
                return score;
            }
        }
        return null;
    }

    private UnitScoreState getUnitState(Unit unit) {
        for (int i = 0; i < unitStates.size(); i++) {
            UnitScoreState state = unitStates.get(i);
            if (state.getUnitId() == unit.getId()) {
                return state;
            }
        }
        UnitScoreState state = new UnitScoreState(unit.getId(), unit.getTeamName());
        unitStates.add(state);
        return state;
    }

    private boolean isStrongPowerUp(PowerUpType type) {
        return type == PowerUpType.MEGA_HEALTH
                || type == PowerUpType.POWER_CORE
                || type == PowerUpType.BATTLE_CORE;
    }
}
