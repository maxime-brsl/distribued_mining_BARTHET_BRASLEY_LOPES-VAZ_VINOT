public class Solution {
    private String hash;
    private String nonce;
    private int difficulty;

    public Solution(String hash, String nonce, int difficulty) {
        this.hash = hash;
        this.nonce = nonce;
        this.difficulty = difficulty;
    }

    public int getDifficulty() {
        return difficulty;
    }

    public String getHash() {
        return hash;
    }

    public String getNonce() {
        return nonce;
    }
}
