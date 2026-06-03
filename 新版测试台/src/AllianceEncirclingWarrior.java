import java.util.ArrayList;

/*
 * 4V4 合作版 Encircling 策略。
 *
 * 设计目标：
 * 1. 四名队友属于同一阵营时，自动按 id 分出不同职责。
 * 2. 每个战士先根据同一份局面计算团队计划，再按自己的职责执行。
 * 3. 团队计划包括集合、争夺强力道具、集火围攻、保护残血队友。
 * 4. 攻击时尽量围绕同一个目标形成夹击；没有队友支援时，避免孤立追击。
 * 5. 残血时脱离战斗，优先找回血点或安全道具，而不是原地防守等死。
 *
 * 这个策略只使用公开接口：
 * - GameState / UnitInfo 读取局面；
 * - Action 返回行动；
 * - getPowerUps() / getPowerUpTypeAt() 读取道具类型。
 * 它没有访问或修改 GameEngine 内部状态，因此和其他策略处在同一规则空间内。
 */
public class AllianceEncirclingWarrior extends Warrior {
    private static final int SAFE_DISTANCE_TO_TEAM = 4;
    private static final int MAX_TEAM_SPREAD = 8;
    private static final int POWER_UP_SEARCH_DISTANCE = 10;
    private static final int FOCUS_SEARCH_DISTANCE = 9;
    private static final String PLAN_REGROUP = "regroup";
    private static final String PLAN_CONTEST_POWER = "contest strong power-up";
    private static final String PLAN_DENY_HEALING = "deny enemy healing";
    private static final String PLAN_PINCER = "pincer focus";
    private static final String PLAN_COMPACT = "compact pressure";

    public AllianceEncirclingWarrior() {
        super("Alliance Encircling");
    }

    public AllianceEncirclingWarrior(String name) {
        super(name);
    }

    @Override
    public Action chooseAction(GameState state, UnitInfo self) {
        int role = roleIndex(state, self);
        TeamPlan plan = buildTeamPlan(state, self, role);
        UnitInfo focusTarget = plan.focusTarget;

        state.traceValue("4v4 role", roleName(role));
        state.traceValue("team plan", plan.mode);
        state.traceValue("team spread", teamSpread(state, self));
        state.traceValue("teammates", state.getLivingTeammates(self).size());
        state.traceUnit("focus target", focusTarget);
        state.traceValue("role destination", plan.roleDestination);
        state.traceValue("incoming damage here", incomingDamageAt(state, self, self.getPosition()));
        state.traceValue("nearest strong power-up", state.findNearestStrongPowerUp(self));

        UnitInfo killTarget = findKillTargetInRange(state, self);
        if (killTarget != null) {
            state.trace("mode = finish target");
            state.traceUnit("target", killTarget);
            return Action.attack(killTarget.getId());
        }

        Choice rescue = createRescueChoice(state, self, role);
        if (rescue != null) {
            state.trace("mode = " + rescue.label);
            state.traceValue("score", rescue.score);
            return rescue.action;
        }

        Choice emergency = createEmergencyChoice(state, self, role);
        if (emergency != null) {
            state.trace("mode = " + emergency.label);
            state.traceValue("score", emergency.score);
            return emergency.action;
        }

        ArrayList<Choice> choices = new ArrayList<Choice>();
        addAttackChoices(choices, state, self, role, plan);
        addPowerUpChoices(choices, state, self, role, plan);
        addHealingChoice(choices, state, self, role);
        addTeamPlanChoice(choices, state, self, role, plan);
        addFocusMoveChoice(choices, state, self, role, focusTarget);
        addFormationChoice(choices, state, self, role, plan);

        Choice best = chooseBest(choices);
        if (best != null) {
            state.trace("mode = " + best.label);
            state.traceValue("score", best.score);
            return best.action;
        }

        state.trace("mode = safe reposition");
        return safeReposition(state, self, role);
    }

