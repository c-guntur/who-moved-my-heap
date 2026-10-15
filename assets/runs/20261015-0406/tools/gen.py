"""Assemble Results2 files, analysis docs and Mermaid charts for one run.

usage: python3 gen.py <project_root> <RUN_TS> <notes.json>
"""
import json, os, re, subprocess, sys
from parse import parse_table, parse_histo
from charts import build

ROOT, RUN_TS, NOTES = sys.argv[1], sys.argv[2], sys.argv[3]
NOTES = json.load(open(NOTES))
RUN_DIR = f'{ROOT}/assets/runs/{RUN_TS}'
DOCS = f'{ROOT}/assets/docs'
CHARTS = f'{DOCS}/charts'
os.makedirs(CHARTS, exist_ok=True)
MMDC = os.environ.get('MMDC')
PP = os.environ.get('MMDC_PUPPETEER')

CLASSES = os.environ['CLASSES'].split(',') if os.environ.get('CLASSES') else ['AnyMatchUtility', 'CountUtility', 'FindFirstUtility',
           'AnyMatchEagerLazy', 'CountEagerLazy', 'FindFirstEagerLazy']
CONFIGS = ['coh-on', 'coh-off', 'ea-off']
TESTS = [1, 500, 1000]
MEASURE_S = 40.0  # 2 forks x 10 iterations x 2 s

histo = {c: parse_histo(f'{RUN_DIR}/all-{c}.histo') for c in ['histo-coh-on', 'histo-coh-off']}
meta = open(f'{RUN_DIR}/meta.txt').read()


def kind(m):
    if m.startswith('jdk'): return 'JDK stream'
    if 'With' in m: return 'EC fat-free (*With)'
    if m.startswith('ecLazy'): return 'EC lazy, capturing'
    return 'EC eager, capturing'


def f(x, d=1):
    if x is None: return 'n/a'
    if x == 0: return '0'
    if abs(x) < 0.01: return '≈0'
    if abs(x) >= 100: return f'{x:,.0f}'
    if abs(x) >= 10: return f'{x:,.{min(d,1)}f}'
    return f'{x:,.{max(d,2)}f}'


def fb(x):
    return '≈0' if x < 0.5 else f'{x:,.0f}'


def derive(row):
    ns = row['ns'][0]
    calls = MEASURE_S * 1e9 / ns
    gcc = row.get('gc.count', (0,))[0] or 0
    gct = row.get('gc.time', (0,))[0] or 0
    return {
        'ns': ns, 'ns_err': row['ns'][1], 'mcalls': 1000.0 / ns, 'calls': calls,
        'bytes': row.get('gc.alloc.rate.norm', (0,))[0] or 0,
        'gc_count': gcc, 'gc_time': gct,
        'gc_per_b': gcc / calls * 1e9, 'gct_per_b': gct / calls * 1e9,
    }


def methods_of(table):
    ms = sorted({m for m, _ in table})
    return sorted(ms, key=lambda m: (not m.startswith('jdk'), 'Lazy' in m, 'With' in m, m))


def ratio(a, b):
    return a / b if b else float('inf')


def embed(c):
    key, caption, png, mmd = c
    return [f'.{caption}', f'image::charts/{png}[{caption}]',
            f'Mermaid source: link:charts/{mmd}[{mmd}]', '']


