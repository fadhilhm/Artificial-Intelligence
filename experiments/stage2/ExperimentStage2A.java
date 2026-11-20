import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import crossover.*;
import model.*;
import model.Individual.ChromosomeType;
import mutation.*;
import selection.*;

/**
 * Harness eksperimen Stage 2A (one-factor-at-a-time: pop -> pc -> pm -> elitism).
 *
 * CLI (8 arg, path relatif dari root repo):
 *   java -cp <build> ExperimentStage2A <size> <pop> <pc> <pm> <elitism> <models> <seedCount> <outCsv>
 *
 *   size      : tiny | small | normal | large
 *   pop       : population size (int)
 *   pc pm elitism : double [0,1]
 *   models    : comma-separated model ID Stage 1, contoh "7,4,5" (lihat tabel MODELS)
 *   seedCount : jumlah seed diambil dari depan experiments/stage1/seeds_30.txt
 *   outCsv    : file CSV tujuan (append; header ditulis hanya jika file baru)
 *
 * Satu proses = satu task (size, value): models x 4 peta x seedCount run.
 * GA tidak diduplikasi: semua run lewat Main.loadAndPreprocess + Main.runGA,
 * operator di-set ke field statis Main per model (pola Stage 1).
 *
 * Resume: baris yang sudah ada di outCsv dengan kunci
 * (size, pop, pc, pm, elitism, model_id, map, seed) dilewati.
 * Karena runner memakai satu CSV per step/tag, kunci ini ekuivalen dengan
 * (step, size, value, model, map, seed). --no-resume di runner mengosongkan
 * CSV sebelum task dimulai.
 *
 * Append aman untuk banyak proses: FileOutputStream append (O_APPEND) dan
 * satu baris = satu write() atomik di Linux, flush tiap baris.
 *
 * TIDAK membaca config.txt. Tidak mengubah file Stage 1.
 */
public class ExperimentStage2A {

    static final String SEEDS_FILE = "experiments/stage1/seeds_30.txt";

    // p override header test case (Stage 2A): tiny=3, small=10, normal=24
    static final Map<String, Integer> P_OVERRIDE = Map.of(
            "tiny", 3, "small", 10, "normal", 24, "large", 40, "large_a", 40, "large_b", 40, "large_c", 40);

    static final Map<String, String[]> MAPS = Map.of(
            "tiny", new String[]{
                    "experiments/stage2/maps/tiny_acak_03.txt",
                    "experiments/stage2/maps/tiny_acak_04.txt",
                    "experiments/stage2/maps/tiny_klaster_02.txt",
                    "experiments/stage2/maps/tiny_labirin_02.txt"},
            "small", new String[]{
                    "TestCases/n20_acak_03.txt",
                    "TestCases/n20_acak_04.txt",
                    "TestCases/n20_klaster_02.txt",
                    "TestCases/n20_labirin_02.txt"},
            "normal", new String[]{
                    "TestCases/n40_acak_03.txt",
                    "TestCases/n40_acak_04.txt",
                    "TestCases/n40_klaster_02.txt",
                    "TestCases/n40_labirin_02.txt"},
            "large", new String[]{
                    "TestCases/n80_acak_01.txt",
                    "TestCases/n80_acak_02.txt",
                    "TestCases/n80_klaster_01.txt",
                    "TestCases/n80_klaster_02.txt",
                    "TestCases/n80_labirin_01.txt",
                    "TestCases/n80_labirin_02.txt"},
            "large_a", new String[]{
                    "TestCases/n80_acak_01.txt",
                    "TestCases/n80_acak_02.txt"},
            "large_b", new String[]{
                    "TestCases/n80_klaster_01.txt",
                    "TestCases/n80_klaster_02.txt"},
            "large_c", new String[]{
                    "TestCases/n80_labirin_01.txt",
                    "TestCases/n80_labirin_02.txt"});