    private void addAttackChoices(
            ArrayList<Choice> choices,
            GameState state,
            UnitInfo self,
            int role,
            TeamPlan plan) {
        UnitInfo focusTarget = plan.focusTarget;
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo enemy = enemies.get(i);
            if (self.distanceTo(enemy) > self.getRange()) {
                continue;
            }

            int damage = expectedDamage(self, enemy);
            int counterDamage = counterDamageAfterAttack(state, self, enemy);
            boolean canFinish = damage >= enemy.getHealth() || teamCanFinishSoon(state, self, enemy);

            double score = 950 + damage * 4.0 + targetValue(state, self, enemy) * 30.0;
            if (focusTarget != null && enemy.getId() == focusTarget.getId()) {
                score += 170;
            }
            if (canFinish) {
                score += 260;
            }
            if (enemyCanHitPosition(enemy, self.getPosition())) {
                score += 70;
            }
            if (counterDamage >= self.getHealth()) {
                score -= 700;
            } else {
                score -= counterDamage * 8.0;
            }
            if (nearbyTeammates(state, self, enemy.getPosition(), 3) >= 1) {
                score += 110;
            }
            if (!canFinish
                    && nearbyTeammates(state, self, enemy.getPosition(), 4) == 0
                    && !PLAN_PINCER.equals(plan.mode)) {
                score -= 260;
            }
            if (PLAN_PINCER.equals(plan.mode)
                    && focusTarget != null
                    && enemy.getId() == focusTarget.getId()) {
                score += 120;
            }
            if (role == 0) {
                score += 35;
            } else if (role == 2 && !canFinish) {
                score -= 60;
            }

            choices.add(new Choice(Action.attack(enemy.getId()), score, "focus attack"));
        }
    }

    private void addPowerUpChoices(
            ArrayList<Choice> choices,
            GameState state,
            UnitInfo self,
            int role,
            TeamPlan plan) {
        ArrayList<PowerUp> powerUps = state.getPowerUps();
        for (int i = 0; i < powerUps.size(); i++) {
            PowerUp powerUp = powerUps.get(i);
            Position position = powerUp.getPosition();
            int distance = self.distanceTo(position);
            if (distance > POWER_UP_SEARCH_DISTANCE) {
                continue;
            }

            int enemyDistance = nearestEnemyDistanceTo(state, self, position);
            int teammateDistance = nearestTeammateDistanceTo(state, self, position);
            int danger = incomingDamageAt(state, self, position);
            boolean teammateBetter = teammateDistance < distance
                    || (teammateDistance == distance && role != 2 && !state.isStrongPowerUpAt(position));

            double score = powerUpValue(powerUp.getType(), self, role);
            score -= distance * 24.0;
            score += (enemyDistance - distance) * 32.0;
            score -= danger * 42.0;

            if (state.isStrongPowerUpAt(position)) {
                score += 140;
                if (nearbyTeammates(state, self, position, 4) >= 1) {
                    score += 55;
                }
                if (PLAN_CONTEST_POWER.equals(plan.mode)
                        && plan.powerUpTarget != null
                        && position.equals(plan.powerUpTarget)) {
                    score += 220;
                }
            }
            if (role == 2) {
                score += 90;
            }
            if (teammateBetter) {
                score -= 140;
            }
            if (enemyDistance + 1 < distance && !state.isStrongPowerUpAt(position)) {
                score -= 180;
            }
            if (danger >= self.getHealth()) {
                score -= 800;
            }

            Action action = moveTowardSafely(state, self, position, role);
            choices.add(new Choice(action, score, "assigned power-up"));
        }
    }

    private void addHealingChoice(ArrayList<Choice> choices, GameState state, UnitInfo self, int role) {
        if (self.getHealth() >= self.getMaxHealth()) {
            return;
        }

        double healthRate = healthRate(self);
        if (healthRate > 0.58) {
            return;
        }

        Position healing = state.findNearestHealingPoint(self);
        if (healing == null) {
            return;
        }

        int distance = self.distanceTo(healing);
        int danger = incomingDamageAt(state, self, healing);
        double score = 700 + (1.0 - healthRate) * 380.0 - distance * 18.0 - danger * 55.0;
        if (self.getPosition().equals(healing)) {
            score += 120;
            choices.add(new Choice(Action.defend(), score, "hold healing point"));
            return;
        }
        if (danger >= self.getHealth()) {
            score -= 700;
        }
        if (role == 1 || role == 3) {
            score += 25;
        }

        choices.add(new Choice(moveTowardSafely(state, self, healing, role), score, "recover"));
    }

    private void addFocusMoveChoice(
            ArrayList<Choice> choices,
            GameState state,
            UnitInfo self,
            int role,
            UnitInfo target) {
        if (target == null || self.distanceTo(target) > FOCUS_SEARCH_DISTANCE) {
            return;
        }

        if (isBadSoloDuel(state, self, target) && nearbyTeammates(state, self, target.getPosition(), 4) == 0) {
            return;
        }

        double score = 410 + targetValue(state, self, target) * 42.0 - self.distanceTo(target) * 10.0;
        if (nearbyTeammates(state, self, target.getPosition(), 5) >= 1) {
            score += 85;
        }
        if (role == 0 || role == 1) {
            score += 45;
        } else if (role == 2 && healthRate(self) < 0.72) {
            score -= 80;
        }

        Action action = moveTowardSafely(state, self, target.getPosition(), role);
        score -= movementDanger(state, self, nextPosition(self, action)) * 35.0;
        choices.add(new Choice(action, score, "group focus move"));
    }

    private void addTeamPlanChoice(
            ArrayList<Choice> choices,
            GameState state,
            UnitInfo self,
            int role,
            TeamPlan plan) {
        if (plan.roleDestination == null) {
            return;
        }

        Action action = moveTowardSafely(state, self, plan.roleDestination, role);
        Position next = nextPosition(self, action);
        double score = plan.baseScore;
        score += (self.distanceTo(plan.roleDestination) - next.distanceTo(plan.roleDestination)) * 90.0;
        score -= movementDanger(state, self, next) * 40.0;
        if (PLAN_REGROUP.equals(plan.mode) && self.distanceTo(teamCenter(state, self)) > SAFE_DISTANCE_TO_TEAM) {
            score += 180;
        }
        if (PLAN_PINCER.equals(plan.mode) && role <= 1) {
            score += 70;
        }

        choices.add(new Choice(action, score, plan.mode));
    }

    private void addFormationChoice(
            ArrayList<Choice> choices,
            GameState state,
            UnitInfo self,
            int role,
            TeamPlan plan) {
        UnitInfo nearestTeammate = nearestTeammate(state, self);
        if (nearestTeammate == null) {
            return;
        }

        Position teamCenter = teamCenter(state, self);
        int teammateDistance = self.distanceTo(nearestTeammate);
        double score = 260;
        if (teammateDistance > SAFE_DISTANCE_TO_TEAM) {
            score += (teammateDistance - SAFE_DISTANCE_TO_TEAM) * 70.0;
        }
        if (role == 3) {
            score += 35;
        }
        if (PLAN_REGROUP.equals(plan.mode)) {
            score += 120;
        }

        choices.add(new Choice(moveTowardSafely(state, self, teamCenter, role), score, "keep formation"));
    }

    private Choice createRescueChoice(GameState state, UnitInfo self, int role) {
        UnitInfo teammate = mostThreatenedTeammate(state, self);
        if (teammate == null) {
            return null;
        }

        UnitInfo attacker = enemyThreateningPosition(state, self, teammate.getPosition());
        if (attacker == null) {
            return null;
        }

        double score = 1080 + (1.0 - healthRate(teammate)) * 280.0;
        if (self.distanceTo(attacker) <= self.getRange()) {
            return new Choice(Action.attack(attacker.getId()), score + 140, "rescue attack");
        }
        if (role == 2 && healthRate(self) > 0.70) {
            score -= 80;
        }
        return new Choice(moveTowardSafely(state, self, attacker.getPosition(), role), score, "rescue move");
    }

    private Choice createEmergencyChoice(GameState state, UnitInfo self, int role) {
        int currentDamage = incomingDamageAt(state, self, self.getPosition());
        boolean lowHealth = healthRate(self) <= 0.34;
        boolean immediateDanger = currentDamage >= self.getHealth() || threatCountAt(state, self, self.getPosition()) >= 2;
        if (!lowHealth && !immediateDanger) {
            return null;
        }

        if (state.isHealingPoint(self.getPosition()) && currentDamage < self.getHealth()) {
            return new Choice(Action.defend(), 1250, "emergency healing");
        }

        Direction bestDirection = null;
        double bestScore = -9999;
        Position healing = state.findNearestHealingPoint(self);
        Position strongPowerUp = state.findNearestStrongPowerUp(self);
        Direction[] directions = Direction.values();
        for (int i = 0; i < directions.length; i++) {
            Position next = self.getPosition().move(directions[i]);
            if (!state.isOpen(next)) {
                continue;
            }

            int nextDamage = incomingDamageAt(state, self, next);
            double score = 1300;
            score += (currentDamage - nextDamage) * 115.0;
            score -= threatCountAt(state, self, next) * 220.0;
            score -= nextDamage * 90.0;
            if (healing != null) {
                score += (self.distanceTo(healing) - next.distanceTo(healing)) * 55.0;
            }
            if (strongPowerUp != null && self.distanceTo(strongPowerUp) <= 6) {
                score += (self.distanceTo(strongPowerUp) - next.distanceTo(strongPowerUp)) * 30.0;
            }
            UnitInfo teammate = nearestTeammate(state, self);
            if (teammate != null && next.distanceTo(teammate.getPosition()) <= SAFE_DISTANCE_TO_TEAM) {
                score += 70;
            }
            if (nextDamage >= self.getHealth()) {
                score -= 1000;
            }

            if (bestDirection == null || score > bestScore) {
                bestDirection = directions[i];
                bestScore = score;
            }
        }

        if (bestDirection == null) {
            return new Choice(Action.defend(), 900, "emergency defend");
        }
        return new Choice(Action.move(bestDirection), bestScore, "emergency escape");
    }

    private TeamPlan buildTeamPlan(GameState state, UnitInfo self, int role) {
        /*
         * 四名战士没有私有通信通道，所以合作必须来自“同一局面推导出同一计划”。
         * 这里先计算团队中心、共同集火目标、强力道具目标和队伍分散程度，
         * 再给当前角色分配一个具体的战术位置。
         */
        Position center = teamCenter(state, self);
        UnitInfo focusTarget = chooseSharedFocusTarget(state, self);
        UnitInfo healingTarget = chooseHealingDenyTarget(state, self);
        Position strongPowerUp = chooseSharedStrongPowerUp(state, self);
        int spread = teamSpread(state, self);

        String mode = PLAN_COMPACT;
        double baseScore = 390;
        if (center != null && (spread > MAX_TEAM_SPREAD || self.distanceTo(center) > SAFE_DISTANCE_TO_TEAM + 2)) {
            mode = PLAN_REGROUP;
            baseScore = 820;
        } else if (strongPowerUp != null && shouldContestStrongPowerUp(state, self, strongPowerUp)) {
            mode = PLAN_CONTEST_POWER;
            baseScore = 760;
        } else if (healingTarget != null && shouldDenyHealing(state, self, healingTarget)) {
            focusTarget = healingTarget;
            mode = PLAN_DENY_HEALING;
            baseScore = 780;
        } else if (focusTarget != null && shouldPincerFocus(state, self, focusTarget)) {
            mode = PLAN_PINCER;
            baseScore = 700;
        }

        Position roleDestination = roleDestinationForPlan(state, self, role, mode, center, focusTarget, strongPowerUp);
        return new TeamPlan(mode, focusTarget, strongPowerUp, roleDestination, baseScore);
    }

    private Position roleDestinationForPlan(
            GameState state,
            UnitInfo self,
            int role,
            String mode,
            Position center,
            UnitInfo focusTarget,
            Position strongPowerUp) {
        if (PLAN_REGROUP.equals(mode)) {
            return roleSlotAround(state, self, center, role, focusTarget == null ? null : focusTarget.getPosition());
        }

        if (PLAN_CONTEST_POWER.equals(mode) && strongPowerUp != null) {
            if (role == 2) {
                // 发育位直接抢核心资源。
                return strongPowerUp;
            }
            if (role == 3) {
                UnitInfo weak = weakestTeammateIncludingSelf(state, self);
                if (weak != null && healthRate(weak) < 0.45) {
                    return weak.getPosition();
                }
            }
            // 其他角色不和发育位挤同一格，而是靠近强力道具形成护送圈。
            return roleSlotAround(state, self, strongPowerUp, role, center);
        }

        if (PLAN_DENY_HEALING.equals(mode) && focusTarget != null) {
            Position healing = state.findNearestHealingPoint(focusTarget);
            if (healing != null) {
                if (role == 0 || role == 1) {
                    return roleSlotAround(state, self, healing, role, focusTarget.getPosition());
                }
                if (role == 2) {
                    Position usefulPowerUp = chooseBestUsefulPowerUp(state, self);
                    if (usefulPowerUp != null && self.distanceTo(usefulPowerUp) <= 5) {
                        return usefulPowerUp;
                    }
                }
                return roleSlotAround(state, self, midpoint(healing, focusTarget.getPosition()), role, healing);
            }
        }

        if (PLAN_PINCER.equals(mode) && focusTarget != null) {
            return tacticalPositionAroundTarget(state, self, focusTarget, role);
        }

        if (role == 2) {
            Position usefulPowerUp = chooseBestUsefulPowerUp(state, self);
            if (usefulPowerUp != null) {
                return usefulPowerUp;
            }
        }

        if (focusTarget != null && center != null) {
            return roleSlotAround(state, self, midpoint(center, focusTarget.getPosition()), role, focusTarget.getPosition());
        }
        return roleSlotAround(state, self, center, role, null);
    }

    private UnitInfo chooseSharedFocusTarget(GameState state, UnitInfo self) {
        /*
         * 这个目标只使用团队整体信息打分，避免每个战士因为自己的距离不同而选出完全不同目标。
         */
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        UnitInfo best = null;
        double bestScore = -9999;
        Position center = teamCenter(state, self);
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo enemy = enemies.get(i);
            double score = targetValue(state, self, enemy);
            score += teamPotentialDamage(state, self, enemy) * 13.0;
            score += teamMembersNear(state, self, enemy.getPosition(), 6) * 80.0;
            score -= enemySupportNear(state, self, enemy, 3) * 95.0;
            if (center != null) {
                score -= center.distanceTo(enemy.getPosition()) * 7.0;
            }
            if (enemy.getHealth() <= teamPotentialDamage(state, self, enemy)) {
                score += 180;
            }
            if (state.isHealingPoint(enemy.getPosition())) {
                score += 45;
            }
            if (best == null || score > bestScore) {
                best = enemy;
                bestScore = score;
            }
        }
        return best;
    }

    private UnitInfo chooseHealingDenyTarget(GameState state, UnitInfo self) {
        /*
         * 残血敌人通常会向最近治疗点或回血道具移动。与其四个人追着当前位置跑，
         * 更有效的方式是提前占据它的治疗路线和治疗点周围格子。
         */
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        UnitInfo best = null;
        double bestScore = -9999;
        Position center = teamCenter(state, self);
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo enemy = enemies.get(i);
            if (healthRate(enemy) > 0.52) {
                continue;
            }
            Position healing = state.findNearestHealingPoint(enemy);
            if (healing == null) {
                continue;
            }
            int enemyToHealing = enemy.distanceTo(healing);
            int teamToHealing = nearestTeamDistanceTo(state, self, healing);
            if (enemyToHealing > 7 || teamToHealing > enemyToHealing + 5) {
                continue;
            }

            double score = 600;
            score += (1.0 - healthRate(enemy)) * 520.0;
            score += enemy.getAttackPower() * 9.0 + enemy.getRange() * 30.0;
            score += (enemyToHealing - teamToHealing) * 55.0;
            score += teamMembersNear(state, self, healing, 5) * 70.0;
            if (center != null) {
                score -= center.distanceTo(healing) * 8.0;
            }
            if (best == null || score > bestScore) {
                best = enemy;
                bestScore = score;
            }
        }
        return best;
    }

    private boolean shouldDenyHealing(GameState state, UnitInfo self, UnitInfo enemy) {
        Position healing = state.findNearestHealingPoint(enemy);
        if (healing == null) {
            return false;
        }
        int enemyToHealing = enemy.distanceTo(healing);
        int teamToHealing = nearestTeamDistanceTo(state, self, healing);
        if (enemyToHealing <= 2 && teamToHealing <= 5) {
            return true;
        }
        if (teamPotentialDamage(state, self, enemy) >= enemy.getHealth()) {
            return true;
        }
        return healthRate(enemy) <= 0.42 && teamToHealing <= enemyToHealing + 3;
    }

    private Position chooseSharedStrongPowerUp(GameState state, UnitInfo self) {
        ArrayList<PowerUp> powerUps = state.getPowerUps();
        Position best = null;
        double bestScore = -9999;
        Position center = teamCenter(state, self);
        for (int i = 0; i < powerUps.size(); i++) {
            PowerUp powerUp = powerUps.get(i);
            Position position = powerUp.getPosition();
            if (!state.isStrongPowerUpAt(position)) {
                continue;
            }
            int teamDistance = nearestTeamDistanceTo(state, self, position);
            int enemyDistance = nearestEnemyDistanceTo(state, self, position);
            double score = 700 + powerUpValue(powerUp.getType(), self, 2);
            score -= teamDistance * 34.0;
            score += (enemyDistance - teamDistance) * 46.0;
            if (center != null) {
                score -= center.distanceTo(position) * 8.0;
            }
            if (best == null || score > bestScore) {
                best = position;
                bestScore = score;
            }
        }
        return best;
    }

    private boolean shouldContestStrongPowerUp(GameState state, UnitInfo self, Position strongPowerUp) {
        int teamDistance = nearestTeamDistanceTo(state, self, strongPowerUp);
        int enemyDistance = nearestEnemyDistanceTo(state, self, strongPowerUp);
        if (teamDistance <= 3) {
            return true;
        }
        if (teamDistance > 10) {
            return false;
        }
        return teamDistance <= enemyDistance + 2 && teamSpread(state, self) <= MAX_TEAM_SPREAD + 2;
    }

    private boolean shouldPincerFocus(GameState state, UnitInfo self, UnitInfo focusTarget) {
        if (focusTarget == null) {
            return false;
        }
        int teamPotential = teamPotentialDamage(state, self, focusTarget);
        int teamNearby = teamMembersNear(state, self, focusTarget.getPosition(), 6);
        int enemyNearby = enemySupportNear(state, self, focusTarget, 4);
        if (teamPotential >= focusTarget.getHealth()) {
            return true;
        }
        return teamNearby >= 2 && teamNearby >= enemyNearby && teamSpread(state, self) <= MAX_TEAM_SPREAD + 1;
    }

    private Position chooseBestUsefulPowerUp(GameState state, UnitInfo self) {
        ArrayList<PowerUp> powerUps = state.getPowerUps();
        Position best = null;
        double bestScore = -9999;
        for (int i = 0; i < powerUps.size(); i++) {
            PowerUp powerUp = powerUps.get(i);
            Position position = powerUp.getPosition();
            if (self.distanceTo(position) > POWER_UP_SEARCH_DISTANCE) {
                continue;
            }
            double score = powerUpValue(powerUp.getType(), self, 2);
            score -= self.distanceTo(position) * 28.0;
            score += (nearestEnemyDistanceTo(state, self, position) - self.distanceTo(position)) * 30.0;
            if (best == null || score > bestScore) {
                best = position;
                bestScore = score;
            }
        }
        return best;
    }

    private Position tacticalPositionAroundTarget(GameState state, UnitInfo self, UnitInfo target, int role) {
        Position focus = target.getPosition();
        Position center = teamCenter(state, self);
        Position preferred = preferredPincerSlot(focus, center, role);
        Position best = null;
        double bestScore = 999999;
        int desiredDistance = Math.max(1, self.getRange());
        for (int row = 0; row < state.getRows(); row++) {
            for (int col = 0; col < state.getCols(); col++) {
                Position position = new Position(row, col);
                if (!isOpenForSelf(state, self, position)) {
                    continue;
                }
                int distanceToTarget = position.distanceTo(focus);
                if (distanceToTarget == 0 || distanceToTarget > Math.max(2, self.getRange() + 1)) {
                    continue;
                }
                double score = 0;
                score += Math.abs(distanceToTarget - desiredDistance) * 45.0;
                score += self.distanceTo(position) * 9.0;
                score += position.distanceTo(preferred) * 22.0;
                score += incomingDamageAt(state, self, position) * 38.0;
                score -= teamMembersNear(state, self, position, 3) * 20.0;
                if (best == null || score < bestScore) {
                    best = position;
                    bestScore = score;
                }
            }
        }
        if (best != null) {
            return best;
        }
        return focus;
    }

    private Position roleSlotAround(
            GameState state,
            UnitInfo self,
            Position anchor,
            int role,
            Position pressurePoint) {
        if (anchor == null) {
            return null;
        }
        Position preferred = preferredTeamSlot(anchor, pressurePoint, role);
        return nearestOpenForSelfAround(state, self, preferred, anchor);
    }

    private Position preferredTeamSlot(Position anchor, Position pressurePoint, int role) {
        int rowDirection = 0;
        int colDirection = 1;
        if (pressurePoint != null) {
            int rowDelta = pressurePoint.getRow() - anchor.getRow();
            int colDelta = pressurePoint.getCol() - anchor.getCol();
            if (Math.abs(rowDelta) >= Math.abs(colDelta)) {
                rowDirection = rowDelta >= 0 ? 1 : -1;
                colDirection = 0;
            } else {
                rowDirection = 0;
                colDirection = colDelta >= 0 ? 1 : -1;
            }
        }

        if (role == 0) {
            // 先锋站在靠近压力点的一侧。
            return offset(anchor, rowDirection, colDirection);
        }
        if (role == 1) {
            // 支援站在侧翼，方便补刀或挡路。
            return offset(anchor, colDirection, -rowDirection);
        }
        if (role == 2) {
            // 发育位站在另一侧翼，保留转向道具的空间。
            return offset(anchor, -colDirection, rowDirection);
        }
        // 锚点站在队伍后侧，避免四个人全部压到同一格附近。
        return offset(anchor, -rowDirection, -colDirection);
    }

    private Position preferredPincerSlot(Position focus, Position center, int role) {
        if (center == null) {
            return preferredTeamSlot(focus, null, role);
        }

        int rowDelta = focus.getRow() - center.getRow();
        int colDelta = focus.getCol() - center.getCol();
        int rowDirection = 0;
        int colDirection = 0;
        if (Math.abs(rowDelta) >= Math.abs(colDelta)) {
            rowDirection = rowDelta >= 0 ? -1 : 1;
        } else {
            colDirection = colDelta >= 0 ? -1 : 1;
        }

        if (role == 0) {
            return offset(focus, rowDirection, colDirection);
        }
        if (role == 1) {
            return offset(focus, colDirection, -rowDirection);
        }
        if (role == 2) {
            return offset(focus, -colDirection, rowDirection);
        }
        return offset(focus, rowDirection * 2, colDirection * 2);
    }

    private Position nearestOpenForSelfAround(GameState state, UnitInfo self, Position preferred, Position anchor) {
        Position best = null;
        double bestScore = 999999;
        for (int row = 0; row < state.getRows(); row++) {
            for (int col = 0; col < state.getCols(); col++) {
                Position position = new Position(row, col);
                if (!isOpenForSelf(state, self, position)) {
                    continue;
                }
                double score = position.distanceTo(preferred) * 20.0 + position.distanceTo(anchor) * 6.0;
                score += incomingDamageAt(state, self, position) * 28.0;
                if (best == null || score < bestScore) {
                    best = position;
                    bestScore = score;
                }
            }
        }
        return best;
    }

    private boolean isOpenForSelf(GameState state, UnitInfo self, Position position) {
        return position != null && (position.equals(self.getPosition()) || state.isOpen(position));
    }

    private Position offset(Position position, int rowOffset, int colOffset) {
        return new Position(position.getRow() + rowOffset, position.getCol() + colOffset);
    }

    private Position midpoint(Position first, Position second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return new Position((first.getRow() + second.getRow()) / 2, (first.getCol() + second.getCol()) / 2);
    }

    private ArrayList<UnitInfo> livingAllies(GameState state, UnitInfo self) {
        ArrayList<UnitInfo> allies = new ArrayList<UnitInfo>();
        allies.add(self);
        ArrayList<UnitInfo> teammates = state.getLivingTeammates(self);
        for (int i = 0; i < teammates.size(); i++) {
            allies.add(teammates.get(i));
        }
        return allies;
    }

    private int teamSpread(GameState state, UnitInfo self) {
        ArrayList<UnitInfo> allies = livingAllies(state, self);
        int spread = 0;
        for (int i = 0; i < allies.size(); i++) {
            for (int j = i + 1; j < allies.size(); j++) {
                int distance = allies.get(i).distanceTo(allies.get(j));
                if (distance > spread) {
                    spread = distance;
                }
            }
        }
        return spread;
    }

    private int nearestTeamDistanceTo(GameState state, UnitInfo self, Position position) {
        int best = self.distanceTo(position);
        ArrayList<UnitInfo> teammates = state.getLivingTeammates(self);
        for (int i = 0; i < teammates.size(); i++) {
            int distance = teammates.get(i).distanceTo(position);
            if (distance < best) {
                best = distance;
            }
        }
        return best;
    }

    private int teamMembersNear(GameState state, UnitInfo self, Position position, int limit) {
        int count = self.distanceTo(position) <= limit ? 1 : 0;
        ArrayList<UnitInfo> teammates = state.getLivingTeammates(self);
        for (int i = 0; i < teammates.size(); i++) {
            if (teammates.get(i).distanceTo(position) <= limit) {
                count++;
            }
        }
        return count;
    }

    private int teamPotentialDamage(GameState state, UnitInfo self, UnitInfo target) {
        int damage = 0;
        ArrayList<UnitInfo> allies = livingAllies(state, self);
        for (int i = 0; i < allies.size(); i++) {
            UnitInfo ally = allies.get(i);
            int distance = ally.distanceTo(target);
            if (distance <= ally.getRange()) {
                damage += ally.getAttackPower();
            } else if (distance <= ally.getRange() + 2) {
                damage += Math.max(1, ally.getAttackPower() / 2);
            }
        }
        return damage;
    }

    private int enemySupportNear(GameState state, UnitInfo self, UnitInfo target, int limit) {
        int count = 0;
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo enemy = enemies.get(i);
            if (enemy.getId() != target.getId() && enemy.distanceTo(target) <= limit) {
                count++;
            }
        }
        return count;
    }

    private UnitInfo weakestTeammateIncludingSelf(GameState state, UnitInfo self) {
        UnitInfo best = self;
        ArrayList<UnitInfo> teammates = state.getLivingTeammates(self);
        for (int i = 0; i < teammates.size(); i++) {
            UnitInfo teammate = teammates.get(i);
            if (healthRate(teammate) < healthRate(best)) {
                best = teammate;
            }
        }
        return best;
    }

    private UnitInfo findKillTargetInRange(GameState state, UnitInfo self) {
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        UnitInfo best = null;
        double bestValue = -9999;
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo enemy = enemies.get(i);
            if (self.distanceTo(enemy) > self.getRange()) {
                continue;
            }
            if (expectedDamage(self, enemy) < enemy.getHealth()
                    && !teamCanFinishSoon(state, self, enemy)) {
                continue;
            }
            double value = targetValue(state, self, enemy);
            if (best == null || value > bestValue) {
                best = enemy;
                bestValue = value;
            }
        }
        return best;
    }

    private double targetValue(GameState state, UnitInfo self, UnitInfo enemy) {
        double missingHealth = enemy.getMaxHealth() - enemy.getHealth();
        double value = missingHealth * 1.4 + enemy.getAttackPower() * 7.0 + enemy.getRange() * 35.0;
        if (enemy.getHealth() <= self.getAttackPower() * 3) {
            value += 90;
        }
        if (state.isHealingPoint(enemy.getPosition())) {
            value += 40;
        }
        return value;
    }

    private double powerUpValue(PowerUpType type, UnitInfo self, int role) {
        if (type == PowerUpType.BATTLE_CORE) {
            return 900;
        }
        if (type == PowerUpType.POWER_CORE) {
            return 780;
        }
        if (type == PowerUpType.MEGA_HEALTH) {
            return healthRate(self) < 0.70 ? 760 : 560;
        }
        if (type == PowerUpType.RANGE) {
            return self.getRange() <= 1 ? 660 : 430;
        }
        if (type == PowerUpType.ATTACK) {
            return role == 0 ? 640 : 560;
        }
        return healthRate(self) < 0.62 ? 610 : 360;
    }

    private Action moveTowardSafely(GameState state, UnitInfo self, Position target, int role) {
        if (target == null || self.getPosition().equals(target)) {
            return safeReposition(state, self, role);
        }

        Direction bestDirection = null;
        double bestScore = -9999;
        Direction[] directions = Direction.values();
        for (int i = 0; i < directions.length; i++) {
            Position next = self.getPosition().move(directions[i]);
            if (!state.isOpen(next)) {
                continue;
            }

            double score = 600;
            score += (self.distanceTo(target) - next.distanceTo(target)) * 95.0;
            score -= movementDanger(state, self, next) * 45.0;
            score -= teamSplitCost(state, self, next, role);
            if (state.isHealingPoint(next) && self.getHealth() < self.getMaxHealth()) {
                score += 35;
            }
            if (bestDirection == null || score > bestScore) {
                bestDirection = directions[i];
                bestScore = score;
            }
        }

        if (bestDirection == null) {
            return state.moveToward(self, target);
        }
        return Action.move(bestDirection);
    }

    private Action safeReposition(GameState state, UnitInfo self, int role) {
        UnitInfo threat = nearestEnemy(state, self);
        Position teamCenter = teamCenter(state, self);
        Direction bestDirection = null;
        double bestScore = -9999;
        Direction[] directions = Direction.values();
        for (int i = 0; i < directions.length; i++) {
            Position next = self.getPosition().move(directions[i]);
            if (!state.isOpen(next)) {
                continue;
            }

            double score = 200;
            score -= movementDanger(state, self, next) * 55.0;
            score -= teamSplitCost(state, self, next, role);
            if (threat != null) {
                score += next.distanceTo(threat.getPosition()) * 12.0;
            }
            if (teamCenter != null) {
                score += (self.distanceTo(teamCenter) - next.distanceTo(teamCenter)) * 24.0;
            }

            if (bestDirection == null || score > bestScore) {
                bestDirection = directions[i];
                bestScore = score;
            }
        }
        if (bestDirection == null) {
            return Action.defend();
        }
        return Action.move(bestDirection);
    }

    private int roleIndex(GameState state, UnitInfo self) {
        int index = 0;
        ArrayList<UnitInfo> teammates = state.getLivingTeammates(self);
        for (int i = 0; i < teammates.size(); i++) {
            if (teammates.get(i).getId() < self.getId()) {
                index++;
            }
        }
        return Math.min(index, 3);
    }

    private String roleName(int role) {
        if (role == 0) {
            return "先锋：优先接战和压制";
        }
        if (role == 1) {
            return "支援：靠近队友并补刀";
        }
        if (role == 2) {
            return "发育：优先分配道具";
        }
        return "锚点：保持阵型和保护残局";
    }

    private UnitInfo mostThreatenedTeammate(GameState state, UnitInfo self) {
        ArrayList<UnitInfo> teammates = state.getLivingTeammates(self);
        UnitInfo best = null;
        double bestScore = -9999;
        for (int i = 0; i < teammates.size(); i++) {
            UnitInfo teammate = teammates.get(i);
            int damage = incomingDamageAt(state, self, teammate.getPosition());
            if (damage == 0 && healthRate(teammate) > 0.50) {
                continue;
            }
            double score = damage * 10.0 + (1.0 - healthRate(teammate)) * 180.0 - self.distanceTo(teammate) * 8.0;
            if (best == null || score > bestScore) {
                best = teammate;
                bestScore = score;
            }
        }
        return best;
    }

    private UnitInfo enemyThreateningPosition(GameState state, UnitInfo self, Position position) {
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        UnitInfo best = null;
        int bestDamage = -1;
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo enemy = enemies.get(i);
            if (position.distanceTo(enemy.getPosition()) <= enemy.getRange()
                    && enemy.getAttackPower() > bestDamage) {
                best = enemy;
                bestDamage = enemy.getAttackPower();
            }
        }
        return best;
    }

    private boolean teamCanFinishSoon(GameState state, UnitInfo self, UnitInfo enemy) {
        int damage = expectedDamage(self, enemy);
        ArrayList<UnitInfo> teammates = state.getLivingTeammates(self);
        for (int i = 0; i < teammates.size(); i++) {
            UnitInfo teammate = teammates.get(i);
            if (teammate.distanceTo(enemy) <= teammate.getRange()) {
                damage += Math.max(1, teammate.getAttackPower());
            } else if (teammate.distanceTo(enemy) <= teammate.getRange() + 1) {
                damage += Math.max(1, teammate.getAttackPower() / 2);
            }
        }
        return damage >= enemy.getHealth();
    }

    private boolean isBadSoloDuel(GameState state, UnitInfo self, UnitInfo enemy) {
        if (enemy == null || expectedDamage(self, enemy) >= enemy.getHealth()) {
            return false;
        }
        if (self.getAttackPower() >= enemy.getAttackPower() + 4 || self.getRange() > enemy.getRange()) {
            return false;
        }
        int myTurns = turnsToDefeat(enemy.getHealth(), Math.max(1, self.getAttackPower()));
        int enemyTurns = turnsToDefeat(self.getHealth(), Math.max(1, enemy.getAttackPower()));
        return enemyTurns <= myTurns;
    }

    private int expectedDamage(UnitInfo self, UnitInfo enemy) {
        return Math.min(self.getAttackPower(), enemy.getHealth());
    }

    private int turnsToDefeat(int health, int damage) {
        return (health + damage - 1) / damage;
    }

    private int counterDamageAfterAttack(GameState state, UnitInfo self, UnitInfo target) {
        int damage = 0;
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo enemy = enemies.get(i);
            if (target != null
                    && enemy.getId() == target.getId()
                    && expectedDamage(self, target) >= target.getHealth()) {
                continue;
            }
            if (enemyCanHitPosition(enemy, self.getPosition())) {
                damage += enemy.getAttackPower();
            }
        }
        return damage;
    }

    private int incomingDamageAt(GameState state, UnitInfo self, Position position) {
        int damage = 0;
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo enemy = enemies.get(i);
            if (position.distanceTo(enemy.getPosition()) <= enemy.getRange()) {
                damage += enemy.getAttackPower();
            }
        }
        return damage;
    }

    private int threatCountAt(GameState state, UnitInfo self, Position position) {
        int count = 0;
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo enemy = enemies.get(i);
            if (position.distanceTo(enemy.getPosition()) <= enemy.getRange()) {
                count++;
            }
        }
        return count;
    }

    private boolean enemyCanHitPosition(UnitInfo enemy, Position position) {
        return enemy != null && position.distanceTo(enemy.getPosition()) <= enemy.getRange();
    }

    private double movementDanger(GameState state, UnitInfo self, Position position) {
        return incomingDamageAt(state, self, position) + threatCountAt(state, self, position) * 4.0;
    }

    private double teamSplitCost(GameState state, UnitInfo self, Position next, int role) {
        UnitInfo teammate = nearestTeammate(state, self);
        if (teammate == null) {
            return 0;
        }
        int distance = next.distanceTo(teammate.getPosition());
        int allowed = role == 2 ? SAFE_DISTANCE_TO_TEAM + 2 : SAFE_DISTANCE_TO_TEAM;
        if (distance <= allowed) {
            return 0;
        }
        return (distance - allowed) * 42.0;
    }

    private int nearbyTeammates(GameState state, UnitInfo self, Position position, int limit) {
        int count = 0;
        ArrayList<UnitInfo> teammates = state.getLivingTeammates(self);
        for (int i = 0; i < teammates.size(); i++) {
            if (teammates.get(i).distanceTo(position) <= limit) {
                count++;
            }
        }
        return count;
    }

    private int nearestEnemyDistanceTo(GameState state, UnitInfo self, Position position) {
        int best = 9999;
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        for (int i = 0; i < enemies.size(); i++) {
            int distance = enemies.get(i).distanceTo(position);
            if (distance < best) {
                best = distance;
            }
        }
        return best;
    }

    private int nearestTeammateDistanceTo(GameState state, UnitInfo self, Position position) {
        int best = 9999;
        ArrayList<UnitInfo> teammates = state.getLivingTeammates(self);
        for (int i = 0; i < teammates.size(); i++) {
            int distance = teammates.get(i).distanceTo(position);
            if (distance < best) {
                best = distance;
            }
        }
        return best;
    }

    private UnitInfo nearestEnemy(GameState state, UnitInfo self) {
        return state.findNearestEnemy(self);
    }

    private UnitInfo nearestTeammate(GameState state, UnitInfo self) {
        ArrayList<UnitInfo> teammates = state.getLivingTeammates(self);
        UnitInfo best = null;
        int bestDistance = 9999;
        for (int i = 0; i < teammates.size(); i++) {
            UnitInfo teammate = teammates.get(i);
            int distance = self.distanceTo(teammate);
            if (best == null || distance < bestDistance) {
                best = teammate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private Position teamCenter(GameState state, UnitInfo self) {
        int rowSum = self.getRow();
        int colSum = self.getCol();
        int count = 1;
        ArrayList<UnitInfo> teammates = state.getLivingTeammates(self);
        for (int i = 0; i < teammates.size(); i++) {
            UnitInfo teammate = teammates.get(i);
            rowSum += teammate.getRow();
            colSum += teammate.getCol();
            count++;
        }
        Position center = new Position(rowSum / count, colSum / count);
        if (state.isOpen(center)) {
            return center;
        }
        return nearestOpenAround(state, center);
    }

    private Position nearestOpenAround(GameState state, Position target) {
        Position best = null;
        int bestDistance = 9999;
        for (int row = 0; row < state.getRows(); row++) {
            for (int col = 0; col < state.getCols(); col++) {
                Position position = new Position(row, col);
                if (!state.isOpen(position)) {
                    continue;
                }
                int distance = position.distanceTo(target);
                if (best == null || distance < bestDistance) {
                    best = position;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private Position nextPosition(UnitInfo self, Action action) {
        if (action == null || action.getType() != ActionType.MOVE || action.getDirection() == null) {
            return self.getPosition();
        }
        return self.getPosition().move(action.getDirection());
    }

    private double healthRate(UnitInfo unit) {
        return unit.getHealth() * 1.0 / Math.max(1, unit.getMaxHealth());
    }

    private Choice chooseBest(ArrayList<Choice> choices) {
        Choice best = null;
        for (int i = 0; i < choices.size(); i++) {
            Choice choice = choices.get(i);
            if (best == null || choice.score > best.score) {
                best = choice;
            }
        }
        return best;
    }

    private static class Choice {
        private final Action action;
        private final double score;
        private final String label;

        private Choice(Action action, double score, String label) {
            this.action = action;
            this.score = score;
            this.label = label;
        }
    }

    private static class TeamPlan {
        private final String mode;
        private final UnitInfo focusTarget;
        private final Position powerUpTarget;
        private final Position roleDestination;
        private final double baseScore;

        private TeamPlan(
                String mode,
                UnitInfo focusTarget,
                Position powerUpTarget,
                Position roleDestination,
                double baseScore) {
            this.mode = mode;
            this.focusTarget = focusTarget;
            this.powerUpTarget = powerUpTarget;
            this.roleDestination = roleDestination;
            this.baseScore = baseScore;
        }
    }
}
