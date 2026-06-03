public class MatchConfig {
    private final String redWarriorAClass;
    private final String redWarriorBClass;
    private final String blueWarriorAClass;
    private final String blueWarriorBClass;
    private final String greenWarriorAClass;
    private final String greenWarriorBClass;
    private final String yellowWarriorAClass;
    private final String yellowWarriorBClass;

    public MatchConfig(String redWarriorClass, String blueWarriorClass, String greenWarriorClass, String yellowWarriorClass) {
        this(
                redWarriorClass,
                redWarriorClass,
                blueWarriorClass,
                blueWarriorClass,
                greenWarriorClass,
                greenWarriorClass,
                yellowWarriorClass,
                yellowWarriorClass);
    }

    public MatchConfig(
            String redWarriorAClass,
            String redWarriorBClass,
            String blueWarriorAClass,
            String blueWarriorBClass,
            String greenWarriorAClass,
            String greenWarriorBClass,
            String yellowWarriorAClass,
            String yellowWarriorBClass) {
        this.redWarriorAClass = redWarriorAClass;
        this.redWarriorBClass = redWarriorBClass;
        this.blueWarriorAClass = blueWarriorAClass;
        this.blueWarriorBClass = blueWarriorBClass;
        this.greenWarriorAClass = greenWarriorAClass;
        this.greenWarriorBClass = greenWarriorBClass;
        this.yellowWarriorAClass = yellowWarriorAClass;
        this.yellowWarriorBClass = yellowWarriorBClass;
    }

    public String getRedWarriorAClass() {
        return redWarriorAClass;
    }

    public String getRedWarriorBClass() {
        return redWarriorBClass;
    }

    public String getBlueWarriorAClass() {
        return blueWarriorAClass;
    }

    public String getBlueWarriorBClass() {
        return blueWarriorBClass;
    }

    public String getGreenWarriorAClass() {
        return greenWarriorAClass;
    }

    public String getGreenWarriorBClass() {
        return greenWarriorBClass;
    }

    public String getYellowWarriorAClass() {
        return yellowWarriorAClass;
    }

    public String getYellowWarriorBClass() {
        return yellowWarriorBClass;
    }

    public String getRedWarriorClass() {
        return pairText(redWarriorAClass, redWarriorBClass);
    }

    public String getBlueWarriorClass() {
        return pairText(blueWarriorAClass, blueWarriorBClass);
    }

    public String getGreenWarriorClass() {
        return pairText(greenWarriorAClass, greenWarriorBClass);
    }

    public String getYellowWarriorClass() {
        return pairText(yellowWarriorAClass, yellowWarriorBClass);
    }

    private String pairText(String first, String second) {
        if (first.equals(second)) {
            return first;
        }
        return first + " / " + second;
    }
}