def gen_class(cls):
    tables = {c: parse_table(f'{RUN_DIR}/{cls}-{c}.txt') for c in CONFIGS}
    on = tables['coh-on']
    ms = methods_of(on)
    jdk = [m for m in ms if m.startswith('jdk')][0]
    D = {c: {k: derive(v) for k, v in t.items()} for c, t in tables.items()}
    H = {c: {(m, t): v for (k, m, t), v in h.items() if k == cls + 'Benchmark'} for c, h in histo.items()}
    note = NOTES.get(cls, {})

    # ---------- Results2 ----------
    r = [f'= {cls}Benchmark results (run {RUN_TS})', ':toc:', '',
         'Assembled from the raw JMH output in `assets/runs/' + RUN_TS + '/`. Numbers are copied verbatim.', '',
         '== Run metadata', '', '....', meta.rstrip(), '....', '']
    for c in CONFIGS:
        flags = {'coh-on': '-XX:+UseCompactObjectHeaders', 'coh-off': '-XX:-UseCompactObjectHeaders',
                 'ea-off': '-XX:+UseCompactObjectHeaders -XX:-DoEscapeAnalysis'}[c]
        r += [f'== {c} (`-XX:+UseG1GC -Xms2g -Xmx2g {flags}`)', '', '....',
              open(f'{RUN_DIR}/{cls}-{c}.txt', encoding='utf-8').read().rstrip(), '....', '']
    for c in ['histo-coh-on', 'histo-coh-off']:
        r += [f'== Object counts: {c}', '',
              'Per-class lines averaged over clean iterations (no GC during the iteration). '
              'Totals are the sum of the per-class lines (classes with at least 0.5 objects per call); '
              'the profiler\'s own `total` line is not used because it includes its own allocations.', '',
              '[cols="3,1,1,1,1",options="header"]', '|===', '|Benchmark |test |clean iterations |objects per call |bytes per call']
        for m in ms:
            for t in TESTS:
                h = H[c].get((m, t))
                if h: r.append(f'|{m} |{t} |{h["clean_iters"]}/{h["iters"]} |{h["objs"]:.3f} |{h["bytes"]:.1f}')
        r += ['|===', '', '.Per-class breakdown', '....']
        for m in ms:
            for t in TESTS:
                h = H[c].get((m, t))
                if not h: continue
                r.append(f'{m} test={t}')
                for k, (o, b) in sorted(h['classes'].items(), key=lambda kv: -kv[1][1]):
                    r.append(f'    {o:7.3f} obj {b:8.1f} B  {k}')
                if not h['classes']: r.append('    (nothing at or above 0.5 objects per call)')
        r += ['....', '']
    r += ['== Commands', '', '....', COMMANDS.replace('${CLASS}', cls).replace('${RUN_TS}', RUN_TS), '....', '']
    res2 = f'{ROOT}/assets/runs/{RUN_TS}-{cls}Results2.adoc'
    open(res2, 'w').write('\n'.join(r))

    # ---------- charts ----------
    charts = {c[0]: c for c in build(cls, ms, D, TESTS, CHARTS, RUN_TS, MMDC, PP)}

    # ---------- analysis ----------
    a = [f'= {cls}: fat-free lambdas and garbage (run {RUN_TS})', ':toc:', ':toclevels: 2', '',
         f'Input: link:../runs/{RUN_TS}-{cls}Results2.adoc[{RUN_TS}-{cls}Results2.adoc]. '
         'Environment: Claude cloud sandbox (4 vCPU Intel Xeon KVM guest, OpenJDK 25.0.4), not the local Mac, '
         'so absolute timings will differ from a run on the Mac. Bytes and object counts do not depend on the machine.', '',
         '== Summary', '', note.get('summary', 'TODO'), '']
    # head to head
    a += ['== Head-to-head (compact headers on)', '',
          'Throughput = 1000 ÷ ns per call. Calls measured = 40 s ÷ ns per call. '
          'Objects per call come from the object-count run; bytes per call from `gc.alloc.rate.norm`.', '',
          '[cols="3,2,1,1,1,1,1,1",options="header"]', '|===',
          '|Implementation |Kind |Throughput (M calls/s) |Time per call (ns) |Objects per call |Bytes per call |GC runs per 1B calls |GC time per 1B calls (ms)']
    for t in TESTS:
        a.append(f'8+h|test = {t}')
        for m in ms:
            d = D['coh-on'][(m, t)]; h = H['histo-coh-on'].get((m, t))
            a.append(f'|`{m}` |{kind(m)} |{f(d["mcalls"])} |{f(d["ns"])} ± {f(d["ns_err"])} |'
                     f'{f(h["objs"]) if h else "n/a"} |{fb(d["bytes"])} |{f(d["gc_per_b"])} |{f(d["gct_per_b"])}')
        # comparison row: best EC fat-free vs JDK
        dj = D['coh-on'][(jdk, t)]; hj = H['histo-coh-on'].get((jdk, t))
        parts = []
        for m in ms:
            if m == jdk: continue
            d = D['coh-on'][(m, t)]; h = H['histo-coh-on'].get((m, t))
            fo = (hj['objs'] - h['objs']) if (h and hj) else None
            parts.append(f'`{m}` vs JDK: {ratio(dj["ns"], d["ns"]):.1f}x faster, '
                         f'{f(fo) if fo is not None else "n/a"} fewer objects and {fb(dj["bytes"] - d["bytes"])} fewer bytes per call, '
                         f'{f(d["gc_per_b"])} vs {f(dj["gc_per_b"])} GC runs per billion calls')
        a.append('8+a|' + ' +\n'.join(parts))
    a += ['|===', '']
    a += embed(charts['throughput'])
    a += embed(charts['gc-per-billion'])

    # garbage
    a += ['== Garbage: where it comes from', '', note.get('garbage', 'TODO'), '',
          '=== Objects per call by class (object-count run, compact headers on)', '', '....']
    for t in TESTS:
        for m in ms:
            h = H['histo-coh-on'].get((m, t))
            if not h: continue
            a.append(f'{m} test={t}: {h["objs"]:.2f} objects, {h["bytes"]:.0f} B per call')
            for k, (o, b) in sorted(h['classes'].items(), key=lambda kv: -kv[1][1]):
                a.append(f'    {o:6.2f} x {k}  ({b:.0f} B)')
    a += ['....', '']
    a += embed(charts['bytes-per-call'])
    a += ['=== Escape analysis off (upper bound for lambda garbage)', '',
          'Same benchmarks with `-XX:-DoEscapeAnalysis`. Real applications cannot count on escape analysis '
          '(megamorphic call sites, code that is not inlined), so this shows what each lambda shape costs when '
          'it does not help. It is an upper bound, not typical production behaviour.', '',
          '[cols="3,2,1,1,1,1",options="header"]', '|===',
          '|Implementation |Kind |Bytes per call, EA on |Bytes per call, EA off |Time per call EA on (ns) |Time per call EA off (ns)']
    for t in TESTS:
        a.append(f'6+h|test = {t}')
        for m in ms:
            d1 = D['coh-on'][(m, t)]; d2 = D['ea-off'][(m, t)]
            a.append(f'|`{m}` |{kind(m)} |{fb(d1["bytes"])} |{fb(d2["bytes"])} |{f(d1["ns"])} |{f(d2["ns"])}')
    a += ['|===', '', note.get('ea', ''), '']
    a += embed(charts['ea-off-bytes'])

    # spikes
    a += ['== Spikes and heap headroom (derived, not measured)', '',
          'Formulas: allocation rate (MB/s) = bytes per call × calls/s ÷ 10^6. '
          'Young collections per minute = allocation rate ÷ 512 MB (example young generation) × 60. '
          'Allocated during a 10 s spike = bytes per call × calls/s × 10 s: the young-generation space the spike needs, '
          'or the collections it causes when that space is not there. Loads are application-wide, across all threads.', '',
          '[cols="3,1,1,1,1,1",options="header"]', '|===',
          '|Implementation |Bytes per call |MB/s at 1 M calls/s |MB/s at 10 M calls/s |Young GCs per minute at 10 M calls/s |MB allocated in a 10 s spike at 10 M calls/s']
    for t in TESTS:
        a.append(f'6+h|test = {t}')
        for m in ms:
            b = D['coh-on'][(m, t)]['bytes']
            a.append(f'|`{m}` |{fb(b)} |{f(b)} |{f(b * 10)} |{f(b * 10 / 512 * 60)} |{f(b * 100)}')
    a += ['|===', '',
          'JMH also reports `gc.alloc.rate` (MB/s), but that number scales with how fast the benchmark runs, so a faster '
          'method can show a higher MB/s while allocating the same per call. `gc.alloc.rate.norm` (bytes per call) is the '
          'number to size heaps with.', '', note.get('spike', ''), '']
    a += embed(charts['spike-headroom'])

    # COH
    a += ['== Compact object headers', '',
          '[cols="3,1,1,1,1,1,1",options="header"]', '|===',
          '|Implementation |Bytes per call on |Bytes per call off |Saved |Objects per call on |Objects per call off |Time per call on / off (ns)']
    for t in TESTS:
        a.append(f'7+h|test = {t}')
        for m in ms:
            d1 = D['coh-on'][(m, t)]; d2 = D['coh-off'][(m, t)]
            h1 = H['histo-coh-on'].get((m, t)); h2 = H['histo-coh-off'].get((m, t))
            saved = f'{(1 - d1["bytes"] / d2["bytes"]) * 100:.0f}%' if d2['bytes'] > 1 else 'n/a'
            a.append(f'|`{m}` |{fb(d1["bytes"])} |{fb(d2["bytes"])} |{saved} |{f(h1["objs"]) if h1 else "n/a"} |'
                     f'{f(h2["objs"]) if h2 else "n/a"} |{f(d1["ns"])} / {f(d2["ns"])}')
    a += ['|===', '', 'Bytes per call here are `gc.alloc.rate.norm` from the main runs. Where that is a few bytes below the '
          'object-count total, the JIT scalar-replaced one pipeline object in some forks; the object-count run (exact '
          'per-class counts) is the reference for what each call allocates.', '', note.get('coh', ''), '']
    a += embed(charts['coh-bytes'])

    # supporting
    a += ['== Supporting analysis', '', note.get('supporting', ''), '',
          '=== Raw GC totals (compact headers on, per 40 s measurement)', '',
          'Raw totals are shown for completeness only. Do not compare them across implementations: a faster '
          'method fits more calls into the same 40 s.', '',
          '[cols="3,1,1,1,1",options="header"]', '|===', '|Implementation |test |calls measured |gc.count |gc.time (ms)']
    for t in TESTS:
        for m in ms:
            d = D['coh-on'][(m, t)]
            a.append(f'|`{m}` |{t} |{d["calls"]:.3g} |{f(d["gc_count"])} |{f(d["gc_time"])}')
    a += ['|===', '', '== Caveats', '', note.get('caveats', CAVEATS), '',
          '== Reproduce', '', f'Run folder: `assets/runs/{RUN_TS}/`. Results: `assets/runs/{RUN_TS}-{cls}Results2.adoc`.', '',
          '....', COMMANDS.replace('${CLASS}', cls).replace('${RUN_TS}', RUN_TS), '....', '']
    open(f'{DOCS}/{RUN_TS}-{cls}Analysis.adoc', 'w').write('\n'.join(a))
    return D, H


