package model;

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
     * @param totalEmptyCells banyak cell dalam grid yang kosong
     * @param p banyak Fire Station dalam individu
     * @param rand object Random
     */
    public void initializeRandomPopulation(int totalEmptyCells, int p, Random rand) {
        initializeRandomPopulation(totalEmptyCells, p, rand, Individual.ChromosomeType.ARRAY_FS);
    }
    
    /**
     * Inisialisasi populasi dengan tipe kromosom tertentu.
     * - ARRAY_FS  : panjang p, isi ID sel kosong unik
     * - BIT_STRING: panjang totalEmptyCells, isi p buah 1 dan sisanya 0
     *
     * @param totalEmptyCells banyak cell kosong
     * @param p banyak Fire Station
     * @param rand object Random
     * @param type tipe representasi kromosom
     */
    public void initializeRandomPopulation(int totalEmptyCells, int p, Random rand,
                                           Individual.ChromosomeType type) {
        for (int i = 0; i < individuals.length; i++) {
            if (type == Individual.ChromosomeType.ARRAY_FS) {
                int[] chromosome = new int[p];
                boolean[] used = new boolean[totalEmptyCells + 1];
                int count = 0;

                while (count < p) {
                    int randomCellID = rand.nextInt(totalEmptyCells) + 1;
                    if (!used[randomCellID]) {
                        used[randomCellID] = true;
                        chromosome[count] = randomCellID;
                        count++;
                    }
                }
                
                individuals[i] = new Individual(chromosome, Individual.ChromosomeType.ARRAY_FS);
            } else {
                // BIT_STRING: array panjang K, isi p buah 1
                int[] chromosome = new int[totalEmptyCells]; // default 0
                boolean[] used = new boolean[totalEmptyCells + 1];
                int count = 0;

                while (count < p) {
                    int randomCellID = rand.nextInt(totalEmptyCells) + 1;
                    if (!used[randomCellID]) {
                        used[randomCellID] = true;
                        chromosome[randomCellID - 1] = 1; // ID id -> indeks id-1
                        count++;
                    }
                }

                individuals[i] = new Individual(chromosome, Individual.ChromosomeType.BIT_STRING);
            }
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