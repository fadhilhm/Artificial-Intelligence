#!/usr/bin/env python3
"""Runner eksperimen Stage 2A (one-factor-at-a-time: pop -> pc -> pm -> elitism).

Contoh (dry-run dulu):
  python3 -u experiments/stage2/run_stage2a.py --step pop \
      --values 50,100,200,300,500 --models 1,3,8 \
      --pc 0.8 --pm 0.05 --elitism 0.2 --dry-run

Run nyata (lanjutkan tanpa --dry-run):
  python3 -u experiments/stage2/run_stage2a.py --step pop \
      --values 50,100,200,300,500 --models 1,3,8 \
      --pc 0.8 --pm 0.05 --elitism 0.2 --workers 4 --tag pop

Perilaku:
  - Compile sekali (javac dari PATH) ke experiments/stage2/build/.
  - Task per (size, value): 3 size x 5 value = 15 task, paralel --workers.
  - Satu task = len(models) x 4 peta x seeds run (default 3x4x30 = 360).
  - stdout tiap proses Java di-stream LIVE ke terminal (Popen + reader
    thread) dengan prefix [task size value], dan di-tee ke
    experiments/stage2/results/logs/<tag>_<size>_<value>.log.
  - CSV append: experiments/stage2/results/stage2a_<tag>.csv.
    Resume default ON: harness Java melewati kombinasi yang sudah ada.
    --no-resume mengosongkan CSV tag ini sebelum run (mulai segar).
  - Hyperparameter yang di-fix WAJIB diberikan lewat flag (--pop/--pc/
    --pm/--elitism); nilai step di-supply lewat --values (5 nilai).
  - Tidak membaca config.txt. Seed dibaca apa adanya dari
    experiments/stage1/seeds_30.txt oleh harness Java.
"""
import argparse
import shutil
import subprocess
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STAGE2 = ROOT / "experiments" / "stage2"
BUILD = STAGE2 / "build"
RESULTS = STAGE2 / "results"
LOGS = RESULTS / "logs"
SEEDS_FILE = ROOT / "experiments" / "stage1" / "seeds_30.txt"

SIZES = ["tiny", "small", "normal"]
PARAMS = ["pop", "pc", "pm", "elitism"]
MAPS_PER_SIZE = 4
N_VALUES = 5

PRINT_LOCK = threading.Lock()
PROCS_LOCK = threading.Lock()
PROCS = []


def log(msg):
    with PRINT_LOCK:
        print(msg, flush=True)


def parse_args():
    ap = argparse.ArgumentParser(
        description="Runner Stage 2A GA fire station (OFAT: pop, pc, pm, elitism)")
    ap.add_argument("--step", required=True, choices=PARAMS,
                    help="hyperparameter yang divariasikan pada step ini")
    ap.add_argument("--values", required=True,
                    help="5 nilai kandidat dipisah koma, contoh 50,100,200,300,500")
    ap.add_argument("--models", required=True,
                    help="model ID Stage 1 dipisah koma, contoh 7,4,5 (lihat README)")
    ap.add_argument("--seeds", type=int, default=30,
                    help="jumlah seed dari depan seeds_30.txt (default 30)")
    ap.add_argument("--workers", type=int, default=4,
                    help="proses Java paralel (default 4)")
    ap.add_argument("--tag", default=None,
                    help="tag nama file CSV/log (default: nama step)")
    ap.add_argument("--pop", type=int, default=None, help="pop tetap (baseline)")
    ap.add_argument("--pc", type=float, default=None, help="pc tetap (baseline)")
    ap.add_argument("--pm", type=float, default=None, help="pm tetap (baseline)")
    ap.add_argument("--elitism", type=float, default=None, help="elitism tetap (baseline)")
    ap.add_argument("--dry-run", action="store_true",
                    help="print daftar task + total run, lalu exit (tanpa compile/run)")
    ap.add_argument("--no-resume", action="store_true",
                    help="kosongkan CSV tag ini sebelum run (default: resume ON)")
    return ap.parse_args()


