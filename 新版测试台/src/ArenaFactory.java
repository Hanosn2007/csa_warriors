public class ArenaFactory {
    public static GameEngine createDefaultEngine() {
        GameMap map = GameMap.createDefault();
        GameEngine engine = new GameEngine(map, GameEngine.DEFAULT_MAX_ROUNDS);

        Team red = new Team("Red", 'R');
        Team blue = new Team("Blue", 'B');
        Team green = new Team("Green", 'G');
        Team yellow = new Team("Yellow", 'Y');

        engine.addTeam(red);
        engine.addTeam(blue);
        engine.addTeam(green);
        engine.addTeam(yellow);

        engine.addWarrior(red, new TeamAlphaWarrior(), 0, 0);
        engine.addWarrior(red, new TeamAlphaWarrior(), 0, 1);

        engine.addWarrior(blue, new AllianceEncirclingWarrior(), 0, 11);
        engine.addWarrior(blue, new AllianceEncirclingWarrior(), 1, 11);

        engine.addWarrior(green, new AllianceEncirclingWarrior(), 11, 0);
        engine.addWarrior(green, new AllianceEncirclingWarrior(), 10, 0);

        engine.addWarrior(yellow, new AllianceEncirclingWarrior(), 11, 11);
        engine.addWarrior(yellow, new AllianceEncirclingWarrior(), 10, 11);

        engine.addStartingPowerUps(5);

        return engine;
    }
}
