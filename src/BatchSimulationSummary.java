import java.util.ArrayList;

public class BatchSimulationSummary {
    private final ArrayList<BatchTeamStats> teamStats;
    private final String detailText;
    private final int requestedMatches;
    private final int completedMatches;
    private final int noWinnerMatches;
    private final int unfinishedMatches;

    public BatchSimulationSummary(
            ArrayList<BatchTeamStats> teamStats,
            String detailText,
            int requestedMatches,
            int completedMatches,
            int noWinnerMatches,
            int unfinishedMatches) {
        this.teamStats = teamStats;
        this.detailText = detailText;
        this.requestedMatches = requestedMatches;
        this.completedMatches = completedMatches;
        this.noWinnerMatches = noWinnerMatches;
        this.unfinishedMatches = unfinishedMatches;
    }

    public ArrayList<BatchTeamStats> getTeamStats() {
        return teamStats;
    }

    public String getDetailText() {
        return detailText;
    }

    public int getRequestedMatches() {
        return requestedMatches;
    }

    public int getCompletedMatches() {
        return completedMatches;
    }

    public int getNoWinnerMatches() {
        return noWinnerMatches;
    }

    public int getUnfinishedMatches() {
        return unfinishedMatches;
    }
}