def parse_values(step, raw):
    toks = [t.strip() for t in raw.split(",") if t.strip()]
    if not toks:
        sys.exit("ERROR: --values kosong")
    vals = []
    for t in toks:
        try:
            v = int(t) if step == "pop" else float(t)
        except ValueError:
            sys.exit(f"ERROR: nilai '{t}' tidak valid untuk step {step}")
        if step == "pop" and v <= 0:
            sys.exit(f"ERROR: pop harus > 0, dapat {v}")
        if step != "pop" and not (0.0 <= v <= 1.0):
            sys.exit(f"ERROR: {step} harus di [0,1], dapat {v}")
        vals.append(v)
    return vals


def parse_models(raw):
    ids = []
    for t in raw.split(","):
        t = t.strip()
        if not t:
            continue
        try:
            i = int(t)
        except ValueError:
            sys.exit(f"ERROR: model ID '{t}' bukan integer")
        if not (1 <= i <= 16):
            sys.exit(f"ERROR: model ID di luar 1..16: {i}")
        if i in ids:
            sys.exit(f"ERROR: model ID duplikat: {i}")
        ids.append(i)
    if not ids:
        sys.exit("ERROR: --models kosong")
    return ids


def validate_fixed(args):
    missing = [p for p in PARAMS if p != args.step and getattr(args, p) is None]
    if missing:
        sys.exit("ERROR: hyperparameter yang di-fix wajib diberikan: "
                 + ", ".join("--" + m for m in missing)
                 + f" (step {args.step} divariasikan lewat --values)")
    if args.pc is not None and not (0 <= args.pc <= 1):
        sys.exit("ERROR: --pc harus di [0,1]")
    if args.pm is not None and not (0 <= args.pm <= 1):
        sys.exit("ERROR: --pm harus di [0,1]")
    if args.elitism is not None and not (0 <= args.elitism <= 1):
        sys.exit("ERROR: --elitism harus di [0,1]")
    if args.pop is not None and args.pop <= 0:
        sys.exit("ERROR: --pop harus > 0")


def resolve_params(args, value):
    p = {"pop": args.pop, "pc": args.pc, "pm": args.pm, "elitism": args.elitism}
    p[args.step] = value
    return p


def fmt_param(step, v):
    return str(v) if step == "pop" else repr(v)


def count_seeds():
    if not SEEDS_FILE.exists():
        sys.exit(f"ERROR: file seed tidak ditemukan: {SEEDS_FILE}")
    lines = [l.strip() for l in SEEDS_FILE.read_text().splitlines()
             if l.strip() and not l.strip().startswith("#")]
    return len(lines)


def compile_once():
    javac = shutil.which("javac")
    if not javac:
        sys.exit("ERROR: javac tidak ditemukan di PATH")
    sources = ["Main.java", "BFS.java"]
    for pkg in ("model", "crossover", "mutation", "selection"):
        sources += sorted(str(p.relative_to(ROOT)) for p in (ROOT / pkg).glob("*.java"))
    sources.append("experiments/stage2/ExperimentStage2A.java")
    BUILD.mkdir(parents=True, exist_ok=True)
    cmd = [javac, "-d", str(BUILD)] + sources
    log("[compile] " + " ".join(cmd))
    t0 = time.time()
    r = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True)
    if r.returncode != 0:
        log(r.stdout)
        log(r.stderr)
        sys.exit(f"ERROR: compile gagal (exit {r.returncode})")
    log(f"[compile] OK ({time.time() - t0:.1f}s) -> {BUILD}")


def stream_task(size, value, args, models_str, out_csv, tag):
    java = shutil.which("java")
    if not java:
        sys.exit("ERROR: java tidak ditemukan di PATH")
    p = resolve_params(args, value)
    label = f"[task {size} {fmt_param(args.step, value)}]"
    cmd = [java, "-cp", str(BUILD), "ExperimentStage2A",
           size, str(p["pop"]), repr(p["pc"]), repr(p["pm"]), repr(p["elitism"]),
           models_str, str(args.seeds), str(out_csv)]
    log_path = LOGS / f"{tag}_{size}_{fmt_param(args.step, value)}.log"
    t0 = time.time()
    with open(log_path, "a", encoding="utf-8") as lf:
        lf.write(f"# {time.strftime('%Y-%m-%d %H:%M:%S')} cmd: {' '.join(cmd)}\n")
        lf.flush()
        proc = subprocess.Popen(cmd, cwd=ROOT, stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, text=True, bufsize=1)
        if proc.stdout is None:
            raise RuntimeError("stdout pipe tidak tersedia")
        with PROCS_LOCK:
            PROCS.append(proc)

        def reader():
            for line in proc.stdout:
                line = line.rstrip("\n")
                log(f"{label} {line}")
                lf.write(line + "\n")
                lf.flush()

        th = threading.Thread(target=reader, daemon=True)
        th.start()
        rc = proc.wait()
        th.join(timeout=30)
        with PROCS_LOCK:
            PROCS.remove(proc)
        lf.write(f"# exit={rc} elapsed={time.time() - t0:.1f}s\n")
    status = "OK" if rc == 0 else f"GAGAL(exit={rc})"
    log(f"{label} selesai: {status} ({time.time() - t0:.1f}s) log={log_path}")
    return size, value, rc


