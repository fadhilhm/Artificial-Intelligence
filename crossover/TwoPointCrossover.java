package crossover;
import java.util.Arrays;
import java.util.Random;

import model.Individual;

/**
 * Kelas ini mengimplementasikan interface Crossover menggunakan metode
 * Two-Point Crossover yang disesuaikan untuk masalah kombinasi subset
 * (pencarian posisi Fire Station pada grid peta).
 *
 * Dilengkapi dengan fungsi perbaikan (repair) otomatis untuk memastikan
 * tidak ada ID Fire Station yang duplikat atau tidak valid pada anak (offspring).
 *
 * === CATATAN ===
 * Sama seperti SinglePointCrossover, kelas ini sekarang mendukung dua
 * representasi kromosom (ARRAY_FS dan BIT_STRING). Panjang loop diambil dari
 * parent.getChromosome().length dan repair dipilih berdasarkan tipe.
 */
public class TwoPointCrossover implements Crossover {

    /**
     * Melakukan proses persilangan (crossover) dua titik potong antara dua induk
     * untuk menghasilkan dua individu keturunan (offspring). Bagian tengah
     * di antara dua titik potong tersebut akan ditukar antar induk.
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
        // Init array penampung untuk 2 anak (offspring) yang akan dilahirkan
        Individual[] offsprings = new Individual[2];

        // Panjang kromosom diambil sekali dari array, bukan dari p.
        //   - ARRAY_FS  : panjang = p
        //   - BIT_STRING: panjang = totalEmptyCells
        int[] g1 = parent1.getChromosome();
        int[] g2 = parent2.getChromosome();
        int len = g1.length; // diasumsikan g1.length == g2.length

        if (rand.nextDouble() < crossoverRate && len > 1) {

            // Menentukan dua titik potong (cut points) secara acak
            int pt1 = rand.nextInt(len);
            int pt2 = rand.nextInt(len);

            // Memastikan kedua titik potong tidak berada di indeks yang sama
            while (pt1 == pt2) {
                pt2 = rand.nextInt(len);
            }

            // Memastikan pt1 selalu lebih kecil dari pt2 (berfungsi sebagai batas kiri dan batas kanan)
            if (pt1 > pt2) {
                int temp = pt1;
                pt1 = pt2;
                pt2 = temp;
            }

            // Inisialisasi array kromosom mentah untuk anak pertama (c1) dan anak kedua (c2)
            int[] c1 = new int[len];
            int[] c2 = new int[len];

            // Melakukan perulangan untuk menukar segmen gen antar parent berdasarkan dua titik potong
            for (int i = 0; i < len; i++) {
                if (i >= pt1 && i < pt2) {
                    // Di area tengah (antara pt1 dan pt2): Segmen ditukar
                    // Anak 1 mewarisi dari Parent 2, Anak 2 mewarisi dari Parent 1
                    c1[i] = g2[i];
                    c2[i] = g1[i];
                } else {
                    // Di area luar (kiri pt1 dan kanan pt2): Segmen tetap
                    // Anak 1 mewarisi dari Parent 1, Anak 2 mewarisi dari Parent 2
                    c1[i] = g1[i];
                    c2[i] = g2[i];
                }
            }

            offsprings[0] = buildChild(c1, p, totalEmptyCells, rand, parent1.getType());
            offsprings[1] = buildChild(c2, p, totalEmptyCells, rand, parent1.getType());
        } else {
            offsprings[0] = parent1.getClone();
            offsprings[1] = parent2.getClone();
        }

        // Rerturn  array yang berisi kedua anak hasil proses
        return offsprings;
    }

    /**
     * Membungkus hasil repair menjadi Individual dengan tipe yang sesuai.
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

        // CEk gen pada kromosom mentah dan menandai nilai yang valid & unik
        for (int val : chromosome) {
            if (val > 0 && val <= totalEmptyCells && !active[val]) {
                active[val] = true;
            }
        }

        // Init array penampung final berukuran 'p' dan indeks pelacak
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

        // Sort elemen di dalam kromosom secara ascending agar rapi dan konsisten
        Arrays.sort(repaired);

        // Return array kromosom yang sudah diperbaiki dan siap digunakan
        return repaired;
    }

    /**
     * Repair untuk BIT_STRING: memastikan jumlah bit 1 tepat p.
     * Kalau lebih dari p, matikan 1 secara acak. Kalau kurang, nyalakan 0 secara acak.
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
            int toRemove = ones - p;
            while (toRemove > 0) {
                int i = rand.nextInt(repaired.length);
                if (repaired[i] == 1) {
                    repaired[i] = 0;
                    toRemove--;
                }
            }
        } else if (ones < p) {
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