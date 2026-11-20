# Stage 2A — Sequential Hyperparameter Tuning (OFAT)

Harness eksperimen Stage 2A untuk GA fire station. Urutan tuning:
**pop -> pc -> pm -> elitism** (one-factor-at-a-time). Pemenang tiap step
dibekukan sebagai baseline step berikutnya.

- 3 model terbaik Stage 1 (ID lewat CLI, tidak hardcode)
- 5 nilai kandidat per hyperparameter
- 3 ukuran: `tiny` (n10, p=3), `small` (n20, p=10), `normal` (n40, p=24)
  — p di-override dari header test case
- 4 peta per ukuran: 2 acak, 1 klaster, 1 labirin (peta BARU, bukan peta Stage 1)
- 30 seed dari `experiments/stage1/seeds_30.txt` (apa adanya, tidak diacak ulang)
- Per step: 3 model x 5 nilai x 3 size x 4 peta x 30 seed = **5400 run**
  (15 task paralel; tiap task 360 run)
- `maxGen = budget / pop`, budget dari `Main.budgetFromSize`
  (tiny=10000, small=50000, normal=200000), stagnasi tetap `Main.MAX_STAGNANT=200`
- Semua run lewat `Main.loadAndPreprocess` + `Main.runGA` (tidak ada duplikasi GA)
- **Tidak membaca `config.txt`** (isinya pop=1000, bukan baseline)

## PENTING: asumsi rekonstruksi Stage 1

Checkout ini (branch `main`) TIDAK memuat `experiments/stage1/ExperimentStage1.java`,
`run_stage1.py`, atau `seeds_30.txt` asli. Yang tersedia hanya branch
`origin/Experiment_Stage1` (`Stage1Automation.java` + `stage1results/stage1ranks`).
Harness ini dibangun terhadap `Main.java` di `main` (operator via field statis),
dengan dua rekonstruksi yang perlu kamu ketahui:

1. **Seed** — `experiments/stage1/seeds_30.txt` dibuat ulang PERSIS dengan
   algoritma `Stage1Automation.createSeeds()`: `Random(24056)`, `nextInt(65537)`,
   dedup sampai 30 unik. Diverifikasi identik dengan varian tanpa dedup
   (`Main.runExperiment`): 30 draw pertama semuanya unik, jadi kedua varian
   menghasilkan daftar yang sama. Jika kamu punya file seeds asli yang berbeda,
   timpa file ini sebelum run.
2. **Model ID** — tabel di bawah mengikuti urutan loop `Stage1Automation`
   (chromosome ARRAY_FS dulu, lalu BIT_STRING; selection tournament(4) lalu
   roulette; crossover single lalu two; mutation random_replacement lalu
   targeted). Jika numbering di `ExperimentStage1.java` aslimu berbeda,
   sesuaikan urutan di `buildModels()` (`ExperimentStage2A.java`) atau
   petakan ID-nya saat memanggil CLI.

### Tabel model ID (urutan loop Stage1Automation) + peringkat Stage 1

| ID | Chromosome | Selection | Crossover | Mutation | Rank Stage 1 |
|----|------------|-----------|-----------|----------|--------------|
| 1  | ARRAY_FS   | tournament_4 | single_point | random_replacement | 1 |
| 2  | ARRAY_FS   | tournament_4 | single_point | targeted | 11 |
| 3  | ARRAY_FS   | tournament_4 | two_point | random_replacement | 2 |
| 4  | ARRAY_FS   | tournament_4 | two_point | targeted | 9 |
| 5  | ARRAY_FS   | roulette | single_point | random_replacement | 4 |
| 6  | ARRAY_FS   | roulette | single_point | targeted | 8 |
| 7  | ARRAY_FS   | roulette | two_point | random_replacement | 6 |
| 8  | ARRAY_FS   | roulette | two_point | targeted | 3 |
| 9  | BIT_STRING | tournament_4 | single_point | random_replacement | 13 |
| 10 | BIT_STRING | tournament_4 | single_point | targeted | 12 |
| 11 | BIT_STRING | tournament_4 | two_point | random_replacement | 14 |
| 12 | BIT_STRING | tournament_4 | two_point | targeted | 10 |
| 13 | BIT_STRING | roulette | single_point | random_replacement | 15 |
| 14 | BIT_STRING | roulette | single_point | targeted | 7 |
| 15 | BIT_STRING | roulette | two_point | random_replacement | 16 |
| 16 | BIT_STRING | roulette | two_point | targeted | 5 |

