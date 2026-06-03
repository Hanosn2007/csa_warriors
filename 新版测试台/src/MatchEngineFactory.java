import java.io.File;
import java.io.FilenameFilter;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;

public class MatchEngineFactory {
    public static GameEngine createEngine(MatchConfig config) {
        return createEngine(config, null);
    }

    public static GameEngine createEngine(MatchConfig config, long seed) {
        return createEngine(config, Long.valueOf(seed));
    }

    private static GameEngine createEngine(MatchConfig config, Long seed) {
        GameMap map = GameMap.createDefault();
        GameEngine engine = new GameEngine(map, GameEngine.DEFAULT_MAX_ROUNDS);
        if (seed != null) {
            engine.setRandomSeed(seed.longValue());
        }

        Team red = new Team("Red", 'R');
        Team blue = new Team("Blue", 'B');
        Team green = new Team("Green", 'G');
        Team yellow = new Team("Yellow", 'Y');

        engine.addTeam(red);
        engine.addTeam(blue);
        engine.addTeam(green);
        engine.addTeam(yellow);

        engine.addWarrior(red, createWarrior(config.getRedWarriorAClass(), "Red A"), 0, 0);
        engine.addWarrior(red, createWarrior(config.getRedWarriorBClass(), "Red B"), 0, 1);

        engine.addWarrior(blue, createWarrior(config.getBlueWarriorAClass(), "Blue A"), 0, 11);
        engine.addWarrior(blue, createWarrior(config.getBlueWarriorBClass(), "Blue B"), 1, 11);

        engine.addWarrior(green, createWarrior(config.getGreenWarriorAClass(), "Green A"), 11, 0);
        engine.addWarrior(green, createWarrior(config.getGreenWarriorBClass(), "Green B"), 10, 0);

        engine.addWarrior(yellow, createWarrior(config.getYellowWarriorAClass(), "Yellow A"), 11, 11);
        engine.addWarrior(yellow, createWarrior(config.getYellowWarriorBClass(), "Yellow B"), 10, 11);

        engine.addStartingPowerUps(5);
        return engine;
    }

    public static GameEngine createAllianceEngine(MatchConfig config) {
        return createAllianceEngine(config, null);
    }

    public static GameEngine createAllianceEngine(MatchConfig config, long seed) {
        return createAllianceEngine(config, Long.valueOf(seed));
    }

    private static GameEngine createAllianceEngine(MatchConfig config, Long seed) {
        GameMap map = GameMap.createDefault();
        GameEngine engine = new GameEngine(map, GameEngine.DEFAULT_MAX_ROUNDS);
        if (seed != null) {
            engine.setRandomSeed(seed.longValue());
        }

        Team challengers = new Team("Challengers", 'C');
        Team baseline = new Team("Baseline", 'O');

        engine.addTeam(challengers);
        engine.addTeam(baseline);

        engine.addWarrior(challengers, createWarrior(config.getRedWarriorAClass(), "Challenger A1"), 0, 0);
        engine.addWarrior(challengers, createWarrior(config.getRedWarriorBClass(), "Challenger A2"), 0, 1);
        engine.addWarrior(challengers, createWarrior(config.getBlueWarriorAClass(), "Challenger B1"), 11, 0);
        engine.addWarrior(challengers, createWarrior(config.getBlueWarriorBClass(), "Challenger B2"), 10, 0);

        engine.addWarrior(baseline, createWarrior(config.getGreenWarriorAClass(), "Baseline A1"), 0, 11);
        engine.addWarrior(baseline, createWarrior(config.getGreenWarriorBClass(), "Baseline A2"), 1, 11);
        engine.addWarrior(baseline, createWarrior(config.getYellowWarriorAClass(), "Baseline B1"), 11, 11);
        engine.addWarrior(baseline, createWarrior(config.getYellowWarriorBClass(), "Baseline B2"), 10, 11);

        engine.addStartingPowerUps(5);
        return engine;
    }

    public static ArrayList<String> findAvailableWarriorClasses() {
        ArrayList<String> names = new ArrayList<String>();
        File srcDir = new File(System.getProperty("user.dir"), "src");
        File[] files = srcDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".java") && !name.equals("Warrior.java");
            }
        });

        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                String className = files[i].getName().substring(0, files[i].getName().length() - ".java".length());
                if (canCreateWarrior(className)) {
                    names.add(className);
                }
            }
        }

        Collections.sort(names);
        return names;
    }

    public static Warrior createWarrior(String className, String displayName) {
        try {
            Class<?> rawClass = Class.forName(className);
            if (!Warrior.class.isAssignableFrom(rawClass)) {
                throw new IllegalArgumentException(className + " is not a Warrior.");
            }

            try {
                Constructor<?> stringConstructor = rawClass.getConstructor(String.class);
                return (Warrior) stringConstructor.newInstance(displayName);
            } catch (NoSuchMethodException exception) {
                Constructor<?> emptyConstructor = rawClass.getConstructor();
                return (Warrior) emptyConstructor.newInstance();
            }
        } catch (Exception exception) {
            throw new IllegalArgumentException("无法创建策略类: " + className + "。请确认它已经编译，并且有 public 无参构造方法。");
        }
    }

    private static boolean canCreateWarrior(String className) {
        try {
            createWarrior(className, className);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
