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
 *
 * === CATATAN ===
 * Kelas ini sekarang mendukung dua representasi kromosom:
 *   - ARRAY_FS  : array panjang p berisi ID sel kosong
 *   - BIT_STRING: array panjang totalEmptyCells berisi 0/1
 * Panjang loop diambil dari parent.getChromosome().length (bukan dari p),
 * dan repair dipilih berdasarkan Individual.getType().
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

        // Panjang kromosom diambil sekali dari array, bukan dari p.
        // Alasannya:
        //   - ARRAY_FS  : panjang = p
        //   - BIT_STRING: panjang = totalEmptyCells (bisa jauh lebih besar dari p)
        // Mengambil sekali juga menghindari pemanggilan getChromosome() berulang di loop.
        int[] g1 = parent1.getChromosome();
        int[] g2 = parent2.getChromosome();
        int len = g1.length; // diasumsikan g1.length == g2.length

        // kondisi panjang memakai len, bukan p.
        if (rand.nextDouble() < crossoverRate && len > 1) {
            // Menentukan titik potong (cut point) secara acak dalam rentang 1 sampai (len - 1)
            // === UBAH: p -> len ===
            int cutPoint = rand.nextInt(len - 1) + 1;
            
            int[] c1 = new int[len];
            int[] c2 = new int[len];

            // Melakukan perulangan untuk menukar segmen gen antar parent berdasarkan cutPoint
            // === UBAH: batas loop p -> len, dan akses gen lewat g1/g2 (bukan getChromosome() berulang) ===
            for (int i = 0; i < len; i++) {
                if (i < cutPoint) {
                    // Sebelum titik potong: Anak 1 mewarisi dari Parent 1, Anak 2 mewarisi dari Parent 2
                    c1[i] = g1[i];
                    c2[i] = g2[i];
                } else {
                    // Setelah titik potong: Anak 1 mewarisi dari Parent 2, Anak 2 mewarisi dari Parent 1
                    c1[i] = g2[i];
                    c2[i] = g1[i];
                }
            }

            offsprings[0] = buildChild(c1, p, totalEmptyCells, rand, parent1.getType());
            offsprings[1] = buildChild(c2, p, totalEmptyCells, rand, parent1.getType());
        } else {
            // Jika tidak memenuhi probabilitas crossover, anak menjadi salinan (clone) persis dari parent masing-masing
            offsprings[0] = parent1.getClone();
            offsprings[1] = parent2.getClone();
        }

        // Mengembalikan array yang berisi kedua anak hasil proses
        return offsprings;
    }

    /**
     * Membungkus hasil repair menjadi Individual dengan tipe yang sesuai.
     * Kalau ARRAY_FS, panggil repairArrayFS. Kalau BIT_STRING, panggil repairBitString.
     *
     * @param raw             kromosom anak mentah hasil persilangan
     * @param p               jumlah Fire Station target
     * @param totalEmptyCells batas ID sel kosong (dipakai ARRAY_FS)
     * @param rand            objek Random untuk pengisian acak
     * @param type            tipe kromosom anak
     * @return                Individual anak yang sudah valid
     */
    private Individual buildChild(int[] raw, int p, int totalEmptyCells, Random rand,
                                  Individual.ChromosomeType type) {
        if (type == Individual.ChromosomeType.ARRAY_FS) {
            return new Individual(repairArrayFS(raw, p, totalEmptyCells, rand), type);
        }
        return new Individual(repairBitString(raw, p, rand), type);
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
    private int[] repairArrayFS(int[] chromosome, int p, int totalEmptyCells, Random rand) {
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

    /**
     * Repair untuk BIT_STRING: memastikan jumlah bit 1 tepat p.
     * Kalau lebih dari p, matikan 1 secara acak. Kalau kurang, nyalakan 0 secara acak.
     * Tidak perlu sort karena posisi bit pada array sudah kanonik (indeks = ID-1).
     *
     * @param raw  kromosom anak mentah (0/1)
     * @param p    jumlah Fire Station target
     * @param rand objek Random
     * @return     kromosom 0/1 valid dengan tepat p buah 1
     */
    private int[] repairBitString(int[] raw, int p, Random rand) {
        int[] repaired = raw.clone();
        int ones = 0;
        for (int b : repaired) if (b == 1) ones++;

        if (ones > p) {
            // Kelebihan FS: matikan 1 secara acak
            int toRemove = ones - p;
            while (toRemove > 0) {
                int i = rand.nextInt(repaired.length);
                if (repaired[i] == 1) {
                    repaired[i] = 0;
                    toRemove--;
                }
            }
        } else if (ones < p) {
            // Kekurangan FS: nyalakan 0 secara acak
            int toAdd = p - ones;
            while (toAdd > 0) {
                int i = rand.nextInt(repaired.length);
                if (repaired[i] == 0) {
                    repaired[i] = 1;
                    toAdd--;
                }
            }
        }
        return repaired;
    }
}