//点子
//要用上powerRate的进度
//要避免僵局

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.PriorityQueue;

public class TeamHanson2Warrior extends Warrior {
    private GameState state = null;
    private UnitInfo self = null;
    private UnitInfo bestTeamEnemy;

    public TeamHanson2Warrior() {
        super("Hanson team");
    }

    //debug
    public int[][] debugCostMap(GameState state, UnitInfo self) {
        AStar aStar = new AStar(state, self, null, null);
//        aStar.addEnemyCost();
        return aStar.copyGrid();
    }

    public DebugPathResult debugPath(GameState state, UnitInfo self, Position target) {
        AStar aStar = new AStar(state, self, null, null);
//        aStar.addEnemyCost();
        Block end = aStar.findPath(target, self.getPosition());
        return new DebugPathResult(aStar.copyGrid(), aStar.pathFrom(end), end == null ? -1 : end.getGCost());
    }
    //debug
    private double eulerDistance(Position pos1, Position pos2) {
        int x = Math.abs(pos1.getRow() -  pos2.getRow());
        int y = Math.abs(pos1.getCol() -  pos2.getCol());
        return Math.sqrt( x * x + y * y );
    }
    private Action moveToPosition(Position target, UnitInfo ignoredEnemy) {
        if (target == null) {
            return Action.defend();
        }
        if (self.getPosition().equals(target)) {
            return Action.defend();
        }
        AStar aStar = new AStar(state, self, ignoredEnemy, null);
//        aStar.addEnemyCost();

        Block end = aStar.findPath(target, self.getPosition());
        //保证astar确实找到了目标，没有的话就保守前进
        if (end != null && end.getParent() != null) {
            Block next = end.getParent();
            return state.moveToward(self, new Position(next.getRow(), next.getCol()));
        }
        return state.moveToward(self, target);
    }
    private double positionThreat(Position pos){
        double pointThreat = 0;
        for (UnitInfo e : state.getLivingEnemies(self)){
            double distanceRate = Math.pow(Math.max(1, eulerDistance(e.getPosition(), pos)), -1);
            pointThreat += Rate.unitThreat(e) * distanceRate;
        }
        return pointThreat;
    }
    private ArrayList<Position> findAllHealingPoints(){
        ArrayList<Position> powerUps = new ArrayList<>();
        for(int row = 0; row < state.getRows(); row++){
            for(int col = 0; col < state.getCols(); col++){
                Position pos = new Position(row, col);
                if (state.isHealingPoint(pos)){
                    powerUps.add(pos);
                }
            }
        }
        return powerUps;
    }
    private void printLog(UnitInfo self, String msg){
        int selfId = self.getId();
        String log = self.getId() + " : " + msg + " ";
        if (selfId == 1){
            System.out.print(log);
        }else{
            System.out.println(log);
        }
    }
    //从团队质心
    private UnitInfo findBestEnemy(Position pos, double threat, double range) {
        UnitInfo bestEnemy = null;
        double bestThreat = threat;
        for (UnitInfo e : state.getLivingEnemies(self)){
            double currentThreat = Rate.unitThreat(e);
            if (currentThreat < bestThreat && eulerDistance(e.getPosition(), pos) <= range) {
                bestEnemy = e;
                bestThreat = currentThreat;
            }
        }
        return bestEnemy;
    }
    private UnitInfo findBestEnemyForTeam(double range) {
        ArrayList<UnitInfo> teamMates = state.getLivingTeammates(self);
        UnitInfo self1 = self;
        UnitInfo self2;
        if (!teamMates.isEmpty()) {
            self2 = teamMates.get(0);
        }else{
            self2 = self1;
        }
        //
        int minRow = Math.min(self1.getRow(), self2.getRow());
        int maxRow = Math.max(self1.getRow(), self2.getRow());
        int minCol = Math.min(self1.getCol(), self2.getCol());
        int maxCol = Math.max(self1.getCol(), self2.getCol());
        int centerRow = minRow + (maxRow - minRow) / 2;
        int centerCol = minCol + (maxCol - minCol) / 2;
        Position center = new Position(centerRow, centerCol);
        //
        double avgThreat = (Rate.unitThreat(self1) + Rate.unitThreat(self2)) / 2.0;
        //
        return findBestEnemy(center, 9999, range);
    }
    private Position findBestHealingPoint(){
        Position bestHealingPoint = null;
        double bestThreat = Integer.MAX_VALUE;
        for (Position pos : findAllHealingPoints()) {
            double pointThreat = positionThreat(pos);
            if (pointThreat < bestThreat && !state.isOccupied(pos)){
                bestThreat = pointThreat;
                bestHealingPoint = pos;
            }
        }
        return bestHealingPoint;
    }
    private Position findBestPowerUp() {
        Position bestPowerUp = null;
        double bestThreat = Integer.MAX_VALUE;
        for (Position pos : state.getPowerUpPositions()) {
            double pointThreat = positionThreat(pos);
            if (pointThreat < bestThreat && !state.isOccupied(pos)){
                bestThreat = pointThreat;
                bestPowerUp = pos;
            }
        }
        return bestPowerUp;
    }