    static final Map<String, Integer> SIZE_N = Map.of(
            "tiny", 10, "small", 20, "normal", 40, "large", 80, "large_a", 80, "large_b", 80, "large_c", 80);

    static final String CSV_HEADER =
            "size,pop,pc,pm,elitism,model_id,chromosome,selection,crossover,mutation,"
            + "map,p,seed,max_gen,cost,mean_cost,evaluation,elapsed_ms";

    // ------------------------------------------------------------------
    // Tabel model Stage 1: ID 1..16 mengikuti urutan loop Stage1Automation
    // (branch origin/Experiment_Stage1): chromosome ARRAY_FS lalu BIT_STRING;
    // selection tournament(4) lalu roulette; crossover single lalu two;
    // mutation random_replacement lalu targeted.
    // Peringkat Stage 1 (stage1results/stage1ranks) dicantumkan di README.
    // ------------------------------------------------------------------
    static final class ModelSpec {
        final int id;
        final ChromosomeType chromosome;
        final Selection selection;
        final Crossover crossover;
        final Mutation mutation;
        final String chromosomeName, selectionName, crossoverName, mutationName;

        ModelSpec(int id, ChromosomeType chromosome, Selection selection,
                  Crossover crossover, Mutation mutation,
                  String chromosomeName, String selectionName,
                  String crossoverName, String mutationName) {
            this.id = id;
            this.chromosome = chromosome;
            this.selection = selection;
            this.crossover = crossover;
            this.mutation = mutation;
            this.chromosomeName = chromosomeName;
            this.selectionName = selectionName;
            this.crossoverName = crossoverName;
            this.mutationName = mutationName;
        }
    }

    static final ModelSpec[] MODELS = buildModels();

