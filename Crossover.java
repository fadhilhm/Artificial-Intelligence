/**
 * Crossover
 */
public interface Crossover {
    Individual[] crossover(Individual p1, Individual p2, double pc, int p, int totalEmptyCells, Random md);

    public boolean contains(Individual[] child, int valueParent, int cutPoint) {
        for (int i = 0; i < cutPoint - 1; i++) {
            if (child[i] == valueParent) {
                return true;
            }
        }
        return false;
    }
}