package crossover;

import java.util.Arrays;
import java.util.Random;

import model.Individual;

/**
 * Kelas ini mengimplementasikan interface Crossover menggunakan metode 
 * Single-Point Crossover yang disesuaikan untuk masalah kombinasi subset 
 * (pencarian posisi Fire Station pada grid peta). 
 * 
 * Dilengkapi dengan fungsi perbaikan (repair) otomatis untuk memastikan 
 * tidak ada ID Fire Station yang duplikat atau tidak valid pada anak (offspring).
 */
public class SinglePointCrossover implements Crossover {
    
    /**
     * Melakukan proses persilangan (crossover) satu titik potong antara dua induk 
     * untuk menghasilkan dua individu keturunan (offspring).
     * 
     * @param parent1         Induk pertama (Parent 1) yang menjadi sumber genetik
     * @param parent2         Induk kedua (Parent 2) yang menjadi sumber genetik
     * @param crossoverRate   Nilai probabilitas crossover (peluang terjadinya persilangan, misal 0.8 atau 80%)
     * @param p               Ukuran atau panjang kromosom (jumlah total Fire Station yang ditempatkan)
     * @param totalEmptyCells Batas maksimum nilai ID sel kosong yang valid pada peta grid
     * @param rand            Objek Random untuk pengundian nilai peluang acak secara probabilistik
     * @return                Array berisi dua objek Individual baru hasil keturunan (offspring)
     */
    @Override
    public Individual[] crossover(Individual parent1, Individual parent2, double crossoverRate, int p, int totalEmptyCells, Random rand) {
        // Menyiapkan array penampung untuk 2 anak (offspring) yang akan dilahirkan
        Individual[] offsprings = new Individual[2];

        // Mengecek apakah angka acak memenuhi peluang crossover dan apakah panjang kromosom > 1
        if (rand.nextDouble() < crossoverRate && p > 1) {
            // Menentukan titik potong (cut point) secara acak dalam rentang 1 sampai (p - 1)
            int cutPoint = rand.nextInt(p - 1) + 1;
            
            // Inisialisasi array kromosom mentah untuk anak pertama (c1) dan anak kedua (c2)
            int[] c1 = new int[p];
            int[] c2 = new int[p];

            // Melakukan perulangan untuk menukar segmen gen antar parent berdasarkan cutPoint
            for (int i = 0; i < p; i++) {
                if (i < cutPoint) {
                    // Sebelum titik potong: Anak 1 mewarisi dari Parent 1, Anak 2 mewarisi dari Parent 2
                    c1[i] = parent1.getChromosome()[i];
                    c2[i] = parent2.getChromosome()[i];
                } else {
                    // Setelah titik potong: Anak 1 mewarisi dari Parent 2, Anak 2 mewarisi dari Parent 1
                    c1[i] = parent2.getChromosome()[i];
                    c2[i] = parent1.getChromosome()[i];
                }
            }
            
            // Menjalankan fungsi repair untuk membersihkan duplikat & memastikan validitas,
            // lalu membungkus hasilnya ke dalam objek Individual baru untuk anak pertama dan kedua.
            offsprings[0] = new Individual(repair(c1, p, totalEmptyCells, rand));
            offsprings[1] = new Individual(repair(c2, p, totalEmptyCells, rand));
        } else {
            // Jika tidak memenuhi probabilitas crossover, anak menjadi salinan (clone) persis dari parent masing-masing
            offsprings[0] = parent1.getClone();
            offsprings[1] = parent2.getClone();
        }

        // Mengembalikan array yang berisi kedua anak hasil proses
        return offsprings;
    }

    /**
     * Memperbaiki kromosom anak hasil persilangan mentah guna menghilangkan nilai duplikat, 
     * mencegah nilai kosong/cacat, serta mengurutkan ID sel secara ascending.
     * 
     * @param chromosome      Array kromosom anak mentah (raw chromosome) hasil persilangan
     * @param p               Ukuran atau panjang kromosom target yang harus dipenuhi
     * @param totalEmptyCells Batas maksimum nilai ID sel kosong yang tersedia di peta
     * @param rand            Objek Random untuk memilih ID sel pengganti secara acak jika ada kekurangan gen
     * @return                Array kromosom baru berukuran 'p' yang sudah valid, unik, dan terurut
     */
    private int[] repair(int[] chromosome, int p, int totalEmptyCells, Random rand) {
        // Membuat array boolean untuk menandai ID sel mana saja yang sudah aktif/terpakai (indeks 1 sampai totalEmptyCells)
        boolean[] active = new boolean[totalEmptyCells + 1];
        
        // Memeriksa gen pada kromosom mentah dan menandai nilai yang valid & unik
        for (int val : chromosome) {
            if (val > 0 && val <= totalEmptyCells && !active[val]) {
                active[val] = true;
            }
        }
        
        // Menyiapkan array penampung final berukuran 'p' dan indeks pelacak
        int[] repaired = new int[p];
        int idx = 0;
        
        // Memasukkan kembali gen-gen unik yang valid dari penanda 'active' ke array 'repaired'
        for (int i = 1; i <= totalEmptyCells && idx < p; i++) {
            if (active[i]) {
                repaired[idx++] = i;
            }
        }
        
        // Jika jumlah gen kurang dari 'p' (karena ada duplikat yang dibuang sebelumnya),
        // cari sel kosong alternatif lain secara acak untuk mengisi kekurangan slot tersebut.
        while (idx < p) {
            int randCell = rand.nextInt(totalEmptyCells) + 1;
            if (!active[randCell]) {
                active[randCell] = true;
                repaired[idx++] = randCell;
            }
        }
        
        // Mengurutkan elemen di dalam kromosom secara ascending agar rapi dan konsisten
        Arrays.sort(repaired);
        
        // Mengembalikan array kromosom yang sudah diperbaiki dan siap digunakan
        return repaired;
    }
}