    @Override
    public Action chooseAction(GameState state, UnitInfo self) {
        return Action.defend();
    }
}

class Rate {
    static double MAX_THREAT = 4.63;
    //标准倍率计算
    static double healthRate(UnitInfo self){
        return (double) self.getHealth() / self.getMaxHealth();
    }
    static double attackRate(UnitInfo self){
        return self.getAttackPower() / 11.0;
    } //最大攻击力17
    static double rangeRate(UnitInfo self){
        return (double) self.getRange();
    }
    //单位增幅进度 0 - 1，只有攻击力和范围能永久提升
    static double powerUpRate(UnitInfo self) {
        double attackProgress = Math.max(0, Math.min(1, (self.getAttackPower() - 11) / 6.0));
        double rangeProgress = Math.max(0, Math.min(1, (self.getRange() - 1) / 2.0));
        return (attackProgress + rangeProgress) / 2.0;
    }
    //
    static double unitThreat(UnitInfo self) {
        return healthRate(self) * attackRate(self) * (rangeRate(self) * 100);
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

    private GameState state;
    private UnitInfo self;
    private UnitInfo ignoreEnemy;
    private Position powerUp[];
    //debug
    public int[][] copyGrid() {
        int[][] copy = new int[rows][cols];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                copy[row][col] = grid[row][col];
            }
        }
        return copy;
    }
    public ArrayList<Position> pathFrom(Block end) {
        ArrayList<Position> path = new ArrayList<Position>();
        Block current = end;
        while (current != null) {
            path.add(new Position(current.getRow(), current.getCol()));
            current = current.getParent();
        }
        return path;
    }
    //debug
    private double eulerDistance(Position pos1, Position pos2) {
        int x = Math.abs(pos1.getRow() -  pos2.getRow());
        int y = Math.abs(pos1.getCol() -  pos2.getCol());
        return Math.sqrt( x * x + y * y );
    }
    private boolean posInList(Position pos, ArrayList<Position> list) {
        for (Position p : list) {
            if (p.getRow() == pos.getRow() && p.getCol() == pos.getCol()) {
                return true;
            }
        }
        return false;
    }
    private ArrayList<Position> findAllHealingPoints(){
        ArrayList<Position> powerUps = new ArrayList<>();
        for(int row = 0; row < state.getRows(); row++){
            for(int col = 0; col < state.getCols(); col++){
                Position pos = new Position(row, col);
                if (state.isHealingPoint(pos)){
                    powerUps.add(pos);
                }
            }
        }
        return powerUps;
    }

    private double positionCost(Position pos, ArrayList<Position> ignoreSource){
        double pointThreat = 2;
        for (UnitInfo e : state.getLivingEnemies(self)){
            if (!posInList(e.getPosition(), ignoreSource)){
                double distanceRate = Math.pow(Math.max(1, eulerDistance(e.getPosition(), pos)), -1.2);
                pointThreat += (1 + Rate.unitThreat(e)) * distanceRate;
            }
        }
        for (Position p : state.getPowerUpPositions()){
            if (!posInList(p, ignoreSource)){
                double distanceRate = Math.pow(Math.max(1, eulerDistance(p, pos)), -1);
                pointThreat -= 1 * distanceRate;
            }
        }
        for (Position h : findAllHealingPoints()){
            if (!posInList(h, ignoreSource)){
                double distanceRate = Math.pow(Math.max(1, eulerDistance(h, pos)), -1);
                pointThreat -= 2 * distanceRate;
            }
        }
        pointThreat = Math.max(pointThreat, 0);
        return pointThreat;
    }
    //
    public AStar(GameState state, UnitInfo self, UnitInfo ignoreEnemy, Position[] powerUp){
        //写入工具
        this.state = state;
        this.self = self;
        this.ignoreEnemy = ignoreEnemy;
        this.powerUp = powerUp;
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
//                    grid[row][col] = 1;
                    ArrayList<Position> ignores = new ArrayList<>();
                    grid[row][col] = Math.max(1, (int)positionCost(new Position(row, col), ignores));
                }
            }
        }
    }

    public Block findPath(Position start, Position goal){
        grid[start.getRow()][start.getCol()] = 1;
        grid[goal.getRow()][goal.getCol()] = 1;
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
                return current;
            }
            //当前格子四周尝试添加
            tryAdd(frontier, blockStep, current, goal, -1, 0);
            tryAdd(frontier, blockStep, current, goal, 1, 0);
            tryAdd(frontier, blockStep, current, goal, 0, -1);
            tryAdd(frontier, blockStep, current, goal, 0, 1);
        }
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
        //tryadd这个的新格子坐标
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
//debug
class DebugPathResult {
    private final int[][] costMap;
    private final ArrayList<Position> path;
    private final int totalCost;

    public DebugPathResult(int[][] costMap, ArrayList<Position> path, int totalCost) {
        this.costMap = costMap;
        this.path = path;
        this.totalCost = totalCost;
    }

    public int[][] getCostMap() {
        return costMap;
    }

    public ArrayList<Position> getPath() {
        return path;
    }

    public int getTotalCost() {
        return totalCost;
    }

    public boolean isReachable() {
        return totalCost >= 0;
    }
}
//debug
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
