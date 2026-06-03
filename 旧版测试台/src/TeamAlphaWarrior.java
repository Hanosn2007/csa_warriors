import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.PriorityQueue;

public class TeamAlphaWarrior extends Warrior {
    private static final boolean DEBUG = false;
    private static final int BASE_STEP_COST = 10;
    private static final int INF = 1000000000;

    public TeamAlphaWarrior() {
        super("Team Alpha");
    }

    @Override
    public Action chooseAction(GameState state, UnitInfo self) {
        ArrayList<Candidate> choices = new ArrayList<Candidate>();

        addAttackChoices(choices, state, self);
        addHealChoice(choices, state, self);
        addRetreatChoice(choices, state, self);
        addPowerUpChoice(choices, state, self);
        addTeamFightChoice(choices, state, self);
        addChaseChoice(choices, state, self);
        addDefendFallback(choices, state, self);

        Candidate best = pickBest(choices);
        if (DEBUG) {
            printChoices(self, choices, best);
        }
        return best == null ? Action.defend() : best.action;
    }

    private void addAttackChoices(ArrayList<Candidate> choices, GameState state, UnitInfo self) {
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        double hpNeed = hpUrgency(self);
        double currentDanger = tileDanger(state, self, self.getPosition());
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo e = enemies.get(i);
            if (self.distanceTo(e) > self.getRange()) continue;
            int damage = expectedDamage(self, e);
            boolean kills = e.getHealth() <= damage;
            double score = 0.70
                    + 0.20 * (kills ? 1.0 : 0.0)
                    + 0.06 * (1.0 - hpRatio(e))
                    + 0.04 * unitDanger(e);
            if (!kills && hpNeed > 0.65 && currentDanger > 0.45) {
                score -= 0.22;
            }
            choices.add(new Candidate("Attack", score, "enemy#" + e.getId(), Action.attack(e.getId())));
        }
    }

    private void addHealChoice(ArrayList<Candidate> choices, GameState state, UnitInfo self) {
        double need = hpUrgency(self);
        if (need <= 0.02) return;

        ArrayList<Position> healingPoints = allHealingPoints(state);
        Candidate best = null;
        for (int i = 0; i < healingPoints.size(); i++) {
            Position hp = healingPoints.get(i);
            if (!self.getPosition().equals(hp) && state.isOccupied(hp)) continue;

            Action action;
            PathResult path;
            if (self.getPosition().equals(hp)) {
                action = Action.defend();
                path = new PathResult(true, null, hp, 0, 0, 0.0);
            } else {
                path = findPath(state, self, hp);
                if (!path.reachable || path.firstStep == null) continue;
                action = Action.move(path.firstStep);
            }

            double distancePenalty = clamp01(path.steps / 10.0);
            double targetSafety = 1.0 - clamp01(tileDanger(state, self, hp));
            double score = 0.08 + 0.78 * need + 0.10 * targetSafety - 0.20 * distancePenalty - 0.12 * path.risk;
            if (isInLethalDanger(state, self, self.getPosition())) score += 0.18;
            Candidate c = new Candidate("Heal", score, hp.toString(), action);
            if (best == null || c.score > best.score) best = c;
        }
        if (best != null) choices.add(best);
    }

    private void addRetreatChoice(ArrayList<Candidate> choices, GameState state, UnitInfo self) {
        double here = tileDanger(state, self, self.getPosition());
        if (here < 0.20 && hpUrgency(self) < 0.45) return;

        Direction bestDir = null;
        double bestDanger = here;
        Direction[] dirs = Direction.values();
        for (int i = 0; i < dirs.length; i++) {
            Position next = self.getPosition().move(dirs[i]);
            if (!state.isOpen(next)) continue;
            double d = tileDanger(state, self, next);
            if (bestDir == null || d < bestDanger) {
                bestDir = dirs[i];
                bestDanger = d;
            }
        }
        if (bestDir != null && bestDanger + 0.05 < here) {
            double score = 0.18 + 0.45 * hpUrgency(self) + 0.37 * clamp01(here - bestDanger);
            choices.add(new Candidate("Retreat", score, "safer tile", Action.move(bestDir)));
        }
    }

    private void addPowerUpChoice(ArrayList<Candidate> choices, GameState state, UnitInfo self) {
        ArrayList<Position> powerUps = state.getPowerUpPositions();
        Candidate best = null;
        for (int i = 0; i < powerUps.size(); i++) {
            Position p = powerUps.get(i);
            if (state.isOccupied(p)) continue;
            PathResult path = findPath(state, self, p);
            if (!path.reachable || path.firstStep == null) continue;
            if (path.steps > 12) continue;

            double distanceFactor = 1.0 - clamp01((path.steps - 1) / 10.0);
            double targetSafety = 1.0 - clamp01(tileDanger(state, self, p));
            double needPower = 1.0 - powerProgress(self);
            double score = (0.28 + 0.20 * needPower + 0.12 * missingHp(self) + 0.20 * targetSafety)
                    * distanceFactor
                    - 0.12 * path.risk;
            Candidate c = new Candidate("PowerUp", score, p.toString(), Action.move(path.firstStep));
            if (best == null || c.score > best.score) best = c;
        }
        if (best != null && best.score > 0.12) choices.add(best);
    }

    private void addTeamFightChoice(ArrayList<Candidate> choices, GameState state, UnitInfo self) {
        ArrayList<UnitInfo> mates = healthyTeammates(state, self);
        if (mates.isEmpty()) return;
        if (hpUrgency(self) > 0.75) return;

        UnitInfo target = selectTeamTarget(state, self, mates);
        if (target == null || self.distanceTo(target) <= self.getRange()) return;
        PathResult path = findPathToAttackSquare(state, self, target);
        if (!path.reachable || path.firstStep == null) return;

        double distFactor = 1.0 - clamp01((path.steps - 1) / 12.0);
        double readiness = Math.min(hpRatio(self), averageTeamHp(self, mates));
        double score = 0.22 + 0.45 * targetValue(state, self, target, true)
                + 0.15 * readiness + 0.14 * distFactor
                - 0.16 * hpUrgency(self) - 0.10 * path.risk;
        choices.add(new Candidate("TeamFight", score, "enemy#" + target.getId(), Action.move(path.firstStep)));
    }

    private void addChaseChoice(ArrayList<Candidate> choices, GameState state, UnitInfo self) {
        UnitInfo target = selectIndividualTarget(state, self);
        if (target == null || self.distanceTo(target) <= self.getRange()) return;
        PathResult path = findPathToAttackSquare(state, self, target);
        if (!path.reachable || path.firstStep == null) return;

        double distFactor = 1.0 - clamp01((path.steps - 1) / 14.0);
        double score = 0.16 + 0.48 * targetValue(state, self, target, false)
                + 0.20 * distFactor - 0.20 * hpUrgency(self) - 0.10 * path.risk;
        choices.add(new Candidate("Chase", score, "enemy#" + target.getId(), Action.move(path.firstStep)));
    }

    private void addDefendFallback(ArrayList<Candidate> choices, GameState state, UnitInfo self) {
        double danger = tileDanger(state, self, self.getPosition());
        double score = 0.04 + 0.36 * danger + 0.20 * hpUrgency(self);
        choices.add(new Candidate("Defend", score, "none", Action.defend()));
    }

    private Candidate pickBest(ArrayList<Candidate> choices) {
        Candidate best = null;
        for (int i = 0; i < choices.size(); i++) {
            Candidate c = choices.get(i);
            if (c == null || c.action == null || Double.isNaN(c.score)) continue;
            if (best == null || c.score > best.score) best = c;
        }
        return best;
    }

    private UnitInfo selectTeamTarget(GameState state, UnitInfo self, ArrayList<UnitInfo> mates) {
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        UnitInfo best = null;
        double bestScore = -1;
        Position center = teamCenter(self, mates);
        int teamDamage = self.getAttackPower();
        for (int i = 0; i < mates.size(); i++) teamDamage += mates.get(i).getAttackPower();

        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo e = enemies.get(i);
            int turnsToKill = Math.max(1, (int)Math.ceil(e.getHealth() / (double)Math.max(1, teamDamage)));
            double killWindow = 1.0 - clamp01((turnsToKill - 1) / 4.0);
            double proximity = 1.0 - clamp01(center.distanceTo(e.getPosition()) / 16.0);
            double score = 0.35 * killWindow + 0.25 * proximity + 0.20 * (1.0 - hpRatio(e)) + 0.20 * unitDanger(e);
            if (best == null || score > bestScore || (Math.abs(score - bestScore) < 0.0001 && e.getId() < best.getId())) {
                best = e;
                bestScore = score;
            }
        }
        return best;
    }

    private UnitInfo selectIndividualTarget(GameState state, UnitInfo self) {
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        UnitInfo best = null;
        double bestScore = -1;
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo e = enemies.get(i);
            double score = targetValue(state, self, e, false);
            if (best == null || score > bestScore || (Math.abs(score - bestScore) < 0.0001 && e.getId() < best.getId())) {
                best = e;
                bestScore = score;
            }
        }
        return best;
    }

    private double targetValue(GameState state, UnitInfo self, UnitInfo e, boolean teamMode) {
        int damage = expectedDamage(self, e);
        double killNow = e.getHealth() <= damage ? 1.0 : 0.0;
        double lowHp = 1.0 - hpRatio(e);
        double danger = unitDanger(e);
        double dist = self.distanceTo(e);
        double proximity = 1.0 - clamp01((dist - self.getRange()) / 14.0);
        double focus = 0.0;
        ArrayList<UnitInfo> mates = state.getLivingTeammates(self);
        for (int i = 0; i < mates.size(); i++) {
            UnitInfo m = mates.get(i);
            if (hpRatio(m) > 0.35) {
                double md = m.distanceTo(e);
                focus = Math.max(focus, 1.0 - clamp01((md - m.getRange()) / 14.0));
            }
        }
        if (!teamMode) focus *= 0.6;
        return clamp01(0.28 * killNow + 0.22 * lowHp + 0.20 * danger + 0.20 * proximity + 0.10 * focus);
    }

    private PathResult findPathToAttackSquare(GameState state, UnitInfo self, UnitInfo target) {
        Position enemyPos = target.getPosition();
        PathResult best = null;
        for (int r = enemyPos.getRow() - self.getRange(); r <= enemyPos.getRow() + self.getRange(); r++) {
            for (int c = enemyPos.getCol() - self.getRange(); c <= enemyPos.getCol() + self.getRange(); c++) {
                Position p = new Position(r, c);
                if (!state.isInside(p) || state.isWall(p)) continue;
                if (p.distanceTo(enemyPos) > self.getRange()) continue;
                if (!p.equals(self.getPosition()) && state.isOccupied(p)) continue;
                PathResult path = findPath(state, self, p);
                if (!path.reachable) continue;
                double adjusted = path.cost + 8.0 * tileDanger(state, self, p);
                if (best == null || adjusted < best.cost + 8.0 * tileDanger(state, self, best.goal)) {
                    best = path;
                }
            }
        }
        return best == null ? PathResult.unreachable() : best;
    }

    private PathResult findPath(GameState state, UnitInfo self, Position goal) {
        Position start = self.getPosition();
        if (start.equals(goal)) return new PathResult(true, null, goal, 0, 0, 0.0);
        if (!state.isInside(goal) || state.isWall(goal) || state.isOccupied(goal)) return PathResult.unreachable();

        int rows = state.getRows();
        int cols = state.getCols();
        int[][] best = new int[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) best[r][c] = INF;
        }

        PriorityQueue<Node> pq = new PriorityQueue<Node>(new Comparator<Node>() {
            @Override public int compare(Node a, Node b) { return a.f - b.f; }
        });
        Node s = new Node(start, 0, start.distanceTo(goal) * BASE_STEP_COST, 0, null);
        pq.add(s);
        best[start.getRow()][start.getCol()] = 0;

        while (!pq.isEmpty()) {
            Node cur = pq.poll();
            if (cur.g != best[cur.pos.getRow()][cur.pos.getCol()]) continue;
            if (cur.pos.equals(goal)) return buildResult(cur, start, goal);

            Direction[] dirs = Direction.values();
            for (int i = 0; i < dirs.length; i++) {
                Position next = cur.pos.move(dirs[i]);
                if (!state.isInside(next) || state.isWall(next)) continue;
                if (!next.equals(start) && state.isOccupied(next)) continue;
                int stepCost = tileStepCost(state, self, next);
                int ng = cur.g + stepCost;
                if (ng < best[next.getRow()][next.getCol()]) {
                    best[next.getRow()][next.getCol()] = ng;
                    int h = next.distanceTo(goal) * BASE_STEP_COST;
                    pq.add(new Node(next, ng, ng + h, cur.steps + 1, cur));
                }
            }
        }
        return PathResult.unreachable();
    }

    private PathResult buildResult(Node end, Position start, Position goal) {
        Node cur = end;
        while (cur.parent != null && !cur.parent.pos.equals(start)) {
            cur = cur.parent;
        }
        Direction first = directionFrom(start, cur.pos);
        double extra = Math.max(0, end.g - end.steps * BASE_STEP_COST);
        double risk = clamp01(extra / Math.max(1.0, end.steps * 30.0));
        return new PathResult(true, first, goal, end.steps, end.g, risk);
    }

    private int tileStepCost(GameState state, UnitInfo self, Position p) {
        double danger = Math.min(1.6, rawTileDanger(state, self, p));
        int cost = BASE_STEP_COST + (int)Math.round(22.0 * danger);
        if (state.isHealingPoint(p) && missingHp(self) > 0.25) cost -= 2;
        if (containsPosition(state.getPowerUpPositions(), p)) cost -= 2;
        return Math.max(1, cost);
    }

    private double tileDanger(GameState state, UnitInfo self, Position p) {
        return clamp01(rawTileDanger(state, self, p) / 1.6);
    }

    private double rawTileDanger(GameState state, UnitInfo self, Position p) {
        double total = 0.0;
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo e = enemies.get(i);
            int d = p.distanceTo(e.getPosition());
            double power = unitDanger(e);
            double lethal = e.getAttackPower() >= self.getHealth() ? 0.35 : 0.0;
            if (d <= e.getRange()) {
                total += 0.85 * power + lethal;
            } else if (d == e.getRange() + 1) {
                total += 0.38 * power;
            } else if (d <= e.getRange() + 3) {
                total += 0.12 * power / Math.max(1, d - e.getRange());
            }
        }
        return total;
    }

    private double unitDanger(UnitInfo u) {
        double attack = clamp01(u.getAttackPower() / 17.0);
        double range = clamp01((u.getRange() - 1) / 3.0);
        double presence = 0.65 + 0.35 * hpRatio(u);
        return clamp01(0.55 * attack + 0.25 * range + 0.20 * presence);
    }

    private int expectedDamage(UnitInfo attacker, UnitInfo target) {
        int damage = attacker.getAttackPower();
        if (target.isDefending()) damage = Math.max(1, damage / 2);
        return damage;
    }

    private boolean isInLethalDanger(GameState state, UnitInfo self, Position p) {
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo e = enemies.get(i);
            if (p.distanceTo(e.getPosition()) <= e.getRange() && e.getAttackPower() >= self.getHealth()) return true;
        }
        return false;
    }

    private ArrayList<Position> allHealingPoints(GameState state) {
        ArrayList<Position> list = new ArrayList<Position>();
        for (int r = 0; r < state.getRows(); r++) {
            for (int c = 0; c < state.getCols(); c++) {
                Position p = new Position(r, c);
                if (state.isHealingPoint(p)) list.add(p);
            }
        }
        return list;
    }

    private ArrayList<UnitInfo> healthyTeammates(GameState state, UnitInfo self) {
        ArrayList<UnitInfo> out = new ArrayList<UnitInfo>();
        ArrayList<UnitInfo> mates = state.getLivingTeammates(self);
        for (int i = 0; i < mates.size(); i++) {
            if (hpRatio(mates.get(i)) > 0.35) out.add(mates.get(i));
        }
        return out;
    }

    private Position teamCenter(UnitInfo self, ArrayList<UnitInfo> mates) {
        int row = self.getRow();
        int col = self.getCol();
        int n = 1;
        for (int i = 0; i < mates.size(); i++) {
            row += mates.get(i).getRow();
            col += mates.get(i).getCol();
            n++;
        }
        return new Position(row / n, col / n);
    }

    private double averageTeamHp(UnitInfo self, ArrayList<UnitInfo> mates) {
        double sum = hpRatio(self);
        for (int i = 0; i < mates.size(); i++) sum += hpRatio(mates.get(i));
        return sum / (mates.size() + 1.0);
    }

    private double hpRatio(UnitInfo u) { return clamp01(u.getHealth() / (double)Math.max(1, u.getMaxHealth())); }
    private double missingHp(UnitInfo u) { return 1.0 - hpRatio(u); }

    private double hpUrgency(UnitInfo u) {
        double x = clamp01((0.68 - hpRatio(u)) / 0.48);
        return x * x * (3.0 - 2.0 * x);
    }

    private double powerProgress(UnitInfo u) {
        double attackProgress = clamp01((u.getAttackPower() - 11) / 6.0);
        double rangeProgress = clamp01((u.getRange() - 1) / 2.0);
        return (attackProgress + rangeProgress) / 2.0;
    }

    private boolean containsPosition(ArrayList<Position> list, Position p) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).equals(p)) return true;
        return false;
    }

    private Direction directionFrom(Position a, Position b) {
        Direction[] dirs = Direction.values();
        for (int i = 0; i < dirs.length; i++) if (a.move(dirs[i]).equals(b)) return dirs[i];
        return null;
    }

    private double clamp01(double x) {
        if (x < 0.0) return 0.0;
        if (x > 1.0) return 1.0;
        return x;
    }

    private void printChoices(UnitInfo self, ArrayList<Candidate> choices, Candidate best) {
        Collections.sort(choices, new Comparator<Candidate>() {
            @Override public int compare(Candidate a, Candidate b) { return Double.compare(b.score, a.score); }
        });
        String s = "Unit " + self.getId() + " choices:";
        for (int i = 0; i < choices.size(); i++) {
            Candidate c = choices.get(i);
            s += " " + c.name + "=" + String.format("%.2f", c.score) + "(" + c.target + ")";
        }
        s += " => " + (best == null ? "none" : best.name);
        System.out.println(s);
    }

    private static class Candidate {
        final String name;
        final double score;
        final String target;
        final Action action;
        Candidate(String name, double score, String target, Action action) {
            this.name = name;
            this.score = score;
            this.target = target;
            this.action = action;
        }
    }

    private static class PathResult {
        final boolean reachable;
        final Direction firstStep;
        final Position goal;
        final int steps;
        final int cost;
        final double risk;
        PathResult(boolean reachable, Direction firstStep, Position goal, int steps, int cost, double risk) {
            this.reachable = reachable;
            this.firstStep = firstStep;
            this.goal = goal;
            this.steps = steps;
            this.cost = cost;
            this.risk = risk;
        }
        static PathResult unreachable() { return new PathResult(false, null, null, 0, INF, 1.0); }
    }

    private static class Node {
        final Position pos;
        final int g;
        final int f;
        final int steps;
        final Node parent;
        Node(Position pos, int g, int f, int steps, Node parent) {
            this.pos = pos;
            this.g = g;
            this.f = f;
            this.steps = steps;
            this.parent = parent;
        }
    }
}
