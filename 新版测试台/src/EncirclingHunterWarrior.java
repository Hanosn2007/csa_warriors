import java.util.ArrayList;
import java.util.Comparator;
import java.util.PriorityQueue;

/*
 * EncirclingHunterWarrior 是当前 2v2 / 四队混战环境下的进阶示例策略。
 *
 * 这份策略的核心思想不是“看到敌人就冲”，而是用评分系统在多个候选行动中做选择：
 * 1. 能击杀时优先击杀，避免错过确定收益。
 * 2. 判断当前对攻是否亏损，血量、攻击力、射程明显劣势时先脱战。
 * 3. 前期优先避开无收益混战，同时争夺道具完成发育。
 * 4. 中期围绕低血量敌人、回血点、强力道具做压迫。
 * 5. 队友距离过远时降低分数，避免两个战士各自送掉。
 *
 * 文件中很多数值不是规则，而是“评分权重”。它们决定策略偏好：
 * 数值越高，该行为越容易被选中；惩罚越高，该行为越容易被放弃。
 */
public class EncirclingHunterWarrior extends Warrior {
    // 功能开关。默认只打开经过验证稳定的组合，其余保留为实验开关，方便后续继续搜索参数。
    private static boolean USE_OPENING_ROUTE = tunableBoolean("eh.openingRoute", false);
    private static boolean USE_INTERCEPT = tunableBoolean("eh.intercept", false);
    private static boolean USE_MOVE_RISK = tunableBoolean("eh.moveRisk", false);
    private static boolean USE_BATTLE_PLAN = tunableBoolean("eh.battlePlan", false);
    private static boolean USE_EMERGENCY_SURVIVAL = tunableBoolean("eh.emergencySurvival", false);
    private static boolean USE_GREEN_HANSON_AVOID = tunableBoolean("eh.greenHansonAvoid", false);
    private static boolean USE_YELLOW_HANSON_AVOID = tunableBoolean("eh.yellowHansonAvoid", false);
    private static boolean USE_BLUE_YELLOW_HANSON_AVOID = tunableBoolean("eh.blueYellowHansonAvoid", false);
    private static boolean USE_COLOR_PROFILE = tunableBoolean("eh.colorProfile", false);
    private static boolean USE_BLUE_GREEN_EDGE_PROFILE = tunableBoolean("eh.blueGreenEdgeProfile", false);
    private static boolean USE_COLOR_PROFILE_V2 = tunableBoolean("eh.colorProfileV2", true);

    // 道具相关权重：基础分越高越爱抢道具；距离惩罚和敌人更近惩罚越高，越不愿意绕远或硬抢。
    private static double POWER_BASE_SCORE = tunableDouble("eh.powerBaseScore", 640.0);
    private static double POWER_DISTANCE_PENALTY = tunableDouble("eh.powerDistancePenalty", 19.0);
    private static double POWER_ENEMY_CLOSER_PENALTY = tunableDouble("eh.powerEnemyCloserPenalty", 190.0);
    private static double POWER_MIN_SCORE = tunableDouble("eh.powerMinScore", -9999.0);
    private static double OPENING_CHASE_RESOURCE_PENALTY = tunableDouble("eh.openingChaseResourcePenalty", 150.0);

    // 对特定已知策略的权重。这里不是使用额外接口，只是根据公开的名字和场面表现调整目标选择倾向。
    private static double HANSON_CHASE_BONUS = tunableDouble("eh.hansonChaseBonus", 160.0);
    private static double HANSON_ATTACK_BONUS = tunableDouble("eh.hansonAttackBonus", 120.0);
    private static double HANSON_TARGET_BONUS = tunableDouble("eh.hansonTargetBonus", 1.2);
    private static double PIGGOD_TWO_ATTACK_BONUS = tunableDouble("eh.piggodTwoAttackBonus", 0.0);
    private static double PIGGOD_TWO_CHASE_BONUS = tunableDouble("eh.piggodTwoChaseBonus", 0.0);
    private static double PIGGOD_TWO_TARGET_BONUS = tunableDouble("eh.piggodTwoTargetBonus", 0.0);
    private static double DANGER_PIGGOD_TWO_ATTACK_BONUS =
            tunableDouble("eh.dangerPiggodTwoAttackBonus", 0.0);
    private static double DANGER_PIGGOD_TWO_CHASE_BONUS =
            tunableDouble("eh.dangerPiggodTwoChaseBonus", 0.0);
    private static double DANGER_PIGGOD_TWO_TARGET_BONUS =
            tunableDouble("eh.dangerPiggodTwoTargetBonus", 0.0);
    private static double RANGED_RISK_ATTACK_PENALTY = tunableDouble("eh.rangedRiskAttackPenalty", 0.0);
    private static double RANGED_RISK_CHASE_PENALTY = tunableDouble("eh.rangedRiskChasePenalty", 0.0);
    private static double RANGED_RISK_TARGET_PENALTY = tunableDouble("eh.rangedRiskTargetPenalty", 0.0);
    private static double LOW_HEALTH_CHASE_BONUS = tunableDouble("eh.lowHealthChaseBonus", 100.0);

    // 回血点相关权重。重点是避免残血时走进敌人已经覆盖的回血点，被“回血-挨打”循环拖死。
    private static double HEALING_TARGET_ATTACK_PENALTY = tunableDouble("eh.healingTargetAttackPenalty", 70.0);
    private static double HEALING_TARGET_VALUE_PENALTY = tunableDouble("eh.healingTargetValuePenalty", 0.7);
    private static double HEALING_UNDER_FIRE_PENALTY = tunableDouble("eh.healingUnderFirePenalty", 0.0);
    private static double HEALING_ENTRY_DAMAGE_PENALTY = tunableDouble("eh.healingEntryDamagePenalty", 18.0);
    private static double HEALING_ENTRY_HUNTER_PENALTY = tunableDouble("eh.healingEntryHunterPenalty", 70.0);
    private static double THIRD_PARTY_FORCE_HEALTH_RATE = tunableDouble("eh.thirdPartyForceHealthRate", 0.0);
    private static double THIRD_PARTY_ATTACK_PENALTY = tunableDouble("eh.thirdPartyAttackPenalty", 0.0);
    private static double THIRD_PARTY_CHASE_PENALTY = tunableDouble("eh.thirdPartyChasePenalty", 0.0);
    private static double SEVERE_DUEL_HEALTH_RATE = tunableDouble("eh.severeDuelHealthRate", -1.0);
    private static double SEVERE_DUEL_ATTACK_TURNS = tunableDouble("eh.severeDuelAttackTurns", 0.0);
    private static double LOW_HEALTH_POWER_UP_THREAT_PENALTY =
            tunableDouble("eh.lowHealthPowerUpThreatPenalty", 180.0);
    private static double TEAMMATE_THREAT_BONUS_SCALE = tunableDouble("eh.teammateThreatBonusScale", 1.0);

    // 边角和队友距离惩罚。边角容易被堵，队友太远时无法互相补刀或救援。
    private static double EDGE_TRAP_POWER_PENALTY = tunableDouble("eh.edgeTrapPowerPenalty", 60.0);
    private static double EDGE_TRAP_MOVE_PENALTY = tunableDouble("eh.edgeTrapMovePenalty", 40.0);
    private static double BLUE_GREEN_EDGE_POWER_PENALTY =
            tunableDouble("eh.blueGreenEdgePowerPenalty", 60.0);
    private static double BLUE_GREEN_EDGE_MOVE_PENALTY =
            tunableDouble("eh.blueGreenEdgeMovePenalty", 40.0);
    private static double NEAR_THREAT_MOVE_PENALTY = tunableDouble("eh.nearThreatMovePenalty", 0.0);
    private static double TEAM_SPLIT_PENALTY = tunableDouble("eh.teamSplitPenalty", 8.0);

    // 开局避战窗口。Red / Blue 起点更容易早期被卷入战斗，所以给了更长的避战时间。
    private static int OPENING_AVOID_BUFFER = tunableInt("eh.openingAvoidBuffer", 3);
    private static int OPENING_TURN_LIMIT = tunableInt("eh.openingTurnLimit", 18);
    private static int OPENING_TURN_LIMIT_RED = tunableInt("eh.openingTurnLimitRed", 22);
    private static int OPENING_TURN_LIMIT_BLUE = tunableInt("eh.openingTurnLimitBlue", 22);
    private static int OPENING_TURN_LIMIT_GREEN = tunableInt("eh.openingTurnLimitGreen", -1);
    private static int OPENING_TURN_LIMIT_YELLOW = tunableInt("eh.openingTurnLimitYellow", -1);
    private static int STAGNATION_BREAK_TURNS = tunableInt("eh.stagnationBreakTurns", 9999);
    private static int ENDGAME_GUARD_TURN = tunableInt("eh.endgameGuardTurn", 9999);
    private static int ENDGAME_HEALTH_MARGIN = tunableInt("eh.endgameHealthMargin", 18);

    private int turnsTaken;
    private String currentTeamName = "";
    private String lastVisibleSnapshot = "";
    private int stagnantTurns;

    public EncirclingHunterWarrior() {
        super("Encircling Hunter");
    }

