import java.util.ArrayList;

public class ReplaySnapshot {
    private final int stepIndex;
    private final int round;
    private final int lastActingUnitId;
    private final String intentText;
    private final String traceText;
    private final ArrayList<ReplayUnitState> units;
    private final ArrayList<ReplayPowerUpState> powerUps;

    public ReplaySnapshot(int stepIndex, GameEngine engine) {
        this.stepIndex = stepIndex;
        this.round = engine.getCurrentRound();
        this.lastActingUnitId = engine.getLastActingUnitId();
        this.intentText = engine.getLastIntentText();
        this.traceText = engine.getLastDecisionTraceText();
        this.units = captureUnits(engine);
        this.powerUps = capturePowerUps(engine);
    }

    public int getStepIndex() {
        return stepIndex;
    }

    public int getRound() {
        return round;
    }

    public int getLastActingUnitId() {
        return lastActingUnitId;
    }

    public String getIntentText() {
        return intentText;
    }

    public String getTraceText() {
        return traceText;
    }

    public ArrayList<ReplayUnitState> getUnits() {
        return new ArrayList<ReplayUnitState>(units);
    }

    public ArrayList<ReplayPowerUpState> getPowerUps() {
        return new ArrayList<ReplayPowerUpState>(powerUps);
    }

    private ArrayList<ReplayUnitState> captureUnits(GameEngine engine) {
        ArrayList<ReplayUnitState> result = new ArrayList<ReplayUnitState>();
        ArrayList<Team> teams = engine.getTeams();
        for (int i = 0; i < teams.size(); i++) {
            ArrayList<Unit> units = teams.get(i).getUnits();
            for (int j = 0; j < units.size(); j++) {
                result.add(new ReplayUnitState(units.get(j)));
            }
        }
        return result;
    }

    private ArrayList<ReplayPowerUpState> capturePowerUps(GameEngine engine) {
        ArrayList<ReplayPowerUpState> result = new ArrayList<ReplayPowerUpState>();
        ArrayList<PowerUp> powerUps = engine.getPowerUps();
        for (int i = 0; i < powerUps.size(); i++) {
            result.add(new ReplayPowerUpState(powerUps.get(i)));
        }
        return result;
    }
}
