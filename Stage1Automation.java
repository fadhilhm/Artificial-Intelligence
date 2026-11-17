import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import crossover.Crossover;
import crossover.SinglePointCrossover;
import crossover.TwoPointCrossover;
import model.Individual;
import model.Individual.ChromosomeType;
import mutation.Mutation;
import mutation.RandomReplacementMutation;
import mutation.TargetedMutation;
import selection.RouletteWheelSelection;
import selection.Selection;
import selection.TournamentSelection;

final class Stage1Automation {
    private static final String[] MAP_FILES = {
            "TestCases/n10_acak_01.txt",
            "TestCases/n10_acak_02.txt",
            "TestCases/n10_klaster_01.txt",
            "TestCases/n10_labirin_01.txt",

            "TestCases/n20_acak_01.txt",
            "TestCases/n20_acak_02.txt",
            "TestCases/n20_klaster_01.txt",
            "TestCases/n20_labirin_01.txt",

            "TestCases/n40_acak_01.txt",
            "TestCases/n40_acak_02.txt",
            "TestCases/n40_klaster_01.txt",
            "TestCases/n40_labirin_01.txt"
    };

    static void run(Main.Config cfg, String outputDirectory) throws Exception {
        File outputDir = new File(outputDirectory);
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Could not create output directory: " + outputDirectory);
        }
        if (!outputDir.isDirectory()) {
            throw new IOException("Output path is not a directory: " + outputDirectory);
        }

        int[] seeds = createSeeds();
        ChromosomeType[] representations = {
                ChromosomeType.ARRAY_FS,
                ChromosomeType.BIT_STRING
        };
        String[] selectionNames = {"tournament", "roulette"};
        String[] crossoverNames = {"single_point", "two_point"};
        String[] mutationNames = {"random_replacement", "targeted"};

        for (ChromosomeType representation : representations) {
            for (int selectionIndex = 0; selectionIndex < selectionNames.length; selectionIndex++) {
                Selection selection = selectionIndex == 0
                        ? new TournamentSelection(4)
                        : new RouletteWheelSelection();
                for (int crossoverIndex = 0; crossoverIndex < crossoverNames.length; crossoverIndex++) {
                    Crossover crossover = crossoverIndex == 0
                            ? new SinglePointCrossover()
                            : new TwoPointCrossover();
                    for (int mutationIndex = 0; mutationIndex < mutationNames.length; mutationIndex++) {
                        Mutation mutation = mutationIndex == 0
                                ? new RandomReplacementMutation()
                                : new TargetedMutation();
                        RunStats stats = new RunStats();
                        File outputFile = new File(outputDir,
                                "stage1__chromosome-" + representation
                                        + "__selection-" + selectionNames[selectionIndex]
                                        + "__crossover-" + crossoverNames[crossoverIndex]
                                        + "__mutation-" + mutationNames[mutationIndex] + ".csv");

                        for (String path : MAP_FILES) {
                            Main.Preprocessed prep = Main.loadAndPreprocess(path);
                            int maxGen = Main.budgetFromSize(prep.n) / cfg.populationSize;

                            for (int seed : seeds) {
                                Individual best = Main.runGA(prep, cfg.populationSize,
                                        cfg.pc, cfg.pm, cfg.elitism, maxGen, seed,
                                        representation, selection, crossover, mutation);
                                stats.add(best.getCost());
                            }
                        }

                        try (PrintWriter out = new PrintWriter(outputFile)) {
                            out.println("Chromosome,Selection,Crossover,Mutation,Mean cost");
                            out.printf(Locale.US, "%s,%s,%s,%s,%.6f%n",
                                    representation, selectionNames[selectionIndex],
                                    crossoverNames[crossoverIndex], mutationNames[mutationIndex],
                                    stats.sumCost / stats.count);
                        }
                        System.out.println("Wrote " + outputFile.getPath());
                    }
                }
            }
        }
    }

    private static int[] createSeeds() {
        int[] seeds = new int[Main.NUM_OF_SEEDS];
        Set<Integer> uniqueSeeds = new HashSet<>();
        Random random = new Random(Main.SEED);
        int index = 0;
        while (index < seeds.length) {
            int seed = random.nextInt(65537);
            if (uniqueSeeds.add(seed)) {
                seeds[index++] = seed;
            }
        }
        return seeds;
    }

    private static class RunStats {
        int count;
        double sumCost;

        void add(double cost) {
            count++;
            sumCost += cost;
        }
    }
}
