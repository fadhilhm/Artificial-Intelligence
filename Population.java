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

    /**
     * @param totalEmptyCells banyak cell dalam grid yang kosong (tidak ada Tree atau House)
     * @param p banyak Fire Station dalam individu
     * @param rand object Random
     * */
    public void initializeRandomPopulation(int totalEmptyCells, int p, Random rand) {
        for (int i = 0; i < individuals.length; i++) {
            int[] chromosome = new int[p];
            boolean[] used = new boolean[totalEmptyCells+1];

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

    public Individual getIndividual(int index) {
        return this.individuals[index];
    }

    public Individual[] getIndividuals() {
        return this.individuals;
    }
    
    public void setIndividual(int index, Individual individual) {
        this.individuals[index] = individual;
    }

    public int getPopulationSize() {
        return this.individuals.length;
    }
    
    public void sortByFitness() {
        Arrays.sort(this.individuals);
    }
    
    public Individual getBestIndividual() {
        return this.individuals[0];
    }
}