    static ModelSpec[] buildModels() {
        ChromosomeType[] chroms = {ChromosomeType.ARRAY_FS, ChromosomeType.BIT_STRING};
        String[] chromNames = {"ARRAY_FS", "BIT_STRING"};
        String[] selNames = {"tournament_4", "roulette"};
        String[] crossNames = {"single_point", "two_point"};
        String[] mutNames = {"random_replacement", "targeted"};
        ModelSpec[] table = new ModelSpec[16];
        int id = 1;
        for (int ci = 0; ci < chroms.length; ci++) {
            for (int si = 0; si < selNames.length; si++) {
                Selection sel = si == 0 ? new TournamentSelection(4) : new RouletteWheelSelection();
                for (int xi = 0; xi < crossNames.length; xi++) {
                    Crossover cross = xi == 0 ? new SinglePointCrossover() : new TwoPointCrossover();
                    for (int mi = 0; mi < mutNames.length; mi++) {
                        Mutation mut = mi == 0 ? new RandomReplacementMutation() : new TargetedMutation();
                        table[id - 1] = new ModelSpec(id, chroms[ci], sel, cross, mut,
                                chromNames[ci], selNames[si], crossNames[xi], mutNames[mi]);
                        id++;
                    }
                }
            }
        }
        return table;
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Exception e) {
            System.err.println("ExperimentStage2A ERROR: " + e.getMessage());
            System.exit(1);
        }
    }

    static void run(String[] args) throws Exception {
        if (args.length != 8) {
            throw new IllegalArgumentException(
                    "usage: ExperimentStage2A <size> <pop> <pc> <pm> <elitism> <models> <seedCount> <outCsv>");
        }
        String size = args[0].toLowerCase(Locale.ROOT);
        if (!MAPS.containsKey(size)) {
            throw new IllegalArgumentException("size harus tiny|small|normal|large, dapat: " + size);
        }
        int pop = Integer.parseInt(args[1].trim());
        double pc = Double.parseDouble(args[2].trim());
        double pm = Double.parseDouble(args[3].trim());
        double elitism = Double.parseDouble(args[4].trim());
        if (pop <= 0) throw new IllegalArgumentException("pop harus > 0");
        if (pc < 0 || pc > 1 || pm < 0 || pm > 1 || elitism < 0 || elitism > 1) {
            throw new IllegalArgumentException("pc/pm/elitism harus di [0,1]");
        }
        int seedCount = Integer.parseInt(args[6].trim());
        if (seedCount <= 0) throw new IllegalArgumentException("seedCount harus > 0");
        String outCsv = args[7];

        // model ID dari CLI, tidak hardcode
        List<ModelSpec> models = new ArrayList<>();
        Set<Integer> seenIds = new HashSet<>();
        for (String tok : args[5].split(",")) {
            int id = Integer.parseInt(tok.trim());
            if (id < 1 || id > MODELS.length) {
                throw new IllegalArgumentException("model ID di luar 1.." + MODELS.length + ": " + id);
            }
            if (!seenIds.add(id)) {
                throw new IllegalArgumentException("model ID duplikat: " + id);
            }
            models.add(MODELS[id - 1]);
        }

        int[] seeds = loadSeeds(seedCount);
        String[] mapFiles = MAPS.get(size);
        int pOverride = P_OVERRIDE.get(size);

        // preload peta + override p (header test case tidak dipakai untuk p)
        Main.Preprocessed[] preps = new Main.Preprocessed[mapFiles.length];
        for (int k = 0; k < mapFiles.length; k++) {
            preps[k] = Main.loadAndPreprocess(mapFiles[k]);
            if (preps[k].n != SIZE_N.get(size)) {
                throw new IllegalArgumentException("peta " + mapFiles[k] + " n=" + preps[k].n
                        + ", diharapkan n=" + SIZE_N.get(size) + " untuk size " + size);
            }
            preps[k].p = pOverride;
        }

        int budget = Main.budgetFromSize(SIZE_N.get(size));
        int maxGen = budget / pop;
        if (maxGen < 1) {
            throw new IllegalArgumentException("maxGen = budget/pop = " + budget + "/" + pop
                    + " < 1; pop terlalu besar untuk size " + size);
        }

        // resume: baca CSV yang sudah ada
        Set<String> done = readDoneKeys(outCsv);

        Path outPath = Paths.get(outCsv);
        if (outPath.getParent() != null) Files.createDirectories(outPath.getParent());
        boolean newFile = !Files.exists(outPath) || Files.size(outPath) == 0;
        FileOutputStream fos = new FileOutputStream(outCsv, true);
        if (newFile) {
            fos.write((CSV_HEADER + "\n").getBytes(StandardCharsets.UTF_8));
            fos.flush();
        }

        int total = models.size() * mapFiles.length * seedCount;
        System.out.printf(Locale.US,
                "START size=%s pop=%d pc=%.4f pm=%.4f elitism=%.4f models=%s seeds=%d maps=%d maxGen=%d total=%d resume_done=%d out=%s%n",
                size, pop, pc, pm, elitism, args[5], seedCount, mapFiles.length, maxGen, total, done.size(), outCsv);
        System.out.flush();

        int i = 0, executed = 0, skipped = 0;
        for (ModelSpec model : models) {
            // set operator statis Main per model (pola Stage 1)
            Main.CHROMOSOME_TYPE = model.chromosome;
            Main.SELECTION_TYPE = model.selection;
            Main.CROSSOVER_TYPE = model.crossover;
            Main.MUTATION_TYPE = model.mutation;

            for (int k = 0; k < mapFiles.length; k++) {
                String mapName = new File(mapFiles[k]).getName();
                Main.Preprocessed prep = preps[k];
                double sumCost = 0.0;
                int meanCount = 0;

                for (int s = 0; s < seedCount; s++) {
                    i++;
                    int seed = seeds[s];
                    String key = runKey(size, pop, pc, pm, elitism, model.id, mapName, seed);
                    if (done.contains(key)) {
                        skipped++;
                        System.out.printf(Locale.US,
                                "[%s %d %.4f %.4f %.4f %d %s %d %d/%d] SKIP (resume)%n",
                                size, pop, pc, pm, elitism, model.id, mapName, seed, i, total);
                        System.out.flush();
                        continue;
                    }

                    long t0 = System.nanoTime();
                    Individual best = Main.runGA(prep, pop, pc, pm, elitism, maxGen, seed);
                    long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
                    double cost = best.getCost();
                    int[] selectedIds = best.getSelectedIds();
                    double meanSum = 0.0;
                    for (int h = 0; h < prep.numHouses; h++) {
                        int min = Integer.MAX_VALUE;
                        for (int id : selectedIds) {
                            int d = prep.distance[h][id];
                            if (d != -1 && d < min) min = d;
                        }
                        if (min == Integer.MAX_VALUE) min = prep.penalty;
                        meanSum += min;
                    }
                    double meanCostValue = meanSum / prep.numHouses;
                    int eval = Main.evaluationCount;

                    sumCost += cost;
                    meanCount++;
                    double mean = sumCost / meanCount;

                    String row = String.format(Locale.US,
                            "%s,%d,%.4f,%.4f,%.4f,%d,%s,%s,%s,%s,%s,%d,%d,%d,%.6f,%.6f,%d,%d%n",
                            size, pop, pc, pm, elitism, model.id,
                            model.chromosomeName, model.selectionName,
                            model.crossoverName, model.mutationName,
                            mapName, pOverride, seed, maxGen, cost, meanCostValue, eval, elapsedMs);
                    fos.write(row.getBytes(StandardCharsets.UTF_8));
                    fos.flush();
                    done.add(key);
                    executed++;

                    System.out.printf(Locale.US,
                            "[%s %d %.4f %.4f %.4f %d %s %d %d/%d] mean=%.6f eval=%d elapsed_ms=%d%n",
                            size, pop, pc, pm, elitism, model.id, mapName, seed, i, total,
                            mean, eval, elapsedMs);
                    System.out.flush();
                }
            }
        }
        fos.close();
        System.out.printf(Locale.US, "DONE size=%s pop=%d executed=%d skipped=%d total=%d out=%s%n",
                size, pop, executed, skipped, total, outCsv);
        System.out.flush();
    }

    static int[] loadSeeds(int seedCount) throws IOException {
        List<Integer> list = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get(SEEDS_FILE), StandardCharsets.UTF_8)) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            list.add(Integer.parseInt(t));
        }
        if (list.size() < seedCount) {
            throw new IllegalArgumentException(SEEDS_FILE + " hanya punya " + list.size()
                    + " seed, diminta " + seedCount);
        }
        int[] seeds = new int[seedCount];
        for (int i = 0; i < seedCount; i++) seeds[i] = list.get(i);
        return seeds;
    }

    static String fmt4(double v) {
        return String.format(Locale.US, "%.4f", v);
    }

    static String runKey(String size, int pop, double pc, double pm, double elitism,
                         int modelId, String mapName, int seed) {
        return size + "|" + pop + "|" + fmt4(pc) + "|" + fmt4(pm) + "|" + fmt4(elitism)
                + "|" + modelId + "|" + mapName + "|" + seed;
    }

    // Baca CSV lama -> set kunci resume. Kolom:
    // 0 size, 1 pop, 2 pc, 3 pm, 4 elitism, 5 model_id, 10 map, 12 seed
    static Set<String> readDoneKeys(String outCsv) throws IOException {
        Set<String> done = new HashSet<>();
        Path path = Paths.get(outCsv);
        if (!Files.exists(path)) return done;
        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line = br.readLine(); // header
            if (line == null) return done;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] f = line.split(",");
                if (f.length < 13) continue;
                String key = f[0] + "|" + f[1] + "|" + f[2] + "|" + f[3] + "|" + f[4]
                        + "|" + f[5] + "|" + f[10] + "|" + f[12];
                done.add(key);
            }
        }
        return done;
    }
}
