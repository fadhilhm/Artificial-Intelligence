import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

import crossover.*;
import model.*;
import model.Individual.ChromosomeType;
import mutation.*;
import selection.*;

/**
 * Penguji Eksperimen Tahap 2 Cara (A) - Sequential Hyperparameter Tuning
 * Terintegrasi langsung dengan Main.loadAndPreprocess(), Main.Preprocessed,
 * GeneticAlgorithm, dan TargetedMutation milik proyek Anda.
 *
 * Cara kompilasi & menjalankan:
 *   javac -d out *.java model/*.java crossover/*.java mutation/*.java selection/*.java
 *   java -cp out Stage2ATuner tahap2a_input.txt hasil_tahap2a
 */
public class Stage2ATuner {

    public static final String DEFAULT_INPUT_FILE = "tahap2a_input.txt";
    public static final String DEFAULT_OUTPUT_PREFIX = "hasil_tahap2a";

    public static class ModelSpec {
        public String name;
        public ChromosomeType chromosomeType;
        public EvolutionModel evolutionModel;
        public String selectionType;
        public int tournamentSize;
        public String crossoverType;
        public String mutationType;

        public String describe() {
            String sel = selectionType.equalsIgnoreCase("TOURNAMENT")
                    ? "TOURNAMENT(k=" + tournamentSize + ")"
                    : selectionType;
            return String.format("%s [%s | %s | %s | %s | %s]",
                    name, chromosomeType, evolutionModel, sel, crossoverType, mutationType);
        }
    }

    public static class Config {
        public List<ModelSpec> models = new ArrayList<>();
        public List<String> mapPatterns = new ArrayList<>();

        // Parameter tetap dari Tahap 1
        public int basePop = 100;
        public double basePc = 0.80;
        public double basePm = 0.05;
        public double baseElitism = 0.20;

        // 5 angka kandidat di luar angka Tahap 1 untuk masing-masing hyper-parameter
        public List<Integer> candidatePops = new ArrayList<>(Arrays.asList(30, 50, 200, 300, 500));
        public List<Double> candidatePcs = new ArrayList<>(Arrays.asList(0.60, 0.70, 0.75, 0.85, 0.95));
        public List<Double> candidatePms = new ArrayList<>(Arrays.asList(0.01, 0.02, 0.10, 0.20, 0.30));
        public List<Double> candidateElitisms = new ArrayList<>(Arrays.asList(0.05, 0.10, 0.15, 0.25, 0.30));

        // Kriteria pemilihan pemenang setiap langkah: MEAN_RPD, MEAN_COST, atau COMPOSITE
        public String selectionCriterion = "MEAN_RPD";
        public boolean evaluateTahap1Baseline = true;

        // Kontrol eksperimen & penghentian
        public int numSeeds = Main.NUM_OF_SEEDS;
        public int baseSeed = Main.SEED;
        public int maxStagnant = Main.MAX_STAGNANT;
        public long timeLimitMs = 15000; // batas waktu per 1x run (ms), 0 = tanpa batas
        public int threads = 0; // 0 = otomatis pakai semua core CPU
    }

    private static class MapEntry {
        String path;
        String fileName;
        int scale;        // 20 (tiny), 40 (small), 80 (normal)
        String sizeLabel; // tiny, small, normal
        String topology;  // acak, klaster, labirin
        Main.Preprocessed prep;
    }

    private static class TrialResult {
        double cost;
        double fitness;
        int maxHouseDist;
        double avgHouseDist;
        int unreachedHouses;
        int evalsTotal;
        int evalsToBest;
        int gensRun;
        int maxGenAllowed;
        String stopReason;
        double runtimeMs;
        int[] selectedIds;
    }

    private static class MapMetricRow {
        String stepName;
        String modelName;
        ModelSpec model;
        int candidateIdx;
        int popSize;
        double pc;
        double pm;
        double elitism;

        MapEntry map;
        int numSeeds;
        double bestCost;
        double meanCost;
        double medianCost;
        double stdCost;
        double worstCost;
        double ci95Cost;
        double rpdInStepPct;

        double meanMaxHouseDist;
        double meanAvgHouseDist;
        int bestUnreachedHouses;
        double meanEvalsTotal;
        double meanEvalsToBest;
        double meanGensRun;
        int maxGenAllowed;
        double stagnantStopRate;
        double budgetStopRate;
        double timeStopRate;
        double meanRuntimeMs;
        String bestCoords;
    }

    private static class StepCandidateSummary implements Comparable<StepCandidateSummary> {
        String stepName;
        String modelName;
        ModelSpec model;
        int candidateIdx;
        int popSize;
        double pc;
        double pm;
        double elitism;

        int mapsCount;
        double meanRpdAll;
        double stdRpdAll;
        double compositeScore;
        double meanRpdTiny;
        double meanRpdSmall;
        double meanRpdNormal;
        double meanRpdAcak;
        double meanRpdKlaster;
        double meanRpdLabirin;

        double meanCostAll;
        double meanCostTiny;
        double meanCostSmall;
        double meanCostNormal;
        double meanStdCost;

        double meanEvalsTotal;
        double meanEvalsToBest;
        double meanGensRun;
        double meanStagnantRate;
        double meanRuntimeMs;
        boolean isWinner;
        int rankInStep;

        private static String activeCriterion = "MEAN_RPD";

        public double metricValue(String criterion) {
            if ("MEAN_COST".equalsIgnoreCase(criterion)) {
                return meanCostAll;
            } else if ("COMPOSITE".equalsIgnoreCase(criterion)) {
                return meanRpdAll + 0.25 * stdRpdAll;
            }
            return meanRpdAll;
        }

        @Override
        public int compareTo(StepCandidateSummary o) {
            return Double.compare(this.metricValue(activeCriterion), o.metricValue(activeCriterion));
        }
    }

    public static void main(String[] args) throws Exception {
        String inputPath = (args.length >= 1) ? args[0] : DEFAULT_INPUT_FILE;
        String outputPrefix = (args.length >= 2) ? args[1] : DEFAULT_OUTPUT_PREFIX;

        Config cfg = loadOrCreateConfig(inputPath);
        runTahap2A(cfg, outputPrefix);
    }

