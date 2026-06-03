import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

public class GameEngine {
    public static final int NO_ROUND_LIMIT = 0;
    public static final int DEFAULT_MAX_ROUNDS = 500;
    public static final int DEFAULT_NO_STATE_CHANGE_ROUND_LIMIT = 30;
    public static final long STRATEGY_TIMEOUT_MILLIS = 250;

    private final GameMap map;
    private final ArrayList<Team> teams;
    private final ArrayList<PowerUp> powerUps;
    private final ScoreTracker scoreTracker;
    private final int maxRounds;
    private final int noStateChangeRoundLimit;
    private final int healingAmount;
    private final Random random;
    private int nextUnitId;
    private int currentRound;
    private int teamCursor;
    private int unitCursor;
    private int lastActingUnitId;
    private int actionsSincePowerUp;
    private boolean printLogs;
    private boolean storeLogs;
    private boolean startingPowerUpsCreated;
    private boolean scoreFinalized;
    private boolean currentTurnScored;
    private boolean noStateChangeLimitReached;
    private int roundsWithoutStateChange;
    private String previousRoundSnapshot;
    private final ArrayList<String> eventLog;
    private String lastIntentText;
    private String lastDecisionTraceText;
    private TurnAnimation lastAnimation;

    public GameEngine(GameMap map, int maxRounds) {
        this.map = map;
        this.maxRounds = maxRounds;
        this.noStateChangeRoundLimit = DEFAULT_NO_STATE_CHANGE_ROUND_LIMIT;
        this.healingAmount = 8;
        this.random = new Random();
        this.nextUnitId = 1;
        this.currentRound = 1;
        this.teamCursor = 0;
        this.unitCursor = 0;
        this.lastActingUnitId = -1;
        this.actionsSincePowerUp = 0;
        this.printLogs = true;
        this.storeLogs = true;
        this.startingPowerUpsCreated = false;
        this.teams = new ArrayList<Team>();
        this.powerUps = new ArrayList<PowerUp>();
        this.scoreTracker = new ScoreTracker(ScoreConfig.defaults());
        this.eventLog = new ArrayList<String>();
        this.lastIntentText = "比赛尚未开始。";
        this.lastDecisionTraceText = "比赛尚未开始。\n点击“下一步”后，这里会显示本次 chooseAction 的关键值、追踪信息和返回行动。";
        this.lastAnimation = null;
        this.scoreFinalized = false;
        this.currentTurnScored = false;
        this.noStateChangeLimitReached = false;
        this.roundsWithoutStateChange = 0;
        this.previousRoundSnapshot = null;
    }

    public void addTeam(Team team) {
        teams.add(team);
        scoreTracker.addTeam(team.getName());
    }

    public void addWarrior(Team team, Warrior warrior, int row, int col) {
        Position position = new Position(row, col);
        if (!isOpen(position)) {
            throw new IllegalArgumentException("Starting position is not open: " + position);
        }
        team.addUnit(new Unit(nextUnitId, warrior, team.getName(), row, col));
        nextUnitId++;
    }

    public void run() {
        System.out.println("Java Warrior Arena starts!");
        Renderer.printMap(map, teams, powerUps);
        Renderer.printStatus(teams);

        while (!isGameOver()) {
            System.out.println();
            System.out.println("=== Round " + currentRound + " ===");
            stepRound();
            Renderer.printMap(map, teams, powerUps);
            Renderer.printStatus(teams);
        }

        System.out.println();
        System.out.println(getResultText());
        System.out.print(getScoreReport());
    }

    public void log(String message) {
        if (storeLogs) {
            eventLog.add(message);
        }
        if (printLogs) {
            System.out.println(message);
        }
    }

    public void setPrintLogs(boolean printLogs) {
        this.printLogs = printLogs;
    }

    public void setStoreLogs(boolean storeLogs) {
        this.storeLogs = storeLogs;
    }

    public void setRandomSeed(long seed) {
        random.setSeed(seed);
    }

    public GameMap getMap() {
        return map;
    }

    public ArrayList<Team> getTeams() {
        return teams;
    }