**3 model terbaik Stage 1 (rank 1,2,3) = ID `1,3,8`.**
Peringkat diambil dari `stage1results/stage1ranks` di branch `origin/Experiment_Stage1`.

## File

```
experiments/stage1/seeds_30.txt          # 30 seed (rekonstruksi, lihat atas)
experiments/stage2/ExperimentStage2A.java # harness Java (1 task = size x value)
experiments/stage2/run_stage2a.py         # runner paralel + resume + log live
experiments/stage2/analyze_stage2a.py     # agregasi + ranking + CSV + PNG
experiments/stage2/maps/gen_tiny_maps.py  # generator 4 peta tiny (deterministik)
experiments/stage2/maps/tiny_*.txt        # 4 peta tiny baru
experiments/stage2/build/                 # hasil compile (.class, di-ignore git)
experiments/stage2/results/stage2a_<tag>.csv
experiments/stage2/results/logs/
```

Peta yang dipakai:
- tiny: `experiments/stage2/maps/tiny_acak_03.txt`, `tiny_acak_04.txt`,
  `tiny_klaster_02.txt`, `tiny_labirin_02.txt`
- small: `TestCases/n20_acak_03.txt`, `n20_acak_04.txt`, `n20_klaster_02.txt`, `n20_labirin_02.txt`
- normal: `TestCases/n40_acak_03.txt`, `n40_acak_04.txt`, `n40_klaster_02.txt`, `n40_labirin_02.txt`

## Compile (sekali; runner juga meng-compile otomatis)

Dari root repo:

```bash
javac -d experiments/stage2/build \
  Main.java BFS.java model/*.java crossover/*.java mutation/*.java selection/*.java \
  experiments/stage2/ExperimentStage2A.java
```

## Dry-run (wajib sebelum run nyata)

```bash
python3 -u experiments/stage2/run_stage2a.py \
  --step pop --values 50,100,200,300,500 --models 1,3,8 \
  --pc 0.8 --pm 0.05 --elitism 0.2 --dry-run
```

Print 15 task + total 5400 run, tanpa compile/eksekusi.
Hyperparameter yang di-fix WAJIB diberikan (contoh di atas: `--pc --pm --elitism`
untuk step pop). Angka baseline contoh (100/0.8/0.05/0.2) diambil dari
`tahap2a_input.txt` branch lama — **ganti dengan baseline Stage 1 kelompokmu**.

## Run step 1-4 (jalankan berurutan, dalam tmux)

```bash
tmux new -s stage2a
```

Step 1 — pop (baseline pc/pm/elitism dari Stage 1):

```bash
python3 -u experiments/stage2/run_stage2a.py \
  --step pop --values 50,100,200,300,500 --models 1,3,8 \
  --pc 0.8 --pm 0.05 --elitism 0.2 --workers 4 --tag pop
```

Analisis, catat value terbaik (mis. pop=200), lalu Step 2 — pc:

```bash
python3 -u experiments/stage2/analyze_stage2a.py \
  experiments/stage2/results/stage2a_pop.csv --step pop

python3 -u experiments/stage2/run_stage2a.py \
  --step pc --values 0.60,0.70,0.75,0.85,0.95 --models 1,3,8 \
  --pop 200 --pm 0.05 --elitism 0.2 --workers 4 --tag pc
```

