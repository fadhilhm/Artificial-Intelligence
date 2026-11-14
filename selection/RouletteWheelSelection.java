package selection;

import java.util.Random;

import model.Individual;
import model.Population;

/**
 *  Kelas ini mengimplementasikan metode seleksi Roulette Wheel Selection.
 *  Cara kerjanya adalah dengan menghitung total fitness dari seluruh populasi.
 *  Selanjutnya memilih individu secara acak berdasarkan proporsi fitness maing-masing.
 *  Selanjutnya melakukan looping sebanyak populasi, jika currentNum melebihi 
 * randomnumber maka individu pada indeks i akan dipilih sebagai pemenang.
 *  Jika tidak ada individu yang terpilih, maka individu terakhir dalam populasi dijadika pemenang.
 *  Kemudian individu pemenang akan dikembalikan sebagai hasil seleksi.
 *
 */

public class RouletteWheelSelection implements Selection {
    
    @Override 
    public Individual select(Population population, Random rnd){
        /*
        *  @param population Objek populasi tempat kandidat induk akan diambil
        *  @param rand       Objek Random untuk memilih indeks kandidat secara acak dari populasi
        *  return merupakan clone dari individu pemenang rankSelection yang akan dijadikan parent
        */

        int populationSize = population.getPopulationSize();
        
        // Menghitung total fitness dari seluruh populasi
        double totalFitness = 0.0;
        for (int i = 0; i < populationSize; i++) {
            totalFitness += population.getIndividual(i).getFitness();
        }

        // jika didapat nilai fitnes kurag dari sama dengan 0,
        // maka akan mengembalikan individu acak dalam populasi sebagai pemenang.
        if(totalFitness <= 0.0){
            return population.getIndividual(rnd.nextInt(populationSize));
        }

        /**
         * angka acak antara 0 sampai totalFitness
         * currentNum akan menampung jumlah fitness yang telah dihitung
         */
        double randomNum = rnd.nextDouble() * totalFitness;
        double currentNum = 0.0;

        /**
         *  Melakukan looping sebannyak ukuran populasi.
         *  currentNum akan selalu diupdate dengan nilai fitness individu indeks i.
         *  jika nilai currentNum melebihi randomnumber, maka individu pada indeks i 
         * akan dipilih sebagai pemenang.
         *  Jika tidak ada yang terpilih, maka individu indeks terakhir akan terpilih.
         *  Pemenang akan dikembalikan sebagai hasil seleksi.
         */

        for (int i = 0; i < populationSize; i++) {
            currentNum += population.getIndividual(i).getFitness();
            if (currentNum >= randomNum) {
                return population.getIndividual(i);
            }
        }

        return population.getIndividual(populationSize - 1);
    }
        
}