CAVEATS = ('One machine (a cloud VM, not dedicated hardware), one JDK build (OpenJDK 25.0.4 from Ubuntu), G1 with a fixed '
           '2 GB heap. Microbenchmarks, not an application. Mechanisms are read from the class histograms where shown; '
           'anything else is labelled inferred.')
COMMANDS = '''mvn -f pom-bench.xml clean compile
mvn -f pom-bench.xml package
# main runs, CONFIG/FLAGS = coh-on: -XX:+UseCompactObjectHeaders | coh-off: -XX:-UseCompactObjectHeaders
#                          ea-off: -XX:+UseCompactObjectHeaders -XX:-DoEscapeAnalysis
java -jar target-bench/microbenchmarks.jar "conf\\.session\\.shakers\\.${CLASS}Benchmark\\." \\
  -prof gc -jvmArgsAppend "-XX:+UseG1GC -Xms2g -Xmx2g ${FLAGS}" \\
  -rf text -rff assets/runs/${RUN_TS}/${CLASS}-${CONFIG}.txt -o assets/runs/${RUN_TS}/${CLASS}-${CONFIG}.log
# object counts, COH = -XX:+UseCompactObjectHeaders | -XX:-UseCompactObjectHeaders
java -jar target-bench/microbenchmarks.jar "conf\\.session\\.shakers\\." -f 1 -wi 5 -w 1s -i 3 -r 100ms \\
  -jvmArgs "-XX:+UseParallelGC -Xms12g -Xmx12g -Xmn10g ${COH}" -prof prof.HistoProfiler -prof gc \\
  -rf text -rff assets/runs/${RUN_TS}/all-${CONFIG}.txt -o assets/runs/${RUN_TS}/all-${CONFIG}.log
# HISTO lines go to the console, not to the -o file; they were saved to assets/runs/${RUN_TS}/all-${CONFIG}.histo'''

if __name__ == '__main__':
    summary = {}
    for c in CLASSES:
        D, H = gen_class(c)
        summary[c] = {f'{cfg}|{m}|{t}': v for cfg, dd in D.items() for (m, t), v in dd.items()}
        summary[c].update({f'{cfg}|{m}|{t}|histo': {'objs': v['objs'], 'bytes': v['bytes'], 'classes': v['classes'], 'clean': f"{v['clean_iters']}/{v['iters']}"} for cfg, hh in H.items() for (m, t), v in hh.items()})
    json.dump(summary, open(f'{RUN_DIR}/summary.json', 'w'), indent=1, default=str)
    print('ok')