Step 3 — pm (pakai pop & pc terbaik):

```bash
python3 -u experiments/stage2/analyze_stage2a.py \
  experiments/stage2/results/stage2a_pc.csv --step pc

python3 -u experiments/stage2/run_stage2a.py \
  --step pm --values 0.01,0.02,0.10,0.20,0.30 --models 1,3,8 \
  --pop 200 --pc 0.75 --elitism 0.2 --workers 4 --tag pm
```

Step 4 — elitism (pakai pop, pc, pm terbaik):

```bash
python3 -u experiments/stage2/analyze_stage2a.py \
  experiments/stage2/results/stage2a_pm.csv --step pm

python3 -u experiments/stage2/run_stage2a.py \
  --step elitism --values 0.05,0.10,0.15,0.25,0.30 --models 1,3,8 \
  --pop 200 --pc 0.75 --pm 0.10 --workers 4 --tag elitism

python3 -u experiments/stage2/analyze_stage2a.py \
  experiments/stage2/results/stage2a_elitism.csv --step elitism
```

Angka `--values` di atas contoh (kandidat dari branch lama); ganti sesuai
keputusan kelompok. `--models` bisa urutan apa pun, mis. `7,4,5`.

## Resume & mengulang

- **Resume default ON**: harness Java membaca CSV tag terkait dan melewati
  kombinasi `(size, pop, pc, pm, elitism, model, map, seed)` yang sudah ada
  (ekuivalen `(step, size, value, model, map, seed)` karena satu CSV per step).
  Aman melanjutkan run yang terputus: jalankan command yang sama persis.
- `--no-resume`: mengosongkan CSV tag ini dulu, lalu run ulang dari nol.
- Beberapa proses Java append ke CSV yang sama dengan satu `write()` per baris
  (O_APPEND, atomik di Linux) + flush tiap baris.
- Progress live di terminal berprefix `[task <size> <value>]`; per run:
  `[size pop pc pm elitism model map seed i/n] mean=... eval=... elapsed_ms=...`
  (`mean` = rata-rata cost berjalan untuk model+peta itu dalam task).

## tmux & tail log

```bash
tmux new -s stage2a        # buat session
# jalankan command run di dalamnya
# Ctrl-b lalu d            # detach (proses tetap jalan)
tmux attach -t stage2a     # attach lagi

tail -f experiments/stage2/results/logs/pop_runner.log   # ringkasan runner
tail -f experiments/stage2/results/logs/pop_tiny_50.log  # satu task
ls experiments/stage2/results/logs/
```

## Analisis

```bash
python3 -u experiments/stage2/analyze_stage2a.py \
  experiments/stage2/results/stage2a_pop.csv --step pop
```

Output di `experiments/stage2/results/analysis_pop/`:
- `stage2a_pop_summary.csv` — mean/std/95% CI per (value, model, size) + rank
- `stage2a_pop_best.csv` — value terbaik per size (pooled antar model)
- `stage2a_pop_curves.png` — kurva mean ± CI95 per model, subplot per size
- `stage2a_pop_pooled.png` — bar pooled mean per value

CI95 = mean ± 1.96·std/√n (aproksimasi normal; n normalnya 120 per grup).
Butuh `matplotlib` (sudah terpasang di VPS ini via `pip install --user`;
jika pindah mesin: `python3 -m pip install --user matplotlib`).

## Regenerasi peta tiny (opsional, deterministik)

```bash
python3 experiments/stage2/maps/gen_tiny_maps.py
```

## Batasan yang dihormati harness ini

- Tidak mengubah `Main.java`, `model/GeneticAlgorithm.java`, operator,
  atau file Stage 1 yang sudah ada (seeds_30.txt dibuat baru karena belum ada).
- Tidak membaca/mengubah `config.txt`.
- Tidak ada `git push`/commit; hasil CSV & log tetap di working tree lokal.
- java/javac dideteksi dari PATH (Linux), bukan hardcode Windows.