    public static void runTahap2A(Config cfg, String outputPrefix) throws Exception {
        if (cfg.models.isEmpty()) {
            throw new IllegalArgumentException("Minimal harus ada 1 model di file konfigurasi!");
        }

        List<String> resolvedPaths = resolveMapPaths(cfg.mapPatterns);
        if (resolvedPaths.isEmpty()) {
            throw new FileNotFoundException("File peta uji tidak ditemukan untuk MAPS: " + cfg.mapPatterns);
        }

        List<MapEntry> maps = new ArrayList<>();
        for (String path : resolvedPaths) {
            File f = new File(path);
            Main.Preprocessed prep = Main.loadAndPreprocess(path);
            MapEntry me = new MapEntry();
            me.path = path;
            me.fileName = f.getName();
            me.scale = extractScale(f.getName(), prep.n);
            me.sizeLabel = mapScaleToCategory(me.scale);
            me.topology = extractTopology(f.getName());
            me.prep = prep;
            maps.add(me);
        }

        maps.sort((a, b) -> {
            int s = Integer.compare(a.scale, b.scale);
            if (s != 0) return s;
            int t = a.topology.compareTo(b.topology);
            if (t != 0) return t;
            return a.fileName.compareTo(b.fileName);
        });

        int[] seeds = new int[cfg.numSeeds];
        Random r = new Random(cfg.baseSeed);
        for (int i = 0; i < cfg.numSeeds; i++) {
            seeds[i] = r.nextInt(65537);
        }

        int numThreads = (cfg.threads > 0)
                ? cfg.threads
                : Math.max(1, Runtime.getRuntime().availableProcessors());
        ExecutorService pool = Executors.newFixedThreadPool(numThreads);

        printHeader(cfg, maps, numThreads);
        long globalStartMs = System.currentTimeMillis();

        String stepSummaryCsv = outputPrefix + "_step_summary.csv";
        String perMapCsv = outputPrefix + "_per_map.csv";
        String finalSummaryCsv = outputPrefix + "_final_comparison.csv";
        String rawSeedsCsv = outputPrefix + "_raw_30seeds.csv";

        List<StepCandidateSummary> allStepSummaries = new ArrayList<>();
        List<MapMetricRow> allPerMapRows = new ArrayList<>();
        List<String> finalComparisonLines = new ArrayList<>();
        finalComparisonLines.add("model_name,chromosome_type,evolution_model,selection_type,tournament_size,"
                + "crossover_type,mutation_type,"
                + "t1_pop,t1_pc,t1_pm,t1_elitism,t1_mean_cost_all,t1_mean_cost_tiny,t1_mean_cost_small,t1_mean_cost_normal,"
                + "t2a_pop,t2a_pc,t2a_pm,t2a_elitism,t2a_mean_cost_all,t2a_mean_cost_tiny,t2a_mean_cost_small,t2a_mean_cost_normal,"
                + "cost_improvement_pct,t2a_mean_runtime_ms");

        Map<String, List<TrialResult>> trialCache = new HashMap<>();

        try (PrintWriter rawOut = new PrintWriter(new FileWriter(rawSeedsCsv))) {
            rawOut.println("step_name,model_name,chromosome_type,evolution_model,selection_type,tournament_size,"
                    + "crossover_type,mutation_type,pop_size,pc,pm,elitism,"
                    + "map_name,size_category,map_scale,map_topology,seed_idx,seed_val,"
                    + "cost,fitness,max_house_dist,avg_house_dist,unreached_houses,"
                    + "evals_total,evals_to_best,gens_run,max_gen_allowed,stop_reason,runtime_ms,fs_coords");

            for (int mIdx = 0; mIdx < cfg.models.size(); mIdx++) {
                ModelSpec model = cfg.models.get(mIdx);
                System.out.println("\n==========================================================================");
                System.out.printf(Locale.US, "MODEL %d/%d: %s%n", mIdx + 1, cfg.models.size(), model.describe());
                System.out.println("==========================================================================");

                StepCandidateSummary baselineSummary = null;
                if (cfg.evaluateTahap1Baseline) {
                    System.out.printf(Locale.US, "  [Baseline Tahap 1] pop=%d, pc=%.2f, pm=%.2f, elitism=%.2f ... ",
                            cfg.basePop, cfg.basePc, cfg.basePm, cfg.baseElitism);
                    long t0 = System.currentTimeMillis();
                    List<StepCandidateSummary> baseList = evaluateStep(
                            "BASELINE_T1", model, maps, seeds, cfg, pool,
                            Collections.singletonList(cfg.basePop),
                            Collections.singletonList(cfg.basePc),
                            Collections.singletonList(cfg.basePm),
                            Collections.singletonList(cfg.baseElitism),
                            allPerMapRows, rawOut, trialCache);
                    baselineSummary = baseList.get(0);
                    allStepSummaries.add(baselineSummary);
                    System.out.printf(Locale.US, "MeanCost=%.4f (%.2f dtk)%n",
                            baselineSummary.meanCostAll, (System.currentTimeMillis() - t0) / 1000.0);
                }

                // STEP 1: Tune POP_SIZE
                System.out.printf(Locale.US, "  [Step 1/4 - POP_SIZE] Kandidat=%s | Tetap: pc=%.2f, pm=%.2f, elit=%.2f%n",
                        cfg.candidatePops, cfg.basePc, cfg.basePm, cfg.baseElitism);
                long t1 = System.currentTimeMillis();
                List<StepCandidateSummary> s1 = evaluateStep(
                        "STEP1_POP", model, maps, seeds, cfg, pool,
                        cfg.candidatePops,
                        Collections.singletonList(cfg.basePc),
                        Collections.singletonList(cfg.basePm),
                        Collections.singletonList(cfg.baseElitism),
                        allPerMapRows, rawOut, trialCache);
                allStepSummaries.addAll(s1);
                int bestPop = s1.get(0).popSize;
                printStepRanking(s1, (System.currentTimeMillis() - t1) / 1000.0);
                System.out.printf(Locale.US, "  --> TERPILIH Step 1: pop = %d%n%n", bestPop);

                // STEP 2: Tune CROSSOVER_RATE
                System.out.printf(Locale.US, "  [Step 2/4 - PC] Kandidat=%s | Pakai pop=%d | Tetap: pm=%.2f, elit=%.2f%n",
                        cfg.candidatePcs, bestPop, cfg.basePm, cfg.baseElitism);
                long t2 = System.currentTimeMillis();
                List<StepCandidateSummary> s2 = evaluateStep(
                        "STEP2_PC", model, maps, seeds, cfg, pool,
                        Collections.singletonList(bestPop),
                        cfg.candidatePcs,
                        Collections.singletonList(cfg.basePm),
                        Collections.singletonList(cfg.baseElitism),
                        allPerMapRows, rawOut, trialCache);
                allStepSummaries.addAll(s2);
                double bestPc = s2.get(0).pc;
                printStepRanking(s2, (System.currentTimeMillis() - t2) / 1000.0);
                System.out.printf(Locale.US, "  --> TERPILIH Step 2: pc = %.2f%n%n", bestPc);

                // STEP 3: Tune MUTATION_RATE
                System.out.printf(Locale.US, "  [Step 3/4 - PM] Kandidat=%s | Pakai pop=%d, pc=%.2f | Tetap: elit=%.2f%n",
                        cfg.candidatePms, bestPop, bestPc, cfg.baseElitism);
                long t3 = System.currentTimeMillis();
                List<StepCandidateSummary> s3 = evaluateStep(
                        "STEP3_PM", model, maps, seeds, cfg, pool,
                        Collections.singletonList(bestPop),
                        Collections.singletonList(bestPc),
                        cfg.candidatePms,
                        Collections.singletonList(cfg.baseElitism),
                        allPerMapRows, rawOut, trialCache);
                allStepSummaries.addAll(s3);
                double bestPm = s3.get(0).pm;
                printStepRanking(s3, (System.currentTimeMillis() - t3) / 1000.0);
                System.out.printf(Locale.US, "  --> TERPILIH Step 3: pm = %.2f%n%n", bestPm);

                // STEP 4: Tune ELITISM_RATE
                System.out.printf(Locale.US, "  [Step 4/4 - ELITISM] Kandidat=%s | Pakai pop=%d, pc=%.2f, pm=%.2f%n",
                        cfg.candidateElitisms, bestPop, bestPc, bestPm);
                long t4 = System.currentTimeMillis();
                List<StepCandidateSummary> s4 = evaluateStep(
                        "STEP4_ELITISM", model, maps, seeds, cfg, pool,
                        Collections.singletonList(bestPop),
                        Collections.singletonList(bestPc),
                        Collections.singletonList(bestPm),
                        cfg.candidateElitisms,
                        allPerMapRows, rawOut, trialCache);
                allStepSummaries.addAll(s4);
                StepCandidateSummary winner = s4.get(0);
                printStepRanking(s4, (System.currentTimeMillis() - t4) / 1000.0);

                double t1CostAll = (baselineSummary != null) ? baselineSummary.meanCostAll : Double.NaN;
                double t1CostTiny = (baselineSummary != null) ? baselineSummary.meanCostTiny : Double.NaN;
                double t1CostSmall = (baselineSummary != null) ? baselineSummary.meanCostSmall : Double.NaN;
                double t1CostNorm = (baselineSummary != null) ? baselineSummary.meanCostNormal : Double.NaN;
                double impPct = (baselineSummary != null && t1CostAll > 0)
                        ? ((t1CostAll - winner.meanCostAll) / t1CostAll) * 100.0
                        : Double.NaN;

                finalComparisonLines.add(String.format(Locale.US,
                        "%s,%s,%s,%s,%d,%s,%s,%d,%.2f,%.2f,%.2f,%.6f,%.6f,%.6f,%.6f,"
                                + "%d,%.2f,%.2f,%.2f,%.6f,%.6f,%.6f,%.6f,%.4f,%.3f",
                        model.name, model.chromosomeType, model.evolutionModel,
                        model.selectionType, model.tournamentSize,
                        model.crossoverType, model.mutationType,
                        cfg.basePop, cfg.basePc, cfg.basePm, cfg.baseElitism,
                        t1CostAll, t1CostTiny, t1CostSmall, t1CostNorm,
                        winner.popSize, winner.pc, winner.pm, winner.elitism,
                        winner.meanCostAll, winner.meanCostTiny, winner.meanCostSmall, winner.meanCostNormal,
                        impPct, winner.meanRuntimeMs));

                System.out.printf(Locale.US,
                        "  ==> FINAL [%s]: pop=%d, pc=%.2f, pm=%.2f, elitism=%.2f | MeanCost=%.4f (Perbaikan vs T1: %+.2f%%)%n",
                        model.name, winner.popSize, winner.pc, winner.pm, winner.elitism,
                        winner.meanCostAll, Double.isNaN(impPct) ? 0.0 : impPct);
            }
        } finally {
            pool.shutdown();
        }

        writeStepSummaryCsv(stepSummaryCsv, allStepSummaries);
        writePerMapCsv(perMapCsv, allPerMapRows);
        try (PrintWriter pw = new PrintWriter(new FileWriter(finalSummaryCsv))) {
            for (String line : finalComparisonLines) pw.println(line);
        }

        double totalSec = (System.currentTimeMillis() - globalStartMs) / 1000.0;
        System.out.println("\n==========================================================================");
        System.out.printf(Locale.US, "SELESAI TOTAL DALAM %.2f DETIK (%.2f MENIT)!%n", totalSec, totalSec / 60.0);
        System.out.println("  1. Ringkasan Tiap Langkah (Step 1-4) : " + stepSummaryCsv);
        System.out.println("  2. Perbandingan Before vs After T2A  : " + finalSummaryCsv);
        System.out.println("  3. Statistik Detail per Peta         : " + perMapCsv);
        System.out.println("  4. Data Mentah 30 Seeds              : " + rawSeedsCsv);
        System.out.println("==========================================================================");
    }

