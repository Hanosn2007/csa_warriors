public class TurnAnimation {
    private final String type;
    private final Position from;
    private final Position to;

    public TurnAnimation(String type, Position from, Position to) {
        this.type = type;
        this.from = from;
        this.to = to;
    }

    public String getType() {
        return type;
    }

    public Position getFrom() {
        return from;
    }

    public Position getTo() {
        return to;
    }
}
