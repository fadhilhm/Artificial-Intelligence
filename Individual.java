import java.util.Arrays;

public class Individual implements Comparable<Individual> {
    private int[] chromosome; // chromosome berbentuk array of int, setiap index merepresentasikan posisi Fire Station di dalam grid
    private double cost;
    private double fitness;

    /**
     * @param p jumlah Fire Station yang akan ada
     */
    public Individual(int p) {
        this.chromosome = new int[p];
        this.cost = 0.0;
        this.fitness = 0.0;
    }

    /**
     * @param chromosome kromosom dari input luar
     */
    public Individual(int[] chromosome) {
        this.chromosome = chromosome;
        this.cost = 0.0;
        this.fitness = 0.0;
    }

    public void setChromosome(int[] chromosome) {
        this.chromosome = chromosome;
    }

    public int[] getChromosome() {
        return chromosome;
    }

    public double getCost() {
        return cost;
    }

    public void setCost(double cost) {
        this.cost = cost;
    }

    public double getFitness() {
        return fitness;
    }

    public void setFitness(double fitness) {
        this.fitness = fitness;
    }   

    /**
     * @return objek Individual yang baru dan identik
     */
    public Individual getClone() {
        Individual clone = new Individual(this.chromosome.length);
        clone.setChromosome(Arrays.copyOf(chromosome, this.chromosome.length));
        clone.setCost(this.cost);
        clone.setFitness(this.fitness);

        return clone;
    }

    // sort dari fitness terbesar ke terkecil
    @Override 
    public int compareTo(Individual other) {
        return Double.compare(other.fitness, this.fitness);
    }
}