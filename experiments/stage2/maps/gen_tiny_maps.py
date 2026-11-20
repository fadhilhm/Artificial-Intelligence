#!/usr/bin/env python3
"""Generator 4 peta tiny (10x10) untuk Stage 2A.

Menghasilkan:
  tiny_acak_03.txt, tiny_acak_04.txt  -> rumah tersebar acak
  tiny_klaster_02.txt                 -> rumah dalam 2-3 klaster
  tiny_labirin_02.txt                 -> dinding 'T' membentuk koridor/labirin

Format sama dengan TestCases/: baris header "m n p", lalu grid per baris.
Karakter: '.' kosong, 'H' rumah, 'T' pohon/tembok (lihat BFS.java).
Header p=3 (tetap di-override oleh harness Stage 2A: tiny=3).

Deterministik: seed RNG tetap per peta, jadi hasil bisa direproduksi.
Validasi: semua sel '.' terhubung (4-arah) dan tiap 'H' bersebelahan
dengan komponen kosong tersebut, supaya tidak ada rumah yang penalti.

Jalankan dari root repo:  python3 experiments/stage2/maps/gen_tiny_maps.py
"""
import random
from pathlib import Path

HERE = Path(__file__).resolve().parent
M = N = 10
P_HEADER = 3


def neighbors(r, c):
    for dr, dc in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        nr, nc = r + dr, c + dc
        if 0 <= nr < M and 0 <= nc < N:
            yield nr, nc


def validate(grid):
    empties = [(r, c) for r in range(M) for c in range(N) if grid[r][c] == '.']
    if len(empties) < P_HEADER:
        return False
    # BFS antar sel kosong: harus satu komponen
    seen = {empties[0]}
    queue = [empties[0]]
    while queue:
        r, c = queue.pop()
        for nr, nc in neighbors(r, c):
            if grid[nr][nc] == '.' and (nr, nc) not in seen:
                seen.add((nr, nc))
                queue.append((nr, nc))
    if len(seen) != len(empties):
        return False
    # tiap rumah harus punya akses ke komponen kosong
    for r in range(M):
        for c in range(N):
            if grid[r][c] == 'H':
                if not any(grid[nr][nc] == '.' for nr, nc in neighbors(r, c)):
                    return False
    return True


def blank():
    return [['.' for _ in range(N)] for _ in range(M)]


def place(grid, ch, count, rng, avoid=()):
    cells = [(r, c) for r in range(M) for c in range(N)
             if grid[r][c] == '.' and (r, c) not in avoid]
    rng.shuffle(cells)
    for r, c in cells[:count]:
        grid[r][c] = ch
    return cells[:count]


def gen_acak(rng):
    grid = blank()
    place(grid, 'T', rng.randint(10, 14), rng)
    place(grid, 'H', rng.randint(12, 16), rng)
    return grid


def gen_klaster(rng):
    grid = blank()
    # 3 pusat klaster di wilayah berbeda
    centers = [(rng.randint(1, 3), rng.randint(1, 3)),
               (rng.randint(1, 3), rng.randint(6, 8)),
               (rng.randint(6, 8), rng.randint(3, 6))]
    houses = []
    for cr, cc in centers:
        blob = [(cr, cc)]
        for dr in (-1, 0, 1):
            for dc in (-1, 0, 1):
                if (dr or dc) and rng.random() < 0.55:
                    blob.append((cr + dr, cc + dc))
        for r, c in blob:
            if 0 <= r < M and 0 <= c < N and grid[r][c] == '.':
                grid[r][c] = 'H'
                houses.append((r, c))
    place(grid, 'T', rng.randint(8, 12), rng, avoid=set(houses))
    return grid


