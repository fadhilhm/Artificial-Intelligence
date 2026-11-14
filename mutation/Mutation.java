package mutation;

import java.util.Random;

import model.Individual;

/**
 * Interface untuk operasi mutasi pada algoritma genetika.
 * Dibuat modular agar memungkinkan penggunaan strategi mutasi lain di masa depan.
 */
public interface Mutation {

    /**
     * Melakukan mutasi pada kromosom sebuah individu.
     * 
     * @param individual Individu yang kromosomnya akan dimutasi.
     * @param mutationRate Probabilitas terjadinya mutasi pada setiap gen (bernilai 0.0 hingga 1.0).
     * @param totalEmptyCells Jumlah sel kosong di dalam grid (sebagai batas pencarian titik baru).
     * @param rand Objek Random untuk membangkitkan angka acak secara seragam.
     */
    void mutate(Individual individual, double mutationRate, int totalEmptyCells, Random rand);
}