def main():
    args = parse_args()
    validate_fixed(args)
    values = parse_values(args.step, args.values)
    models = parse_models(args.models)
    models_str = ",".join(str(m) for m in models)
    tag = args.tag or args.step
    out_csv = RESULTS / f"stage2a_{tag}.csv"
    n_seeds_avail = count_seeds()
    if args.seeds < 1 or args.seeds > n_seeds_avail:
        sys.exit(f"ERROR: --seeds harus 1..{n_seeds_avail} (tersedia di {SEEDS_FILE})")
    if args.workers < 1:
        sys.exit("ERROR: --workers harus >= 1")

    runs_per_task = len(models) * MAPS_PER_SIZE * args.seeds
    tasks = [(size, v) for size in SIZES for v in values]
    total_runs = len(tasks) * runs_per_task

    log(f"Stage 2A runner: step={args.step} tag={tag} models={models_str} "
        f"seeds={args.seeds} workers={args.workers}")
    log(f"  values={values}")
    fixed = {p: getattr(args, p) for p in PARAMS if p != args.step}
    log(f"  fixed={fixed}")
    log(f"  out_csv={out_csv}")
    log(f"  task={len(tasks)} (3 size x {len(values)} value), "
        f"run/task={runs_per_task}, total_run={total_runs}")

    if args.dry_run:
        log("[dry-run] daftar task:")
        for size, v in tasks:
            p = resolve_params(args, v)
            log(f"  size={size:6s} {args.step}={fmt_param(args.step, v):>6s} "
                f"pop={p['pop']} pc={p['pc']} pm={p['pm']} elitism={p['elitism']} "
                f"-> {runs_per_task} run")
        log(f"[dry-run] total {total_runs} run. Tidak ada compile/eksekusi.")
        return 0

    RESULTS.mkdir(parents=True, exist_ok=True)
    LOGS.mkdir(parents=True, exist_ok=True)
    compile_once()

    if args.no_resume:
        if out_csv.exists():
            out_csv.write_text("", encoding="utf-8")
            log(f"[no-resume] CSV dikosongkan: {out_csv}")
    elif out_csv.exists():
        n_rows = max(0, sum(1 for _ in out_csv.open(encoding="utf-8")) - 1)
        log(f"[resume] {n_rows} baris sudah ada di {out_csv}; kombinasi yang sama akan di-skip")

    runner_log = LOGS / f"{tag}_runner.log"
    t_start = time.time()
    results = []
    try:
        with ThreadPoolExecutor(max_workers=args.workers) as ex:
            futs = {ex.submit(stream_task, size, v, args, models_str, out_csv, tag): (size, v)
                    for size, v in tasks}
            for fut in as_completed(futs):
                results.append(fut.result())
    except KeyboardInterrupt:
        log("[runner] KeyboardInterrupt: menghentikan proses Java...")
        with PROCS_LOCK:
            for proc in PROCS:
                proc.terminate()
        raise

    failed = [(s, v, rc) for s, v, rc in results if rc != 0]
    elapsed = time.time() - t_start
    with open(runner_log, "a", encoding="utf-8") as rf:
        rf.write(f"{time.strftime('%Y-%m-%d %H:%M:%S')} step={args.step} tag={tag} "
                 f"tasks={len(results)} failed={len(failed)} elapsed={elapsed:.1f}s\n")
    log(f"[runner] selesai: {len(results) - len(failed)}/{len(results)} task OK, "
        f"{elapsed:.1f}s, csv={out_csv}")
    if failed:
        for s, v, rc in failed:
            log(f"[runner] GAGAL: size={s} value={v} exit={rc}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
