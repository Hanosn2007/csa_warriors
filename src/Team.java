import java.util.ArrayList;

public class Team {
    private final String name;
    private final char symbol;
    private final ArrayList<Unit> units;

    public Team(String name, char symbol) {
        this.name = name;
        this.symbol = symbol;
        this.units = new ArrayList<Unit>();
    }

    public String getName() {
        return name;
    }

    public char getSymbol() {
        return symbol;
    }

    public void addUnit(Unit unit) {
        units.add(unit);
    }

    public ArrayList<Unit> getUnits() {
        return units;
    }

    public boolean hasLivingUnits() {
        for (int i = 0; i < units.size(); i++) {
            if (units.get(i).isAlive()) {
                return true;
            }
        }
        return false;
    }
}
