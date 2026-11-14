package model;

import java.util.Arrays;

public class Individual implements Comparable<Individual> {

    
    // Tipe representasi kromosom. Default ARRAY_FS supaya kode lama tidak pecah.
    public enum ChromosomeType { ARRAY_FS, BIT_STRING }

    // chromosome: setiap index merepresentasikan posisi Fire Station di dalam grid.
    // - ARRAY_FS  : panjang = p, isi = ID sel kosong (1-based)
    // - BIT_STRING: panjang = totalEmptyCells, isi = 0/1 (1 = FS, 0 = bukan FS)
    private int[] chromosome;
    private double cost;
    private double fitness;

    
    private ChromosomeType type;

    /**
     * @param p jumlah Fire Station yang akan ada
     */
    public Individual(int p) {
        this.chromosome = new int[p];
        this.cost = 0.0;
        this.fitness = 0.0;
        this.type = ChromosomeType.ARRAY_FS; 
    }

    /**
     * @param chromosome kromosom dari input luar (default ARRAY_FS)
     */
    public Individual(int[] chromosome) {
        this.chromosome = chromosome;
        this.cost = 0.0;
        this.fitness = 0.0;
        this.type = ChromosomeType.ARRAY_FS; 
    }

    
    /**
     * Konstruktor dengan tipe eksplisit, dipakai oleh crossover/mutation
     * supaya anak membawa tipe yang sama dengan parent.
     */
    public Individual(int[] chromosome, ChromosomeType type) {
        this.chromosome = chromosome;
        this.cost = 0.0;
        this.fitness = 0.0;
        this.type = type;
    }

    public void setChromosome(int[] chromosome) {
        this.chromosome = chromosome;
    }

    public int[] getChromosome() {
        return chromosome;
    }

    
    public ChromosomeType getType() {
        return type;
    }

    
    /**
     * Mengembalikan ID sel kosong yang dipilih sebagai Fire Station.
     * - ARRAY_FS : return chromosome apa adanya (sudah berisi ID)
     * - BIT_STRING: scan bit 1, kembalikan (index + 1) karena ID 1-based
     *
     * Method ini dipakai oleh GeneticAlgorithm.evaluateIndividual supaya
     * evaluasi fitness tidak perlu tahu tipe kromosom.
     */
    public int[] getSelectedIds() {
        if (type == ChromosomeType.ARRAY_FS) {
            return chromosome;
        }

        int count = 0;
        for (int b : chromosome) 
            if (b == 1) count++;
        
        int[] ids = new int[count];
        int k = 0;
        
        for (int i = 0; i < chromosome.length; i++) {
            if (chromosome[i] == 1) ids[k++] = i + 1; // 1-based
        }
        
        return ids;
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
        Individual clone = new Individual(Arrays.copyOf(chromosome, this.chromosome.length), this.type);
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