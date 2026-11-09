import java.util.Random;

/**
 * Interface ini mendefinisikan kontrak utama untuk metode pemilihan induk 
 * (parent selection) dalam Algoritma Genetik. Setiap strategi seleksi 
 * (seperti Tournament Selection, Roulette Wheel, dll.) wajib mengimplementasikan 
 * interface ini untuk memilih individu yang akan bereproduksi.
 */
interface Selection {
    /**
     * Memilih satu individu dari populasi yang ada untuk dijadikan induk (parent).
     * 
     * @param population Objek populasi yang berisi sekumpulan individu saat ini
     * @param rand       Objek Random untuk pengundian kandidat atau indeks secara acak
     * @return           Individu terpilih yang akan digunakan dalam proses persilangan (crossover)
     */
    public Individual select(Population population, Random rand);
}