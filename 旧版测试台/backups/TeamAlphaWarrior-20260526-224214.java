import java.util.ArrayList;
import java.util.Comparator;
import java.util.PriorityQueue;

public class TeamAlphaWarrior extends Warrior {
    public TeamAlphaWarrior() {
        super("Team Alpha666");
    }
    private Action moveToPosition(GameState state, UnitInfo self, Position target, UnitInfo ignoredEnemy) {
        AStar aStar = new AStar(state, self, ignoredEnemy);
        aStar.addEnemyCost();

        Block end = aStar.findPath(target, self.getPosition());
        //保证astar确实找到了目标，没有的话就保守前进
        if (end != null && end.getParent() != null) {
            Block next = end.getParent();
            return state.moveToward(self, new Position(next.getRow(), next.getCol()));
        }
        return state.moveToward(self, target);
    }
    private UnitInfo findBestEnemyInRange(GameState state, UnitInfo self, int range) {
        ArrayList<UnitInfo> enemies = state.getLivingEnemies(self);
        //用敌人可攻击价值计算方法，选出给定范围内最值得攻击的
        UnitInfo bestEnemy = null;
        double bestValue = -9999;
        //
        for (UnitInfo enemy : enemies) {
            if (self.distanceTo(enemy) > range) {
                continue;
            }
            double value = Rate.attackValue(self, enemy);
            if (bestEnemy == null || value > bestValue) {
                bestEnemy = enemy;
                bestValue = value;
            }
        }

        return bestEnemy;
    }
    @Override
    public Action chooseAction(GameState state, UnitInfo self) {
        //保血
        double curHealthRate = Rate.unitHealthRate(self);
        if (curHealthRate <= 0.6) {
            Position healingPoint = state.findNearestHealingPoint(self);
            if (healingPoint != null) {
                if (self.getPosition().equals(healingPoint)) {
                    return Action.defend();
                }
                return moveToPosition(state, self, healingPoint, null);
            }
            UnitInfo nearestEnemy = state.findNearestEnemy(self);
            if (nearestEnemy != null) {
                return state.moveAwayFrom(self, nearestEnemy.getPosition());
            }
            return Action.defend();
        }
        //杀范围内最值得的
        UnitInfo attackTarget = findBestEnemyInRange(state, self, self.getRange());
        if (attackTarget != null) {
            return Action.attack(attackTarget.getId());
        }
        //近处道具
        Position powerUp = state.findNearestPowerUp(self);
        if (powerUp != null && self.distanceTo(powerUp) <= 8) {
            return moveToPosition(state, self, powerUp, null);
        }
        //前往最值得杀的敌人
        UnitInfo nearestBestEnemy = findBestEnemyInRange(state, self, 9999);
        if (nearestBestEnemy != null) {
            return moveToPosition(state, self, nearestBestEnemy.getPosition(), nearestBestEnemy);
        }
        if (powerUp != null) {
            return moveToPosition(state, self, powerUp, null);
        }
        return Action.defend();
    }
}

class Rate{
    static double unitHealthRate(UnitInfo self){
        return (double) self.getHealth() / self.getMaxHealth();
    }
    static double unitThreat(UnitInfo self) {
        double healthRate = (double) self.getHealth() / self.getMaxHealth();
        double attackRate = (double) self.getAttackPower() / 11.0;
        double rangeRate = (double) self.getRange() / 1.0;
        return healthRate * attackRate * rangeRate;
    }
    static double unitWeakness(UnitInfo self) {
        double missingHealthRate = 1.0 - (double) self.getHealth() / self.getMaxHealth();
        double defendRate = 1.0;
        if (self.isDefending()) {
            defendRate = 0.5;
        }
        return missingHealthRate * defendRate;
    }
    static double attackValue(UnitInfo self, UnitInfo enemy) {
        double threat = unitThreat(enemy);
        double weakness = unitWeakness(enemy);
        double disPunish = self.distanceTo(enemy) * 0.15;
        //如果可以秒掉敌人就加大分
        double lethalBonus = 0;
        if (self.getAttackPower() >= enemy.getHealth()) {
            lethalBonus = 1.5;
        }
        //优先
        return weakness * 2.0 + threat * 0.8 + lethalBonus - disPunish;
    }
    //给astar计算敌人附近格子的额外代价的工具方法
    static int enemyBlockCost(UnitInfo self, UnitInfo enemy, double dist, int alertRadius) {
        //敌人位置的代价计算
        double selfThreat = unitThreat(self);
        double enemyThreat = unitThreat(enemy);

        double relativeThreat = enemyThreat / Math.max(0.1, selfThreat);
        double distanceCost = alertRadius - dist + 1;

        return (int) (distanceCost * relativeThreat * 3);
    }
}

class AStar{
    private final int[][] grid;
    private final int rows;
    private final int cols;