    @Override
    public Action chooseAction(GameState state, UnitInfo self) {
        turnsTaken++;
        currentTeamName = self.getTeamName();
        updateVisibleStagnation(state);

        // trace 会显示在可视化调试面板中。这里只放关键判断，方便观察“为什么这一回合这么走”。
        state.traceValue("role", roleName(state, self));
        state.traceValue("self turn", turnsTaken);
        state.traceValue("visible stagnation", stagnantTurns);
        state.traceValue("health rate", String.format("%.2f", healthRate(self)));
        state.traceValue("team readiness", String.format("%.2f", teamReadiness(state, self)));
        state.traceValue("incoming damage here", incomingDamageAt(state, self, self.getPosition()));
        state.traceValue("second threat count", secondThreatCount(state, self, null));
        state.traceUnit("primary threat", findPrimaryThreat(state, self));
        state.traceUnit("unwinnable duel threat", findUnwinnableDuelThreat(state, self));

        // 最高优先级：如果本次攻击能直接击杀，就先拿确定收益。
        UnitInfo killTarget = findBestEnemyInRange(state, self, self.getRange(), true);
        if (killTarget != null) {
            state.trace("mode = finish target");
            state.traceUnit("target", killTarget);
            return Action.attack(killTarget.getId());
        }

        // 极端危险时直接进入生存逻辑，避免为了抢道具或追人被秒杀。
        Choice emergencyChoice = createEmergencySurvivalChoice(state, self);
        if (emergencyChoice != null) {
            state.trace("mode = " + emergencyChoice.label);
            state.traceValue("score", emergencyChoice.score);
            return emergencyChoice.action;
        }

        // 如果当前对手在数学上对攻无胜算，就尝试脱战并寻找回血点或道具翻盘。
        UnitInfo unwinnableThreat = findUnwinnableDuelThreat(state, self);
        if (unwinnableThreat != null && !canHelpTeammateCounterKill(state, self, unwinnableThreat)) {
            Choice comebackChoice = createComebackDisengageChoice(state, self, unwinnableThreat);
            if (comebackChoice != null) {
                state.trace("mode = " + comebackChoice.label);
                state.traceUnit("target", unwinnableThreat);
                state.traceValue("score", comebackChoice.score);
                return comebackChoice.action;
            }
        }

        // 开局只在敌人逼近或即将被攻击时触发避战，目标是让敌方先和第三方发生碰撞。
        Choice openingChoice = createOpeningAvoidChoice(state, self);
        if (openingChoice != null) {
            state.trace("mode = " + openingChoice.label);
            state.traceValue("score", openingChoice.score);
            return openingChoice.action;
        }

        // 如果到后期已经按存活数/总血量领先，策略转为保守，避免无意义追击送掉优势。
        Choice endgameChoice = createEndgameLeadChoice(state, self);
        if (endgameChoice != null) {
            state.trace("mode = " + endgameChoice.label);
            state.traceValue("score", endgameChoice.score);
            return endgameChoice.action;
        }

        // 长时间没有状态变化时，主动寻找破局目标，避免被无进展规则判负。
        Choice stagnationChoice = createStagnationBreakChoice(state, self);
        if (stagnationChoice != null) {
            state.trace("mode = " + stagnationChoice.label);
            state.traceValue("score", stagnationChoice.score);
            return stagnationChoice.action;
        }

        // 强制攻击只用于“收益足够明确”的情况。否则进入下面的统一评分系统。
        UnitInfo attackTarget = findBestEnemyInRange(state, self, self.getRange(), false);
        if (attackTarget != null && shouldForceAttack(state, self, attackTarget)) {
            state.trace("mode = forced attack");
            state.traceUnit("target", attackTarget);
            return Action.attack(attackTarget.getId());
        }

        // 常规决策：把攻击、撤退、回血、抢道具、追击等候选动作都加入列表，用分数最高者执行。
        ArrayList<Choice> choices = new ArrayList<Choice>();
        addAttackChoices(choices, state, self);
        addEscapeChoice(choices, state, self);
        addHealingChoice(choices, state, self);
        addPowerUpChoice(choices, state, self);
        addOpeningRouteChoice(choices, state, self);
        addInterceptChoice(choices, state, self);
        addChaseChoices(choices, state, self);

        Choice best = chooseBest(choices);
        if (best != null) {
            state.trace("mode = " + best.label);
            state.traceValue("score", best.score);
            return best.action;
        }

        state.trace("mode = safe reposition");
        return safeReposition(state, self);
    }

    private boolean shouldForceAttack(GameState state, UnitInfo self, UnitInfo target) {
        // 这个方法决定“是否跳过评分系统，直接攻击”。
        // 只有击杀、队友可补刀、或局面足够安全时才强制攻击；否则交给候选评分决定。
        if (expectedDamage(self, target) >= target.getHealth()) {
            return true;
        }
        if (canTeamFinishNow(state, self, target)) {
            return true;
        }
        if (shouldAvoidAttackExchange(state, self, target)) {
            return false;
        }
        BattlePlan battlePlan = evaluateBattlePlan(state, self, target);
        if (USE_BATTLE_PLAN && battlePlan.shouldAvoid && !battlePlan.teamCanFinish) {
            return false;
        }
        if (isOpening() && !battleReady(state, self) && target.getHealth() > self.getAttackPower() * 2) {
            return false;
        }
        if (immediateThreatCount(state, self) >= 2 && healthRate(self) < 0.70) {
            return false;
        }
        if (state.isHealingPoint(target.getPosition()) && expectedDamage(self, target) < target.getHealth()) {
            return false;
        }
        if (target.isDefending() && state.isHealingPoint(target.getPosition())) {
            return false;
        }
        if (THIRD_PARTY_FORCE_HEALTH_RATE > 0
                && healthRate(self) < THIRD_PARTY_FORCE_HEALTH_RATE
                && secondThreatCount(state, self, target) > 0
                && expectedDamage(self, target) < target.getHealth()
                && !canTeamFinishNow(state, self, target)) {
            return false;
        }
        if (isHanson(target)
                && self.getHealth() > self.getMaxHealth() * 0.50
                && (!USE_BATTLE_PLAN || battlePlan.score >= -20)) {
            return true;
        }
        if (target.getHealth() <= self.getAttackPower() * 3 && self.getHealth() > self.getMaxHealth() * 0.35) {
            return true;
        }
        return healthRate(self) > 0.50 && dangerAround(state, self) <= 3.5;
    }

