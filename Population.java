import java.util.Arrays;
import java.util.Random;

public class Population {
    private Individual[] individuals;

    /**
     * @param populationSize jumlah individu
     */
    public Population(int populationSize) {
        this.individuals = new Individual[populationSize];
    }

    public int initializeRandomPopulation(int totalEmptyCells, int p, Random rand) {
        for (int i = 0; i < individuals.length; i++) {
            int[] chromosome = new int[p];
            boolean[] used = new boolean[totalEmptyCells];

            int count = 0;
            while (count < p) {
                int randomCellID = rand.nextInt(totalEmptyCells) + 1;
            
                if (!used[randomCellID]) {
                    used[randomCellID] = true;
                    chromosome[count] = randomCellID;
                    count++;
                }
            }

            individuals[i] = new Individual(chromosome);
        }
    }
}
