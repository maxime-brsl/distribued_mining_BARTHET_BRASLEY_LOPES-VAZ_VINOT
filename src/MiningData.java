public class MiningData {
    private int start;
    private int difficulty;
    private String data;
    private String hash;

    public MiningData() {
        this.start = -1;
        this.data = null;
        this.hash = null;
        this.difficulty = -1;
    }

    public void setStart(int start) {
        this.start = start;
    }

    public void setDifficulty(int difficulty) {
        this.difficulty = difficulty;
    }

    public void setData(String data) {
        this.data = data;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    // Indique si on a toutes les informations nécessaires pour commencer le minage
    public boolean isReady() {
        return start != -1 && difficulty != -1 && data != null && hash != null;
    }
}