    private static List<StepCandidateSummary> evaluateStep(
            String stepName, ModelSpec model, List<MapEntry> maps, int[] seeds,
            Config cfg, ExecutorService pool,
            List<Integer> pops, List<Double> pcs, List<Double> pms, List<Double> elitisms,
            List<MapMetricRow> outMapRows, PrintWriter rawOut,
            Map<String, List<TrialResult>> cache) throws Exception {

        List<MapMetricRow> stepMapRows = new ArrayList<>();

        int candIdx = 0;
        for (int pop : pops) {
            for (double pc : pcs) {
                for (double pm : pms) {
                    for (double elit : elitisms) {
                        candIdx++;
                        for (MapEntry map : maps) {
                            MapMetricRow mm = evaluateOnSingleMap(
                                    stepName, model, candIdx, pop, pc, pm, elit,
                                    map, seeds, cfg, pool, rawOut, cache);
                            stepMapRows.add(mm);
                        }
                    }
                }
            }
        }

        Map<String, Double> bestMeanByMap = new HashMap<>();
        for (MapMetricRow r : stepMapRows) {
            bestMeanByMap.merge(r.map.fileName, r.meanCost, Math::min);
        }
        for (MapMetricRow r : stepMapRows) {
            double bestM = bestMeanByMap.get(r.map.fileName);
            r.rpdInStepPct = (bestM > 0.0) ? ((r.meanCost - bestM) / bestM) * 100.0 : 0.0;
        }
        outMapRows.addAll(stepMapRows);

        Map<Integer, List<MapMetricRow>> byCand = new LinkedHashMap<>();
        for (MapMetricRow r : stepMapRows) {
            byCand.computeIfAbsent(r.candidateIdx, k -> new ArrayList<>()).add(r);
        }

        List<StepCandidateSummary> summaries = new ArrayList<>();
        for (List<MapMetricRow> rows : byCand.values()) {
            MapMetricRow first = rows.get(0);
            StepCandidateSummary s = new StepCandidateSummary();
            s.stepName = stepName;
            s.modelName = model.name;
            s.model = model;
            s.candidateIdx = first.candidateIdx;
            s.popSize = first.popSize;
            s.pc = first.pc;
            s.pm = first.pm;
            s.elitism = first.elitism;
            s.mapsCount = rows.size();

            double sumRpd = 0, sumCost = 0, sumStd = 0, sumEvTot = 0, sumEvBest = 0, sumGens = 0, sumStag = 0, sumTime = 0;
            double rTiny = 0, cTinyCost = 0; int nTiny = 0;
            double rSmall = 0, cSmallCost = 0; int nSmall = 0;
            double rNorm = 0, cNormCost = 0; int nNorm = 0;
            double rAcak = 0; int nAcak = 0;
            double rKlas = 0; int nKlas = 0;
            double rLab = 0; int nLab = 0;

            for (MapMetricRow r : rows) {
                sumRpd += r.rpdInStepPct;
                sumCost += r.meanCost;
                sumStd += r.stdCost;
                sumEvTot += r.meanEvalsTotal;
                sumEvBest += r.meanEvalsToBest;
                sumGens += r.meanGensRun;
                sumStag += r.stagnantStopRate;
                sumTime += r.meanRuntimeMs;

                if ("tiny".equalsIgnoreCase(r.map.sizeLabel)) {
                    rTiny += r.rpdInStepPct; cTinyCost += r.meanCost; nTiny++;
                } else if ("small".equalsIgnoreCase(r.map.sizeLabel)) {
                    rSmall += r.rpdInStepPct; cSmallCost += r.meanCost; nSmall++;
                } else {
                    rNorm += r.rpdInStepPct; cNormCost += r.meanCost; nNorm++;
                }

                if ("acak".equalsIgnoreCase(r.map.topology)) { rAcak += r.rpdInStepPct; nAcak++; }
                else if ("klaster".equalsIgnoreCase(r.map.topology)) { rKlas += r.rpdInStepPct; nKlas++; }
                else if ("labirin".equalsIgnoreCase(r.map.topology)) { rLab += r.rpdInStepPct; nLab++; }
            }

            s.meanRpdAll = sumRpd / rows.size();
            double sq = 0;
            for (MapMetricRow r : rows) sq += (r.rpdInStepPct - s.meanRpdAll) * (r.rpdInStepPct - s.meanRpdAll);
            s.stdRpdAll = (rows.size() > 1) ? Math.sqrt(sq / (rows.size() - 1)) : 0.0;
            s.compositeScore = s.meanRpdAll + 0.25 * s.stdRpdAll;

            s.meanRpdTiny = (nTiny > 0) ? rTiny / nTiny : Double.NaN;
            s.meanRpdSmall = (nSmall > 0) ? rSmall / nSmall : Double.NaN;
            s.meanRpdNormal = (nNorm > 0) ? rNorm / nNorm : Double.NaN;
            s.meanRpdAcak = (nAcak > 0) ? rAcak / nAcak : Double.NaN;
            s.meanRpdKlaster = (nKlas > 0) ? rKlas / nKlas : Double.NaN;
            s.meanRpdLabirin = (nLab > 0) ? rLab / nLab : Double.NaN;

            s.meanCostAll = sumCost / rows.size();
            s.meanCostTiny = (nTiny > 0) ? cTinyCost / nTiny : Double.NaN;
            s.meanCostSmall = (nSmall > 0) ? cSmallCost / nSmall : Double.NaN;
            s.meanCostNormal = (nNorm > 0) ? cNormCost / nNorm : Double.NaN;
            s.meanStdCost = sumStd / rows.size();

            s.meanEvalsTotal = sumEvTot / rows.size();
            s.meanEvalsToBest = sumEvBest / rows.size();
            s.meanGensRun = sumGens / rows.size();
            s.meanStagnantRate = sumStag / rows.size();
            s.meanRuntimeMs = sumTime / rows.size();

            summaries.add(s);
        }

        StepCandidateSummary.activeCriterion = cfg.selectionCriterion;
        Collections.sort(summaries);

        for (int i = 0; i < summaries.size(); i++) {
            summaries.get(i).rankInStep = i + 1;
            summaries.get(i).isWinner = (i == 0);
        }

        return summaries;
    }