    private void addAttackChoices(ArrayList<Choice> choices, GameState state, UnitInfo self) {
        // 攻击候选：只考虑当前射程内的敌人。
        // 分数由伤害、目标价值、队友是否能配合、反击风险共同决定。
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo enemy = enemies.get(i);
            if (self.distanceTo(enemy) > self.getRange()) {
                continue;
            }

            int damage = expectedDamage(self, enemy);
            double score = 1000 + damage * 2.0 + attackValue(self, enemy) * 18;
            BattlePlan battlePlan = evaluateBattlePlan(state, self, enemy);
            if (expectedDamage(self, enemy) >= enemy.getHealth()) {
                score += 220;
            }
            if (canTeamFinishNow(state, self, enemy)) {
                score += 180;
            }
            if (USE_BATTLE_PLAN) {
                score += battlePlan.score * 1.2;
                if (battlePlan.teamCanFinish) {
                    score += 110;
                }
                if (battlePlan.shouldAvoid) {
                    score -= 180;
                }
            }
            if (isHanson(enemy)) {
                score += hansonAttackBonus();
            }
            if (isPlz(enemy)) {
                score += 60;
            }
            if (isEarlyPiggodTwo(state, enemy)) {
                if (shouldPressurePiggodTwo(self, enemy)) {
                    score += 110;
                } else {
                    score -= 10;
                }
            }
            if (isPiggodTwo(enemy) && shouldPressurePiggodTwo(self, enemy)) {
                score += PIGGOD_TWO_ATTACK_BONUS;
            }
            if (isDangerPiggodTwo(self, enemy)) {
                score += DANGER_PIGGOD_TWO_ATTACK_BONUS;
            }
            if (isRangedDuelRisk(state, self, enemy)) {
                score -= RANGED_RISK_ATTACK_PENALTY;
            }
            if (enemyCanHitPosition(enemy, self.getPosition())) {
                score += 80;
            }
            score += scaledTeammateThreatBonus(state, self, enemy);
            score -= attackRiskPenalty(state, self, enemy);
            if (teammateNearTarget(state, self, enemy, 4)) {
                score += 90;
            }
            if (shouldAvoidAttackExchange(state, self, enemy)) {
                score -= 150;
            }
            if (expectedDamage(self, enemy) < enemy.getHealth() && !canTeamFinishNow(state, self, enemy)) {
                score -= secondThreatCount(state, self, enemy) * THIRD_PARTY_ATTACK_PENALTY;
            }
            if (isOpening() && !battleReady(state, self) && expectedDamage(self, enemy) < enemy.getHealth()) {
                score -= 220;
            }
            if (immediateThreatCount(state, self) >= 2 && expectedDamage(self, enemy) < enemy.getHealth()) {
                score -= 240;
            }
            if (state.isHealingPoint(enemy.getPosition()) && expectedDamage(self, enemy) < enemy.getHealth()) {
                score -= HEALING_TARGET_ATTACK_PENALTY;
                if (damage <= 8) {
                    score -= 50;
                }
            }
            choices.add(new Choice(Action.attack(enemy.getId()), score, "attack"));
        }
    }

    private void addEscapeChoice(ArrayList<Choice> choices, GameState state, UnitInfo self) {
        // 撤退候选：当血量较低、被多人威胁、或对攻明显亏损时才加入。
        // 这避免策略在安全状态下过度保守。
        UnitInfo primaryThreat = findPrimaryThreat(state, self);
        if (primaryThreat == null) {
            return;
        }

        double currentHealthRate = healthRate(self);
        double danger = dangerAround(state, self);
        int immediateThreats = immediateThreatCount(state, self);
        boolean surrounded = immediateThreats >= 2 && currentHealthRate < 0.66;
        boolean losingExchange = self.distanceTo(primaryThreat) <= self.getRange()
                && shouldAvoidAttackExchange(state, self, primaryThreat);
        boolean secondThreatClose = secondThreatCount(state, self, primaryThreat) > 0
                && currentHealthRate < 0.60;
        if (!surrounded && !losingExchange && !secondThreatClose && (currentHealthRate > 0.34 || danger < 2.0)) {
            return;
        }

        double score = 850 + (1.0 - currentHealthRate) * 90 + danger * 18;
        if (surrounded) {
            score += 180;
        }
        if (losingExchange) {
            score += 230;
        }
        if (secondThreatClose) {
            score += 95;
        }
        if (state.isHealingPoint(self.getPosition())) {
            score -= 80;
        }

        choices.add(new Choice(moveAwaySafely(state, self, primaryThreat.getPosition()), score, "escape"));
    }

    private void addHealingChoice(ArrayList<Choice> choices, GameState state, UnitInfo self) {
        // 回血候选：血量不满时才考虑。
        // 如果回血点已经被敌人覆盖，会扣分，避免进入“回血点被守尸”的循环。
        Position healingPoint = state.findNearestHealingPoint(self);
        if (healingPoint == null || self.getHealth() >= self.getMaxHealth()) {
            return;
        }

        double currentHealthRate = healthRate(self);
        if (currentHealthRate > 0.52 && !self.getPosition().equals(healingPoint)) {
            return;
        }

        int missingHealth = self.getMaxHealth() - self.getHealth();
        int distance = self.distanceTo(healingPoint);
        double score = 700 + missingHealth * 0.65 - distance * 5.0 - dangerAround(state, self) * 10.0;
        boolean atHealingPoint = self.getPosition().equals(healingPoint);
        if (!atHealingPoint) {
            score -= incomingDamageAt(state, self, healingPoint) * HEALING_ENTRY_DAMAGE_PENALTY;
            score -= nearbyHunterCountAt(state, self, healingPoint) * HEALING_ENTRY_HUNTER_PENALTY;
        }
        if (atHealingPoint && incomingDamageAt(state, self, self.getPosition()) > 0) {
            score -= HEALING_UNDER_FIRE_PENALTY;
        }

        if (currentHealthRate <= 0.35) {
            score += 80;
        } else if (currentHealthRate <= 0.55) {
            score += 40;
        }

        Action action;
        if (atHealingPoint) {
            score += 24;
            action = Action.defend();
        } else {
            action = moveToPosition(state, self, healingPoint, null);
        }

        choices.add(new Choice(action, score, "healing"));
    }

    private void addPowerUpChoice(ArrayList<Choice> choices, GameState state, UnitInfo self) {
        // 道具候选：前中期的重要发育来源。
        // 这里会比较自己、队友、敌人到道具的距离，避免两个队友抢同一个道具。
        ArrayList<Position> powerUps = state.getPowerUpPositions();
        if (powerUps.size() == 0) {
            return;
        }

        for (int i = 0; i < powerUps.size(); i++) {
            Position powerUp = powerUps.get(i);
            int distance = self.distanceTo(powerUp);
            if (distance > 9) {
                continue;
            }

            int nearestEnemyDistance = nearestEnemyDistanceTo(state, self, powerUp);
            int nearestTeammateDistance = nearestTeammateDistanceTo(state, self, powerUp);
            int dangerAtPowerUp = incomingDamageAt(state, self, powerUp);
            PowerRace race = evaluatePowerRace(state, self, powerUp);
            double score = powerBaseScore() - distance * powerDistancePenalty();
            score += race.score;
            if (!race.worthContesting) {
                score -= 260;
            }
            if (distance <= 3) {
                score += 50;
            }
            if (!battleReady(state, self)) {
                score += isHarvester(state, self) ? 95 : 45;
            }
            if (isOpening() && isScout(self)) {
                score += 55;
            }
            if (isOpening() && !isScout(self) && nearestTeammateDistance > 4) {
                score -= 35;
            }
            if (distance < nearestEnemyDistance) {
                score += 95;
            } else if (nearestEnemyDistance < distance) {
                score -= powerEnemyCloserPenalty();
            }
            score -= dangerAtPowerUp * 35.0;
            if (isOpening() && dangerAtPowerUp > 0) {
                score -= 80;
            }
            if (nearestTeammateDistance < distance) {
                score -= 45;
            }
            if (nearestTeammateDistance <= distance && isHarvester(state, self)) {
                score -= 80;
            }
            UnitInfo hanson = nearestHanson(state, self);
            if (hanson != null && self.distanceTo(hanson) <= 5 && battleReady(state, self)) {
                score -= 80;
            }
            if (healthRate(self) <= 0.45) {
                score -= 80;
                UnitInfo threat = findPrimaryThreat(state, self);
                if (threat != null
                        && self.distanceTo(threat) <= threat.getRange() + 3
                        && distance > 2
                        && nearestEnemyDistance <= distance + 1) {
                    score -= LOW_HEALTH_POWER_UP_THREAT_PENALTY;
                }
            }
            score -= edgeTrapPenalty(state, self, powerUp) * edgeTrapPowerPenalty();

            Action action = moveToPosition(state, self, powerUp, null);
            score -= movementRiskPenalty(state, self, action);
            score -= teamSplitPenalty(state, self, nextPosition(self, action));
            if (score >= POWER_MIN_SCORE) {
                choices.add(new Choice(action, score, "power-up"));
            }
        }
    }

    private void addOpeningRouteChoice(ArrayList<Choice> choices, GameState state, UnitInfo self) {
        if (!USE_OPENING_ROUTE) {
            return;
        }
        if (!isOpening() || immediateThreatCount(state, self) > 0) {
            return;
        }
        Position target = openingRouteTarget(state, self);
        if (target == null || self.getPosition().equals(target)) {
            return;
        }
        Position nearbyPowerUp = state.findNearestPowerUp(self);
        if (nearbyPowerUp != null && self.distanceTo(nearbyPowerUp) <= 3) {
            return;
        }

        Action action = moveToPosition(state, self, target, null);
        double score = isHarvester(state, self) ? 470 : 410;
        score += (self.distanceTo(target) - nextPosition(self, action).distanceTo(target)) * 55.0;
        score -= movementRiskPenalty(state, self, action);
        if (isProtector(state, self)) {
            UnitInfo teammate = findNearestTeammate(state, self);
            if (teammate != null && nextPosition(self, action).distanceTo(teammate.getPosition()) <= 5) {
                score += 45;
            }
        }
        choices.add(new Choice(action, score, "opening route"));
    }

    private void addInterceptChoice(ArrayList<Choice> choices, GameState state, UnitInfo self) {
        if (!USE_INTERCEPT) {
            return;
        }
        if (!battleReady(state, self) && healthRate(self) < 0.72) {
            return;
        }

        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        Position bestPosition = null;
        UnitInfo bestEnemy = null;
        double bestScore = -9999;
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo enemy = enemies.get(i);
            Position resource = predictedEnemyResourceTarget(state, enemy);
            if (resource == null) {
                continue;
            }
            Position intercept = bestInterceptPosition(state, self, enemy, resource);
            if (intercept == null) {
                continue;
            }

            int myDistance = self.distanceTo(intercept);
            int enemyDistance = enemy.distanceTo(resource);
            if (myDistance > enemyDistance + 3) {
                continue;
            }

            double score = 340 + (enemyDistance - myDistance) * 36.0;
            score += enemy.getRange() < self.getRange() ? 80 : 0;
            score += enemy.getHealth() <= self.getAttackPower() * 4 ? 70 : 0;
            score += isHanson(enemy) ? 45 : 0;
            score -= incomingDamageAt(state, self, intercept) * 48.0;
            if (teammateNearTarget(state, self, enemy, 6)) {
                score += 45;
            }
            if (isHarvester(state, self) && !battleReady(state, self)) {
                score -= 80;
            }
            if (score > bestScore) {
                bestScore = score;
                bestPosition = intercept;
                bestEnemy = enemy;
            }
        }

        if (bestPosition != null && bestScore >= 360) {
            Action action = moveToPosition(state, self, bestPosition, bestEnemy);
            bestScore -= movementRiskPenalty(state, self, action);
            choices.add(new Choice(action, bestScore, "intercept resource"));
        }
    }

    private void addChaseChoices(ArrayList<Choice> choices, GameState state, UnitInfo self) {
        // 追击候选：用于逼迫低血量敌人、压制关键敌人、或与队友形成夹击。
        // 如果计算出单挑无胜算且队友无法补刀，就不会加入这个追击候选。
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo enemy = enemies.get(i);
            if (isUnwinnableDuel(state, self, enemy) && !canHelpTeammateCounterKill(state, self, enemy)) {
                continue;
            }
            int distance = self.distanceTo(enemy);
            double score = 260 + attackValue(self, enemy) * 45 - distance * 6.0;
            BattlePlan battlePlan = evaluateBattlePlan(state, self, enemy);
            if (USE_BATTLE_PLAN) {
                score += battlePlan.score * 0.55;
                if (battlePlan.teamCanFinish) {
                    score += 90;
                }
                if (battlePlan.shouldAvoid && !battlePlan.canWinDuel) {
                    score -= 130;
                }
            }
            if (isHanson(enemy)) {
                score += hansonChaseBonus();
            }
            if (isPlz(enemy) && distance <= 8) {
                score += 90;
            }
            if (isEarlyPiggodTwo(state, enemy)) {
                if (shouldPressurePiggodTwo(self, enemy)) {
                    score += 120;
                } else {
                    score -= 10;
                }
            }
            if (isPiggodTwo(enemy) && shouldPressurePiggodTwo(self, enemy)) {
                score += PIGGOD_TWO_CHASE_BONUS;
            }
            if (isDangerPiggodTwo(self, enemy)) {
                score += DANGER_PIGGOD_TWO_CHASE_BONUS;
            }
            if (isRangedDuelRisk(state, self, enemy)) {
                score -= RANGED_RISK_CHASE_PENALTY;
            }
            if (enemy.getHealth() <= self.getAttackPower() * 3) {
                score += LOW_HEALTH_CHASE_BONUS;
            }
            score += scaledTeammateThreatBonus(state, self, enemy);
            if (teammateNearTarget(state, self, enemy, 6)) {
                score += 90;
            }
            if (!isScout(self) && teammateNearTarget(state, self, enemy, 5)) {
                score += 45;
            }
            if (isScout(self) && !teammateNearTarget(state, self, enemy, 6)) {
                score -= 35;
            }
            Position nearestPowerUp = state.findNearestPowerUp(self);
            if (isOpening()
                    && !battleReady(state, self)
                    && nearestPowerUp != null
                    && self.distanceTo(nearestPowerUp) <= 6
                    && distance > self.getRange() + 1
                    && !teammateNearTarget(state, self, enemy, 5)) {
                score -= isHarvester(state, self) ? OPENING_CHASE_RESOURCE_PENALTY : 80;
            }
            if (isOpening() && !battleReady(state, self) && enemy.getHealth() > self.getAttackPower() * 3) {
                score -= 170;
            }
            if (healthRate(self) <= 0.45) {
                score -= 80;
            }
            if (expectedDamage(self, enemy) < enemy.getHealth() && !canTeamFinishNow(state, self, enemy)) {
                score -= secondThreatCount(state, self, enemy) * THIRD_PARTY_CHASE_PENALTY;
            }
            Action action = moveToPosition(state, self, enemy.getPosition(), enemy);
            score -= movementRiskPenalty(state, self, action);
            score -= teamSplitPenalty(state, self, nextPosition(self, action));
            choices.add(new Choice(action, score, "chase"));
        }
    }

    private UnitInfo findBestEnemyInRange(GameState state, UnitInfo self, int range, boolean mustKill) {
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        UnitInfo bestEnemy = null;
        double bestValue = -9999;

        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo enemy = enemies.get(i);
            if (self.distanceTo(enemy) > range) {
                continue;
            }
            if (mustKill && expectedDamage(self, enemy) < enemy.getHealth()) {
                continue;
            }

            double value = attackValue(self, enemy);
            if (isHanson(enemy)) {
                value += hansonTargetBonus();
            }
            if (isPlz(enemy)) {
                value += 0.8;
            }
            if (isEarlyPiggodTwo(state, enemy)) {
                if (shouldPressurePiggodTwo(self, enemy)) {
                    value += 0.8;
                } else {
                    value -= 0.1;
                }
            }
            if (isPiggodTwo(enemy) && shouldPressurePiggodTwo(self, enemy)) {
                value += PIGGOD_TWO_TARGET_BONUS;
            }
            if (isDangerPiggodTwo(self, enemy)) {
                value += DANGER_PIGGOD_TWO_TARGET_BONUS;
            }
            if (isRangedDuelRisk(state, self, enemy)) {
                value -= RANGED_RISK_TARGET_PENALTY;
            }
            if (enemyCanHitPosition(enemy, self.getPosition())) {
                value += 0.6;
            }
            double teammateThreatValue = scaledTeammateThreatBonus(state, self, enemy);
            if (teammateThreatValue > 0) {
                value += 0.8 * TEAMMATE_THREAT_BONUS_SCALE;
            }
            if (canTeamFinishNow(state, self, enemy)) {
                value += 1.1;
            }
            if (state.isHealingPoint(enemy.getPosition()) && expectedDamage(self, enemy) < enemy.getHealth()) {
                value -= HEALING_TARGET_VALUE_PENALTY;
            }
            if (teammateNearTarget(state, self, enemy, 4)) {
                value += 0.8;
            }
            if (bestEnemy == null || value > bestValue) {
                bestEnemy = enemy;
                bestValue = value;
            }
        }

        return bestEnemy;
    }

    private Action moveToPosition(GameState state, UnitInfo self, Position target, UnitInfo ignoredEnemy) {
        if (target == null) {
            return safeReposition(state, self);
        }
        if (self.getPosition().equals(target)) {
            if (state.isHealingPoint(self.getPosition()) && self.getHealth() < self.getMaxHealth()) {
                return Action.defend();
            }
            return safeReposition(state, self);
        }

        CounterPathfinder pathfinder = new CounterPathfinder(state, self, ignoredEnemy);
        pathfinder.addEnemyCost();
        CounterBlock end = pathfinder.findPath(target, self.getPosition());
        if (end != null && end.getParent() != null) {
            CounterBlock next = end.getParent();
            return state.moveToward(self, new Position(next.getRow(), next.getCol()));
        }
        return state.moveToward(self, target);
    }

    private Choice createOpeningAvoidChoice(GameState state, UnitInfo self) {
        if (!isOpening()) {
            return null;
        }

        UnitInfo nearestEnemy = findPrimaryThreat(state, self);
        if (nearestEnemy == null) {
            return null;
        }

        int distance = self.distanceTo(nearestEnemy);
        int immediateThreats = immediateThreatCount(state, self);
        if (distance > nearestEnemy.getRange() + OPENING_AVOID_BUFFER && immediateThreats == 0) {
            return null;
        }
        if (battleReady(state, self) && distance > self.getRange()) {
            return null;
        }

        Direction bestDirection = null;
        double bestScore = -9999;
        Position baitPosition = findThirdPartyBaitPosition(state, self, nearestEnemy);
        Position nearestPowerUp = state.findNearestPowerUp(self);
        UnitInfo teammate = findNearestTeammate(state, self);
        Direction[] directions = Direction.values();
        for (int i = 0; i < directions.length; i++) {
            Position next = self.getPosition().move(directions[i]);
            if (!state.isOpen(next)) {
                continue;
            }

            int newDistance = next.distanceTo(nearestEnemy.getPosition());
            int incomingDamage = incomingDamageAt(state, self, next);
            int nextThreats = threatCountAtPosition(state, self, next);
            double score = 640;
            score += (newDistance - distance) * 85.0;
            score -= incomingDamage * 55.0;
            score -= nextThreats * 150.0;
            if (newDistance > distance) {
                score += 95;
            }
            if (newDistance <= nearestEnemy.getRange()) {
                score -= 260;
            }

            if (baitPosition != null) {
                int oldBaitDistance = self.distanceTo(baitPosition);
                int newBaitDistance = next.distanceTo(baitPosition);
                if (oldBaitDistance > 5) {
                    score += (oldBaitDistance - newBaitDistance) * 22.0;
                } else {
                    score += (newBaitDistance - oldBaitDistance) * 10.0;
                }
            }

            if (nearestPowerUp != null && isScout(self)) {
                score += (self.distanceTo(nearestPowerUp) - next.distanceTo(nearestPowerUp)) * 12.0;
            }

            if (teammate != null && !isScout(self)) {
                int oldTeamDistance = self.distanceTo(teammate);
                int newTeamDistance = next.distanceTo(teammate.getPosition());
                if (newTeamDistance <= 4) {
                    score += 35;
                }
                score += (oldTeamDistance - newTeamDistance) * 14.0;
            }

            if (bestDirection == null || score > bestScore) {
                bestDirection = directions[i];
                bestScore = score;
            }
        }

        if (bestDirection == null || bestScore < 520) {
            return null;
        }
        return new Choice(Action.move(bestDirection), bestScore, "opening avoid and lure");
    }

    private Choice createEndgameLeadChoice(GameState state, UnitInfo self) {
        if (turnsTaken < ENDGAME_GUARD_TURN || !isAheadOnTiebreak(state, self)) {
            return null;
        }
        UnitInfo threat = findPrimaryThreat(state, self);
        Position healingPoint = state.findNearestHealingPoint(self);
        if (self.getHealth() < self.getMaxHealth() && healingPoint != null) {
            if (self.getPosition().equals(healingPoint)) {
                return new Choice(Action.defend(), 980, "endgame hold lead");
            }
            return new Choice(moveToPosition(state, self, healingPoint, null), 960, "endgame heal lead");
        }
        if (threat != null && self.distanceTo(threat) <= threat.getRange() + 2) {
            return new Choice(moveAwaySafely(state, self, threat.getPosition()), 940, "endgame preserve lead");
        }
        return new Choice(safeReposition(state, self), 900, "endgame preserve lead");
    }

    private boolean isAheadOnTiebreak(GameState state, UnitInfo self) {
        int myAlive = 0;
        int myHealth = 0;
        int maxEnemyAlive = 0;
        int maxEnemyHealthAtMaxAlive = 0;
        ArrayList<UnitInfo> living = state.getLivingUnits();
        ArrayList<String> enemyTeams = new ArrayList<String>();

        for (int i = 0; i < living.size(); i++) {
            UnitInfo unit = living.get(i);
            if (unit.getTeamName().equals(self.getTeamName())) {
                myAlive++;
                myHealth += unit.getHealth();
            } else if (!enemyTeams.contains(unit.getTeamName())) {
                enemyTeams.add(unit.getTeamName());
            }
        }

        for (int i = 0; i < enemyTeams.size(); i++) {
            String teamName = enemyTeams.get(i);
            int alive = 0;
            int health = 0;
            for (int j = 0; j < living.size(); j++) {
                UnitInfo unit = living.get(j);
                if (unit.getTeamName().equals(teamName)) {
                    alive++;
                    health += unit.getHealth();
                }
            }
            if (alive > maxEnemyAlive) {
                maxEnemyAlive = alive;
                maxEnemyHealthAtMaxAlive = health;
            } else if (alive == maxEnemyAlive && health > maxEnemyHealthAtMaxAlive) {
                maxEnemyHealthAtMaxAlive = health;
            }
        }

        if (myAlive > maxEnemyAlive) {
            return true;
        }
        return myAlive == maxEnemyAlive && myHealth >= maxEnemyHealthAtMaxAlive + ENDGAME_HEALTH_MARGIN;
    }

    private void updateVisibleStagnation(GameState state) {
        String snapshot = buildVisibleSnapshot(state);
        if (snapshot.equals(lastVisibleSnapshot)) {
            stagnantTurns++;
        } else {
            stagnantTurns = 0;
            lastVisibleSnapshot = snapshot;
        }
    }

    private String buildVisibleSnapshot(GameState state) {
        String snapshot = "";
        ArrayList<UnitInfo> units = state.getLivingUnits();
        for (int i = 0; i < units.size(); i++) {
            UnitInfo unit = units.get(i);
            snapshot += unit.getId()
                    + "|"
                    + unit.getTeamName()
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
        ArrayList<Position> powerUps = state.getPowerUpPositions();
        for (int i = 0; i < powerUps.size(); i++) {
            Position position = powerUps.get(i);
            snapshot += "P|" + position.getRow() + "," + position.getCol() + ";";
        }
        return snapshot;
    }

    private Choice createStagnationBreakChoice(GameState state, UnitInfo self) {
        if (stagnantTurns < STAGNATION_BREAK_TURNS) {
            return null;
        }
        if (healthRate(self) < 0.45 && incomingDamageAt(state, self, self.getPosition()) > 0) {
            return null;
        }

        UnitInfo target = findStagnationTarget(state, self);
        if (target == null) {
            return null;
        }
        if (self.distanceTo(target) <= self.getRange()) {
            return new Choice(Action.attack(target.getId()), 910 + attackValue(self, target) * 20, "break stalemate attack");
        }
        Action action = moveToPosition(state, self, target.getPosition(), target);
        double score = 820 + attackValue(self, target) * 40 - movementRiskPenalty(state, self, action);
        return new Choice(action, score, "break stalemate chase");
    }

    private UnitInfo findStagnationTarget(GameState state, UnitInfo self) {
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        UnitInfo best = null;
        double bestScore = -9999;
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo enemy = enemies.get(i);
            if (isUnwinnableDuel(state, self, enemy) && !canHelpTeammateCounterKill(state, self, enemy)) {
                continue;
            }
            double score = attackValue(self, enemy) * 12.0 - self.distanceTo(enemy) * 3.0;
            if (enemy.getHealth() <= self.getAttackPower() * 4) {
                score += 80;
            }
            if (state.isHealingPoint(enemy.getPosition()) && enemy.isDefending()) {
                score += 65;
            }
            if (best == null || score > bestScore) {
                best = enemy;
                bestScore = score;
            }
        }
        return best;
    }

    private Choice createEmergencySurvivalChoice(GameState state, UnitInfo self) {
        if (!USE_EMERGENCY_SURVIVAL) {
            return null;
        }

        int currentDamage = incomingDamageAt(state, self, self.getPosition());
        int currentThreats = threatCountAtPosition(state, self, self.getPosition());
        boolean immediateLethal = currentDamage >= self.getHealth();
        boolean criticallyLowUnderThreat = healthRate(self) <= 0.22 && currentThreats > 0;
        boolean surroundedLowHealth = currentThreats >= 2 && healthRate(self) <= 0.42;
        if (!immediateLethal && !criticallyLowUnderThreat && !surroundedLowHealth) {
            return null;
        }

        Direction bestDirection = null;
        double bestScore = -9999;
        Position healingPoint = state.findNearestHealingPoint(self);
        Position comebackTarget = findComebackTarget(state, self, findPrimaryThreat(state, self));
        UnitInfo teammate = findNearestTeammate(state, self);
        Direction[] directions = Direction.values();
        for (int i = 0; i < directions.length; i++) {
            Position next = self.getPosition().move(directions[i]);
            if (!state.isOpen(next)) {
                continue;
            }

            int nextDamage = incomingDamageAt(state, self, next);
            int nextThreats = threatCountAtPosition(state, self, next);
            int nearestEnemyDistance = nearestEnemyDistanceTo(state, self, next);

            double score = 1500;
            score += (currentDamage - nextDamage) * 135.0;
            score += (currentThreats - nextThreats) * 210.0;
            score -= nextDamage * 150.0;
            score -= nextThreats * 230.0;
            score -= nearThreatCountAtPosition(state, self, next) * NEAR_THREAT_MOVE_PENALTY;
            score += nearestEnemyDistance * 30.0;
            if (nextDamage >= self.getHealth()) {
                score -= 1200;
            }
            if (nextDamage == 0) {
                score += 260;
            }
            if (nextThreats == 0) {
                score += 180;
            }
            if (state.isHealingPoint(next) && self.getHealth() < self.getMaxHealth()) {
                score += 220;
            }
            if (healingPoint != null) {
                score += (self.distanceTo(healingPoint) - next.distanceTo(healingPoint)) * 44.0;
            }
            if (comebackTarget != null) {
                score += (self.distanceTo(comebackTarget) - next.distanceTo(comebackTarget)) * 28.0;
            }
            if (teammate != null && next.distanceTo(teammate.getPosition()) <= 5) {
                score += 30;
            }
            score -= edgeTrapPenalty(state, self, next) * edgeTrapMovePenalty();

            if (bestDirection == null || score > bestScore) {
                bestDirection = directions[i];
                bestScore = score;
            }
        }

        if (bestDirection == null) {
            return null;
        }
        return new Choice(Action.move(bestDirection), bestScore, "emergency survival");
    }

    private Choice createComebackDisengageChoice(GameState state, UnitInfo self, UnitInfo threat) {
        if (threat == null) {
            return null;
        }

        Position comebackTarget = findComebackTarget(state, self, threat);
        Direction bestDirection = null;
        double bestScore = -9999;
        int currentDamage = incomingDamageAt(state, self, self.getPosition());
        int currentThreats = threatCountAtPosition(state, self, self.getPosition());
        int currentThreatDistance = self.distanceTo(threat);
        UnitInfo teammate = findNearestTeammate(state, self);
        Direction[] directions = Direction.values();
        for (int i = 0; i < directions.length; i++) {
            Position next = self.getPosition().move(directions[i]);
            if (!state.isOpen(next)) {
                continue;
            }

            int nextDamage = incomingDamageAt(state, self, next);
            int nextThreats = threatCountAtPosition(state, self, next);
            int nextThreatDistance = next.distanceTo(threat.getPosition());
            double score = 760;
            score += (currentDamage - nextDamage) * 90.0;
            score += (currentThreats - nextThreats) * 160.0;
            score += (nextThreatDistance - currentThreatDistance) * 55.0;
            if (nextThreatDistance > currentThreatDistance) {
                score += 75;
            }
            if (nextDamage >= self.getHealth()) {
                score -= 500;
            }
            score -= edgeTrapPenalty(state, self, next) * edgeTrapMovePenalty();
            score -= nearThreatCountAtPosition(state, self, next) * NEAR_THREAT_MOVE_PENALTY;

            if (comebackTarget != null) {
                int oldTargetDistance = self.distanceTo(comebackTarget);
                int newTargetDistance = next.distanceTo(comebackTarget);
                score += (oldTargetDistance - newTargetDistance) * 120.0;
                if (newTargetDistance == 0) {
                    score += 260;
                }
                if (state.isHealingPoint(comebackTarget)) {
                    score += (1.0 - healthRate(self)) * 170;
                } else {
                    score += 90;
                }
            }

            if (teammate != null) {
                int oldTeamDistance = self.distanceTo(teammate);
                int newTeamDistance = next.distanceTo(teammate.getPosition());
                if (newTeamDistance <= 4) {
                    score += 55;
                }
                if (oldTeamDistance > 5 && newTeamDistance < oldTeamDistance) {
                    score += 35;
                }
            }

            if (state.isHealingPoint(next) && self.getHealth() < self.getMaxHealth()) {
                score += 180;
            }
            score -= teamSplitPenalty(state, self, next);

            if (bestDirection == null || score > bestScore) {
                bestDirection = directions[i];
                bestScore = score;
            }
        }

        if (bestDirection == null) {
            return null;
        }
        if (bestScore < 720 && currentDamage == 0 && self.distanceTo(threat) > threat.getRange() + 1) {
            return null;
        }
        return new Choice(Action.move(bestDirection), bestScore, "comeback disengage");
    }

    private Position findComebackTarget(GameState state, UnitInfo self, UnitInfo threat) {
        Position healingPoint = state.findNearestHealingPoint(self);
        if (healingPoint != null && self.getHealth() < self.getMaxHealth() * 0.62) {
            return healingPoint;
        }

        Position powerUp = findSafeComebackPowerUp(state, self, threat);
        if (powerUp != null) {
            return powerUp;
        }

        if (healingPoint != null && self.getHealth() < self.getMaxHealth()) {
            return healingPoint;
        }
        return null;
    }

    private Position findSafeComebackPowerUp(GameState state, UnitInfo self, UnitInfo threat) {
        ArrayList<Position> powerUps = state.getPowerUpPositions();
        Position best = null;
        double bestScore = -9999;
        for (int i = 0; i < powerUps.size(); i++) {
            Position powerUp = powerUps.get(i);
            int distance = self.distanceTo(powerUp);
            if (distance > 10) {
                continue;
            }
            int enemyDistance = nearestEnemyDistanceTo(state, self, powerUp);
            if (enemyDistance + 1 < distance) {
                continue;
            }
            int danger = incomingDamageAt(state, self, powerUp);
            if (danger >= self.getHealth()) {
                continue;
            }
            double score = 500 - distance * 45.0 + (enemyDistance - distance) * 35.0 - danger * 50.0;
            if (threat != null) {
                score += powerUp.distanceTo(threat.getPosition()) * 8.0;
            }
            int teammateDistance = nearestTeammateDistanceTo(state, self, powerUp);
            if (teammateDistance < distance) {
                score -= 65;
            }
            if (best == null || score > bestScore) {
                best = powerUp;
                bestScore = score;
            }
        }
        return best;
    }

    private Action moveAwaySafely(GameState state, UnitInfo self, Position dangerPosition) {
        Direction bestDirection = null;
        double bestScore = -9999;
        int currentDistance = self.distanceTo(dangerPosition);
        Direction[] directions = Direction.values();
        for (int i = 0; i < directions.length; i++) {
            Position next = self.getPosition().move(directions[i]);
            if (!state.isOpen(next)) {
                continue;
            }

            int newDistance = next.distanceTo(dangerPosition);
            double score = (newDistance - currentDistance) * 100.0;
            score -= incomingDamageAt(state, self, next) * 75.0;
            score -= threatCountAtPosition(state, self, next) * 120.0;
            score -= nearThreatCountAtPosition(state, self, next) * NEAR_THREAT_MOVE_PENALTY;
            score -= edgeTrapPenalty(state, self, next) * edgeTrapMovePenalty();
            UnitInfo teammate = findNearestTeammate(state, self);
            if (teammate != null) {
                int teammateDistance = next.distanceTo(teammate.getPosition());
                if (teammateDistance <= 5) {
                    score += 30;
                }
            }
            if (bestDirection == null || score > bestScore) {
                bestDirection = directions[i];
                bestScore = score;
            }
        }

        if (bestDirection != null) {
            return Action.move(bestDirection);
        }
        return Action.defend();
    }

    private Action safeReposition(GameState state, UnitInfo self) {
        if (state.isHealingPoint(self.getPosition()) && self.getHealth() < self.getMaxHealth()) {
            return Action.defend();
        }

        Direction bestDirection = null;
        double bestScore = -9999;
        UnitInfo nearestEnemy = findPrimaryThreat(state, self);
        UnitInfo teammate = findNearestTeammate(state, self);
        Position powerUp = state.findNearestPowerUp(self);
        Direction[] directions = Direction.values();
        for (int i = 0; i < directions.length; i++) {
            Position next = self.getPosition().move(directions[i]);
            if (!state.isOpen(next)) {
                continue;
            }

            double score = 0;
            score -= incomingDamageAt(state, self, next) * 90.0;
            score -= threatCountAtPosition(state, self, next) * 130.0;
            score -= nearThreatCountAtPosition(state, self, next) * NEAR_THREAT_MOVE_PENALTY;
            score -= edgeTrapPenalty(state, self, next) * edgeTrapMovePenalty();
            if (nearestEnemy != null) {
                score += next.distanceTo(nearestEnemy.getPosition()) * 10.0;
            }
            if (powerUp != null && healthRate(self) > 0.35) {
                score += (self.distanceTo(powerUp) - next.distanceTo(powerUp)) * 8.0;
            }
            if (teammate != null) {
                int teammateDistance = next.distanceTo(teammate.getPosition());
                if (teammateDistance <= 4) {
                    score += 25;
                } else {
                    score -= (teammateDistance - 4) * 6.0;
                }
            }
            if (state.isHealingPoint(next) && self.getHealth() < self.getMaxHealth()) {
                score += 40;
            }
            score -= teamSplitPenalty(state, self, next);

            if (bestDirection == null || score > bestScore) {
                bestDirection = directions[i];
                bestScore = score;
            }
        }

        if (bestDirection != null) {
            return Action.move(bestDirection);
        }
        return Action.defend();
    }

    private BattlePlan evaluateBattlePlan(GameState state, UnitInfo self, UnitInfo target) {
        int selfDamage = expectedDamage(self, target);
        int teammateNowDamage = 0;
        int teammateSoonDamage = 0;
        ArrayList<UnitInfo> teammates = state.getLivingTeammates(self);
        for (int i = 0; i < teammates.size(); i++) {
            UnitInfo teammate = teammates.get(i);
            if (teammate.distanceTo(target) <= teammate.getRange()) {
                teammateNowDamage += expectedDamage(teammate, target);
            } else if (teammate.distanceTo(target) <= teammate.getRange() + 1) {
                teammateSoonDamage += Math.max(1, teammate.getAttackPower() / 2);
            }
        }

        int burstDamage = selfDamage + teammateNowDamage;
        int twoTurnDamage = burstDamage + selfDamage + teammateNowDamage + teammateSoonDamage;
        int counterDamage = counterDamageAfterAttack(state, self, target);
        int secondThreats = secondThreatCount(state, self, target);
        boolean teammateClose = teammateNearTarget(state, self, target, 5);
        boolean teamCanFinish = burstDamage >= target.getHealth() || canTeamFinishNow(state, self, target);
        int selfTurnsToWin = turnsToDefeat(target.getHealth(), Math.max(1, selfDamage + teammateNowDamage));
        int enemyDamagePerTurn = Math.max(1, target.getAttackPower() + secondThreats * 6);
        int enemyTurnsToWin = turnsToDefeat(self.getHealth(), enemyDamagePerTurn);
        boolean canWinDuel = teamCanFinish
                || twoTurnDamage >= target.getHealth()
                || selfTurnsToWin < enemyTurnsToWin
                || hasClearAttackAdvantage(self, target);
        boolean shouldAvoid = !teamCanFinish
                && !teammateClose
                && (counterDamage >= self.getHealth()
                        || (enemyTurnsToWin <= selfTurnsToWin && healthRate(self) < 0.68)
                        || (secondThreats > 0 && healthRate(self) < 0.62));

        double score = burstDamage * 7.0 + teammateSoonDamage * 4.5;
        score -= counterDamage * 8.0;
        score -= secondThreats * (healthRate(self) < 0.68 ? 55.0 : 25.0);
        score += teamCanFinish ? 120 : 0;
        score += canWinDuel ? 55 : -85;
        score += teammateClose ? 45 : 0;
        if (target.getRange() > self.getRange() && target.getAttackPower() >= self.getAttackPower()) {
            score -= 45;
        }
        return new BattlePlan(score, teamCanFinish, canWinDuel, shouldAvoid);
    }

    private PowerRace evaluatePowerRace(GameState state, UnitInfo self, Position powerUp) {
        int myDistance = self.distanceTo(powerUp);
        int enemyDistance = nearestEnemyDistanceTo(state, self, powerUp);
        int teammateDistance = nearestTeammateDistanceTo(state, self, powerUp);
        int danger = incomingDamageAt(state, self, powerUp);

        boolean enemyClearlyCloser = enemyDistance + 1 < myDistance;
        boolean teammateAlreadyBetter = teammateDistance <= myDistance && teammateDistance < 9999;
        boolean worthContesting = !enemyClearlyCloser || myDistance <= 2 || (battleReady(state, self) && danger < self.getHealth() / 2);
        if (danger >= self.getHealth()) {
            worthContesting = false;
        }

        double score = 0;
        score += (enemyDistance - myDistance) * 28.0;
        score -= danger * 40.0;
        if (enemyClearlyCloser) {
            score -= 130;
        }
        if (teammateAlreadyBetter) {
            score -= isHarvester(state, self) ? 85 : 45;
        }
        if (isHarvester(state, self)) {
            score += 45;
        }
        if (!battleReady(state, self)) {
            score += 40;
        }
        return new PowerRace(score, worthContesting);
    }

    private double movementRiskPenalty(GameState state, UnitInfo self, Action action) {
        if (!USE_MOVE_RISK) {
            return 0;
        }
        Position next = nextPosition(self, action);
        int currentDamage = incomingDamageAt(state, self, self.getPosition());
        int nextDamage = incomingDamageAt(state, self, next);
        int currentThreats = threatCountAtPosition(state, self, self.getPosition());
        int nextThreats = threatCountAtPosition(state, self, next);

        double penalty = 0;
        if (nextDamage > currentDamage) {
            penalty += (nextDamage - currentDamage) * 28.0;
        }
        if (nextThreats > currentThreats) {
            penalty += (nextThreats - currentThreats) * 60.0;
        }
        if (nextDamage >= self.getHealth()) {
            penalty += 500;
        }
        if (healthRate(self) < 0.55) {
            penalty *= 1.2;
        }
        return penalty;
    }

    private Position nextPosition(UnitInfo self, Action action) {
        if (action == null || action.getType() != ActionType.MOVE || action.getDirection() == null) {
            return self.getPosition();
        }
        return self.getPosition().move(action.getDirection());
    }

    private Position openingRouteTarget(GameState state, UnitInfo self) {
        boolean harvest = isHarvester(state, self);
        int row;
        int col;
        if (harvest) {
            row = turnsTaken <= 7 ? 3 : 4;
            col = turnsTaken <= 7 ? 2 : 4;
        } else {
            row = turnsTaken <= 7 ? 2 : 4;
            col = turnsTaken <= 7 ? 4 : 5;
        }
        Position target = mirroredPosition(state, self, row, col);
        if (state.isOpen(target)) {
            return target;
        }
        return nearestOpenAround(state, target);
    }

    private Position mirroredPosition(GameState state, UnitInfo self, int rowFromTop, int colFromLeft) {
        int row = rowFromTop;
        int col = colFromLeft;
        String teamName = self.getTeamName();
        if ("Green".equals(teamName) || "Yellow".equals(teamName)) {
            row = state.getRows() - 1 - rowFromTop;
        }
        if ("Blue".equals(teamName) || "Yellow".equals(teamName)) {
            col = state.getCols() - 1 - colFromLeft;
        }
        return new Position(row, col);
    }

    private Position nearestOpenAround(GameState state, Position target) {
        Position best = null;
        int bestDistance = 9999;
        for (int row = 0; row < state.getRows(); row++) {
            for (int col = 0; col < state.getCols(); col++) {
                Position candidate = new Position(row, col);
                if (!state.isOpen(candidate)) {
                    continue;
                }
                int distance = candidate.distanceTo(target);
                if (best == null || distance < bestDistance) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private Position predictedEnemyResourceTarget(GameState state, UnitInfo enemy) {
        if (healthRate(enemy) < 0.58) {
            Position healingPoint = state.findNearestHealingPoint(enemy);
            if (healingPoint != null) {
                return healingPoint;
            }
        }
        return state.findNearestPowerUp(enemy);
    }

    private Position bestInterceptPosition(GameState state, UnitInfo self, UnitInfo enemy, Position resource) {
        Position best = null;
        double bestScore = -9999;
        for (int row = 0; row < state.getRows(); row++) {
            for (int col = 0; col < state.getCols(); col++) {
                Position candidate = new Position(row, col);
                if (!state.isOpen(candidate)) {
                    continue;
                }
                if (candidate.distanceTo(resource) > self.getRange()) {
                    continue;
                }
                double score = 100 - self.distanceTo(candidate) * 16.0;
                score -= incomingDamageAt(state, self, candidate) * 42.0;
                score += candidate.distanceTo(enemy.getPosition()) <= self.getRange() ? 45 : 0;
                if (best == null || score > bestScore) {
                    best = candidate;
                    bestScore = score;
                }
            }
        }
        return best;
    }

    private String roleName(GameState state, UnitInfo self) {
        if (isHarvester(state, self)) {
            return "harvester";
        }
        return "protector";
    }

    private boolean isHarvester(GameState state, UnitInfo self) {
        UnitInfo teammate = findNearestTeammate(state, self);
        if (teammate == null) {
            return true;
        }
        if (self.getAttackPower() > teammate.getAttackPower() || self.getRange() > teammate.getRange()) {
            return false;
        }
        if (healthRate(self) < healthRate(teammate) - 0.18) {
            return true;
        }
        return isScout(self);
    }

    private boolean isProtector(GameState state, UnitInfo self) {
        return !isHarvester(state, self);
    }

    private double teamReadiness(GameState state, UnitInfo self) {
        int attack = self.getAttackPower();
        int range = self.getRange();
        double health = healthRate(self);
        ArrayList<UnitInfo> teammates = state.getLivingTeammates(self);
        for (int i = 0; i < teammates.size(); i++) {
            UnitInfo teammate = teammates.get(i);
            attack += teammate.getAttackPower();
            range = Math.max(range, teammate.getRange());
            health += healthRate(teammate);
        }
        health = health / (teammates.size() + 1);
        double attackScore = Math.min(1.0, attack / 28.0);
        double rangeScore = Math.min(1.0, range / 2.0);
        return attackScore * 0.45 + rangeScore * 0.30 + health * 0.25;
    }

    private static boolean tunableBoolean(String key, boolean defaultValue) {
        String value = System.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
    }

    private static double tunableDouble(String key, double defaultValue) {
        String value = System.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private static int tunableInt(String key, int defaultValue) {
        String value = System.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
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

    private boolean isOpening() {
        return turnsTaken <= openingTurnLimit();
    }

    private int openingTurnLimit() {
        String teamName = currentTeamName;
        if ("Red".equals(teamName) && OPENING_TURN_LIMIT_RED >= 0) {
            return OPENING_TURN_LIMIT_RED;
        }
        if ("Blue".equals(teamName) && OPENING_TURN_LIMIT_BLUE >= 0) {
            return OPENING_TURN_LIMIT_BLUE;
        }
        if ("Green".equals(teamName) && OPENING_TURN_LIMIT_GREEN >= 0) {
            return OPENING_TURN_LIMIT_GREEN;
        }
        if ("Yellow".equals(teamName) && OPENING_TURN_LIMIT_YELLOW >= 0) {
            return OPENING_TURN_LIMIT_YELLOW;
        }
        return OPENING_TURN_LIMIT;
    }

    private boolean isScout(UnitInfo self) {
        return self != null && self.getId() % 2 == 0;
    }

    private UnitInfo findNearestTeammate(GameState state, UnitInfo self) {
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

    private UnitInfo findPrimaryThreat(GameState state, UnitInfo self) {
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        UnitInfo best = null;
        double bestScore = -9999;
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo enemy = enemies.get(i);
            int distance = self.distanceTo(enemy);
            double score = enemy.getAttackPower() * 6.0 + enemy.getRange() * 45.0 - distance * 18.0;
            if (distance <= enemy.getRange()) {
                score += 1000 + (enemy.getRange() - distance) * 80.0;
            } else if (distance <= enemy.getRange() + 2) {
                score += 420 - (distance - enemy.getRange()) * 90.0;
            }
            if (isHanson(enemy)) {
                score += 25;
            }
            if (best == null || score > bestScore) {
                best = enemy;
                bestScore = score;
            }
        }
        return best;
    }

    private Position findThirdPartyBaitPosition(GameState state, UnitInfo self, UnitInfo hunter) {
        if (hunter == null) {
            return null;
        }

        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        UnitInfo best = null;
        int bestScore = 9999;
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo enemy = enemies.get(i);
            if (enemy.getTeamName().equals(hunter.getTeamName())) {
                continue;
            }
            int score = hunter.distanceTo(enemy) * 3 + self.distanceTo(enemy);
            if (best == null || score < bestScore) {
                best = enemy;
                bestScore = score;
            }
        }
        if (best == null) {
            return new Position(state.getRows() / 2, state.getCols() / 2);
        }
        return best.getPosition();
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

    private int threatCountAtPosition(GameState state, UnitInfo self, Position position) {
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

    private int nearThreatCountAtPosition(GameState state, UnitInfo self, Position position) {
        int count = 0;
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo enemy = enemies.get(i);
            if (position.distanceTo(enemy.getPosition()) <= enemy.getRange() + 1) {
                count++;
            }
        }
        return count;
    }

    private boolean enemyCanHitPosition(UnitInfo enemy, Position position) {
        return enemy != null && position != null && enemy.getPosition().distanceTo(position) <= enemy.getRange();
    }

    private int teammateThreatBonus(GameState state, UnitInfo self, UnitInfo enemy) {
        ArrayList<UnitInfo> teammates = state.getLivingTeammates(self);
        int bonus = 0;
        for (int i = 0; i < teammates.size(); i++) {
            UnitInfo teammate = teammates.get(i);
            if (!enemyCanHitPosition(enemy, teammate.getPosition())) {
                continue;
            }
            int candidate = 85;
            if (healthRate(teammate) < 0.70) {
                candidate += 55;
            }
            if (teammate.distanceTo(enemy) <= teammate.getRange()) {
                candidate += 25;
            }
            if (candidate > bonus) {
                bonus = candidate;
            }
        }
        return bonus;
    }

    private double scaledTeammateThreatBonus(GameState state, UnitInfo self, UnitInfo enemy) {
        return teammateThreatBonus(state, self, enemy) * TEAMMATE_THREAT_BONUS_SCALE;
    }

    private boolean battleReady(GameState state, UnitInfo self) {
        int teamAttack = self.getAttackPower();
        int teamRange = self.getRange();
        ArrayList<UnitInfo> teammates = state.getLivingTeammates(self);
        for (int i = 0; i < teammates.size(); i++) {
            UnitInfo teammate = teammates.get(i);
            teamAttack += teammate.getAttackPower();
            teamRange = Math.max(teamRange, teammate.getRange());
        }
        return teamAttack >= 26 || teamRange >= 2 || self.getAttackPower() >= 13;
    }

    private UnitInfo nearestHanson(GameState state, UnitInfo self) {
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        UnitInfo best = null;
        int bestDistance = 9999;
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo enemy = enemies.get(i);
            if (!isHanson(enemy)) {
                continue;
            }
            int distance = self.distanceTo(enemy);
            if (best == null || distance < bestDistance) {
                best = enemy;
                bestDistance = distance;
            }
        }
        return best;
    }

    private boolean teammateNearTarget(GameState state, UnitInfo self, UnitInfo target, int distanceLimit) {
        ArrayList<UnitInfo> teammates = state.getLivingTeammates(self);
        for (int i = 0; i < teammates.size(); i++) {
            if (teammates.get(i).distanceTo(target) <= distanceLimit) {
                return true;
            }
        }
        return false;
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

    private double dangerAround(GameState state, UnitInfo self) {
        double danger = 0;
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo enemy = enemies.get(i);
            int distance = self.distanceTo(enemy);
            if (distance <= enemy.getRange()) {
                danger += 2.0 + enemy.getAttackPower() / 8.0;
            } else if (distance <= enemy.getRange() + 1) {
                danger += 1.0;
            }
        }
        return danger;
    }

    private double edgeTrapPenalty(GameState state, UnitInfo self, Position position) {
        if (position == null) {
            return 0;
        }
        int nearbyHunters = nearbyHunterCountAt(state, self, position);
        if (healthRate(self) >= 0.60 && nearbyHunters == 0 && incomingDamageAt(state, self, position) == 0) {
            return 0;
        }
        int edgeDistance = Math.min(
                Math.min(position.getRow(), state.getRows() - 1 - position.getRow()),
                Math.min(position.getCol(), state.getCols() - 1 - position.getCol()));
        if (edgeDistance >= 2) {
            return 0;
        }

        double penalty = edgeDistance == 0 ? 1.0 : 0.45;
        penalty += nearbyHunters * 0.55;
        if (healthRate(self) < 0.55) {
            penalty *= 1.35;
        }
        if (healthRate(self) < 0.35) {
            penalty *= 1.35;
        }
        if (state.isHealingPoint(position) && self.getHealth() < self.getMaxHealth()) {
            penalty *= 0.70;
        }
        return penalty;
    }

    private int nearbyHunterCountAt(GameState state, UnitInfo self, Position position) {
        int count = 0;
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo enemy = enemies.get(i);
            int distance = position.distanceTo(enemy.getPosition());
            if (distance <= enemy.getRange() + 2 || distance <= 3) {
                count++;
            }
        }
        return count;
    }

    private double teamSplitPenalty(GameState state, UnitInfo self, Position next) {
        double penaltyValue = teamSplitPenaltyValue();
        if (penaltyValue <= 0 || next == null) {
            return 0;
        }
        UnitInfo teammate = findNearestTeammate(state, self);
        if (teammate == null) {
            return 0;
        }
        int distance = next.distanceTo(teammate.getPosition());
        if (distance <= 5) {
            return 0;
        }
        double pressure = dangerAround(state, self);
        if (healthRate(self) < 0.60) {
            pressure += 1.0;
        }
        if (incomingDamageAt(state, self, next) > 0) {
            pressure += 1.0;
        }
        return (distance - 5) * penaltyValue * Math.max(0.5, pressure);
    }

    private double teamSplitPenaltyValue() {
        if (USE_COLOR_PROFILE_V2 && "Red".equals(currentTeamName)) {
            return 8.0;
        }
        return TEAM_SPLIT_PENALTY;
    }

    private int immediateThreatCount(GameState state, UnitInfo self) {
        int count = 0;
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo enemy = enemies.get(i);
            if (self.distanceTo(enemy) <= enemy.getRange()) {
                count++;
            }
        }
        return count;
    }

    private UnitInfo findUnwinnableDuelThreat(GameState state, UnitInfo self) {
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        UnitInfo best = null;
        int bestPressure = -9999;
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo enemy = enemies.get(i);
            if (!isUnwinnableDuel(state, self, enemy)) {
                continue;
            }
            int distance = self.distanceTo(enemy);
            if (distance > enemy.getRange() + 1 && distance > self.getRange()
                    && !isApproachingRangedPredator(self, enemy, distance)) {
                continue;
            }
            int pressure = enemy.getAttackPower() * 10 + enemy.getRange() * 20 - distance * 8;
            if (best == null || pressure > bestPressure) {
                best = enemy;
                bestPressure = pressure;
            }
        }
        return best;
    }

    private boolean isApproachingRangedPredator(UnitInfo self, UnitInfo enemy, int distance) {
        return enemy.getRange() > self.getRange()
                && enemy.getAttackPower() >= self.getAttackPower() + 2
                && distance <= enemy.getRange() + 2;
    }

    private boolean isUnwinnableDuel(GameState state, UnitInfo self, UnitInfo target) {
        if (target == null || expectedDamage(self, target) >= target.getHealth()) {
            return false;
        }
        if (canHelpTeammateCounterKill(state, self, target)) {
            return false;
        }
        if (hasClearAttackAdvantage(self, target)) {
            return false;
        }
        boolean hasHealthDisadvantage = self.getHealth() < target.getHealth();
        boolean severeByHealthRate = self.getHealth() < self.getMaxHealth() * SEVERE_DUEL_HEALTH_RATE;
        boolean severeByTradeDepth = SEVERE_DUEL_ATTACK_TURNS > 0
                && self.getHealth() + self.getAttackPower() * SEVERE_DUEL_ATTACK_TURNS < target.getHealth();
        boolean hasSevereHealthDisadvantage = severeByHealthRate || severeByTradeDepth;
        if (self.getAttackPower() > target.getAttackPower() && !hasSevereHealthDisadvantage) {
            return false;
        }
        boolean hasSevereAttackDisadvantage = target.getAttackPower() >= self.getAttackPower() + 4;
        if (!hasHealthDisadvantage && !hasSevereAttackDisadvantage) {
            return false;
        }

        boolean targetCanTrade = target.getRange() >= self.getRange()
                || self.distanceTo(target) <= target.getRange() + 1;

        int selfDamage = Math.max(1, self.getAttackPower());
        int targetDamage = Math.max(1, target.getAttackPower());
        int selfTurnsToWin = turnsToDefeat(target.getHealth(), selfDamage);
        int targetTurnsToWin = turnsToDefeat(self.getHealth(), targetDamage);
        return targetCanTrade && targetTurnsToWin <= selfTurnsToWin;
    }

    private boolean hasClearAttackAdvantage(UnitInfo self, UnitInfo target) {
        if (self.getAttackPower() >= target.getAttackPower() + 4) {
            return true;
        }
        return self.getRange() > target.getRange() && self.getAttackPower() >= target.getAttackPower();
    }

    private int turnsToDefeat(int health, int damage) {
        return (health + damage - 1) / damage;
    }

    private boolean canHelpTeammateCounterKill(GameState state, UnitInfo self, UnitInfo target) {
        if (target == null) {
            return false;
        }
        ArrayList<UnitInfo> teammates = state.getLivingTeammates(self);
        for (int i = 0; i < teammates.size(); i++) {
            UnitInfo teammate = teammates.get(i);
            if (!enemyCanHitPosition(target, teammate.getPosition())) {
                continue;
            }
            int combinedDamage = 0;
            if (self.distanceTo(target) <= self.getRange()) {
                combinedDamage += expectedDamage(self, target);
            }
            if (teammate.distanceTo(target) <= teammate.getRange()) {
                combinedDamage += expectedDamage(teammate, target);
            }
            if (combinedDamage >= target.getHealth()) {
                return true;
            }
        }
        return false;
    }

    private boolean canTeamFinishNow(GameState state, UnitInfo self, UnitInfo target) {
        if (target == null) {
            return false;
        }
        int combinedDamage = expectedDamage(self, target);
        ArrayList<UnitInfo> teammates = state.getLivingTeammates(self);
        for (int i = 0; i < teammates.size(); i++) {
            UnitInfo teammate = teammates.get(i);
            if (teammate.distanceTo(target) <= teammate.getRange()) {
                combinedDamage += expectedDamage(teammate, target);
            }
        }
        return combinedDamage >= target.getHealth();
    }

    private boolean shouldAvoidAttackExchange(GameState state, UnitInfo self, UnitInfo target) {
        if (target == null || expectedDamage(self, target) >= target.getHealth()) {
            return false;
        }
        if (isUnwinnableDuel(state, self, target) && !canHelpTeammateCounterKill(state, self, target)) {
            return true;
        }

        boolean teammateClose = teammateNearTarget(state, self, target, 4);
        int counterDamage = counterDamageAfterAttack(state, self, target);
        int healthAfterCounter = self.getHealth() - counterDamage;
        int enemyHealthAfterHit = target.getHealth() - expectedDamage(self, target);
        boolean targetCanHitBack = enemyCanHitPosition(target, self.getPosition());
        boolean targetHitsHarder = target.getAttackPower() >= self.getAttackPower() + 2;
        int secondThreats = secondThreatCount(state, self, target);

        if (healthAfterCounter <= 0) {
            return true;
        }
        if (secondThreats > 0 && healthRate(self) < 0.56 && !teammateClose) {
            return true;
        }
        if (targetCanHitBack && targetHitsHarder && healthRate(self) < 0.50 && !teammateClose) {
            return true;
        }
        if (targetCanHitBack && enemyHealthAfterHit > self.getAttackPower() && healthAfterCounter < self.getMaxHealth() * 0.28) {
            return true;
        }
        return false;
    }

    private double attackRiskPenalty(GameState state, UnitInfo self, UnitInfo target) {
        if (target == null || expectedDamage(self, target) >= target.getHealth()) {
            return 0;
        }

        double penalty = 0;
        if (enemyCanHitPosition(target, self.getPosition())) {
            if (target.getAttackPower() > self.getAttackPower()) {
                penalty += 95;
            } else {
                penalty += 35;
            }
        }

        int secondThreats = secondThreatCount(state, self, target);
        if (healthRate(self) < 0.68) {
            penalty += secondThreats * 90.0;
        } else {
            penalty += secondThreats * 30.0;
        }
        int healthAfterCounter = self.getHealth() - counterDamageAfterAttack(state, self, target);
        if (healthAfterCounter < self.getMaxHealth() * 0.38) {
            penalty += 90;
        }
        if (healthRate(self) < 0.55) {
            penalty *= 1.15;
        }
        if (teammateNearTarget(state, self, target, 4)) {
            penalty *= 0.55;
        }
        return penalty;
    }

    private int counterDamageAfterAttack(GameState state, UnitInfo self, UnitInfo target) {
        int damage = 0;
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo enemy = enemies.get(i);
            if (target != null && enemy.getId() == target.getId()
                    && expectedDamage(self, target) >= target.getHealth()) {
                continue;
            }
            if (enemyCanHitPosition(enemy, self.getPosition())) {
                damage += enemy.getAttackPower();
            }
        }
        return damage;
    }

    private int secondThreatCount(GameState state, UnitInfo self, UnitInfo primaryTarget) {
        int count = 0;
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        for (int i = 0; i < enemies.size(); i++) {
            UnitInfo enemy = enemies.get(i);
            if (primaryTarget != null && enemy.getId() == primaryTarget.getId()) {
                continue;
            }
            int distance = self.distanceTo(enemy);
            if (distance <= enemy.getRange() + 1) {
                count++;
            }
        }
        return count;
    }

    private boolean isHanson(UnitInfo unit) {
        return unit != null && unit.getName().indexOf("Hanson") >= 0;
    }

    private boolean isPlz(UnitInfo unit) {
        return unit != null && unit.getName().indexOf("Plz") >= 0;
    }

    private boolean isYellowTeam(UnitInfo self) {
        return self != null && "Yellow".equals(self.getTeamName());
    }

    private boolean isEarlyPiggodTwo(GameState state, UnitInfo unit) {
        return isPiggodTwo(unit) && state.getLivingUnits().size() > 4;
    }

    private boolean isPiggodTwo(UnitInfo unit) {
        return unit != null && unit.getName().indexOf("Piggod 2") >= 0;
    }

    private double hansonAttackBonus() {
        if (useGreenHansonAvoidProfile()) {
            return 120.0;
        }
        return HANSON_ATTACK_BONUS;
    }

    private double hansonChaseBonus() {
        if (useGreenHansonAvoidProfile()) {
            return 160.0;
        }
        return HANSON_CHASE_BONUS;
    }

    private double hansonTargetBonus() {
        if (useGreenHansonAvoidProfile()) {
            return 1.2;
        }
        return HANSON_TARGET_BONUS;
    }

    private boolean useGreenHansonAvoidProfile() {
        return (USE_GREEN_HANSON_AVOID && "Green".equals(currentTeamName))
                || (USE_YELLOW_HANSON_AVOID && "Yellow".equals(currentTeamName))
                || (USE_BLUE_YELLOW_HANSON_AVOID
                        && ("Blue".equals(currentTeamName) || "Yellow".equals(currentTeamName)));
    }

    private boolean shouldPressurePiggodTwo(UnitInfo self, UnitInfo enemy) {
        return isPiggodTwo(enemy)
                && (enemy.getAttackPower() >= self.getAttackPower() + 2
                        || enemy.getRange() > self.getRange()
                        || enemy.getHealth() <= self.getAttackPower() * 4);
    }

    private boolean isDangerPiggodTwo(UnitInfo self, UnitInfo enemy) {
        return isPiggodTwo(enemy)
                && (enemy.getRange() > self.getRange()
                        || enemy.getAttackPower() >= self.getAttackPower() + 4
                        || enemy.getHealth() <= self.getAttackPower() * 3);
    }

    private double powerBaseScore() {
        if (USE_COLOR_PROFILE && "Yellow".equals(currentTeamName)) {
            return 650.0;
        }
        return POWER_BASE_SCORE;
    }

    private double powerDistancePenalty() {
        if (USE_COLOR_PROFILE && "Yellow".equals(currentTeamName)) {
            return 18.0;
        }
        return POWER_DISTANCE_PENALTY;
    }

    private double powerEnemyCloserPenalty() {
        if (USE_COLOR_PROFILE && "Yellow".equals(currentTeamName)) {
            return 220.0;
        }
        return POWER_ENEMY_CLOSER_PENALTY;
    }

    private double edgeTrapPowerPenalty() {
        if ((USE_COLOR_PROFILE || USE_BLUE_GREEN_EDGE_PROFILE || USE_COLOR_PROFILE_V2)
                && ("Blue".equals(currentTeamName) || "Green".equals(currentTeamName))) {
            return BLUE_GREEN_EDGE_POWER_PENALTY;
        }
        return EDGE_TRAP_POWER_PENALTY;
    }

    private double edgeTrapMovePenalty() {
        if ((USE_COLOR_PROFILE || USE_BLUE_GREEN_EDGE_PROFILE || USE_COLOR_PROFILE_V2)
                && ("Blue".equals(currentTeamName) || "Green".equals(currentTeamName))) {
            return BLUE_GREEN_EDGE_MOVE_PENALTY;
        }
        return EDGE_TRAP_MOVE_PENALTY;
    }

    private boolean isRangedDuelRisk(GameState state, UnitInfo self, UnitInfo target) {
        if (target == null || target.getRange() <= self.getRange()) {
            return false;
        }
        if (expectedDamage(self, target) >= target.getHealth() || canTeamFinishNow(state, self, target)) {
            return false;
        }
        if (teammateNearTarget(state, self, target, 4)) {
            return false;
        }
        if (hasClearAttackAdvantage(self, target)) {
            return false;
        }
        boolean healthNotComfortable = self.getHealth() <= target.getHealth() + 18;
        boolean attackNotComfortable = self.getAttackPower() <= target.getAttackPower() + 2;
        return healthNotComfortable && attackNotComfortable;
    }

    private double healthRate(UnitInfo unit) {
        return (double) unit.getHealth() / unit.getMaxHealth();
    }

    private double threat(UnitInfo unit) {
        double hp = (double) unit.getHealth() / unit.getMaxHealth();
        double attack = (double) unit.getAttackPower() / 11.0;
        double range = (double) unit.getRange();
        return hp * attack * range;
    }

    private double weakness(UnitInfo unit) {
        double missing = 1.0 - (double) unit.getHealth() / unit.getMaxHealth();
        double defendRate = unit.isDefending() ? 0.5 : 1.0;
        return missing * defendRate;
    }

    private double attackValue(UnitInfo self, UnitInfo enemy) {
        double value = weakness(enemy) * 2.0 + threat(enemy) * 0.8 - self.distanceTo(enemy) * 0.15;
        if (expectedDamage(self, enemy) >= enemy.getHealth()) {
            value += 1.5;
        }
        return value;
    }

    private int expectedDamage(UnitInfo self, UnitInfo enemy) {
        int damage = self.getAttackPower();
        if (enemy.isDefending()) {
            damage = Math.max(1, damage / 2);
        }
        return Math.min(damage, enemy.getHealth());
    }

    private int enemyBlockCost(UnitInfo self, UnitInfo enemy, double distance, int alertRadius) {
        double selfThreat = Math.max(0.1, threat(self));
        double relativeThreat = threat(enemy) / selfThreat;
        double distanceCost = alertRadius - distance + 1;
        int cost = (int) (distanceCost * relativeThreat * 3);
        if (isHanson(enemy)) {
            cost += 1;
        }
        return cost;
    }

    private static class BattlePlan {
        private final double score;
        private final boolean teamCanFinish;
        private final boolean canWinDuel;
        private final boolean shouldAvoid;

        private BattlePlan(double score, boolean teamCanFinish, boolean canWinDuel, boolean shouldAvoid) {
            this.score = score;
            this.teamCanFinish = teamCanFinish;
            this.canWinDuel = canWinDuel;
            this.shouldAvoid = shouldAvoid;
        }
    }

    private static class PowerRace {
        private final double score;
        private final boolean worthContesting;

        private PowerRace(double score, boolean worthContesting) {
            this.score = score;
            this.worthContesting = worthContesting;
        }
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

    private class CounterPathfinder {
        private final int[][] grid;
        private final int rows;
        private final int cols;
        private final GameState state;
        private final UnitInfo self;
        private final UnitInfo ignoredEnemy;
        private final ArrayList<UnitInfo> enemies;

        private CounterPathfinder(GameState state, UnitInfo self, UnitInfo ignoredEnemy) {
            this.state = state;
            this.self = self;
            this.ignoredEnemy = ignoredEnemy;
            this.enemies = state.getLivingEnemies(self);
            this.rows = state.getRows();
            this.cols = state.getCols();
            this.grid = new int[rows][cols];
            initializeMap();
        }

        private void initializeMap() {
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    Position position = new Position(row, col);
                    boolean ignored = ignoredEnemy != null
                            && ignoredEnemy.getRow() == row
                            && ignoredEnemy.getCol() == col;
                    if ((state.isOccupied(position) || state.isWall(position)) && !ignored) {
                        grid[row][col] = -1;
                    } else {
                        grid[row][col] = 1;
                    }
                }
            }
            grid[self.getRow()][self.getCol()] = 1;
        }

        private void addEnemyCost() {
            for (int i = 0; i < enemies.size(); i++) {
                UnitInfo enemy = enemies.get(i);
                if (ignoredEnemy != null && ignoredEnemy.getId() == enemy.getId()) {
                    continue;
                }
                int alertRadius = enemy.getRange();
                if (enemy.getAttackPower() >= 15) {
                    alertRadius++;
                }
                for (int row = Math.max(0, enemy.getRow() - alertRadius);
                        row <= Math.min(rows - 1, enemy.getRow() + alertRadius);
                        row++) {
                    for (int col = Math.max(0, enemy.getCol() - alertRadius);
                            col <= Math.min(cols - 1, enemy.getCol() + alertRadius);
                            col++) {
                        if (grid[row][col] == -1) {
                            continue;
                        }
                        double distance = Math.abs(enemy.getRow() - row) + Math.abs(enemy.getCol() - col);
                        if (distance <= alertRadius) {
                            grid[row][col] += enemyBlockCost(self, enemy, distance, alertRadius);
                        }
                    }
                }
            }
        }

        private CounterBlock findPath(Position start, Position goal) {
            int originalGoalCost = grid[goal.getRow()][goal.getCol()];
            if (originalGoalCost == -1 && !state.isWall(goal)) {
                grid[goal.getRow()][goal.getCol()] = 1;
            }

            PriorityQueue<CounterBlock> frontier =
                    new PriorityQueue<CounterBlock>(Comparator.comparingInt(CounterBlock::getFCost));
            int[][] bestCost = new int[rows][cols];
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    bestCost[row][col] = Integer.MAX_VALUE;
                }
            }

            CounterBlock first = new CounterBlock(
                    start.getRow(),
                    start.getCol(),
                    0,
                    distance(start.getRow(), start.getCol(), goal.getRow(), goal.getCol()),
                    null);
            frontier.add(first);
            bestCost[start.getRow()][start.getCol()] = 0;

            while (!frontier.isEmpty()) {
                CounterBlock current = frontier.poll();
                if (current.getRow() == goal.getRow() && current.getCol() == goal.getCol()) {
                    grid[goal.getRow()][goal.getCol()] = originalGoalCost;
                    return current;
                }
                tryAdd(frontier, bestCost, current, goal, -1, 0);
                tryAdd(frontier, bestCost, current, goal, 1, 0);
                tryAdd(frontier, bestCost, current, goal, 0, -1);
                tryAdd(frontier, bestCost, current, goal, 0, 1);
            }

            grid[goal.getRow()][goal.getCol()] = originalGoalCost;
            return null;
        }

        private void tryAdd(
                PriorityQueue<CounterBlock> frontier,
                int[][] bestCost,
                CounterBlock current,
                Position goal,
                int rowChange,
                int colChange) {
            int nextRow = current.getRow() + rowChange;
            int nextCol = current.getCol() + colChange;
            if (nextRow < 0 || nextRow >= rows || nextCol < 0 || nextCol >= cols) {
                return;
            }
            if (grid[nextRow][nextCol] == -1) {
                return;
            }

            int newCost = current.getGCost() + grid[nextRow][nextCol];
            if (newCost >= bestCost[nextRow][nextCol]) {
                return;
            }

            bestCost[nextRow][nextCol] = newCost;
            CounterBlock next = new CounterBlock(
                    nextRow,
                    nextCol,
                    newCost,
                    distance(nextRow, nextCol, goal.getRow(), goal.getCol()),
                    current);
            frontier.add(next);
        }

        private int distance(int row1, int col1, int row2, int col2) {
            return Math.abs(row1 - row2) + Math.abs(col1 - col2);
        }
    }

    private static class CounterBlock {
        private final int row;
        private final int col;
        private final int gCost;
        private final int hCost;
        private final CounterBlock parent;

        private CounterBlock(int row, int col, int gCost, int hCost, CounterBlock parent) {
            this.row = row;
            this.col = col;
            this.gCost = gCost;
            this.hCost = hCost;
            this.parent = parent;
        }

        private int getRow() {
            return row;
        }

        private int getCol() {
            return col;
        }

        private int getGCost() {
            return gCost;
        }

        private int getFCost() {
            return gCost + hCost;
        }

        private CounterBlock getParent() {
            return parent;
        }
    }
}
