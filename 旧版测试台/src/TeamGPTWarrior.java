import java.util.ArrayList;
import java.util.Comparator;
import java.util.PriorityQueue;

public class TeamGPTWarrior extends Warrior {
    private static final boolean DEBUG = false;
    private static final int INF = 1000000000;
    private static final int BASE_STEP_COST = 10;
    private static final double EPS = 1.0e-9;

    private Position lastMoveOrigin = null;
    private Position lastSeenPosition = null;
    private String lastDecision = "";
    private int stationaryTurns = 0;
    private int reverseMoveStreak = 0;

    public TeamGPTWarrior() {
        super("Hanson team");
    }

    @Override
    public Action chooseAction(GameState state, UnitInfo self) {
        if (state == null || self == null || !self.isAlive()) {
            return Action.defend();
        }

        updateMemoryAtTurnStart(self);

        ArrayList<Candidate> choices = new ArrayList<Candidate>();
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        UnitInfo teamTarget = selectTeamTarget(state, self);
        double survivalUrgency = survivalUrgency(state, self, self.getPosition());

        addAttackChoices(choices, state, self, teamTarget);
        addEmergencyRetreatChoices(choices, state, self);
        addHealChoices(choices, state, self);
        addPowerUpChoices(choices, state, self, survivalUrgency);
        addTeamFightChoice(choices, state, self, teamTarget, survivalUrgency);
        addRallyChoice(choices, state, self, survivalUrgency);
        addChaseChoice(choices, state, self, teamTarget, survivalUrgency);
        addCenterControlChoice(choices, state, self, survivalUrgency);
        addDefendChoice(choices, state, self);

        Candidate best = pickBest(choices, state, self);
        if (best == null || !isLegalAction(state, self, best.action)) {
            best = new Candidate("Fallback", 0.0, "safe", fallbackAction(state, self), null);
        }

        rememberDecision(self, best);
        if (DEBUG) {
            printChoices(self, choices, best);
        }
        return best.action;
    }