    private static MapMetricRow evaluateOnSingleMap(
            String stepName, ModelSpec model, int candIdx,
            int popSize, double pc, double pm, double elitism,
            MapEntry map, int[] seeds, Config cfg, ExecutorService pool,
            PrintWriter rawOut, Map<String, List<TrialResult>> cache) throws Exception {

        String cacheKey = String.format(Locale.US, "%s|%s|%d|%.5f|%.5f|%.5f",
                model.name, map.fileName, popSize, pc, pm, elitism);

        List<TrialResult> results = cache.get(cacheKey);
        if (results == null) {
            final Main.Preprocessed prep = map.prep;
            final int budget = Main.budgetFromSize(map.scale);
            TargetedMutation.setContext(prep.distance, prep.numHouses);

            List<Future<TrialResult>> futures = new ArrayList<>(seeds.length);
            for (int s = 0; s < seeds.length; s++) {
                final long seedVal = seeds[s];
                futures.add(pool.submit(() ->
                        runSingleSeed(prep, budget, cfg, model, popSize, pc, pm, elitism, seedVal)));
            }

            results = new ArrayList<>(seeds.length);
            for (Future<TrialResult> f : futures) {
                results.add(f.get());
            }
            cache.put(cacheKey, results);
        }

        int n = results.size();
        double[] costs = new double[n];
        double sumCost = 0, sumMaxD = 0, sumAvgD = 0, sumEvTot = 0, sumEvBest = 0, sumGens = 0, sumTime = 0;
        int stagCount = 0, budgetCount = 0, timeCount = 0;
        TrialResult bestTrial = null;

        for (int s = 0; s < n; s++) {
            TrialResult tr = results.get(s);
            costs[s] = tr.cost;
            sumCost += tr.cost;
            sumMaxD += tr.maxHouseDist;
            sumAvgD += tr.avgHouseDist;
            sumEvTot += tr.evalsTotal;
            sumEvBest += tr.evalsToBest;
            sumGens += tr.gensRun;
            sumTime += tr.runtimeMs;

            if ("STAGNANT".equals(tr.stopReason)) stagCount++;
            else if ("TIME_LIMIT".equals(tr.stopReason)) timeCount++;
            else budgetCount++;

            if (bestTrial == null || tr.cost < bestTrial.cost) bestTrial = tr;

            String coords = formatCoords(tr.selectedIds, map.prep.cellCoords);
            rawOut.printf(Locale.US,
                    "%s,%s,%s,%s,%s,%d,%s,%s,%d,%.3f,%.3f,%.3f,%s,%s,%d,%s,%d,%d,"
                            + "%.6f,%.6f,%d,%.4f,%d,%d,%d,%d,%d,%s,%.3f,\"%s\"%n",
                    stepName, model.name, model.chromosomeType, model.evolutionModel,
                    model.selectionType, model.tournamentSize, model.crossoverType, model.mutationType,
                    popSize, pc, pm, elitism,
                    map.fileName, map.sizeLabel, map.scale, map.topology, s + 1, seeds[s],
                    tr.cost, tr.fitness, tr.maxHouseDist, tr.avgHouseDist, tr.unreachedHouses,
                    tr.evalsTotal, tr.evalsToBest, tr.gensRun, tr.maxGenAllowed,
                    tr.stopReason, tr.runtimeMs, coords);
        }

        double[] sorted = Arrays.copyOf(costs, n);
        Arrays.sort(sorted);
        double meanCost = sumCost / n;
        double medianCost = (n % 2 == 1) ? sorted[n / 2] : 0.5 * (sorted[n / 2 - 1] + sorted[n / 2]);
        double sq = 0;
        for (double c : costs) sq += (c - meanCost) * (c - meanCost);
        double stdCost = (n > 1) ? Math.sqrt(sq / (n - 1)) : 0.0;
        double ci95 = (n > 0) ? 1.96 * (stdCost / Math.sqrt(n)) : 0.0;

        MapMetricRow row = new MapMetricRow();
        row.stepName = stepName;
        row.modelName = model.name;
        row.model = model;
        row.candidateIdx = candIdx;
        row.popSize = popSize;
        row.pc = pc;
        row.pm = pm;
        row.elitism = elitism;
        row.map = map;
        row.numSeeds = n;
        row.bestCost = sorted[0];
        row.meanCost = meanCost;
        row.medianCost = medianCost;
        row.stdCost = stdCost;
        row.worstCost = sorted[n - 1];
        row.ci95Cost = ci95;
        row.meanMaxHouseDist = sumMaxD / n;
        row.meanAvgHouseDist = sumAvgD / n;
        row.bestUnreachedHouses = bestTrial.unreachedHouses;
        row.meanEvalsTotal = sumEvTot / n;
        row.meanEvalsToBest = sumEvBest / n;
        row.meanGensRun = sumGens / n;
        row.maxGenAllowed = bestTrial.maxGenAllowed;
        row.stagnantStopRate = (100.0 * stagCount) / n;
        row.budgetStopRate = (100.0 * budgetCount) / n;
        row.timeStopRate = (100.0 * timeCount) / n;
        row.meanRuntimeMs = sumTime / n;
        row.bestCoords = formatCoords(bestTrial.selectedIds, map.prep.cellCoords);
        return row;
    }

