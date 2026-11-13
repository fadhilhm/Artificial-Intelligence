import java.util.Random;

/**
 *  Kelas ini mengimplementasikan metode seleksi rank selection.
 *  Cara kerjanya dengan mengurutkan individu berdasarkan nilai fitness
 * dari yang terkecil ke terbesar.
 *  Lalu memberikan peluang dari indeks 0 dengan nilai 1 sampai akhir dengan nilai populationSize.
 *  Lalu memilih angka acak antara 0 sampai totalRank - 1.
 *  Selanjutnya melakukan looping sebanyak populasi, jika currentNum
 * melebihi randomnumber maka individu pada indeks i akan dipilih sebagai pemenang.
 *  Jika tidak ada individu yang terpilih, maka individu terakhir dalam populasi dijadika pemenang.
 *  Kemudian individu pemenang akan dikembalikan sebagai hasil seleksi.
 */

public class RankSelection implements Selection {

/*
 *  Melakukan seleksi dengan menggunakan metode rank selection.
 *  yakni dengan mengurutkan individu berdasarkan nilai fitness, kemudian memberikan
 * peluang seleksi dari indeks 0 diberi nilai 1 dan secara increment berlanjut sampai 
 * indeks terakhir yang diberi nilai populationsize.
 * 
 *  @param population Objek populasi tempat kandidat induk akan diambil
 *  @param rand       Objek Random untuk memilih indeks kandidat secara acak dari populasi
 *  return merupakan clone dari individu pemenang rankSelection yang akan dijadikan parent
 */
    
    @Override 
    public Individual select(Population population, Random rnd){
        int populationSize = population.getPopulationSize();
        
        // mengurutkan individu berdasarkan nilai fitness dari terkecil hingga terbesar
        population.sortByFitness();

        /**
         *  Menghitung total rank untuk populasi saat ini.
         *  nilai random antara 0 sampai totalRank - 1
         *  currentNum akan menampung jumlah rank yang telah dihitung
         */
        int totalRank = (populationSize * (populationSize + 1)) / 2;
        int randomnumber = rnd.nextInt(totalRank);
        int currentNum = 0;

        /**
         *  Melakukan looping sebannyak ukuran populasi
         *  currentrank adalah peringkat individu saat ini (dimulai dari 1)
         *  currentNum akan menampung jumlah rank yang telah dihitung
         *  jika nilai currentNum melebihi randomnumber, maka individu pada indeks i akan dipilih sebagai pemenang.
         *  jika tidak, maka akan melanjutkan ke individu berikutnya
         * 
         *  currentNum harus lebih besar dari randomnumber karena jika menggunakan lebih besar sama dengan
         *  bobot kesempatan menang pada individu dapat tidak sesuai.
         */
        for (int i =0; i<populationSize; i++){
            int currentrank = i + 1;
            currentNum += currentrank;
            if (currentNum > randomnumber){
                return population.getIndividual(i);
            }
        }

        // jika tidak ada individu yang terpilih, maka akan mengembalikan
        // individu terakhir dalam populasi.
        return population.getIndividual(populationSize - 1);
    }
    
}
