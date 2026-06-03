public class MatchConfig {
    private final String redWarriorClass;
    private final String blueWarriorClass;
    private final String greenWarriorClass;
    private final String yellowWarriorClass;

    public MatchConfig(String redWarriorClass, String blueWarriorClass, String greenWarriorClass, String yellowWarriorClass) {
        this.redWarriorClass = redWarriorClass;
        this.blueWarriorClass = blueWarriorClass;
        this.greenWarriorClass = greenWarriorClass;
        this.yellowWarriorClass = yellowWarriorClass;
    }

    public String getRedWarriorClass() {
        return redWarriorClass;
    }

    public String getBlueWarriorClass() {
        return blueWarriorClass;
    }

    public String getGreenWarriorClass() {
        return greenWarriorClass;
    }

    public String getYellowWarriorClass() {
        return yellowWarriorClass;
    }
}