    private static TrialResult runSingleSeed(
            Main.Preprocessed prep, int budget, Config cfg, ModelSpec model,
            int popSize, double pc, double pm, double elitism, long seed) {

        long startNs = System.nanoTime();

        double safeElitism = elitism;
        if ((int) (popSize * safeElitism) >= popSize) {
            safeElitism = Math.max(0.0, (popSize - 1.0) / popSize);
        }

        Selection selection = buildSelection(model.selectionType, model.tournamentSize);
        Crossover crossover = buildCrossover(model.crossoverType);
        Mutation mutation = buildMutation(model.mutationType);

        Population population = new Population(popSize);
        population.initializeRandomPopulation(prep.totalEmpty, prep.p, new Random(seed), model.chromosomeType);

        GeneticAlgorithm ga = new GeneticAlgorithm(
                prep.distance, prep.numHouses, prep.totalEmpty, prep.p, prep.penalty,
                pc, pm, safeElitism, seed,
                selection, crossover, mutation, model.evolutionModel);

        ga.evaluatePopulation(population);
        population.sortByFitness();

        Individual bestSoFar = population.getBestIndividual().getClone();
        double bestFitness = bestSoFar.getFitness();
        int evalsToBest = ga.getEvaluationCount();
        int stagnantCounter = 0;
        int gensRun = 0;

        int maxGen = Math.max(1, budget / popSize);
        String stopReason = "MAX_GEN";

        while (gensRun < maxGen) {
            if (cfg.timeLimitMs > 0 && ((System.nanoTime() - startNs) / 1_000_000.0) >= cfg.timeLimitMs) {
                stopReason = "TIME_LIMIT";
                break;
            }

            ga.evolveStep(population);
            population.sortByFitness();
            gensRun++;

            Individual currBest = population.getBestIndividual();
            double currFit = currBest.getFitness();
            if (currFit > bestFitness + 1e-9) {
                bestFitness = currFit;
                bestSoFar = currBest.getClone();
                evalsToBest = ga.getEvaluationCount();
                stagnantCounter = 0;
            } else {
                stagnantCounter++;
                if (cfg.maxStagnant > 0 && stagnantCounter >= cfg.maxStagnant) {
                    stopReason = "STAGNANT";
                    break;
                }
            }
        }

        long endNs = System.nanoTime();

        int[] selectedIds = bestSoFar.getSelectedIds();
        int maxHouseDist = 0;
        long totalHouseDist = 0;
        int unreached = 0;

        for (int h = 0; h < prep.numHouses; h++) {
            int minD = Integer.MAX_VALUE;
            for (int fsId : selectedIds) {
                int d = prep.distance[h][fsId];
                if (d != -1 && d < minD) minD = d;
            }
            if (minD == Integer.MAX_VALUE) {
                unreached++;
                minD = prep.penalty;
            }
            if (minD > maxHouseDist) maxHouseDist = minD;
            totalHouseDist += minD;
        }

        TrialResult res = new TrialResult();
        res.cost = bestSoFar.getCost();
        res.fitness = bestSoFar.getFitness();
        res.maxHouseDist = maxHouseDist;
        res.avgHouseDist = (prep.numHouses > 0) ? ((double) totalHouseDist / prep.numHouses) : 0.0;
        res.unreachedHouses = unreached;
        res.evalsTotal = ga.getEvaluationCount();
        res.evalsToBest = evalsToBest;
        res.gensRun = gensRun;
        res.maxGenAllowed = maxGen;
        res.stopReason = stopReason;
        res.runtimeMs = (endNs - startNs) / 1_000_000.0;
        res.selectedIds = selectedIds;
        return res;
    }