def components(grid):
    """Komponen terhubung sel '.' (4-arah)."""
    unseen = {(r, c) for r in range(M) for c in range(N) if grid[r][c] == '.'}
    comps = []
    while unseen:
        start = next(iter(unseen))
        seen = {start}
        queue = [start]
        unseen.discard(start)
        while queue:
            r, c = queue.pop()
            for nr, nc in neighbors(r, c):
                if grid[nr][nc] == '.' and (nr, nc) in unseen:
                    unseen.discard((nr, nc))
                    seen.add((nr, nc))
                    queue.append((nr, nc))
        comps.append(seen)
    return comps


def repair_connectivity(grid, rng):
    """Bobol dinding 'T' secukupnya sampai semua sel '.' satu komponen."""
    for _ in range(M * N):
        comps = components(grid)
        if len(comps) <= 1:
            return
        label = {}
        for i, comp in enumerate(comps):
            for cell in comp:
                label[cell] = i
        # cari sel T yang menjembatani dua komponen berbeda
        bridges = []
        for r in range(M):
            for c in range(N):
                if grid[r][c] != 'T':
                    continue
                labels = {label[(nr, nc)] for nr, nc in neighbors(r, c)
                          if (nr, nc) in label}
                if len(labels) >= 2:
                    bridges.append((r, c))
        if bridges:
            r, c = rng.choice(bridges)
        else:
            # tidak ada jembatan langsung: bobol T yang menempel komponen mana pun
            touches = [(r, c) for r in range(M) for c in range(N)
                       if grid[r][c] == 'T'
                       and any((nr, nc) in label for nr, nc in neighbors(r, c))]
            if not touches:
                return
            r, c = rng.choice(touches)
        grid[r][c] = '.'


def place_houses_safe(grid, count, rng):
    """Tempatkan H satu per satu; batalkan jika memutus konektivitas sel '.'."""
    spots = [(r, c) for r in range(M) for c in range(N) if grid[r][c] == '.']
    rng.shuffle(spots)
    placed = 0
    for r, c in spots:
        if placed >= count:
            break
        grid[r][c] = 'H'
        if validate(grid):
            placed += 1
        else:
            grid[r][c] = '.'
    return placed


def gen_labirin(rng):
    grid = blank()
    # segmen dinding horizontal & vertikal (gaya labirin TestCases:
    # tembok parsial dengan celah, bukan maze sempurna)
    for r in (2, 5, 8):
        for _ in range(rng.randint(1, 2)):
            length = rng.randint(3, 6)
            start = rng.randint(0, N - length)
            for c in range(start, start + length):
                grid[r][c] = 'T'
    for c in (3, 6):
        for _ in range(rng.randint(1, 2)):
            length = rng.randint(3, 5)
            start = rng.randint(0, M - length)
            for r in range(start, start + length):
                if grid[r][c] != 'T':
                    grid[r][c] = 'T'
    repair_connectivity(grid, rng)
    place_houses_safe(grid, rng.randint(11, 14), rng)
    return grid


def write_map(name, grid):
    path = HERE / name
    lines = [f"{M} {N} {P_HEADER}"] + [''.join(row) for row in grid]
    path.write_text('\n'.join(lines) + '\n', encoding='utf-8')
    h = sum(row.count('H') for row in grid)
    t = sum(row.count('T') for row in grid)
    e = sum(row.count('.') for row in grid)
    print(f"wrote {path.name}: H={h} T={t} empty={e}")


def main():
    specs = [
        ("tiny_acak_03.txt", gen_acak, 20260301),
        ("tiny_acak_04.txt", gen_acak, 20260401),
        ("tiny_klaster_02.txt", gen_klaster, 20260201),
        ("tiny_labirin_02.txt", gen_labirin, 20260202),
    ]
    for name, gen, base_seed in specs:
        for attempt in range(500):
            rng = random.Random(base_seed + attempt)
            grid = gen(rng)
            if validate(grid):
                write_map(name, grid)
                break
        else:
            raise SystemExit(f"gagal menghasilkan peta valid: {name}")


if __name__ == "__main__":
    main()