    private void addAttackChoices(ArrayList<Candidate> choices, GameState state, UnitInfo self, UnitInfo teamTarget) {
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        double urgency = survivalUrgency(state, self, self.getPosition());
        double currentIncoming = expectedIncomingDamage(state, self, self.getPosition());
        double outnumber = localOutnumberRisk(state, self, self.getPosition());

        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo enemy = enemies.get(i);
            if (self.distanceTo(enemy) > self.getRange()) {
                continue;
            }

            int damage = effectiveDamage(self, enemy);
            boolean kill = damage >= enemy.getHealth();
            double targetValue = targetValue(state, self, enemy, teamTarget);
            double focused = teamTarget != null && enemy.getId() == teamTarget.getId() ? 1.0 : 0.0;
            double retaliationDrop = 0.0;
            if (kill && self.getPosition().distanceTo(enemy.getPosition()) <= enemy.getRange()) {
                retaliationDrop = clamp01(enemy.getAttackPower() / Math.max(1.0, currentIncoming));
            }

            double score;
            if (kill) {
                score = 1.08
                        + 0.16 * targetValue
                        + 0.10 * focused
                        + 0.08 * retaliationDrop
                        - 0.08 * outnumber;
            } else {
                double enemyCanHitMe = self.getPosition().distanceTo(enemy.getPosition()) <= enemy.getRange() ? 1.0 : 0.0;
                score = 0.50
                        + 0.24 * targetValue
                        + 0.09 * focused
                        + 0.06 * enemyCanHitMe
                        - 0.22 * urgency
                        - 0.10 * outnumber;
            }

            if (currentIncoming >= self.getHealth() && !kill) {
                score -= 0.20;
            }
            choices.add(new Candidate("Attack", clamp(score, 0.0, 1.35), "enemy#" + enemy.getId(), Action.attack(enemy.getId()), null));
        }
    }

    private void addEmergencyRetreatChoices(ArrayList<Candidate> choices, GameState state, UnitInfo self) {
        Position here = self.getPosition();
        double currentDanger = rawTileDanger(state, self, here);
        double currentIncoming = expectedIncomingDamage(state, self, here);
        double urgency = survivalUrgency(state, self, here);
        Direction[] directions = Direction.values();

        for (int i = 0; i < directions.length; i++) {
            Direction direction = directions[i];
            Position next = here.move(direction);
            if (!state.isOpen(next)) {
                continue;
            }

            double nextDanger = rawTileDanger(state, self, next);
            double nextIncoming = expectedIncomingDamage(state, self, next);
            double dangerImprovement = clamp01((currentDanger - nextDanger) / 1.20);
            double damageImprovement = clamp01((currentIncoming - nextIncoming) / 24.0);
            double safety = 1.0 - clamp01(nextDanger / 1.60);
            double teamPenalty = movesAwayFromHealthyTeammate(state, self, next) ? 0.05 : 0.0;

            boolean useful = nextDanger + 0.05 < currentDanger
                    || nextIncoming + 1.0 < currentIncoming
                    || currentIncoming >= self.getHealth() * 0.65;
            if (!useful) {
                continue;
            }

            double score = 0.16
                    + 0.50 * urgency
                    + 0.22 * dangerImprovement
                    + 0.20 * damageImprovement
                    + 0.08 * safety
                    - teamPenalty;
            choices.add(new Candidate("Retreat", clamp(score, 0.0, 1.20), next.toString(), Action.move(direction), next));
        }
    }

    private void addHealChoices(ArrayList<Candidate> choices, GameState state, UnitInfo self) {
        double missing = missingHp(self);
        double urgency = survivalUrgency(state, self, self.getPosition());
        Position here = self.getPosition();
        boolean onHealing = state.isHealingPoint(here);

        if (onHealing && missing > 0.01) {
            double incoming = expectedIncomingDamage(state, self, here);
            double defended = expectedIncomingDamageIfDefending(state, self, here);
            double defendGain = clamp01((incoming - defended) / 18.0);
            double score = 0.33
                    + 0.52 * urgency
                    + 0.28 * missing
                    + 0.12 * defendGain;
            choices.add(new Candidate("HealHold", clamp(score, 0.0, 1.25), here.toString(), Action.defend(), null));
            return;
        }

        if (missing < 0.12 && urgency < 0.34) {
            return;
        }

        ArrayList<Position> healingPoints = findHealingPoints(state);
        for (int i = 0; i < healingPoints.size(); i++) {
            Position hp = healingPoints.get(i);
            if (!hp.equals(here) && state.isOccupied(hp)) {
                continue;
            }
            PathResult path = findPath(state, self, hp);
            if (!path.reachable || path.firstStep == null) {
                continue;
            }
            if (path.steps > 14 && urgency < 0.65) {
                continue;
            }

            double targetDanger = rawTileDanger(state, self, hp);
            double targetSafety = 1.0 - clamp01(targetDanger / 1.60);
            double distancePenalty = clamp01(path.steps / 12.0);
            double pathRisk = path.risk;
            double score = 0.10
                    + 0.62 * urgency
                    + 0.25 * missing
                    + 0.12 * targetSafety
                    - 0.22 * distancePenalty
                    - 0.14 * pathRisk;

            if (expectedIncomingDamage(state, self, here) >= self.getHealth()) {
                score += 0.10;
            }
            choices.add(new Candidate("Heal", clamp(score, 0.0, 1.18), hp.toString(), Action.move(path.firstStep), here.move(path.firstStep)));
        }
    }

    private void addPowerUpChoices(ArrayList<Candidate> choices, GameState state, UnitInfo self, double survivalUrgency) {
        ArrayList<Position> powerUps = state.getPowerUpPositions();
        if (powerUps.isEmpty()) {
            return;
        }

        for (int i = 0; i < powerUps.size(); i++) {
            Position power = powerUps.get(i);
            if (!power.equals(self.getPosition()) && state.isOccupied(power)) {
                continue;
            }
            PathResult path = findPath(state, self, power);
            if (!path.reachable || path.firstStep == null) {
                continue;
            }
            if (path.steps > 13 && survivalUrgency < 0.45) {
                continue;
            }

            int enemyDistance = nearestEnemyDistanceToPosition(state, self, power);
            double contest;
            if (path.steps + 1 < enemyDistance) {
                contest = 1.0;
            } else if (path.steps <= enemyDistance) {
                contest = 0.65;
            } else if (path.steps <= enemyDistance + 1) {
                contest = 0.35;
            } else {
                contest = 0.0;
            }

            double targetDanger = rawTileDanger(state, self, power);
            double targetSafety = 1.0 - clamp01(targetDanger / 1.60);
            double distanceFactor = 1.0 - clamp01((path.steps - 1) / 12.0);
            double resourceNeed = 0.48 * (1.0 - powerProgress(self))
                    + 0.30 * missingHp(self)
                    + 0.22 * rangeNeed(self);
            double adjacentBonus = path.steps == 1 ? 0.18 : 0.0;

            double score = 0.13
                    + 0.34 * resourceNeed
                    + 0.20 * distanceFactor
                    + 0.14 * contest
                    + 0.12 * targetSafety
                    + adjacentBonus
                    - 0.17 * path.risk
                    - 0.22 * survivalUrgency
                    - (finishMode(state, self) && path.steps > 1 ? 0.18 : 0.0);

            // Unknown power-ups are allowed to win under moderate risk when they are very close.
            if (survivalUrgency > 0.70 && path.steps > 2) {
                score -= 0.20;
            }
            choices.add(new Candidate("PowerUp", clamp(score, 0.0, 1.05), power.toString(), Action.move(path.firstStep), self.getPosition().move(path.firstStep)));
        }
    }

    private void addTeamFightChoice(ArrayList<Candidate> choices, GameState state, UnitInfo self, UnitInfo teamTarget, double survivalUrgency) {
        if (teamTarget == null || survivalUrgency > 0.68) {
            return;
        }
        if (self.distanceTo(teamTarget) <= self.getRange()) {
            return;
        }

        PathResult path = findPathToAttackSquare(state, self, teamTarget);
        if (!path.reachable || path.firstStep == null) {
            return;
        }

        double readiness = teamReadiness(state, self, teamTarget);
        double value = targetValue(state, self, teamTarget, teamTarget);
        double distanceFactor = 1.0 - clamp01((path.steps - 1) / 12.0);
        Position next = self.getPosition().move(path.firstStep);
        double nextOutnumber = localOutnumberRisk(state, self, next);

        double score = 0.22
                + 0.42 * value
                + 0.16 * readiness
                + 0.14 * distanceFactor
                - 0.16 * path.risk
                - 0.14 * survivalUrgency
                - 0.10 * nextOutnumber
                + (finishMode(state, self) ? 0.10 : 0.0);

        choices.add(new Candidate("TeamFight", clamp(score, 0.0, 1.05), "enemy#" + teamTarget.getId(), Action.move(path.firstStep), next));
    }

    private void addRallyChoice(ArrayList<Candidate> choices, GameState state, UnitInfo self, double survivalUrgency) {
        ArrayList<UnitInfo> mates = state.getLivingTeammates(self);
        if (mates.isEmpty() || survivalUrgency > 0.62) {
            return;
        }
        UnitInfo mate = mates.get(0);
        int dist = self.distanceTo(mate);
        if (dist <= 3) {
            return;
        }

        Position target = bestOpenSquareNear(state, self, mate.getPosition(), 2);
        if (target == null) {
            return;
        }
        PathResult path = findPath(state, self, target);
        if (!path.reachable || path.firstStep == null) {
            return;
        }

        double separation = clamp01((dist - 3) / 7.0);
        double score = 0.18
                + 0.32 * separation
                - 0.12 * path.risk
                - 0.08 * survivalUrgency;
        choices.add(new Candidate("Rally", clamp(score, 0.0, 0.72), mate.getPosition().toString(), Action.move(path.firstStep), self.getPosition().move(path.firstStep)));
    }

    private void addChaseChoice(ArrayList<Candidate> choices, GameState state, UnitInfo self, UnitInfo teamTarget, double survivalUrgency) {
        if (survivalUrgency > 0.58) {
            return;
        }
        UnitInfo target = bestIndividualTarget(state, self, teamTarget);
        if (target == null || self.distanceTo(target) <= self.getRange()) {
            return;
        }

        PathResult path = findPathToAttackSquare(state, self, target);
        if (!path.reachable || path.firstStep == null) {
            return;
        }
        if (path.steps > 15) {
            return;
        }

        double value = targetValue(state, self, target, teamTarget);
        double distanceFactor = 1.0 - clamp01((path.steps - 1) / 14.0);
        Position next = self.getPosition().move(path.firstStep);
        double score = 0.14
                + 0.42 * value
                + 0.16 * distanceFactor
                - 0.14 * path.risk
                - 0.10 * localOutnumberRisk(state, self, next)
                - 0.10 * survivalUrgency
                + (finishMode(state, self) ? 0.14 : 0.0);
        choices.add(new Candidate("Chase", clamp(score, 0.0, 0.88), "enemy#" + target.getId(), Action.move(path.firstStep), next));
    }

    private void addCenterControlChoice(ArrayList<Candidate> choices, GameState state, UnitInfo self, double survivalUrgency) {
        if (survivalUrgency > 0.50 || finishMode(state, self)) {
            return;
        }
        Position target = bestStrategicCenter(state, self);
        if (target == null || target.equals(self.getPosition())) {
            return;
        }
        PathResult path = findPath(state, self, target);
        if (!path.reachable || path.firstStep == null) {
            return;
        }
        double score = 0.08
                + 0.08 * (1.0 - clamp01(path.steps / 10.0))
                - 0.08 * path.risk;
        if (stationaryTurns >= 2) {
            score += 0.12;
        }
        choices.add(new Candidate("Center", clamp(score, 0.0, 0.36), target.toString(), Action.move(path.firstStep), self.getPosition().move(path.firstStep)));
    }

    private void addDefendChoice(ArrayList<Candidate> choices, GameState state, UnitInfo self) {
        Position here = self.getPosition();
        double incoming = expectedIncomingDamage(state, self, here);
        double defended = expectedIncomingDamageIfDefending(state, self, here);
        double urgency = survivalUrgency(state, self, here);
        double defendGain = clamp01((incoming - defended) / 18.0);
        double healHold = state.isHealingPoint(here) && missingHp(self) > 0.01 ? 1.0 : 0.0;

        double score = 0.03
                + 0.42 * urgency
                + 0.18 * defendGain
                + 0.18 * healHold;

        if (incoming <= 0.1 && healHold == 0.0) {
            score = 0.03;
        }
        choices.add(new Candidate("Defend", clamp(score, 0.0, 0.95), "self", Action.defend(), null));
    }

    private Candidate pickBest(ArrayList<Candidate> choices, GameState state, UnitInfo self) {
        Candidate best = null;
        double bestScore = -9999.0;
        for (int i = 0; i < choices.size(); i++) {
            Candidate c = choices.get(i);
            if (c == null || c.action == null || !isLegalAction(state, self, c.action)) {
                continue;
            }
            double adjusted = c.score;
            if (c.nextPosition != null
                    && lastMoveOrigin != null
                    && c.nextPosition.equals(lastMoveOrigin)
                    && c.score < 1.00) {
                adjusted -= 0.18 + Math.min(0.20, 0.05 * reverseMoveStreak);
            }
            if (stationaryTurns >= 3 && c.action.getType() == ActionType.DEFEND && c.score < 0.75) {
                adjusted -= 0.15;
            }

            if (best == null
                    || adjusted > bestScore + EPS
                    || (Math.abs(adjusted - bestScore) <= EPS && c.tieBreakKey() < best.tieBreakKey())) {
                best = c;
                bestScore = adjusted;
            }
        }
        return best;
    }

    private UnitInfo selectTeamTarget(GameState state, UnitInfo self) {
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        if (enemies.isEmpty()) {
            return null;
        }

        ArrayList<UnitInfo> team = healthyTeamMembers(state, self);
        if (team.isEmpty()) {
            team.add(self);
        }

        int teamDamage = 0;
        double centerRow = 0.0;
        double centerCol = 0.0;
        for (int i = 0; i < team.size(); i++) {
            UnitInfo u = team.get(i);
            teamDamage += u.getAttackPower();
            centerRow += u.getRow();
            centerCol += u.getCol();
        }
        centerRow /= team.size();
        centerCol /= team.size();
        Position center = new Position((int)Math.round(centerRow), (int)Math.round(centerCol));

        UnitInfo best = null;
        double bestScore = -9999.0;
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo enemy = enemies.get(i);
            int turns = (int)Math.ceil(enemy.getHealth() / Math.max(1.0, teamDamage));
            double killWindow = 1.0 - clamp01((turns - 1) / 5.0);
            double avgDistToRange = averageDistanceToAttackRange(team, enemy);
            double proximity = 1.0 - clamp01(avgDistToRange / 13.0);
            double lowHp = 1.0 - hpRatio(enemy);
            double elim = enemyTeamAliveCount(state, enemy) == 1 ? 1.0 : 0.0;
            double isolation = enemyIsolation(state, enemy);
            double danger = unitDanger(enemy);
            double crowdPenalty = enemyCrowdPenalty(state, self, enemy.getPosition());

            double score = 0.23 * elim
                    + 0.22 * killWindow
                    + 0.18 * proximity
                    + 0.15 * lowHp
                    + 0.10 * isolation
                    + 0.08 * danger
                    - 0.10 * crowdPenalty;

            // Avoid making the whole team walk across the map for a full-health target early.
            if (avgDistToRange > 10 && lowHp < 0.30 && elim == 0.0) {
                score -= 0.10;
            }

            if (best == null
                    || score > bestScore + EPS
                    || (Math.abs(score - bestScore) <= EPS && enemy.getId() < best.getId())) {
                best = enemy;
                bestScore = score;
            }
        }
        return best;
    }

    private UnitInfo bestIndividualTarget(GameState state, UnitInfo self, UnitInfo teamTarget) {
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        UnitInfo best = null;
        double bestValue = -9999.0;
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo enemy = enemies.get(i);
            double v = targetValue(state, self, enemy, teamTarget);
            if (best == null
                    || v > bestValue + EPS
                    || (Math.abs(v - bestValue) <= EPS && enemy.getId() < best.getId())) {
                best = enemy;
                bestValue = v;
            }
        }
        return best;
    }

    private double targetValue(GameState state, UnitInfo self, UnitInfo enemy, UnitInfo teamTarget) {
        int damage = effectiveDamage(self, enemy);
        double kill = damage >= enemy.getHealth() ? 1.0 : 0.0;
        double lowHp = 1.0 - hpRatio(enemy);
        double proximity = 1.0 - clamp01(Math.max(0, self.distanceTo(enemy) - self.getRange()) / 13.0);
        double focused = teamTarget != null && enemy.getId() == teamTarget.getId() ? 1.0 : 0.0;
        double elim = enemyTeamAliveCount(state, enemy) == 1 ? 1.0 : 0.0;
        double focusPotential = teammateFocusPotential(state, self, enemy);
        double isolated = enemyIsolation(state, enemy);

        return clamp01(0.20 * kill
                + 0.18 * elim
                + 0.17 * lowHp
                + 0.15 * proximity
                + 0.13 * focusPotential
                + 0.09 * focused
                + 0.05 * unitDanger(enemy)
                + 0.03 * isolated);
    }

    private PathResult findPathToAttackSquare(GameState state, UnitInfo self, UnitInfo target) {
        Position enemyPos = target.getPosition();
        PathResult best = PathResult.unreachable();
        int range = self.getRange();
        for (int row = enemyPos.getRow() - range; row <= enemyPos.getRow() + range; row++) {
            for (int col = enemyPos.getCol() - range; col <= enemyPos.getCol() + range; col++) {
                Position p = new Position(row, col);
                if (!state.isInside(p) || state.isWall(p)) {
                    continue;
                }
                if (p.distanceTo(enemyPos) > range) {
                    continue;
                }
                if (!p.equals(self.getPosition()) && state.isOccupied(p)) {
                    continue;
                }
                PathResult path = findPath(state, self, p);
                if (!path.reachable) {
                    continue;
                }

                double goalDanger = rawTileDanger(state, self, p);
                double rangeKiteBonus = self.getRange() > target.getRange()
                        ? 2.0 * Math.min(self.getRange(), p.distanceTo(enemyPos))
                        : 0.0;
                double adjustedCost = path.cost + 8.0 * goalDanger - rangeKiteBonus;
                if (!best.reachable || adjustedCost < best.adjustedCost - EPS) {
                    best = path.withAdjustedCost(adjustedCost);
                }
            }
        }
        return best;
    }

    private PathResult findPath(GameState state, UnitInfo self, Position goal) {
        Position start = self.getPosition();
        if (goal == null || !state.isInside(goal) || state.isWall(goal)) {
            return PathResult.unreachable();
        }
        if (!goal.equals(start) && state.isOccupied(goal)) {
            return PathResult.unreachable();
        }
        if (start.equals(goal)) {
            return new PathResult(true, 0, 0, null, 0.0, 0.0);
        }

        int rows = state.getRows();
        int cols = state.getCols();
        int[][] bestCost = new int[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                bestCost[r][c] = INF;
            }
        }

        PriorityQueue<PathNode> pq = new PriorityQueue<PathNode>(new Comparator<PathNode>() {
            @Override
            public int compare(PathNode a, PathNode b) {
                if (a.fCost != b.fCost) {
                    return a.fCost - b.fCost;
                }
                return a.hCost - b.hCost;
            }
        });

        PathNode startNode = new PathNode(start, 0, start.distanceTo(goal) * BASE_STEP_COST, null, null);
        bestCost[start.getRow()][start.getCol()] = 0;
        pq.add(startNode);

        while (!pq.isEmpty()) {
            PathNode cur = pq.poll();
            if (cur.gCost != bestCost[cur.position.getRow()][cur.position.getCol()]) {
                continue;
            }
            if (cur.position.equals(goal)) {
                return buildPathResult(state, self, cur);
            }
            Direction[] dirs = Direction.values();
            for (int i = 0; i < dirs.length; i++) {
                Direction dir = dirs[i];
                Position next = cur.position.move(dir);
                if (!state.isInside(next) || state.isWall(next)) {
                    continue;
                }
                if (!next.equals(goal) && !next.equals(start) && state.isOccupied(next)) {
                    continue;
                }
                if (next.equals(goal) && !goal.equals(start) && state.isOccupied(goal)) {
                    continue;
                }
                int stepCost = tileStepCost(state, self, next);
                int newCost = cur.gCost + stepCost;
                if (newCost >= bestCost[next.getRow()][next.getCol()]) {
                    continue;
                }
                bestCost[next.getRow()][next.getCol()] = newCost;
                int h = next.distanceTo(goal) * BASE_STEP_COST;
                pq.add(new PathNode(next, newCost, h, cur, dir));
            }
        }
        return PathResult.unreachable();
    }

    private PathResult buildPathResult(GameState state, UnitInfo self, PathNode end) {
        int steps = 0;
        double dangerSum = 0.0;
        PathNode cur = end;
        Direction first = null;
        while (cur.parent != null) {
            steps++;
            dangerSum += Math.min(1.6, rawTileDanger(state, self, cur.position));
            first = cur.directionFromParent;
            cur = cur.parent;
        }
        double risk = steps <= 0 ? 0.0 : clamp01(dangerSum / (steps * 1.20));
        return new PathResult(true, end.gCost, steps, first, risk, end.gCost);
    }

    private int tileStepCost(GameState state, UnitInfo self, Position p) {
        double danger = Math.min(1.6, rawTileDanger(state, self, p));
        int cost = BASE_STEP_COST + (int)Math.round(18.0 * danger);
        if (state.isHealingPoint(p) && missingHp(self) > 0.28) {
            cost -= 2;
        }
        if (containsPosition(state.getPowerUpPositions(), p)) {
            cost -= 2;
        }
        return Math.max(1, cost);
    }

    private double rawTileDanger(GameState state, UnitInfo self, Position p) {
        double danger = 0.0;
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo e = enemies.get(i);
            int d = p.distanceTo(e.getPosition());
            double ud = unitDanger(e);
            double focus = enemyFocusWeight(state, self, e, p);
            double c = 0.0;
            if (d <= e.getRange()) {
                c = 0.62 + 0.42 * ud;
                if (e.getAttackPower() >= self.getHealth()) {
                    c += 0.28;
                }
            } else if (d == e.getRange() + 1) {
                c = 0.30 * ud;
            } else if (d <= e.getRange() + 3) {
                c = 0.10 * ud / Math.max(1, d - e.getRange());
            }
            danger += c * focus;
        }
        return danger;
    }

    private double expectedIncomingDamage(GameState state, UnitInfo self, Position p) {
        double damage = 0.0;
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo e = enemies.get(i);
            if (p.distanceTo(e.getPosition()) <= e.getRange()) {
                damage += e.getAttackPower() * enemyFocusWeight(state, self, e, p);
            }
        }
        return damage;
    }

    private double expectedIncomingDamageIfDefending(GameState state, UnitInfo self, Position p) {
        double total = 0.0;
        double bestReduction = 0.0;
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo e = enemies.get(i);
            if (p.distanceTo(e.getPosition()) <= e.getRange()) {
                double w = enemyFocusWeight(state, self, e, p);
                double normal = e.getAttackPower() * w;
                double defended = Math.max(1, e.getAttackPower() / 2) * w;
                total += normal;
                bestReduction = Math.max(bestReduction, normal - defended);
            }
        }
        return Math.max(0.0, total - bestReduction);
    }

    private double enemyFocusWeight(GameState state, UnitInfo self, UnitInfo enemy, Position myPosition) {
        int myDistance = enemy.getPosition().distanceTo(myPosition);
        int closer = 0;
        int equal = 0;
        ArrayList<UnitInfo> living = state.getLivingUnits();
        for (int i = 0; i < living.size(); i++) {
            UnitInfo u = living.get(i);
            if (u.getId() == self.getId()) {
                continue;
            }
            if (!u.isEnemyOf(enemy)) {
                continue;
            }
            int d = enemy.distanceTo(u);
            if (d < myDistance) {
                closer++;
            } else if (d == myDistance) {
                equal++;
            }
        }
        if (closer > 0) {
            return 0.45;
        }
        if (equal > 0) {
            return 0.68;
        }
        return 1.0;
    }

    private double survivalUrgency(GameState state, UnitInfo self, Position p) {
        double hpUrgency = hpUrgency(self);
        double incoming = expectedIncomingDamage(state, self, p);
        double deathPressure = clamp01((incoming / Math.max(1.0, self.getHealth()) - 0.28) / 0.85);
        deathPressure = smooth01(deathPressure);
        double tileDanger = clamp01(rawTileDanger(state, self, p) / 1.50);
        return clamp01(Math.max(hpUrgency, Math.max(deathPressure, 0.55 * tileDanger + 0.45 * hpUrgency)));
    }

    private double hpUrgency(UnitInfo u) {
        double x = clamp01((0.70 - hpRatio(u)) / 0.52);
        return smooth01(x);
    }

    private double unitDanger(UnitInfo u) {
        double attack = clamp01((u.getAttackPower() - 8.0) / 10.0);
        double range = clamp01((u.getRange() - 1.0) / 2.0);
        double presence = 0.65 + 0.35 * hpRatio(u);
        return clamp01(0.54 * attack + 0.26 * range + 0.20 * presence);
    }

    private double hpRatio(UnitInfo u) {
        return clamp01(u.getHealth() / Math.max(1.0, u.getMaxHealth()));
    }

    private double missingHp(UnitInfo u) {
        return 1.0 - hpRatio(u);
    }

    private double powerProgress(UnitInfo u) {
        double attackProgress = clamp01((u.getAttackPower() - 11.0) / 6.0);
        double rangeProgress = clamp01((u.getRange() - 1.0) / 2.0);
        return 0.55 * attackProgress + 0.45 * rangeProgress;
    }

    private double rangeNeed(UnitInfo u) {
        return 1.0 - clamp01((u.getRange() - 1.0) / 2.0);
    }

    private int effectiveDamage(UnitInfo attacker, UnitInfo target) {
        int damage = attacker.getAttackPower();
        if (target.isDefending()) {
            damage = Math.max(1, damage / 2);
        }
        return damage;
    }

    private ArrayList<UnitInfo> healthyTeamMembers(GameState state, UnitInfo self) {
        ArrayList<UnitInfo> team = new ArrayList<UnitInfo>();
        if (hpRatio(self) > 0.35) {
            team.add(self);
        }
        ArrayList<UnitInfo> mates = state.getLivingTeammates(self);
        for (int i = 0; i < mates.size(); i++) {
            UnitInfo mate = mates.get(i);
            if (hpRatio(mate) > 0.35) {
                team.add(mate);
            }
        }
        return team;
    }

    private double averageDistanceToAttackRange(ArrayList<UnitInfo> units, UnitInfo enemy) {
        if (units.isEmpty()) {
            return 999.0;
        }
        double total = 0.0;
        for (int i = 0; i < units.size(); i++) {
            UnitInfo u = units.get(i);
            total += Math.max(0, u.distanceTo(enemy) - u.getRange());
        }
        return total / units.size();
    }

    private double teamReadiness(GameState state, UnitInfo self, UnitInfo target) {
        ArrayList<UnitInfo> mates = state.getLivingTeammates(self);
        if (mates.isEmpty()) {
            return 0.20;
        }
        UnitInfo mate = mates.get(0);
        if (hpRatio(mate) <= 0.35) {
            return 0.25;
        }
        int mateDist = Math.max(0, mate.distanceTo(target) - mate.getRange());
        int selfDist = Math.max(0, self.distanceTo(target) - self.getRange());
        double close = 1.0 - clamp01(mateDist / 9.0);
        double sync = 1.0 - clamp01(Math.abs(mateDist - selfDist) / 6.0);
        return clamp01(0.60 * close + 0.40 * sync);
    }

    private double teammateFocusPotential(GameState state, UnitInfo self, UnitInfo target) {
        ArrayList<UnitInfo> mates = state.getLivingTeammates(self);
        if (mates.isEmpty()) {
            return 0.0;
        }
        UnitInfo mate = mates.get(0);
        if (hpRatio(mate) <= 0.30) {
            return 0.0;
        }
        int d = Math.max(0, mate.distanceTo(target) - mate.getRange());
        return 1.0 - clamp01(d / 10.0);
    }

    private int enemyTeamAliveCount(GameState state, UnitInfo enemy) {
        int count = 0;
        ArrayList<UnitInfo> living = state.getLivingUnits();
        for (int i = 0; i < living.size(); i++) {
            UnitInfo u = living.get(i);
            if (u.getTeamName().equals(enemy.getTeamName())) {
                count++;
            }
        }
        return count;
    }

    private double enemyIsolation(GameState state, UnitInfo enemy) {
        int nearestMate = 99;
        ArrayList<UnitInfo> living = state.getLivingUnits();
        for (int i = 0; i < living.size(); i++) {
            UnitInfo u = living.get(i);
            if (u.getId() == enemy.getId()) {
                continue;
            }
            if (u.getTeamName().equals(enemy.getTeamName())) {
                nearestMate = Math.min(nearestMate, enemy.distanceTo(u));
            }
        }
        if (nearestMate == 99) {
            return 1.0;
        }
        return clamp01((nearestMate - 2) / 6.0);
    }

    private double enemyCrowdPenalty(GameState state, UnitInfo self, Position p) {
        int count = 0;
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        for (int i = 0; i < enemies.size(); i++) {
            if (enemies.get(i).getPosition().distanceTo(p) <= 3) {
                count++;
            }
        }
        return clamp01((count - 1) / 4.0);
    }

    private double localOutnumberRisk(GameState state, UnitInfo self, Position p) {
        int nearEnemies = 0;
        int nearAllies = 1;
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        for (int i = 0; i < enemies.size(); i++) {
            if (enemies.get(i).getPosition().distanceTo(p) <= 3) {
                nearEnemies++;
            }
        }
        ArrayList<UnitInfo> mates = state.getLivingTeammates(self);
        for (int i = 0; i < mates.size(); i++) {
            if (mates.get(i).getPosition().distanceTo(p) <= 3 && hpRatio(mates.get(i)) > 0.30) {
                nearAllies++;
            }
        }
        return clamp01((nearEnemies - nearAllies) / 4.0);
    }

    private boolean movesAwayFromHealthyTeammate(GameState state, UnitInfo self, Position next) {
        ArrayList<UnitInfo> mates = state.getLivingTeammates(self);
        if (mates.isEmpty()) {
            return false;
        }
        UnitInfo mate = mates.get(0);
        if (hpRatio(mate) <= 0.35) {
            return false;
        }
        return next.distanceTo(mate.getPosition()) > self.distanceTo(mate);
    }

    private boolean finishMode(GameState state, UnitInfo self) {
        if (enemyTeamCount(state, self) <= 2) {
            return true;
        }
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        for (int i = 0; i < enemies.size(); i++) {
            if (hpRatio(enemies.get(i)) < 0.34) {
                return true;
            }
        }
        return false;
    }

    private int enemyTeamCount(GameState state, UnitInfo self) {
        ArrayList<String> names = new ArrayList<String>();
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        for (int i = 0; i < enemies.size(); i++) {
            String name = enemies.get(i).getTeamName();
            if (!names.contains(name)) {
                names.add(name);
            }
        }
        return names.size();
    }

    private int nearestEnemyDistanceToPosition(GameState state, UnitInfo self, Position p) {
        int best = 9999;
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        for (int i = 0; i < enemies.size(); i++) {
            best = Math.min(best, enemies.get(i).distanceTo(p));
        }
        return best;
    }

    private ArrayList<Position> findHealingPoints(GameState state) {
        ArrayList<Position> points = new ArrayList<Position>();
        for (int r = 0; r < state.getRows(); r++) {
            for (int c = 0; c < state.getCols(); c++) {
                Position p = new Position(r, c);
                if (state.isHealingPoint(p)) {
                    points.add(p);
                }
            }
        }
        return points;
    }

    private Position bestOpenSquareNear(GameState state, UnitInfo self, Position center, int radius) {
        Position best = null;
        double bestScore = 99999.0;
        for (int r = center.getRow() - radius; r <= center.getRow() + radius; r++) {
            for (int c = center.getCol() - radius; c <= center.getCol() + radius; c++) {
                Position p = new Position(r, c);
                if (!state.isInside(p) || state.isWall(p) || state.isOccupied(p)) {
                    continue;
                }
                double score = p.distanceTo(center) * 10.0
                        + rawTileDanger(state, self, p) * 8.0
                        + self.distanceTo(p);
                if (best == null || score < bestScore) {
                    best = p;
                    bestScore = score;
                }
            }
        }
        return best;
    }

    private Position bestStrategicCenter(GameState state, UnitInfo self) {
        Position[] candidates = new Position[] {
                new Position(5, 5), new Position(5, 6), new Position(6, 4), new Position(6, 8),
                new Position(3, 3), new Position(7, 8), new Position(1, 6), new Position(9, 5)
        };
        Position best = null;
        double bestScore = 99999.0;
        for (int i = 0; i < candidates.length; i++) {
            Position p = candidates[i];
            if (!state.isInside(p) || state.isWall(p)) {
                continue;
            }
            if (!p.equals(self.getPosition()) && state.isOccupied(p)) {
                continue;
            }
            PathResult path = findPath(state, self, p);
            if (!path.reachable) {
                continue;
            }
            double score = path.steps * 4.0 + path.risk * 10.0 + rawTileDanger(state, self, p) * 6.0;
            if (best == null || score < bestScore) {
                best = p;
                bestScore = score;
            }
        }
        return best;
    }

    private boolean containsPosition(ArrayList<Position> positions, Position target) {
        for (int i = 0; i < positions.size(); i++) {
            if (positions.get(i).equals(target)) {
                return true;
            }
        }
        return false;
    }

    private Action fallbackAction(GameState state, UnitInfo self) {
        UnitInfo attackTarget = null;
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo e = enemies.get(i);
            if (self.distanceTo(e) <= self.getRange()) {
                if (attackTarget == null
                        || e.getHealth() < attackTarget.getHealth()
                        || (e.getHealth() == attackTarget.getHealth() && e.getId() < attackTarget.getId())) {
                    attackTarget = e;
                }
            }
        }
        if (attackTarget != null) {
            return Action.attack(attackTarget.getId());
        }

        Position here = self.getPosition();
        Direction bestDir = null;
        double bestScore = 99999.0;
        Direction[] dirs = Direction.values();
        for (int i = 0; i < dirs.length; i++) {
            Direction dir = dirs[i];
            Position next = here.move(dir);
            if (!state.isOpen(next)) {
                continue;
            }
            double score = rawTileDanger(state, self, next) * 20.0 + centerDistance(next);
            if (bestDir == null || score < bestScore) {
                bestDir = dir;
                bestScore = score;
            }
        }
        if (bestDir != null) {
            return Action.move(bestDir);
        }
        return Action.defend();
    }

    private double centerDistance(Position p) {
        return Math.abs(p.getRow() - 5.5) + Math.abs(p.getCol() - 5.5);
    }

    private boolean isLegalAction(GameState state, UnitInfo self, Action action) {
        if (action == null) {
            return false;
        }
        if (action.getType() == ActionType.MOVE) {
            Direction d = action.getDirection();
            return d != null && state.isOpen(self.getPosition().move(d));
        }
        if (action.getType() == ActionType.ATTACK) {
            int id = action.getTargetId();
            ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
            for (int i = 0; i < enemies.size(); i++) {
                UnitInfo e = enemies.get(i);
                if (e.getId() == id && self.distanceTo(e) <= self.getRange()) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    private void updateMemoryAtTurnStart(UnitInfo self) {
        if (lastSeenPosition != null && lastSeenPosition.equals(self.getPosition())) {
            if (!"Attack".equals(lastDecision) && !"HealHold".equals(lastDecision)) {
                stationaryTurns++;
            }
        } else {
            stationaryTurns = 0;
        }
        lastSeenPosition = self.getPosition();
    }

    private void rememberDecision(UnitInfo self, Candidate best) {
        if (best == null) {
            lastDecision = "";
            lastMoveOrigin = null;
            return;
        }
        lastDecision = best.name;
        boolean reversed = best.nextPosition != null
                && lastMoveOrigin != null
                && best.nextPosition.equals(lastMoveOrigin);
        if (reversed) {
            reverseMoveStreak++;
        } else {
            reverseMoveStreak = 0;
        }
        if (best.action != null && best.action.getType() == ActionType.MOVE) {
            lastMoveOrigin = self.getPosition();
        } else {
            lastMoveOrigin = null;
        }
    }

    private void printChoices(UnitInfo self, ArrayList<Candidate> choices, Candidate best) {
        StringBuilder sb = new StringBuilder();
        sb.append("U").append(self.getId()).append(" ");
        for (int i = 0; i < choices.size(); i++) {
            Candidate c = choices.get(i);
            sb.append(c.name).append("=").append(round(c.score)).append("[").append(c.target).append("] ");
        }
        sb.append("=> ").append(best == null ? "null" : best.name + " " + round(best.score));
        System.out.println(sb.toString());
    }

    private String round(double v) {
        return String.valueOf(Math.round(v * 100.0) / 100.0);
    }

    private double smooth01(double x) {
        x = clamp01(x);
        return x * x * (3.0 - 2.0 * x);
    }

    private double clamp01(double x) {
        return clamp(x, 0.0, 1.0);
    }

    private double clamp(double x, double lo, double hi) {
        if (x < lo) {
            return lo;
        }
        if (x > hi) {
            return hi;
        }
        return x;
    }

    private static class Candidate {
        final String name;
        final double score;
        final String target;
        final Action action;
        final Position nextPosition;

        Candidate(String name, double score, String target, Action action, Position nextPosition) {
            this.name = name;
            this.score = score;
            this.target = target;
            this.action = action;
            this.nextPosition = nextPosition;
        }

        int tieBreakKey() {
            int actionRank = 50;
            if ("Attack".equals(name)) {
                actionRank = 0;
            } else if ("Retreat".equals(name)) {
                actionRank = 1;
            } else if ("HealHold".equals(name)) {
                actionRank = 2;
            } else if ("Heal".equals(name)) {
                actionRank = 3;
            } else if ("PowerUp".equals(name)) {
                actionRank = 4;
            } else if ("TeamFight".equals(name)) {
                actionRank = 5;
            } else if ("Rally".equals(name)) {
                actionRank = 6;
            } else if ("Chase".equals(name)) {
                actionRank = 7;
            }
            return actionRank;
        }
    }

    private static class PathNode {
        final Position position;
        final int gCost;
        final int hCost;
        final int fCost;
        final PathNode parent;
        final Direction directionFromParent;

        PathNode(Position position, int gCost, int hCost, PathNode parent, Direction directionFromParent) {
            this.position = position;
            this.gCost = gCost;
            this.hCost = hCost;
            this.fCost = gCost + hCost;
            this.parent = parent;
            this.directionFromParent = directionFromParent;
        }
    }

    private static class PathResult {
        final boolean reachable;
        final int cost;
        final int steps;
        final Direction firstStep;
        final double risk;
        final double adjustedCost;

        PathResult(boolean reachable, int cost, int steps, Direction firstStep, double risk, double adjustedCost) {
            this.reachable = reachable;
            this.cost = cost;
            this.steps = steps;
            this.firstStep = firstStep;
            this.risk = risk;
            this.adjustedCost = adjustedCost;
        }

        static PathResult unreachable() {
            return new PathResult(false, INF, INF, null, 1.0, INF);
        }

        PathResult withAdjustedCost(double adjustedCost) {
            return new PathResult(reachable, cost, steps, firstStep, risk, adjustedCost);
        }
    }
}