    private static void printStepRanking(List<StepCandidateSummary> rows, double elapsedSec) {
        for (StepCandidateSummary s : rows) {
            System.out.printf(Locale.US,
                    "     #%d | pop=%-4d pc=%.2f pm=%.2f elit=%.2f | MeanRPD=%6.3f%% (Tiny=%5.2f%%, Small=%5.2f%%, Norm=%5.2f%%) | MeanCost=%.4f%n",
                    s.rankInStep, s.popSize, s.pc, s.pm, s.elitism,
                    s.meanRpdAll, s.meanRpdTiny, s.meanRpdSmall, s.meanRpdNormal, s.meanCostAll);
        }
        System.out.printf(Locale.US, "     (Selesai dalam %.2f detik)%n", elapsedSec);
    }

    private static void writeStepSummaryCsv(String path, List<StepCandidateSummary> rows) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            pw.println("step_name,rank_in_step,is_winner,model_name,chromosome_type,evolution_model,"
                    + "selection_type,tournament_size,crossover_type,mutation_type,"
                    + "pop_size,pc,pm,elitism,maps_tested,"
                    + "mean_rpd_all_pct,std_rpd_all_pct,composite_score,"
                    + "mean_rpd_tiny_pct,mean_rpd_small_pct,mean_rpd_normal_pct,"
                    + "mean_rpd_acak_pct,mean_rpd_klaster_pct,mean_rpd_labirin_pct,"
                    + "mean_cost_all,mean_cost_tiny,mean_cost_small,mean_cost_normal,mean_std_cost,"
                    + "mean_evals_total,mean_evals_to_best,mean_gens_run,mean_stagnant_stop_rate,mean_runtime_ms");

