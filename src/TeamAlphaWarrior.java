// ================================================================
// 策略模板
//
// 你只需要修改本文件。
// 本文件中标有“完全不用修改”的位置保持原样。
// 主要修改区域是 chooseAction 方法中的“策略区”。
// ================================================================
import java.util.ArrayList;

// ==================== 完全不用修改：类声明 ====================
// 文件名、类名和 public class 后面的名字必须完全一致。
public class TeamAlphaWarrior extends Warrior {

    // ==================== 完全不用修改：构造方法声明 ====================
    // 构造方法名字必须和文件名、类名一致：
    // 文件名：TeamAlphaWarrior.java
    // 类名：public class TeamAlphaWarrior
    // 构造方法：public TeamAlphaWarrior()
    public TeamAlphaWarrior() {
        // ==================== 可以修改：队伍显示名称 ====================
        // 只改双引号里面的文字，例如 "Alpha Team"。
        // 不要删除 super，也不要改 super 前后的括号和分号。
        super("Team Alpha666");
    }

    static class Block {
        private final int row;
        private final int col;
        private final int step;
        private final int dist;
        private final int cost;
        private final Block superBlock;

        public Block(int row, int col, int step, int dist, Block superBlock) {
            this.row = row;
            this.col = col;
            this.step = step;
            this.dist = dist;
            this.superBlock = superBlock;
            this.cost = step + dist;
        }
        public int getRow() {
            return row;
        }
        public int getCol() {
            return col;
        }
    }

    static class MyPriorityQueue {
        private final ArrayList<Block> blocks =  new ArrayList<>();
        public MyPriorityQueue() {
            blocks.add(new Block(0,0,0,Integer.MAX_VALUE,null));
        }
        public void add(Block block) throws InterruptedException {
            for (int i = 0; i < blocks.size(); i++){
                if (block.cost < blocks.get(i).cost){
                    blocks.add(i, block);
                    break;
                }
            }
        }
        public Block get(){
            if (blocks.size() == 1){
                return null;
            }
            return blocks.remove(0);
        }
        public boolean empty(){
            return blocks.size() == 1;
        }
    }

    static int calcDistance(Position pos1, Position pos2) {
        return Math.abs(pos1.getRow() - pos2.getRow()) + Math.abs(pos1.getCol() - pos2.getCol());
    }

    static Position[] aStar(GameState state, Position start, Position goal){
        MyPriorityQueue frontier = new MyPriorityQueue();

        return null;
    }















    // ==================== 完全不用修改：方法声明 ====================
    // 游戏每次轮到这个战士行动时，都会自动调用 chooseAction。
    // state 表示当前地图、敌人、队友、治疗点和道具。
    // self 表示这个战士自己。
    @Override
    public Action chooseAction(GameState state, UnitInfo self) throws InterruptedException {

        // ================================================================
        // 策略区开始：这里是主要修改的位置。
        //
        // 可以修改：
        // 1. if / else 的判断顺序。
        // 2. 血量数字、距离数字，例如 35、4。
        // 3. 优先攻击谁、优先抢哪个道具、什么时候撤退。
        // 4. return 的行动，例如 attack、defend、stay、moveToward。
        //
        // 不要修改：
        // 1. public Action chooseAction(GameState state, UnitInfo self)
        // 2. 最外层的 { 和 }。
        // 3. 文件名、类名、构造方法名。
        //
        // 每一种情况最后都要 return 一个 Action。
        // ================================================================

        enum State {FindEnemy, FindPowerUp, Attack, Escape, StayAway}
        State curState = State.FindPowerUp;
        switch (curState) {
            case FindEnemy: {
                Position Enemy = state.findWeakestEnemy(self).getPosition();
            }
            case FindPowerUp: {
                MyPriorityQueue frontier = new MyPriorityQueue();
                for (int i = 0; i < 10; i ++) {
                    frontier.add(new Block(1, 1, 0, (int) (Math.random() * 100), null));
                }
                Block curBlock;
                int index = 0;
                while(!frontier.empty()){
                    curBlock = frontier.get();
                    System.out.println("index" + index + "cost" + curBlock.cost);
                    index++;
                }


            }
        }
        return Action.defend();
        // 1. 如果攻击范围内有残血敌人，先攻击它。
//        UnitInfo target = state.findLowestHealthEnemyInRange(self);
//        if (target != null) {
//            return Action.attack(target.getId());
//        }
//
//
//        // 2. 低血量时，优先远离最近敌人。
//        if (self.getHealth() < 35) {
//            UnitInfo nearestEnemy = state.findNearestEnemy(self);
//            if (nearestEnemy != null) {
//                return state.moveAwayFrom(self, nearestEnemy.getPosition());
//            }
//            return Action.defend();
//        }
//
//        // 3. 附近有道具时，可以先抢道具。
//        Position powerUp = state.findNearestPowerUp(self);
//        if (powerUp != null && self.distanceTo(powerUp) <= 4) {
//            return state.moveToward(self, powerUp);
//        }
//
//        // 4. 没有更重要的事情时，寻找最近敌人。
//        UnitInfo enemy = state.findNearestEnemy(self);
//        if (enemy == null) {
//            return Action.stay();
//        }
//
//        // 5. 敌人在攻击范围内就攻击。
//        if (self.distanceTo(enemy) <= self.getRange()) {
//            return Action.attack(enemy.getId());
//        }
//
//        // 6. 攻击不到敌人时，向敌人靠近一步。
//        // 这也是策略区的一部分，可以修改成其他行动。
//        return state.moveToward(self, enemy.getPosition());
    }
    // ==================== 完全不用修改：类结尾大括号 ====================
}
