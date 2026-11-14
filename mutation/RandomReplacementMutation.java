package mutation;

import java.util.Arrays;
import java.util.Random;

import model.Individual;

/**
 * Implementasi mutasi dengan metode Random Replacement.
 * Metode ini dipilih karena hanya mengubah urutan gen (swap) tidak memberikan
 * variasi baru.
 * Sebaliknya, mutasi ini akan menukar posisi Fire Station lama dengan posisi
 * sel kosong baru yang belum ada di dalam kromosom.
 */
public class RandomReplacementMutation implements Mutation {

    /**
     * Mengeksekusi proses mutasi pada kromosom individu berdasarkan mutation rate.
     * 
     * @param individual      Individu yang kromosomnya akan dimutasi.
     * @param mutationRate    Probabilitas sebuah gen (titik Fire Station) diganti
     *                        (0.0 - 1.0).
     * @param totalEmptyCells Batas jumlah sel kosong (titik maksimal adalah
     *                        totalEmptyCells).
     * @param rand            Objek Random untuk membangkitkan angka probabilitas
     *                        dan ID sel baru.
     */
    @Override
    public void mutate(Individual individual, double mutationRate, int totalEmptyCells, Random rand) {
        int[] chromosome = individual.getChromosome(); 

        boolean isMutated = false;

        for (int i = 0; i < chromosome.length; i++) {
            // Cek apakah gen pada index 'i' ini akan mengalami mutasi
            if (rand.nextDouble() < mutationRate) {
                int newCellID;
                boolean isDuplicate;

                // Cari ID sel kosong baru yang belum digunakan di kromosom saat ini
                do {
                    isDuplicate = false;
                    newCellID = rand.nextInt(totalEmptyCells) + 1; // index 1-based (1 s.d totalEmptyCells)

                    // Validasi agar gen baru tidak sama dengan titik yang sudah ada
                    for (int gene : chromosome) {
                        if (gene == newCellID) {
                            isDuplicate = true;
                            break;
                        }
                    }
                } while (isDuplicate);

                // Ganti posisi lama dengan sel kosong baru
                chromosome[i] = newCellID;
                isMutated = true;
            }
        }

        // Jika terjadi perubahan, update kromosom dan reset nilai fitness/cost
        // karena individu ini harus dievaluasi ulang
        if (isMutated) {
            Arrays.sort(chromosome);

            individual.setChromosome(chromosome);
            individual.setCost(0.0);
            individual.setFitness(0.0);
        }
    }
}