            for (StepCandidateSummary r : rows) {
                pw.printf(Locale.US,
                        "%s,%d,%b,%s,%s,%s,%s,%d,%s,%s,%d,%.3f,%.3f,%.3f,%d,"
                                + "%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,"
                                + "%.6f,%.6f,%.6f,%.6f,%.6f,%.1f,%.1f,%.1f,%.2f,%.3f%n",
                        r.stepName, r.rankInStep, r.isWinner, r.modelName,
                        r.model.chromosomeType, r.model.evolutionModel,
                        r.model.selectionType, r.model.tournamentSize,
                        r.model.crossoverType, r.model.mutationType,
                        r.popSize, r.pc, r.pm, r.elitism, r.mapsCount,
                        r.meanRpdAll, r.stdRpdAll, r.compositeScore,
                        r.meanRpdTiny, r.meanRpdSmall, r.meanRpdNormal,
                        r.meanRpdAcak, r.meanRpdKlaster, r.meanRpdLabirin,
                        r.meanCostAll, r.meanCostTiny, r.meanCostSmall, r.meanCostNormal, r.meanStdCost,
                        r.meanEvalsTotal, r.meanEvalsToBest, r.meanGensRun, r.meanStagnantRate, r.meanRuntimeMs);
            }
        }
    }

    private static void writePerMapCsv(String path, List<MapMetricRow> rows) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            pw.println("step_name,model_name,chromosome_type,evolution_model,selection_type,tournament_size,"
                    + "crossover_type,mutation_type,pop_size,pc,pm,elitism,"
                    + "map_name,size_category,map_scale,map_topology,num_houses,empty_cells,p_fire_stations,num_seeds,"
                    + "best_cost,mean_cost,median_cost,std_cost,worst_cost,ci95_cost,rpd_in_step_pct,"
                    + "mean_max_house_dist,mean_avg_house_dist,best_unreached_houses,"
                    + "mean_evals_total,mean_evals_to_best,mean_gens_run,max_gen_allowed,"
                    + "stagnant_stop_rate,budget_stop_rate,time_stop_rate,mean_runtime_ms,best_fs_coords");

            for (MapMetricRow r : rows) {
                pw.printf(Locale.US,
                        "%s,%s,%s,%s,%s,%d,%s,%s,%d,%.3f,%.3f,%.3f,"
                                + "%s,%s,%d,%s,%d,%d,%d,%d,"
                                + "%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.4f,"
                                + "%.3f,%.4f,%d,%.1f,%.1f,%.1f,%d,%.2f,%.2f,%.2f,%.3f,\"%s\"%n",
                        r.stepName, r.modelName,
                        r.model.chromosomeType, r.model.evolutionModel,
                        r.model.selectionType, r.model.tournamentSize,
                        r.model.crossoverType, r.model.mutationType,
                        r.popSize, r.pc, r.pm, r.elitism,
                        r.map.fileName, r.map.sizeLabel, r.map.scale, r.map.topology,
                        r.map.prep.numHouses, r.map.prep.totalEmpty, r.map.prep.p, r.numSeeds,
                        r.bestCost, r.meanCost, r.medianCost, r.stdCost, r.worstCost, r.ci95Cost, r.rpdInStepPct,
                        r.meanMaxHouseDist, r.meanAvgHouseDist, r.bestUnreachedHouses,
                        r.meanEvalsTotal, r.meanEvalsToBest, r.meanGensRun, r.maxGenAllowed,
                        r.stagnantStopRate, r.budgetStopRate, r.timeStopRate, r.meanRuntimeMs, r.bestCoords);
            }
        }
    }

    private static void printHeader(Config cfg, List<MapEntry> maps, int numThreads) {
        int totalT2ARuns = cfg.models.size() * (5 * cfg.numSeeds) * 4 * maps.size();
        System.out.println("==========================================================================");
        System.out.println("EKSPERIMEN TAHAP 2 CARA (A) - SEQUENTIAL TUNING (PARALLEL " + numThreads + " THREADS)");
        System.out.println("==========================================================================");
        System.out.println("Jumlah Model             : " + cfg.models.size());
        System.out.println("Jumlah Test Case         : " + maps.size() + " peta");
        System.out.println("Seeds per Kasus          : " + cfg.numSeeds + " (baseSeed=" + cfg.baseSeed + ")");
        System.out.printf(Locale.US, "Tetap dari Tahap 1       : pop=%d, pc=%.2f, pm=%.2f, elitism=%.2f%n",
                cfg.basePop, cfg.basePc, cfg.basePm, cfg.baseElitism);
        System.out.println("Kandidat Step 1 (POP)    : " + cfg.candidatePops);
        System.out.println("Kandidat Step 2 (PC)     : " + cfg.candidatePcs);
        System.out.println("Kandidat Step 3 (PM)     : " + cfg.candidatePms);
        System.out.println("Kandidat Step 4 (ELIT)   : " + cfg.candidateElitisms);
        System.out.println("Total Run Tahap 2A       : " + totalT2ARuns + " runs");
    }

    private static String mapScaleToCategory(int scale) {
        if (scale <= 20) return "tiny";
        if (scale <= 40) return "small";
        return "normal";
    }

    private static int extractScale(String fileName, int fallbackN) {
        Matcher m = Pattern.compile("n(\\d+)", Pattern.CASE_INSENSITIVE).matcher(fileName);
        if (m.find()) return Integer.parseInt(m.group(1));
        return fallbackN;
    }

    private static String extractTopology(String fileName) {
        String lower = fileName.toLowerCase(Locale.US);
        if (lower.contains("acak")) return "acak";
        if (lower.contains("klaster") || lower.contains("cluster")) return "klaster";
        if (lower.contains("labirin")) return "labirin";
        return "lainnya";
    }

    private static List<String> resolveMapPaths(List<String> patterns) throws IOException {
        Set<String> uniquePaths = new LinkedHashSet<>();
        for (String rawPattern : patterns) {
            String pat = rawPattern.trim();
            if (pat.isEmpty()) continue;
            File direct = new File(pat);
            if (direct.isFile()) {
                uniquePaths.add(direct.getPath());
                continue;
            }
            if (direct.isDirectory()) {
                File[] files = direct.listFiles((dir, name) -> name.toLowerCase(Locale.US).endsWith(".txt"));
                if (files != null) {
                    Arrays.sort(files);
                    for (File f : files) uniquePaths.add(f.getPath());
                }
                continue;
            }
            int lastSlash = Math.max(pat.lastIndexOf('/'), pat.lastIndexOf('\\'));
            File dir = (lastSlash >= 0) ? new File(pat.substring(0, lastSlash)) : new File(".");
            String glob = (lastSlash >= 0) ? pat.substring(lastSlash + 1) : pat;
            if (dir.isDirectory()) {
                PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
                File[] files = dir.listFiles();
                if (files != null) {
                    Arrays.sort(files);
                    for (File f : files) {
                        if (f.isFile() && matcher.matches(Paths.get(f.getName()))) {
                            uniquePaths.add(f.getPath());
                        }
                    }
                }
            }
        }
        return new ArrayList<>(uniquePaths);
    }

    private static Selection buildSelection(String type, int k) {
        if ("TOURNAMENT".equalsIgnoreCase(type)) return new TournamentSelection(k);
        if ("ROULETTE".equalsIgnoreCase(type)) return new RouletteWheelSelection();
        throw new IllegalArgumentException("Selection tidak dikenal: " + type);
    }

    private static Crossover buildCrossover(String type) {
        if ("SINGLE_POINT".equalsIgnoreCase(type)) return new SinglePointCrossover();
        if ("TWO_POINT".equalsIgnoreCase(type)) return new TwoPointCrossover();
        throw new IllegalArgumentException("Crossover tidak dikenal: " + type);
    }

    private static Mutation buildMutation(String type) {
        if ("RANDOM_REPLACEMENT".equalsIgnoreCase(type)) return new RandomReplacementMutation();
        if ("TARGETED".equalsIgnoreCase(type)) return new TargetedMutation();
        throw new IllegalArgumentException("Mutation tidak dikenal: " + type);
    }

    private static String formatCoords(int[] selectedIds, Point[] cellCoords) {
        int[] copy = Arrays.copyOf(selectedIds, selectedIds.length);
        Arrays.sort(copy);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < copy.length; i++) {
            int id = copy[i];
            if (i > 0) sb.append(";");
            Point pt = cellCoords[id];
            sb.append("(").append(pt.col + 1).append(",").append(pt.row + 1).append(")");
        }
        return sb.toString();
    }

    public static Config loadOrCreateConfig(String path) throws IOException {
        File file = new File(path);
        if (!file.exists()) {
            writeDefaultTemplate(file);
            System.out.println("[INFO] File konfigurasi dibuat otomatis di: " + path);
        }

        Config cfg = new Config();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                int eq = line.indexOf('=');
                if (eq < 0) continue;

                String rawKey = line.substring(0, eq).trim();
                String key = rawKey.toUpperCase(Locale.US);
                String val = line.substring(eq + 1).trim();
                if (val.isEmpty()) continue;

                if (key.startsWith("MODEL.")) {
                    String modelName = rawKey.substring(6).trim();
                    cfg.models.add(parseModelSpec(modelName, val));
                } else {
                    switch (key) {
                        case "MAPS": cfg.mapPatterns = parseStringList(val); break;
                        case "BASE_POP": cfg.basePop = Integer.parseInt(val); break;
                        case "BASE_PC": cfg.basePc = Double.parseDouble(val); break;
                        case "BASE_PM": cfg.basePm = Double.parseDouble(val); break;
                        case "BASE_ELITISM": cfg.baseElitism = Double.parseDouble(val); break;
                        case "CANDIDATE_POPS": cfg.candidatePops = parseIntList(val); break;
                        case "CANDIDATE_PCS": cfg.candidatePcs = parseDoubleList(val); break;
                        case "CANDIDATE_PMS": cfg.candidatePms = parseDoubleList(val); break;
                        case "CANDIDATE_ELITISMS": cfg.candidateElitisms = parseDoubleList(val); break;
                        case "SELECTION_CRITERION": cfg.selectionCriterion = val.toUpperCase(Locale.US); break;
                        case "EVALUATE_TAHAP1_BASELINE": cfg.evaluateTahap1Baseline = Boolean.parseBoolean(val); break;
                        case "NUM_SEEDS": cfg.numSeeds = Integer.parseInt(val); break;
                        case "BASE_SEED": cfg.baseSeed = Integer.parseInt(val); break;
                        case "MAX_STAGNANT": cfg.maxStagnant = Integer.parseInt(val); break;
                        case "TIME_LIMIT_MS": cfg.timeLimitMs = Long.parseLong(val); break;
                        case "THREADS": cfg.threads = Integer.parseInt(val); break;
                        default:
                            System.err.println("[PERINGATAN] Key tidak dikenal: " + rawKey);
                    }
                }
            }
        }
        return cfg;
    }

    private static ModelSpec parseModelSpec(String name, String specStr) {
        String[] parts = specStr.split("\\|");
        if (parts.length < 5) {
            throw new IllegalArgumentException("Format MODEL." + name + " salah. Gunakan: CHROMOSOME | EVOLUTION | SELECTION | CROSSOVER | MUTATION");
        }
        ModelSpec m = new ModelSpec();
        m.name = name;
        m.chromosomeType = ChromosomeType.valueOf(parts[0].trim().toUpperCase(Locale.US));
        m.evolutionModel = EvolutionModel.valueOf(parts[1].trim().toUpperCase(Locale.US));

        String selPart = parts[2].trim().toUpperCase(Locale.US);
        if (selPart.startsWith("TOURNAMENT")) {
            m.selectionType = "TOURNAMENT";
            int colon = selPart.indexOf(':');
            m.tournamentSize = (colon > 0) ? Integer.parseInt(selPart.substring(colon + 1).trim()) : 4;
        } else {
            m.selectionType = selPart;
            m.tournamentSize = 0;
        }

        m.crossoverType = parts[3].trim().toUpperCase(Locale.US);
        m.mutationType = parts[4].trim().toUpperCase(Locale.US);
        return m;
    }

    private static void writeDefaultTemplate(File file) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("# ==========================================================================");
            pw.println("# KONFIGURASI TAHAP 2 CARA (A) - SEQUENTIAL TUNING");
            pw.println("# ==========================================================================");
            pw.println();
            pw.println("# 1. MINIMAL 3 MODEL TERBAIK DARI TAHAP 1");
            pw.println("MODEL.Model1 = ARRAY_FS | GENERATIONAL | TOURNAMENT:4 | SINGLE_POINT | RANDOM_REPLACEMENT");
            pw.println("MODEL.Model2 = ARRAY_FS | GENERATIONAL | TOURNAMENT:4 | TWO_POINT | RANDOM_REPLACEMENT");
            pw.println("MODEL.Model3 = ARRAY_FS | GENERATIONAL | ROULETTE | TWO_POINT | TARGETED");
            pw.println();
            pw.println("# 2. TEST CASE TAHAP 2 (HARUS BERBEDA DARI TAHAP 1!)");
            pw.println("MAPS = TestCases/n20_acak_03.txt, TestCases/n20_acak_04.txt, TestCases/n20_klaster_02.txt, TestCases/n20_labirin_02.txt, TestCases/n40_acak_03.txt, TestCases/n40_acak_04.txt, TestCases/n40_klaster_02.txt, TestCases/n40_labirin_02.txt, TestCases/n80_acak_03.txt, TestCases/n80_acak_04.txt, TestCases/n80_klaster_02.txt, TestCases/n80_labirin_02.txt");
            pw.println();
            pw.println("# 3. HYPER-PARAMETER TETAP DARI TAHAP 1");
            pw.println("BASE_POP = 100");
            pw.println("BASE_PC = 0.80");
            pw.println("BASE_PM = 0.05");
            pw.println("BASE_ELITISM = 0.20");
            pw.println();
            pw.println("# 4. 5 ANGKA KANDIDAT DI LUAR ANGKA TAHAP 1");
            pw.println("CANDIDATE_POPS = 30, 50, 200, 300, 500");
            pw.println("CANDIDATE_PCS = 0.60, 0.70, 0.75, 0.85, 0.95");
            pw.println("CANDIDATE_PMS = 0.01, 0.02, 0.10, 0.20, 0.30");
            pw.println("CANDIDATE_ELITISMS = 0.05, 0.10, 0.15, 0.25, 0.30");
            pw.println();
            pw.println("# 5. PENGATURAN EKSEKUSI");
            pw.println("SELECTION_CRITERION = MEAN_RPD");
            pw.println("EVALUATE_TAHAP1_BASELINE = true");
            pw.println("NUM_SEEDS = 30");
            pw.println("BASE_SEED = 24056");
            pw.println("MAX_STAGNANT = 200");
            pw.println("TIME_LIMIT_MS = 15000");
            pw.println("THREADS = 0");
        }
    }

    private static List<String> parseStringList(String val) {
        List<String> list = new ArrayList<>();
        for (String part : val.split(",")) {
            String s = part.trim();
            if (!s.isEmpty()) list.add(s);
        }
        return list;
    }

    private static List<Integer> parseIntList(String val) {
        List<Integer> list = new ArrayList<>();
        for (String part : val.split(",")) {
            String s = part.trim();
            if (!s.isEmpty()) list.add(Integer.parseInt(s));
        }
        return list;
    }

    private static List<Double> parseDoubleList(String val) {
        List<Double> list = new ArrayList<>();
        for (String part : val.split(",")) {
            String s = part.trim();
            if (!s.isEmpty()) list.add(Double.parseDouble(s));
        }
        return list;
    }
}