    public ArrayList<PowerUp> getPowerUps() {
        return new ArrayList<PowerUp>(powerUps);
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public int getMaxRounds() {
        return maxRounds;
    }

    public boolean hasRoundLimit() {
        return maxRounds > NO_ROUND_LIMIT;
    }

    public String getRoundLimitText() {
        if (hasRoundLimit()) {
            return String.valueOf(maxRounds);
        }
        return "无限";
    }

    public int getNoStateChangeRoundLimit() {
        return noStateChangeRoundLimit;
    }

    public int getRoundsWithoutStateChange() {
        return roundsWithoutStateChange;
    }

    public int getRoundsUntilNoStateChangeLoss() {
        return Math.max(0, noStateChangeRoundLimit - roundsWithoutStateChange);
    }

    public String getNoStateChangeWarningText() {
        if (noStateChangeLimitReached) {
            return "连续 "
                    + noStateChangeRoundLimit
                    + " 回合无状态变化，已触发无进展判负。";
        }
        if (roundsWithoutStateChange == 0) {
            return "无状态变化判负倒计时: "
                    + noStateChangeRoundLimit
                    + " 回合。";
        }
        return "已连续 "
                + roundsWithoutStateChange
                + " 回合无状态变化，剩余 "
                + getRoundsUntilNoStateChangeLoss()
                + " 回合判负。";
    }

    public int getLastActingUnitId() {
        return lastActingUnitId;
    }

    public String getLastIntentText() {
        return lastIntentText;
    }

    public String getLastDecisionTraceText() {
        return lastDecisionTraceText;
    }

    public TurnAnimation getLastAnimation() {
        return lastAnimation;
    }

    public ArrayList<String> getEventLog() {
        return new ArrayList<String>(eventLog);
    }

    public String getScoreReport() {
        finishScoringIfNeeded();
        return scoreTracker.buildReport(teams);
    }

    public ArrayList<TeamScore> getTeamScores() {
        finishScoringIfNeeded();
        return scoreTracker.getTeamScores();
    }

    public String getWinnerName() {
        if (noStateChangeLimitReached) {
            return null;
        }
        if (isRoundLimitReached()) {
            return VictoryChecker.findWinnerByLivingUnitsThenHealth(teams);
        }
        return VictoryChecker.findWinner(teams);
    }

    public boolean isNoStateChangeLimitReached() {
        return noStateChangeLimitReached;
    }

    public boolean isGameOver() {
        return VictoryChecker.countLivingTeams(teams) <= 1
                || noStateChangeLimitReached
                || isRoundLimitReached();
    }

    public String getResultText() {
        finishScoringIfNeeded();
        if (noStateChangeLimitReached) {
            return "无状态变化判负：连续 "
                    + noStateChangeRoundLimit
                    + " 回合没有任何状态变化，比赛结束，无胜者。";
        }
        String winner = VictoryChecker.findWinner(teams);
        if (winner == null) {
            if (isRoundLimitReached()) {
                String tiebreakWinner = VictoryChecker.findWinnerByLivingUnitsThenHealth(teams);
                if (tiebreakWinner != null) {
                    return "500 回合结束：按存活数量、总生命值裁决，Winner: " + tiebreakWinner;
                }
                return "500 回合结束：存活数量和总生命值仍然相同，Result: draw.";
            }
            return "Result: no single winner.";
        }
        return "Winner: " + winner;
    }

    public boolean stepRound() {
        if (isGameOver()) {
            return false;
        }

        int round = currentRound;
        boolean acted = false;
        while (!isGameOver() && currentRound == round) {
            boolean didStep = stepTurn();
            if (!didStep) {
                break;
            }
            acted = true;
        }
        return acted;
    }

    public boolean stepTurn() {
        if (isGameOver()) {
            return false;
        }
        ensureStartingPowerUps();

        while (!isGameOver()) {
            while (teamCursor < teams.size()) {
                Team team = teams.get(teamCursor);
                ArrayList<Unit> units = team.getUnits();
                while (unitCursor < units.size()) {
                    Unit unit = units.get(unitCursor);
                    unitCursor++;

                    if (VictoryChecker.countLivingTeams(teams) <= 1) {
                        return false;
                    }
                    if (unit.isAlive()) {
                        takeTurn(unit);
                        if (!isGameOver() && !hasMoreLivingUnitsThisRound()) {
                            advanceRound();
                        }
                        return true;
                    }
                }
                teamCursor++;
                unitCursor = 0;
            }

            advanceRound();
            if (isGameOver()) {
                return false;
            }
        }

        return false;
    }

    public boolean isOpen(Position position) {
        if (!map.isInside(position) || map.isWall(position)) {
            return false;
        }
        return findLivingUnitAt(position) == null;
    }

    public Unit findUnitById(int id) {
        for (int i = 0; i < teams.size(); i++) {
            ArrayList<Unit> units = teams.get(i).getUnits();
            for (int j = 0; j < units.size(); j++) {
                Unit unit = units.get(j);
                if (unit.getId() == id) {
                    return unit;
                }
            }
        }
        return null;
    }

    public void recordInvalidAction(Unit unit) {
        scoreTracker.recordInvalidAction(unit);
        currentTurnScored = true;
    }

    public void recordDamage(Unit attacker, Unit target, int damage) {
        scoreTracker.recordDamage(attacker, target, damage);
        if (damage > 0) {
            currentTurnScored = true;
        }
    }

    public void recordDefeat(Unit attacker) {
        scoreTracker.recordDefeat(attacker);
        currentTurnScored = true;
    }

    public void addStartingPowerUps(int count) {
        for (int i = 0; i < count; i++) {
            spawnPowerUp(false);
        }
        startingPowerUpsCreated = true;
    }

    private void takeTurn(Unit unit) {
        lastActingUnitId = unit.getId();
        lastAnimation = null;
        currentTurnScored = false;
        unit.setDefending(false);
        Position before = unit.getPosition();
        GameState state = createGameState();
        UnitInfo self = unit.toInfo();
        Action action = chooseSafely(unit, state, self);
        Unit targetBeforeAction = findActionTarget(action);
        lastIntentText = describeIntent(unit, action, targetBeforeAction, state, self);
        lastDecisionTraceText = buildDecisionTrace(unit, self, state, action);
        log("Intent: " + lastIntentText);
        boolean actionSucceeded = ActionResolver.resolve(this, unit, action);
        setAnimation(action, before, unit.getPosition(), targetBeforeAction, actionSucceeded);
        applyPowerUp(unit);
        applyHealingPoint(unit);
        boolean moved = !before.equals(unit.getPosition());
        scoreTracker.recordTurnEnd(unit, moved, action, currentTurnScored);
        actionsSincePowerUp++;
        maybeSpawnPowerUp();
    }

    private boolean hasMoreLivingUnitsThisRound() {
        for (int i = teamCursor; i < teams.size(); i++) {
            Team team = teams.get(i);
            ArrayList<Unit> units = team.getUnits();
            int start = 0;
            if (i == teamCursor) {
                start = unitCursor;
            }
            for (int j = start; j < units.size(); j++) {
                if (units.get(j).isAlive()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void advanceRound() {
        scoreTracker.recordRoundSurvival(teams);
        updateNoStateChangeCounter();
        currentRound++;
        teamCursor = 0;
        unitCursor = 0;
    }

    private void updateNoStateChangeCounter() {
        String snapshot = buildStateSnapshot();
        if (previousRoundSnapshot == null) {
            previousRoundSnapshot = snapshot;
            roundsWithoutStateChange = 0;
            return;
        }

        if (previousRoundSnapshot.equals(snapshot)) {
            roundsWithoutStateChange++;
            int remaining = getRoundsUntilNoStateChangeLoss();
            if (roundsWithoutStateChange >= noStateChangeRoundLimit) {
                noStateChangeLimitReached = true;
                log("警告：连续 "
                        + noStateChangeRoundLimit
                        + " 回合没有任何状态变化，触发无进展判负。");
            } else {
                log("警告：连续 "
                        + roundsWithoutStateChange
                        + " 回合没有状态变化，距离无进展判负还剩 "
                        + remaining
                        + " 回合。");
            }
        } else {
            if (roundsWithoutStateChange > 0) {
                log("状态变化已出现，无进展判负倒计时重置。");
            }
            roundsWithoutStateChange = 0;
            previousRoundSnapshot = snapshot;
        }
    }

    private String buildStateSnapshot() {
        String snapshot = "";
        for (int i = 0; i < teams.size(); i++) {
            Team team = teams.get(i);
            ArrayList<Unit> units = team.getUnits();
            for (int j = 0; j < units.size(); j++) {
                Unit unit = units.get(j);
                snapshot = snapshot
                        + unit.getId()
                        + "|"
                        + unit.getTeamName()
                        + "|"
                        + unit.isAlive()
                        + "|"
                        + unit.getHealth()
                        + "|"
                        + unit.getRow()
                        + ","
                        + unit.getCol()
                        + "|"
                        + unit.getAttackPower()
                        + "|"
                        + unit.getRange()
                        + "|"
                        + unit.isDefending()
                        + ";";
            }
        }

        ArrayList<String> powerUpStates = new ArrayList<String>();
        for (int i = 0; i < powerUps.size(); i++) {
            PowerUp powerUp = powerUps.get(i);
            Position position = powerUp.getPosition();
            powerUpStates.add(powerUp.getType()
                    + "@"
                    + position.getRow()
                    + ","
                    + position.getCol());
        }
        Collections.sort(powerUpStates);
        for (int i = 0; i < powerUpStates.size(); i++) {
            snapshot = snapshot + "P|" + powerUpStates.get(i) + ";";
        }
        return snapshot;
    }

    private Action chooseSafely(Unit unit, GameState state, UnitInfo self) {
        StrategyCall call = new StrategyCall(unit, state, self);
        Thread thread = new Thread(call, "strategy-" + unit.getId());
        thread.setDaemon(true);
        thread.start();

        try {
            thread.join(STRATEGY_TIMEOUT_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log(unit.getTeamName() + "#" + unit.getId() + " strategy was interrupted, so it stays.");
            return Action.stay();
        }

        if (thread.isAlive()) {
            stopStrategyThread(thread);
            log(unit.getTeamName()
                    + "#"
                    + unit.getId()
                    + " strategy took more than "
                    + STRATEGY_TIMEOUT_MILLIS
                    + " ms, so it stays this turn.");
            return Action.stay();
        }

        if (call.getFailure() != null) {
            Throwable exception = call.getFailure();
            log(unit.getTeamName()
                    + "#"
                    + unit.getId()
                    + " strategy error: "
                    + exception.getClass().getSimpleName()
                    + ". It stays this turn.");
            return Action.stay();
        }

        Action action = call.getAction();
        if (action == null) {
            log(unit.getTeamName() + "#" + unit.getId() + " returned null, so it stays.");
            return Action.stay();
        }
        return action;
    }

    private void applyHealingPoint(Unit unit) {
        if (unit.isAlive() && map.isHealingPoint(unit.getPosition())) {
            int before = unit.getHealth();
            unit.heal(healingAmount);
            int recovered = unit.getHealth() - before;
            if (recovered > 0) {
                int score = scoreTracker.recordHealing(unit, recovered, unit.getPosition());
                if (score > 0) {
                    currentTurnScored = true;
                }
                log(unit.getTeamName()
                        + "#"
                        + unit.getId()
                        + " recovers "
                        + recovered
                        + " HP on a healing point.");
            }
        }
    }

    private void applyPowerUp(Unit unit) {
        if (!unit.isAlive()) {
            return;
        }
        PowerUp powerUp = findPowerUpAt(unit.getPosition());
        if (powerUp == null) {
            return;
        }

        powerUps.remove(powerUp);
        scoreTracker.recordPowerUp(unit, powerUp);
        currentTurnScored = true;
        if (powerUp.getType() == PowerUpType.HEALTH) {
            int before = unit.getHealth();
            unit.heal(22);
            log(unitLabel(unit) + " picks up Health Pack and recovers " + (unit.getHealth() - before) + " HP.");
        } else if (powerUp.getType() == PowerUpType.MEGA_HEALTH) {
            int before = unit.getHealth();
            unit.heal(45);
            log(unitLabel(unit) + " picks up Mega Health and recovers " + (unit.getHealth() - before) + " HP.");
        } else if (powerUp.getType() == PowerUpType.ATTACK) {
            unit.addAttackBonus(2);
            log(unitLabel(unit) + " picks up Attack Gem. Attack is now " + unit.getAttackPower() + ".");
        } else if (powerUp.getType() == PowerUpType.RANGE) {
            unit.addRangeBonus(1);
            log(unitLabel(unit) + " picks up Range Lens. Range is now " + unit.getRange() + ".");
        } else if (powerUp.getType() == PowerUpType.POWER_CORE) {
            unit.addAttackBonus(4);
            log(unitLabel(unit) + " picks up Power Core. Attack is now " + unit.getAttackPower() + ".");
        } else {
            int before = unit.getHealth();
            unit.heal(25);
            unit.addAttackBonus(3);
            unit.addRangeBonus(1);
            log(unitLabel(unit)
                    + " picks up Battle Core: +"
                    + (unit.getHealth() - before)
                    + " HP, attack "
                    + unit.getAttackPower()
                    + ", range "
                    + unit.getRange()
                    + ".");
        }
        lastAnimation = new TurnAnimation("POWER", unit.getPosition(), unit.getPosition());
    }

    private void maybeSpawnPowerUp() {
        if (powerUps.size() >= 5) {
            return;
        }
        if (actionsSincePowerUp < 16) {
            return;
        }
        if (random.nextInt(100) < 10) {
            spawnPowerUp(true);
            actionsSincePowerUp = random.nextInt(6);
        }
    }

    private void ensureStartingPowerUps() {
        if (!startingPowerUpsCreated) {
            addStartingPowerUps(3);
        }
        if (previousRoundSnapshot == null) {
            previousRoundSnapshot = buildStateSnapshot();
        }
    }

    private void spawnPowerUp(boolean announce) {
        PowerUpType type = randomPowerUpType();
        for (int attempt = 0; attempt < 120; attempt++) {
            int row = random.nextInt(map.getRows());
            int col = random.nextInt(map.getCols());
            Position position = new Position(row, col);
            if (canPlacePowerUp(position, type)) {
                PowerUp powerUp = new PowerUp(type, position);
                powerUps.add(powerUp);
                if (announce) {
                    log("Power-up spawned: " + powerUp.getDisplayName() + " at " + position + ".");
                }
                return;
            }
        }
    }

    private PowerUpType randomPowerUpType() {
        int value = random.nextInt(1000);
        if (value < 450) {
            return PowerUpType.HEALTH;
        }
        if (value < 770) {
            return PowerUpType.ATTACK;
        }
        if (value < 970) {
            return PowerUpType.RANGE;
        }
        if (value < 985) {
            return PowerUpType.MEGA_HEALTH;
        }
        if (value < 995) {
            return PowerUpType.POWER_CORE;
        }
        return PowerUpType.BATTLE_CORE;
    }

    private boolean canPlacePowerUp(Position position, PowerUpType type) {
        if (!map.isInside(position) || map.isWall(position) || map.isHealingPoint(position)) {
            return false;
        }
        if (findLivingUnitAt(position) != null) {
            return false;
        }
        if (findPowerUpAt(position) != null) {
            return false;
        }
        return isFarEnoughFromLivingUnits(position, type);
    }

    private boolean isFarEnoughFromLivingUnits(Position position, PowerUpType type) {
        int minimumDistance = 3;
        if (isStrongPowerUp(type)) {
            minimumDistance = 4;
        }

        for (int i = 0; i < teams.size(); i++) {
            ArrayList<Unit> units = teams.get(i).getUnits();
            for (int j = 0; j < units.size(); j++) {
                Unit unit = units.get(j);
                if (unit.isAlive() && unit.getPosition().distanceTo(position) < minimumDistance) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isStrongPowerUp(PowerUpType type) {
        return type == PowerUpType.MEGA_HEALTH
                || type == PowerUpType.POWER_CORE
                || type == PowerUpType.BATTLE_CORE;
    }

    private PowerUp findPowerUpAt(Position position) {
        for (int i = 0; i < powerUps.size(); i++) {
            PowerUp powerUp = powerUps.get(i);
            if (powerUp.getPosition().equals(position)) {
                return powerUp;
            }
        }
        return null;
    }

    private GameState createGameState() {
        ArrayList<UnitInfo> infos = new ArrayList<UnitInfo>();
        for (int i = 0; i < teams.size(); i++) {
            ArrayList<Unit> units = teams.get(i).getUnits();
            for (int j = 0; j < units.size(); j++) {
                infos.add(units.get(j).toInfo());
            }
        }
        ArrayList<Position> powerUpPositions = new ArrayList<Position>();
        for (int i = 0; i < powerUps.size(); i++) {
            powerUpPositions.add(powerUps.get(i).getPosition());
        }
        return new GameState(map.copyTiles(), infos, powerUpPositions, powerUps);
    }

    private void finishScoringIfNeeded() {
        if (!scoreFinalized && isGameOver()) {
            scoreTracker.recordFinalBonuses(teams, getWinnerName());
            scoreFinalized = true;
        }
    }

    private boolean isRoundLimitReached() {
        return hasRoundLimit() && currentRound > maxRounds;
    }

    private Unit findActionTarget(Action action) {
        if (action == null || action.getType() != ActionType.ATTACK) {
            return null;
        }
        return findUnitById(action.getTargetId());
    }

    private String describeIntent(Unit unit, Action action, Unit target, GameState state, UnitInfo self) {
        String actor = unitLabel(unit);
        if (action == null) {
            return actor + " will stay because no valid action was returned.";
        }
        if (action.getType() == ActionType.MOVE) {
            Position next = unit.getPosition().move(action.getDirection());
            PowerUp powerUp = findPowerUpAt(next);
            if (powerUp != null) {
                return actor + " moves " + action.getDirection() + " and will step onto " + powerUp.getDisplayName() + ".";
            }
            Position healingPoint = state.findNearestHealingPoint(self);
            if (self.getHealth() < 45
                    && healingPoint != null
                    && next.distanceTo(healingPoint) < unit.getPosition().distanceTo(healingPoint)) {
                return actor + " is low on HP and moves " + action.getDirection() + " toward healing.";
            }
            Position nearestPowerUp = state.findNearestPowerUp(self);
            if (nearestPowerUp != null
                    && next.distanceTo(nearestPowerUp) < unit.getPosition().distanceTo(nearestPowerUp)) {
                return actor + " moves " + action.getDirection() + " to contest a nearby power-up.";
            }
            UnitInfo weakest = state.findWeakestEnemy(self);
            if (weakest != null && next.distanceTo(weakest.getPosition()) < self.distanceTo(weakest)) {
                return actor + " moves " + action.getDirection() + " to pressure the weakest enemy.";
            }
            UnitInfo nearest = state.findNearestEnemy(self);
            if (nearest != null && next.distanceTo(nearest.getPosition()) < self.distanceTo(nearest)) {
                return actor + " moves " + action.getDirection() + " to close distance safely.";
            }
            return actor + " moves " + action.getDirection() + " to search for a better route.";
        }
        if (action.getType() == ActionType.ATTACK) {
            if (target == null) {
                return actor + " wants to attack, but the target id is invalid.";
            }
            if (target.getHealth() <= unit.getAttackPower()) {
                return actor + " wants to finish off " + unitLabel(target) + ".";
            }
            return actor + " wants to attack " + unitLabel(target) + " before it can recover.";
        }
        if (action.getType() == ActionType.DEFEND) {
            return actor + " wants to defend and reduce the next hit.";
        }
        return actor + " chooses to stay and wait for a better chance.";
    }

    private String buildDecisionTrace(Unit unit, UnitInfo self, GameState state, Action action) {
        String text = "本步行动\n"
                + "- 当前战士: "
                + unitLabel(unit)
                + "\n"
                + "- 位置: "
                + unit.getPosition()
                + "\n"
                + "- 返回行动: "
                + formatAction(action)
                + "\n\n";

        text = text
                + "自动观测\n"
                + "- self.getHealth() = "
                + self.getHealth()
                + " / "
                + self.getMaxHealth()
                + "\n"
                + "- self.getAttackPower() = "
                + self.getAttackPower()
                + "\n"
                + "- self.getRange() = "
                + self.getRange()
                + "\n"
                + "- state.findNearestEnemy(self) = "
                + formatUnitInfo(state.findNearestEnemy(self))
                + "\n"
                + "- state.findLowestHealthEnemyInRange(self) = "
                + formatUnitInfo(state.findLowestHealthEnemyInRange(self))
                + "\n"
                + "- state.findNearestPowerUp(self) = "
                + String.valueOf(state.findNearestPowerUp(self))
                + "\n\n";

        text = text + "场上生命值\n";
        ArrayList<UnitInfo> livingUnits = state.getLivingUnits();
        for (int i = 0; i < livingUnits.size(); i++) {
            UnitInfo info = livingUnits.get(i);
            text = text
                    + "- "
                    + info.getTeamName()
                    + "#"
                    + info.getId()
                    + " "
                    + info.getName()
                    + ": HP "
                    + info.getHealth()
                    + "/"
                    + info.getMaxHealth()
                    + ", ATK "
                    + info.getAttackPower()
                    + ", RNG "
                    + info.getRange()
                    + ", POS "
                    + info.getPosition()
                    + "\n";
        }

        text = text + "\n代码追踪\n";
        ArrayList<String> traceLines = state.getTraceLines();
        if (traceLines.size() == 0) {
            text = text
                    + "- 本步没有手动 trace。\n"
                    + "- 可以在 chooseAction 中加入 state.traceValue(\"self.getHealth()\", self.getHealth());\n";
        } else {
            for (int i = 0; i < traceLines.size(); i++) {
                text = text + "- " + traceLines.get(i) + "\n";
            }
        }

        text = text + "\n策略意图\n- " + lastIntentText;
        return text;
    }

    private String formatUnitInfo(UnitInfo info) {
        if (info == null) {
            return "null";
        }
        return info.getTeamName()
                + "#"
                + info.getId()
                + " "
                + info.getName()
                + " HP "
                + info.getHealth()
                + "/"
                + info.getMaxHealth()
                + " ATK "
                + info.getAttackPower()
                + " RNG "
                + info.getRange()
                + " POS "
                + info.getPosition();
    }

    private String formatAction(Action action) {
        if (action == null) {
            return "null";
        }
        if (action.getType() == ActionType.MOVE) {
            return "Action.move(" + action.getDirection() + ")";
        }
        if (action.getType() == ActionType.ATTACK) {
            return "Action.attack(" + action.getTargetId() + ")";
        }
        if (action.getType() == ActionType.DEFEND) {
            return "Action.defend()";
        }
        return "Action.stay()";
    }

    private void setAnimation(
            Action action,
            Position before,
            Position after,
            Unit targetBeforeAction,
            boolean actionSucceeded) {
        if (action == null) {
            return;
        }
        if (!actionSucceeded) {
            lastAnimation = new TurnAnimation("STAY", before, before);
        } else if (action.getType() == ActionType.MOVE) {
            lastAnimation = new TurnAnimation("MOVE", before, after);
        } else if (action.getType() == ActionType.ATTACK && targetBeforeAction != null) {
            lastAnimation = new TurnAnimation("ATTACK", before, targetBeforeAction.getPosition());
        } else if (action.getType() == ActionType.DEFEND) {
            lastAnimation = new TurnAnimation("DEFEND", before, before);
        } else {
            lastAnimation = new TurnAnimation("STAY", before, before);
        }
    }

    private void stopStrategyThread(Thread thread) {
        thread.interrupt();
    }

    private String unitLabel(Unit unit) {
        return unit.getTeamName() + "#" + unit.getId() + " " + unit.getName();
    }

    private Unit findLivingUnitAt(Position position) {
        for (int i = 0; i < teams.size(); i++) {
            ArrayList<Unit> units = teams.get(i).getUnits();
            for (int j = 0; j < units.size(); j++) {
                Unit unit = units.get(j);
                if (unit.isAlive() && unit.getPosition().equals(position)) {
                    return unit;
                }
            }
        }
        return null;
    }

    private static class StrategyCall implements Runnable {
        private final Unit unit;
        private final GameState state;
        private final UnitInfo self;
        private Action action;
        private Throwable failure;

        public StrategyCall(Unit unit, GameState state, UnitInfo self) {
            this.unit = unit;
            this.state = state;
            this.self = self;
            this.action = null;
            this.failure = null;
        }

        @Override
        public void run() {
            try {
                action = unit.getWarrior().chooseAction(state, self);
            } catch (Throwable throwable) {
                failure = throwable;
            }
        }

        public Action getAction() {
            return action;
        }

        public Throwable getFailure() {
            return failure;
        }
    }
}