    private int extraR = 0;
    private GameState state;
    private UnitInfo self;
    private UnitInfo ignoreEnemy;
    private final ArrayList<UnitInfo> enemies;
    //
    public AStar(GameState state, UnitInfo self, UnitInfo ignoreEnemy){
        //写入工具
        this.state = state;
        this.self = self;
        this.ignoreEnemy = ignoreEnemy;
        this.enemies = state.getLivingEnemies(self);
        //写入地图范围
        this.rows = state.getRows();
        this.cols = state.getCols();
        //初始化地图list
        grid = new int[rows][cols];
        //初始化
        initialMap();
    }
    private void initialMap() {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                Position curPos = new Position(row, col);
                boolean curIsIgnEnemy = ignoreEnemy != null && ignoreEnemy.getRow() == row && ignoreEnemy.getCol() == col;
                if ((state.isOccupied(curPos) || state.isWall(curPos)) && !curIsIgnEnemy) {
                    grid[row][col] = -1;
                } else {
                    grid[row][col] = 1;
                }
            }
        }
        //将自己设为1，因为isOccupied会把自己也返回
        grid[self.getRow()][self.getCol()] = 1;
    }
    public void addEnemyCost(){
        //可以给自己设一个半径，在这个半径内如果碰到敌人了，就要重新计算路线，否则按照计算好的路走，避免过度计算。这个半径要参考场上敌人的最大攻击range
        for (UnitInfo enemy : enemies) {
            //是忽略的则跳过
            if (ignoreEnemy != null && ignoreEnemy.getId() == enemy.getId()){
                continue;
            }
            //在敌人周围半径的方块内遍历，减小计算
            //ar设置是基础的攻击范围+额外警戒
            int aR = enemy.getRange() + extraR;
            for (int row = Math.max(0, enemy.getRow() - aR); row <= Math.min(grid.length - 1, enemy.getRow() + aR); row++){
                for (int col = Math.max(0, enemy.getCol() - aR); col <= Math.min(grid[0].length - 1, enemy.getCol() + aR); col++){
                    //不能走跳过
                    if (grid[row][col] == -1) {
                        continue;
                    }
                    //可以则加权
                    double dist = calcDistance(enemy.getRow(), enemy.getCol(), row, col);
                    if (dist <= aR) {
                        int cost = Rate.enemyBlockCost(self, enemy, dist, aR);
                        grid[row][col] += cost;
                    }
                }
            }
        }
    }
    public Block findPath(Position start, Position goal){
        //开始给goal如果是敌人改成1
        int originalGoalCost = grid[goal.getRow()][goal.getCol()];
        if (originalGoalCost == -1 && !state.isWall(goal)) {
            grid[goal.getRow()][goal.getCol()] = 1;
        }
        //初始化优先队列
        PriorityQueue<Block> frontier = new PriorityQueue<>(
                Comparator.comparingInt(Block::getFCost)
        );
        //走到这个格子需要的地图格子本身代价累计
        int[][] blockStep = new int[rows][cols];
        //全填充max，表示没走过
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                blockStep[row][col] = Integer.MAX_VALUE;
            }
        }
        //创建初始block
        Block startBlock = new Block(
                start.getRow(),
                start.getCol(),
                0,
                calcDistance(start.getRow(), start.getCol(), goal.getRow(), goal.getCol()),
                null);
        //给优先队列和step列表初始化
        frontier.add(startBlock);
        blockStep[start.getRow()][start.getCol()] = 0;
        //astar主循环
        while (!frontier.isEmpty()) {
            Block current = frontier.poll();
            if (current.getRow() == goal.getRow() && current.getCol() == goal.getCol()) {
                //计算完再把代价改回去
                grid[goal.getRow()][goal.getCol()] = originalGoalCost;
                return current;
            }
            //当前格子四周尝试添加
            tryAdd(frontier, blockStep, current, goal, -1, 0);
            tryAdd(frontier, blockStep, current, goal, 1, 0);
            tryAdd(frontier, blockStep, current, goal, 0, -1);
            tryAdd(frontier, blockStep, current, goal, 0, 1);
        }
        //计算完再把代价改回去
        grid[goal.getRow()][goal.getCol()] = originalGoalCost;
        //能取的格子取光了才结束，那就是没有结果，寻路失败
        return null;
    }
    private void tryAdd(
            PriorityQueue<Block> frontier,
            int[][] blockStep,
            Block current,
            Position goal,
            int rowChange,
            int colChange) {
        //四周新格子坐标
        int nextRow = current.getRow() + rowChange;
        int nextCol = current.getCol() + colChange;
        //断言1，超出地图范围停止
        if (nextRow < 0 || nextRow >= rows || nextCol < 0 || nextCol >= cols) {
            return;
        }
        //断言2，不能走，比如墙和玩家，停止
        if (grid[nextRow][nextCol] == -1) {
            return;
        }
        //获取step list里的当前值
        int newGCost = current.getGCost() + grid[nextRow][nextCol];
        //如果这次蔓延的step更多，说明已经有的已经是代价更小的路了，也不用覆盖，停止
        if (newGCost >= blockStep[nextRow][nextCol]) {
            return;
        }
        //都没问题开始写入
        blockStep[nextRow][nextCol] = newGCost;
        //创建这个位置的block实例
        Block next = new Block(
                nextRow,
                nextCol,
                newGCost,
                calcDistance(nextRow, nextCol, goal.getRow(), goal.getCol()),
                current
        );
        frontier.add(next);
    }
    private static int calcDistance(int row1, int col1, int row2, int col2) {
        return Math.abs(row1 - row2) + Math.abs(col1 - col2);
    }
}

class Block {
    private final int row;
    private final int col;
    private final int gCost;
    private final int hCost;
    private final Block parent;

    public Block(int row, int col, int gCost, int hCost, Block parent) {
        this.row = row;
        this.col = col;
        this.gCost = gCost;
        this.hCost = hCost;
        this.parent = parent;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public int getGCost() {
        return gCost;
    }

    public int getFCost() {
        return gCost + hCost;
    }

    public Block getParent() {
        return parent;
    }
}
