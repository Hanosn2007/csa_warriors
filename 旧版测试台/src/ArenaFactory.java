public class ArenaFactory {
    public static GameEngine createDefaultEngine() {
        GameMap map = GameMap.createDefault();
        GameEngine engine = new GameEngine(map, GameEngine.NO_ROUND_LIMIT);

        Team red = new Team("Red", 'R');
        Team blue = new Team("Blue", 'B');
        Team green = new Team("Green", 'G');
        Team yellow = new Team("Yellow", 'Y');

        engine.addTeam(red);
        engine.addTeam(blue);
        engine.addTeam(green);
        engine.addTeam(yellow);

        engine.addWarrior(red, new DefaultWarrior(), 0, 0);
        engine.addWarrior(red, new DefaultWarrior(), 0, 1);

        engine.addWarrior(blue, new DefaultWarrior(), 0, 11);
        engine.addWarrior(blue, new DefaultWarrior(), 1, 11);

        engine.addWarrior(green, new DefaultWarrior(), 11, 0);
        engine.addWarrior(green, new DefaultWarrior(), 10, 0);

        engine.addWarrior(yellow, new DefaultWarrior(), 11, 11);
        engine.addWarrior(yellow, new DefaultWarrior(), 10, 11);

        engine.addStartingPowerUps(5);

        return engine;
    }
}
