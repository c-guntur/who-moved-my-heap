"""JDK Stream / FindOps / MatchOps / ReduceOps allocation breakdown for one run, compared with the
Eclipse Collections (EC) versions of the same three operations.

usage: python3 jdk_ops.py <project_root> <RUN_TS> [out_dir]   (out_dir defaults to assets/jdkops)
Reads assets/runs/<RUN_TS>/all-histo-{coh-on,coh-off,ea-off}.histo and summary.json; writes
<out_dir>/<RUN_TS>-JdkStreamOpsAnalysis.adoc and charts in <out_dir>/charts/.
"""
import json, os, sys, textwrap
import matplotlib
matplotlib.use('Agg')
matplotlib.rcParams['text.parse_math'] = False
import matplotlib.pyplot as plt
from parse import parse_histo
from charts import classify

ROOT, RUN_TS = sys.argv[1], sys.argv[2]
OUT = sys.argv[3] if len(sys.argv) > 3 else f'{ROOT}/assets/jdkops'
RUN = f'{ROOT}/assets/runs/{RUN_TS}'
CH = f'{OUT}/charts'
os.makedirs(CH, exist_ok=True)
H = {c: parse_histo(f'{RUN}/all-histo-{c}.histo') for c in ('coh-on', 'coh-off', 'ea-off')}
S = json.load(open(f'{RUN}/summary.json'))
TESTS = (1, 500, 1000)

# Benchmark (Utility = JDK List.copyOf list; EagerLazy has the same JDK benchmark) -> JDK method
OPS = [('anyMatch', 'AnyMatchUtility', 'jdkAnyMatch', 'list.stream().anyMatch(Integer.valueOf(test)::equals)'),
       ('findFirst', 'FindFirstUtility', 'jdkFindFirst', 'list.stream().filter(Integer.valueOf(test)::equals).findFirst().orElse(0)'),
       ('count', 'CountUtility', 'jdkCount', 'list.stream().filter(Integer.valueOf(test)::equals).count()')]

# Origin of every class the JDK benchmarks allocate, and why (verified against JDK 25 bytecode with javap).
ORIGIN = [
    ('java.util.stream.ReferencePipeline$Head', 'Stream source', '`list.stream()`: the pipeline head (AbstractPipeline fields: links, flags, depth, spliterator)'),
    ('java.util.AbstractList$RandomAccessSpliterator', 'Stream source', '`list.stream()`: the spliterator that walks the list'),
    ('java.util.stream.ReferencePipeline$2', 'filter stage', '`filter(p)`: the StatelessOp pipeline stage'),
    ('java.util.stream.ReferencePipeline$2$1', 'filter stage', '`filter(p)`: its Sink.ChainedReference, made when the terminal op runs'),
    ('java.util.stream.MatchOps$MatchOp', 'terminal op', '`anyMatch`: `MatchOps.makeRef` does `new MatchOp(...)` on every call'),
    ('java.util.stream.MatchOps$$Lambda', 'terminal op', '`anyMatch`: the `() -> new MatchSink()` supplier, capturing the predicate and MatchKind, made on every call'),
    ('java.util.stream.MatchOps$1MatchSink', 'terminal op', '`anyMatch`: the sink that tests each element'),
    ('java.util.stream.FindOps$FindSink$OfRef', 'terminal op', '`findFirst`: the sink that keeps the first value (the FindOp itself is a cached static, `OP_FIND_FIRST`)'),
    ('java.util.Optional', 'terminal op', '`findFirst`: the `Optional` result, made by `FindSink.OfRef.get()`'),
    ('java.util.stream.ReduceOps$5', 'terminal op', '`count`: `ReduceOps.makeRefCounting()` does `new ReduceOps$5(...)` on every call'),
    ('java.util.stream.ReduceOps$CountingSink$OfRef', 'terminal op', '`count`: the sink that counts (the `long` result is a cached `Long`)'),
    ('$$Lambda', 'caller lambda', 'the benchmark\'s bound method reference `Integer.valueOf(test)::equals` (captures the Integer)'),
    ('java.lang.Integer', 'Integer box', '`Integer.valueOf(test)`: outside the -128..127 cache, so only at test = 500 and 1000'),
]
# Objects only the EC methods allocate, and only when escape analysis is off (verified against EC 13.0.0 bytecode).
EC_ORIGIN = [
    ('org.eclipse.collections.impl.block.factory.Predicates$BindPredicate2', 'EC object',
     'EC 13.0.0 `ImmutableArrayList` inherits `anySatisfyWith`, `countWith` and `detectWithIfNone` from '
     '`AbstractRichIterable`, which wraps the call in `Predicates.bind(predicate, parameter)`: a new capturing wrapper per call'),
    ('org.eclipse.collections.impl.lazy.LazyIterableAdapter', 'EC object',
     '`asLazy()`: `LazyIterate.adapt(this)` makes a new lazy view of the list on every call'),
    ('org.eclipse.collections.impl.block.procedure.CountProcedure', 'EC object',
     '`asLazy().count`: `LazyIterableAdapter` inherits `count` from `AbstractRichIterable`, which does '
     '`new CountProcedure(predicate)` and runs it through `forEach`'),
    ('java.util.Optional', 'EC object', '`detectOptional` / `detectWithOptional`: the `Optional` result'),
    ('java.lang.Integer', 'Integer box', '`*With` methods: `this.test` is an `int` field, autoboxed to pass it as the '
     'parameter; capturing methods: `Integer.valueOf(test)`. Only at test = 500 and 1000 (outside the cache)'),
    ('$$Lambda', 'caller lambda', 'the benchmark\'s bound method reference `Integer.valueOf(test)::equals` '
     '(captures the Integer). The fat-free `Integer::equals` is a constant and never allocates'),
]
CATS = ['Stream source', 'filter stage', 'terminal op', 'caller lambda', 'Integer box']
ALL_CATS = CATS + ['EC object']
COL = {'Stream source': '#E69F00', 'filter stage': '#56B4E9', 'terminal op': '#0072B2',
       'caller lambda': '#CC79A7', 'Integer box': '#000000', 'EC object': '#D55E00'}
HATCH = {'Integer box': '//', 'EC object': 'xx'}


def origin(cls, method='jdk'):
    if method.startswith('ec'):
        for k, cat, why in EC_ORIGIN:
            if cls == k or (k == '$$Lambda' and cls.startswith('conf.')):
                return cat, why
        return 'other', ''
    for k, cat, why in ORIGIN:
        if cls == k or (k == '$$Lambda' and cls.startswith('conf.')):
            return cat, why
    return 'other', ''


def short(cls):
    return cls.replace('java.util.stream.', '').replace('java.util.', '').replace('java.lang.', '') \
        .replace('conf.session.shakers.', '')


def block(cfg, bench, method, t):
    return H[cfg][(bench + 'Benchmark', method, t)]


def by_cat(cfg, bench, method, t, cats=CATS):
    out = {c: 0.0 for c in cats}
    for cls, (o, b) in block(cfg, bench, method, t)['classes'].items():
        cat = origin(cls, method)[0]
        if cat not in out:
            # A class the ORIGIN table does not know: check it with javap and add it (instructions section 6).
            print(f'WARNING: {cls} ({bench} {method} test={t}) has no origin; add it to ORIGIN', file=sys.stderr)
            continue
        out[cat] += round(b)
    return out


def norm(cfg, bench, method, t):
    return S[bench][f'{cfg}|{method}|{t}']


# ---------- chart 1: bytes per call by origin (stacked), coh-on, test=500 ----------
def chart_origin(t=500):
    name = f'{RUN_TS}-JdkStreamOps-bytes-by-origin'
    fig, ax = plt.subplots(figsize=(12, 5.2), dpi=150)
    left = [0.0] * len(OPS)
    ys = list(range(len(OPS)))[::-1]
    for cat in CATS:
        vals = [by_cat('coh-on', b, m, t)[cat] for _, b, m, _ in OPS]
        bars = ax.barh(ys, vals, left=left, color=COL[cat], hatch=HATCH.get(cat, ''), edgecolor='white',
                       height=0.6, label=cat)
        for bar, v in zip(bars, vals):
            if v:
                ax.text(bar.get_x() + v / 2, bar.get_y() + bar.get_height() / 2, f'{v:.0f} B',
                        ha='center', va='center', fontsize=10, color='white' if cat != 'Stream source' else 'black',
                        fontweight='bold')
        left = [l + v for l, v in zip(left, vals)]
    for y, total, (op, b, m, _) in zip(ys, left, OPS):
        n = block('coh-on', b, m, t)['objs']
        ax.text(total + 3, y, f'{total:.0f} B, {n:.0f} objects', va='center', fontsize=10.5)
    ax.set_yticks(ys)
    ax.set_yticklabels([f'stream().{"filter(p)." if op != "anyMatch" else ""}{op}{"(p)" if op == "anyMatch" else "()"}'
                        for op, *_ in OPS], fontsize=11)
    ax.set_xlim(0, max(left) * 1.3)
    ax.set_xlabel('bytes allocated per call (match at element 500, compact headers on)', fontsize=11)
    ax.spines[['top', 'right']].set_visible(False)
    fig.suptitle('What one JDK stream call leaves behind, by where each object comes from',
                 fontsize=14, fontweight='bold', x=0.01, ha='left')
    ax.set_title(textwrap.fill('Lower is better. The Stream source and the terminal op (MatchOps, FindOps, ReduceOps) '
                               'are paid on every call, whatever the lambda; filter adds its own stage. '
                               'Every Eclipse Collections version allocates 0 B here.', 130),
                 fontsize=10.5, loc='left', color='#333333')
    ax.legend(loc='upper left', bbox_to_anchor=(0, -0.16), ncol=5, fontsize=10, frameon=False)
    fig.tight_layout(rect=(0, 0, 1, 0.96))
    fig.savefig(f'{CH}/{name}.png', facecolor='white', bbox_inches='tight')
    plt.close(fig)
    cats = []
    vals = []
    for op, b, m, _ in OPS:
        for cat, v in by_cat('coh-on', b, m, t).items():
            if v:
                cats.append(' ' * 14 + f'{op}: {cat} = {v:.0f} B')
                vals.append(v)
    mmd = ['---', 'config:', '  xyChart:', '    width: 1400', f'    height: {34 * len(cats) + 140}',
           '    chartOrientation: horizontal', '  themeVariables:', '    xyChart:', '      plotColorPalette: "#0072B2"',
           '---', '%% Generated by jdk_ops.py. Bars: <terminal op>: <origin> = bytes per call (test=500, coh-on).',
           'xychart-beta', '  title "What one JDK stream call leaves behind, by where each object comes from"',
           '  x-axis [' + ', '.join(f'"{c}"' for c in cats) + ']',
           f'  y-axis "bytes allocated per call" 0 --> {max(vals) * 1.15:.0f}',
           '  bar [' + ', '.join(f'{v:.0f}' for v in vals) + ']']
    open(f'{CH}/{name}.mmd', 'w').write('\n'.join(mmd) + '\n')
    return name


# ---------- chart 2: per-class bytes, compact headers on vs off ----------
def chart_coh(t=500):
    name = f'{RUN_TS}-JdkStreamOps-class-sizes-coh'
    rows = []
    seen = set()
    for op, b, m, _ in OPS:
        for cls, (o, by) in sorted(block('coh-on', b, m, t)['classes'].items(), key=lambda kv: -kv[1][1]):
            key = 'caller $$Lambda' if cls.startswith('conf.') else short(cls)
            if key in seen:
                continue
            seen.add(key)
            off = [v for c, v in block('coh-off', b, m, t)['classes'].items() if c == cls][0][1]
            rows.append((key, origin(cls)[0], round(by), round(off)))
    rows.sort(key=lambda r: CATS.index(r[1]))
    fig, ax = plt.subplots(figsize=(12, 7), dpi=150)
    ys = list(range(len(rows)))[::-1]
    h = 0.38
    b1 = ax.barh([y + h / 2 for y in ys], [r[3] for r in rows], h, color='#999999', label='compact headers off', edgecolor='white')
    b2 = ax.barh([y - h / 2 for y in ys], [r[2] for r in rows], h, color='#0072B2', label='compact headers on (JDK 25 -XX:+UseCompactObjectHeaders)', edgecolor='white')
    for bars in (b1, b2):
        for bar in bars:
            ax.text(bar.get_width() + 0.6, bar.get_y() + bar.get_height() / 2, f'{bar.get_width():.0f} B', va='center', fontsize=9)
    for y, r in zip(ys, rows):
        if r[3] != r[2]:
            ax.text(max(r[2], r[3]) + 4.5, y, f'saves {r[3] - r[2]} B', va='center', fontsize=9.5, color='#0072B2', fontweight='bold')
    ax.set_yticks(ys)
    ax.set_yticklabels([f'{r[0]}  ({r[1]})' for r in rows], fontsize=10)
    ax.set_xlim(0, max(r[3] for r in rows) * 1.35)
    ax.set_xlabel('bytes per object', fontsize=11)
    ax.spines[['top', 'right']].set_visible(False)
    fig.suptitle('Size of each object a JDK stream call allocates, compact object headers off vs on',
                 fontsize=14, fontweight='bold', x=0.01, ha='left')
    ax.set_title(textwrap.fill('Lower is better. Compact headers shrink the header from 12 to 8 bytes, but objects are '
                               'padded to 8 bytes, so only some classes get smaller. The object count does not change.', 130),
                 fontsize=10.5, loc='left', color='#333333')
    ax.legend(loc='upper left', bbox_to_anchor=(0, -0.08), ncol=2, fontsize=10, frameon=False)
    fig.tight_layout(rect=(0, 0, 1, 0.96))
    fig.savefig(f'{CH}/{name}.png', facecolor='white', bbox_inches='tight')
    plt.close(fig)
    cats, vals = [], []
    for r in rows:
        for lab, v in (('off', r[3]), ('on', r[2])):
            cats.append(' ' * 14 + f'{r[0]} headers {lab} = {v} B')
            vals.append(v)
    mmd = ['---', 'config:', '  xyChart:', '    width: 1400', f'    height: {30 * len(cats) + 140}',
           '    chartOrientation: horizontal', '  themeVariables:', '    xyChart:', '      plotColorPalette: "#0072B2"',
           '---', '%% Generated by jdk_ops.py. Bars: <class> headers <off|on> = bytes per object.',
           'xychart-beta', '  title "Size of each object a JDK stream call allocates, compact headers off vs on"',
           '  x-axis [' + ', '.join(f'"{c}"' for c in cats) + ']',
           f'  y-axis "bytes per object" 0 --> {max(vals) * 1.15:.0f}',
           '  bar [' + ', '.join(str(v) for v in vals) + ']']
    open(f'{CH}/{name}.mmd', 'w').write('\n'.join(mmd) + '\n')
    return name, rows


# ---------- Eclipse Collections side ----------
# Every implementation of each operation: (benchmark, method, label, code). The JDK stream runs the same in
# *Utility and *EagerLazy, so it is listed once (from *Utility). Utility = EC's Iterate on the same JDK list;
# EagerLazy = an EC ImmutableList (eager methods and asLazy()).
IMPLS = {
    'anyMatch': [
        ('AnyMatchUtility', 'jdkAnyMatch', 'JDK list.stream().anyMatch', 'list.stream().anyMatch(Integer.valueOf(test)::equals)'),
        ('AnyMatchUtility', 'ecAnySatisfy', 'EC Iterate.anySatisfy (JDK list)', 'Iterate.anySatisfy(list, Integer.valueOf(test)::equals)'),
        ('AnyMatchUtility', 'ecAnySatisfyWith', 'EC Iterate.anySatisfyWith (JDK list)', 'Iterate.anySatisfyWith(list, Integer::equals, test)'),
        ('AnyMatchEagerLazy', 'ecAnySatisfy', 'EC ImmutableList.anySatisfy', 'ecList.anySatisfy(Integer.valueOf(test)::equals)'),
        ('AnyMatchEagerLazy', 'ecAnySatisfyWith', 'EC ImmutableList.anySatisfyWith', 'ecList.anySatisfyWith(Integer::equals, test)'),
        ('AnyMatchEagerLazy', 'ecLazyAnySatisfy', 'EC ImmutableList.asLazy().anySatisfy', 'ecList.asLazy().anySatisfy(Integer.valueOf(test)::equals)')],
    'findFirst': [
        ('FindFirstUtility', 'jdkFindFirst', 'JDK list.stream().filter().findFirst', 'list.stream().filter(Integer.valueOf(test)::equals).findFirst().orElse(0)'),
        ('FindFirstUtility', 'ecDetectOptional', 'EC Iterate.detectOptional (JDK list)', 'Iterate.detectOptional(list, Integer.valueOf(test)::equals).orElse(0)'),
        ('FindFirstUtility', 'ecDetectWithOptional', 'EC Iterate.detectWithOptional (JDK list)', 'Iterate.detectWithOptional(list, Integer::equals, test).orElse(0)'),
        ('FindFirstUtility', 'ecDetectIfNone', 'EC Iterate.detectIfNone (JDK list)', 'Iterate.detectIfNone(list, Integer.valueOf(test)::equals, 0)'),
        ('FindFirstUtility', 'ecDetectWithIfNone', 'EC Iterate.detectWithIfNone (JDK list)', 'Iterate.detectWithIfNone(list, Integer::equals, test, 0)'),
        ('FindFirstEagerLazy', 'ecDetectOptional', 'EC ImmutableList.detectOptional', 'ecList.detectOptional(Integer.valueOf(test)::equals).orElse(0)'),
        ('FindFirstEagerLazy', 'ecDetectWithOptional', 'EC ImmutableList.detectWithOptional', 'ecList.detectWithOptional(Integer::equals, test).orElse(0)'),
        ('FindFirstEagerLazy', 'ecDetectIfNone', 'EC ImmutableList.detectIfNone', 'ecList.detectIfNone(Integer.valueOf(test)::equals, () -> 0)'),
        ('FindFirstEagerLazy', 'ecDetectWithIfNone', 'EC ImmutableList.detectWithIfNone', 'ecList.detectWithIfNone(Integer::equals, test, () -> 0)'),
        ('FindFirstEagerLazy', 'ecLazyDetectOptional', 'EC ImmutableList.asLazy().detectOptional', 'ecList.asLazy().detectOptional(Integer.valueOf(test)::equals).orElse(0)'),
        ('FindFirstEagerLazy', 'ecLazyDetectIfNone', 'EC ImmutableList.asLazy().detectIfNone', 'ecList.asLazy().detectIfNone(Integer.valueOf(test)::equals, () -> 0)')],
    'count': [
        ('CountUtility', 'jdkCount', 'JDK list.stream().filter().count', 'list.stream().filter(Integer.valueOf(test)::equals).count()'),
        ('CountUtility', 'ecCount', 'EC Iterate.count (JDK list)', 'Iterate.count(list, Integer.valueOf(test)::equals)'),
        ('CountUtility', 'ecCountWith', 'EC Iterate.countWith (JDK list)', 'Iterate.countWith(list, Integer::equals, test)'),
        ('CountEagerLazy', 'ecCount', 'EC ImmutableList.count', 'ecList.count(Integer.valueOf(test)::equals)'),
        ('CountEagerLazy', 'ecCountWith', 'EC ImmutableList.countWith', 'ecList.countWith(Integer::equals, test)'),
        ('CountEagerLazy', 'ecLazyCount', 'EC ImmutableList.asLazy().count', 'ecList.asLazy().count(Integer.valueOf(test)::equals)')],
}
KIND = {'jdk': 'JDK stream', 'cap': 'capturing lambda', 'lazy': 'lazy, capturing lambda', 'free': 'fat-free *With',
        'bound': '*With, but Predicates.bind (not fat-free)'}


def kind(bench, method):
    return classify(bench, method)[0]


def ea_off(bench, method, t):
    """(objects, bytes) per call from the escape-analysis-off object-count run."""
    bl = block('ea-off', bench, method, t)
    return bl['objs'], bl['bytes']


def chart_ec(op, t=500):
    """Stacked bars: what each JDK and EC version of `op` allocates per call with escape analysis off."""
    name = f'{RUN_TS}-JdkStreamOps-vs-ec-{op}'
    rows = IMPLS[op]
    fig, ax = plt.subplots(figsize=(12, 1.6 + 0.55 * len(rows)), dpi=150)
    ys = list(range(len(rows)))[::-1]
    left = [0.0] * len(rows)
    for cat in ALL_CATS:
        vals = [by_cat('ea-off', b, m, t, ALL_CATS)[cat] for b, m, _, _ in rows]
        if not any(vals):
            continue
        bars = ax.barh(ys, vals, left=left, color=COL[cat], hatch=HATCH.get(cat, ''), edgecolor='white',
                       height=0.62, label=cat)
        for bar, v in zip(bars, vals):
            if v >= 16:
                # A solid box behind the number keeps it readable on hatched segments.
                ax.text(bar.get_x() + v / 2, bar.get_y() + bar.get_height() / 2, f'{v:.0f}',
                        ha='center', va='center', fontsize=9, fontweight='bold',
                        color='black' if cat == 'Stream source' else 'white',
                        bbox=dict(facecolor=COL[cat], edgecolor='none', pad=0.6) if cat in HATCH else None)
        left = [l + v for l, v in zip(left, vals)]
    for y, total, (b, m, _, _) in zip(ys, left, rows):
        n = block('ea-off', b, m, t)['objs']
        on = norm('coh-on', b, m, t)['bytes']
        ax.text(total + 3, y, f'{total:.0f} B, {n:.0f} obj   (normal JVM: {on:.0f} B)', va='center', fontsize=10)
    ax.set_yticks(ys)
    ax.set_yticklabels([f'{lab}  [{KIND[kind(b, m)]}]' for b, m, lab, _ in rows], fontsize=10)
    ax.set_xlim(0, max(left) * 1.55)
    ax.set_xlabel(f'bytes allocated per call, escape analysis off (-XX:-DoEscapeAnalysis), match at element {t}', fontsize=10.5)
    ax.spines[['top', 'right']].set_visible(False)
    jdk = left[0]
    ec = left[1:]
    fig.suptitle(f'{op}: what one call leaves behind, JDK stream vs Eclipse Collections',
                 fontsize=14, fontweight='bold', x=0.01, ha='left')
    ax.set_title(textwrap.fill(
        f'Lower is better. Escape analysis off, so every object the code creates is counted. The JDK stream '
        f'leaves {jdk:.0f} B; EC versions leave {min(ec):.0f} to {max(ec):.0f} B, with no pipeline at all. '
        f'On a normal JVM (in brackets) the JIT removes every EC object; the JDK objects stay.', 130),
        fontsize=10.5, loc='left', color='#333333')
    ax.legend(loc='upper left', bbox_to_anchor=(0, -0.55 / len(rows) * 4), ncol=6, fontsize=10, frameon=False)
    fig.tight_layout(rect=(0, 0, 1, 0.96))
    fig.savefig(f'{CH}/{name}.png', facecolor='white', bbox_inches='tight')
    plt.close(fig)
    cats, vals = [], []
    for (b, m, lab, _), total in zip(rows, left):
        cats.append(' ' * 14 + f'{lab} = {total:.0f} B (normal JVM {norm("coh-on", b, m, t)["bytes"]:.0f} B)')
        vals.append(total)
    mmd = ['---', 'config:', '  xyChart:', '    width: 1400', f'    height: {34 * len(cats) + 140}',
           '    chartOrientation: horizontal', '  themeVariables:', '    xyChart:', '      plotColorPalette: "#0072B2"',
           '---', f'%% Generated by jdk_ops.py. Bars: <implementation> = bytes per call, escape analysis off, test={t}.',
           'xychart-beta', f'  title "{op}: what one call leaves behind, JDK stream vs Eclipse Collections"',
           '  x-axis [' + ', '.join(f'"{c}"' for c in cats) + ']',
           f'  y-axis "bytes per call (-XX:-DoEscapeAnalysis)" 0 --> {max(vals) * 1.15:.0f}',
           '  bar [' + ', '.join(f'{v:.0f}' for v in vals) + ']']
    open(f'{CH}/{name}.mmd', 'w').write('\n'.join(mmd) + '\n')
    return name


def f0(x):
    return f'{x:,.0f}'


# JDK bytes per call at element 500, escape analysis off, for the three operations.
JDK_EA_OFF = [block('ea-off', b, m, 500)['bytes'] for _, b, m, _ in OPS]


def ec_objects(b, m, t=500):
    """'`Class` (16 B), ...' for one implementation, escape analysis off."""
    cl = block('ea-off', b, m, t)['classes']
    if not cl:
        return 'nothing'
    return ', '.join(f'{"benchmark `$$Lambda`" if c.startswith("conf.") else "`" + short(c).split(".")[-1] + "`"} ({v[1]:.0f} B)'
                     for c, v in sorted(cl.items(), key=lambda kv: (ALL_CATS.index(origin(kv[0], m)[0])
                                                                      if origin(kv[0], m)[0] in ALL_CATS else 99, kv[0])))


def ec_doc():
    charts = {op: chart_ec(op) for op in IMPLS}
    a = ['== The same three operations in Eclipse Collections', '',
         'Every benchmark class also has Eclipse Collections (EC 13.0.0) versions of the same operation. In the '
         '`*Utility` classes EC\'s `Iterate` utility runs on the same JDK list as the stream; in the `*EagerLazy` '
         'classes the methods of an EC `ImmutableList` run, eagerly and through `asLazy()`. Two lambda shapes are '
         'compared: *capturing* (`Integer.valueOf(test)::equals`, a new object per call that holds the value) and '
         '*fat-free* (`Integer::equals` with the value passed as a parameter to a `*With` method, a constant lambda '
         'that is never allocated).', '',
         '=== Where each JDK object goes in EC', '',
         'An EC method is a plain loop over the list that calls the predicate, so most of what a stream builds has '
         'no EC counterpart at all (checked in the EC 13.0.0 bytecode with `javap`):', '',
         '[cols="2,3,4",options="header"]', '|===', '|JDK stream object (B per call) |EC equivalent |Why',
         '|Stream source: `ReferencePipeline$Head`, `RandomAccessSpliterator` (80 B) |none '
         '|`Iterate` checks the list type once and runs `for (i = 0; i < size; i++) list.get(i)` '
         '(`RandomAccessListIterate`); `ImmutableList` loops over its own array (`InternalArrayIterate`).',
         '|filter stage: `ReferencePipeline$2`, `$2$1` (72 B) |none |The predicate is called directly in the loop.',
         '|`anyMatch` terminal op: `MatchOp`, sink supplier, `MatchSink` (64 B) |none |The loop returns `true` at the first match.',
         '|`count` terminal op: `ReduceOps$5`, `CountingSink` (40 B) |none |The loop counts in a local `int`.',
         '|`findFirst` terminal op: `FindSink` (16 B) |none |The loop returns the element.',
         '|`findFirst` result: `Optional` (16 B) |`Optional` only for `detectOptional` / `detectWithOptional`; '
         'none for `detectIfNone` |`detectIfNone` returns the element or the default.',
         '|caller lambda `Integer.valueOf(test)::equals` (16 B) |same object for capturing EC calls; none for `*With` '
         '|`*With` takes a constant `Integer::equals` plus the value as a parameter.',
         '|`Integer` box (16 B at test 500, 1000) |same |Belongs to the benchmark\'s value, not to either library.',
         '|===', '',
         'EC adds a few objects of its own in some methods: `asLazy()` makes a `LazyIterableAdapter` per call (and '
         '`asLazy().count` a `CountProcedure`), `detectOptional` returns an `Optional`, and on an `ImmutableList` three '
         'of the `*With` methods are not fat-free in EC 13.0.0 because they go through `Predicates.bind` (see below).', '',
         '=== On a normal JVM', '',
         'Main runs, compact headers on, escape analysis on (the default). Times were measured in the cloud sandbox; '
         'bytes and GC counts do not depend on the machine.', '']
    for op, rows in IMPLS.items():
        a += [f'.{op}', '[cols="4,2,1,1,1,1",options="header"]', '|===',
              '|Implementation |Lambda |ns per call (element 1) |ns per call (element 500) |B per call (element 500) '
              '|GC runs per 1 billion calls (element 500)']
        for b, m, lab, code in rows:
            n1, n5 = norm('coh-on', b, m, 1), norm('coh-on', b, m, 500)
            a.append(f'|{lab} +\n`{code}` |{KIND[kind(b, m)]} |{n1["ns"]:.1f} |{n5["ns"]:.1f} |{n5["bytes"]:.0f} |{n5["gc_per_b"]:,.0f}')
        a += ['|===', '']
    a += ['Every EC version leaves 0 B per call and needed no garbage collection, capturing or fat-free, eager or lazy, '
          'on either list. The JIT inlines the EC loop and the lambda into the caller and then removes every object '
          'they create (escape analysis). It cannot do that for the stream pipeline (see above), so the JDK side keeps '
          'its 160 to 224 B.', '',
          '=== With escape analysis off: what each call creates', '',
          'Escape analysis is a JIT optimisation, not a promise: it gives up when code is not inlined (cold code, '
          'large methods, deep call chains, megamorphic call sites). The object-count run with '
          '`-XX:-DoEscapeAnalysis` (`all-histo-ea-off.histo`, every iteration GC-free) shows what the code itself '
          'creates, which is what an application pays whenever the JIT does not remove it. Match at element 500, '
          'compact headers on.', '']
    for op, rows in IMPLS.items():
        a += [f'.{op}: bytes per call by origin, escape analysis off (normal JVM in brackets)',
              f'image::charts/{charts[op]}.png[{op}: JDK stream vs Eclipse Collections, escape analysis off]',
              f'Mermaid source: link:charts/{charts[op]}.mmd[{charts[op]}.mmd]', '',
              '[cols="3,2,5,1,1",options="header"]', '|===',
              '|Implementation |Lambda |Objects per call |Objects |B per call']
        for b, m, lab, _ in rows:
            o, by = ea_off(b, m, 500)
            objs = ec_objects(b, m) if m.startswith('ec') else 'the JDK pipeline above, plus the caller lambda and `Integer`'
            a.append(f'|{lab} |{KIND[kind(b, m)]} |{objs} |{o:.0f} |{by:.0f}')
        a += ['|===', '']
    a += ['=== Every object the EC versions allocate, and why', '',
          '[cols="3,1,3,5",options="header"]', '|===', '|Class |Origin |Allocated by |Why']
    seen = {}
    for op, rows in IMPLS.items():
        for b, m, lab, _ in rows:
            if not m.startswith('ec'):
                continue
            for t in TESTS:
                for c in block('ea-off', b, m, t)['classes']:
                    key = 'conf.$$Lambda' if c.startswith('conf.') else c
                    seen.setdefault(key, [])
                    if lab not in seen[key]:
                        seen[key].append(lab)
    for c in sorted(seen, key=lambda c: (ALL_CATS.index(origin(c, 'ec')[0]) if origin(c, 'ec')[0] in ALL_CATS else 99, c)):
        cat, why = origin(c, 'ec')
        name = 'benchmark `$$Lambda`' if c.startswith('conf.') else f'`{short(c).split(".")[-1]}`'
        who = 'every capturing call' if c.startswith('conf.') else (
            'every call at test 500 and 1000' if c == 'java.lang.Integer' else
            ', '.join(x.replace('EC ', '') for x in seen[c]))
        a.append(f'|{name} |{cat} |{who} |{why}')
    a += ['|===', '',
          '=== Fat-free vs capturing', '',
          'With escape analysis off the difference between the lambda shapes is plain:', '']
    def eo(b, m):
        return ea_off(b, m, 500)[1]
    a += [f'* *Fat-free (`Iterate.anySatisfyWith`, `countWith`, `detectWithIfNone`):* only the `Integer` box, '
          f'{eo("AnyMatchUtility", "ecAnySatisfyWith"):.0f} B, and nothing at element 1 where the box is cached. '
          'The lambda is a constant.',
          f'* *Capturing (`Iterate.anySatisfy`, `count`, `detectIfNone`):* the lambda plus the box, '
          f'{eo("AnyMatchUtility", "ecAnySatisfy"):.0f} B. A new lambda is made on every call because it holds the value.',
          f'* *Lazy (`asLazy()`):* the capturing lambda plus a `LazyIterableAdapter`, '
          f'{eo("AnyMatchEagerLazy", "ecLazyAnySatisfy"):.0f} B; `count` also makes a `CountProcedure` '
          f'({eo("CountEagerLazy", "ecLazyCount"):.0f} B).',
          f'* *`*With` on an EC 13.0.0 `ImmutableList`:* the `Predicates$BindPredicate2` wrapper plus the box, '
          f'{eo("AnyMatchEagerLazy", "ecAnySatisfyWith"):.0f} B, as much as the capturing version. `ImmutableArrayList` does not '
          'override `anySatisfyWith`, `countWith` or `detectWithIfNone`; it inherits them from `AbstractRichIterable`, '
          'which binds the parameter into a new predicate. `detectWithOptional` is overridden and stays fat-free. '
          '`Iterate` on a JDK list has its own `*With` loops, so all of them are fat-free there.',
          f'* *JDK stream:* {min(JDK_EA_OFF):.0f} to {max(JDK_EA_OFF):.0f} B, '
          'with escape analysis on or off. Its pipeline objects are the bulk of it, and the stream has no `*With` '
          'form, so even a non-capturing predicate would still leave the pipeline behind.', '',
          'So the fat-free claim holds: on the paths that are fat-free, the only allocation left is the boxed value '
          'the caller passes in, and with escape analysis on there is nothing at all. The JDK stream allocates its '
          'pipeline whatever lambda it is given.', '']
    return a


def doc():
    c1 = chart_origin()
    c2, rows = chart_coh()
    a = [f'= JDK Stream, FindOps and MatchOps vs Eclipse Collections: where the garbage comes from (run {RUN_TS})', ':toc:', ':toclevels: 2', '',
         f'Fresh look at the raw results of run `{RUN_TS}` (`assets/runs/{RUN_TS}/`): which objects one JDK `stream()` '
         'call allocates, which part of the Stream API makes each one, and what that costs; then the same numbers for '
         'the Eclipse Collections (EC) versions of each operation, with escape analysis on and off. '
         'Object counts come from the class-histogram runs (`all-histo-coh-on.histo`, `all-histo-coh-off.histo`, '
         '`all-histo-ea-off.histo`, every iteration GC-free); bytes per call from `gc.alloc.rate.norm` in the main runs. '
         'Where each class comes from was checked in the JDK 25.0.4 and EC 13.0.0 bytecode with `javap`. Runs were in the Claude cloud sandbox, not the local Mac: '
         'bytes and object counts do not depend on the machine, timings do.', '',
         '== Summary', '',
         '* *Every JDK stream call allocates a fixed set of 6 to 8 objects (160 to 224 B), wherever '
         'the match is.* None of it is per element: it is the pipeline being built and thrown away on each call.',
         '* *More than a third of it is the Stream source.* `list.stream()` alone costs 80 B (pipeline head 48 B + spliterator 32 B) '
         'before any operation is chosen.',
         '* *The terminal op is the part that differs most.* `anyMatch` (MatchOps) allocates 3 objects, 64 B, because '
         '`MatchOps.makeRef` builds a new `MatchOp` and a new sink-supplier lambda on every call. `findFirst` (FindOps) '
         'allocates 2 objects, 32 B: its `FindOp` is a cached static, but the sink and the `Optional` result are new. '
         '`count` (ReduceOps) allocates 2 objects, 40 B: a new `ReduceOps$5` op on every call plus the counting sink.',
         '* *`filter` adds a whole pipeline stage* (2 objects, 72 B). That is why `stream().anyMatch(p)` (160 B) is '
         'cheaper than `stream().filter(p).findFirst()` (200 B) for the same question: anyMatch puts the predicate '
         'in the terminal op and needs no filter stage.',
         '* *Escape analysis removes none of it.* Bytes per call are identical with `-XX:-DoEscapeAnalysis` for every '
         'JDK benchmark. The same JIT removes every object in the Eclipse Collections loops, including the `Optional` '
         'that `detectOptional` returns.',
         '* *Compact object headers shave 16 to 24 B (7 to 11%)* off a call by shrinking 2 or 3 of its objects by 8 B '
         'each. The number of objects stays the same.',
         '* *Speed:* at match position 1, where almost no elements are scanned, a JDK call takes 29 to 38 ns against '
         '2 to 3 ns for Eclipse Collections. Building and dropping these objects is most of that difference (inferred: '
         'allocation plus the virtual calls through the sink chain).',
         f'* *Eclipse Collections has no pipeline to build.* Its methods are a loop over the list that calls the '
         f'predicate, so the Stream source, filter stage and terminal op have no EC counterpart. On a normal JVM every '
         f'EC version, capturing or fat-free, eager or lazy, allocates 0 B and needed 0 garbage collections.',
         f'* *With escape analysis off, the lambda shape shows.* A fat-free `Iterate.*With` call leaves only the '
         f'`Integer` box the caller passes in ({ea_off("AnyMatchUtility", "ecAnySatisfyWith", 500)[1]:.0f} B, 0 B at '
         f'element 1); a capturing call adds its lambda ({ea_off("AnyMatchUtility", "ecAnySatisfy", 500)[1]:.0f} B); '
         f'`asLazy()` adds a `LazyIterableAdapter`; the JDK stream leaves '
         f'{min(JDK_EA_OFF):.0f} to {max(JDK_EA_OFF):.0f} B. '
         'On an EC 13.0.0 `ImmutableList`, `anySatisfyWith`, `countWith` and `detectWithIfNone` are not fat-free '
         '(`Predicates.bind` makes a wrapper per call).', '',
         '== The three pipelines', '',
         '[cols="1,4",options="header"]', '|===', '|Operation |Benchmark code']
    for op, b, m, code in OPS:
        a.append(f'|`{op}` |`{code}`')
    a += ['|===', '',
          'The same JDK code runs in the `*Utility` and `*EagerLazy` benchmarks (both use a `List.copyOf` list), and the '
          'histograms are identical for both. The tables use the `*Utility` runs.', '',
          '== Every object, and which part of the Stream API makes it', '',
          'Compact headers on, match at element 500 (one call):', '',
          '[cols="3,2,1,1,1,5",options="header"]', '|===',
          '|Class |Origin |anyMatch (B) |findFirst (B) |count (B) |Why it is allocated']
    classes = []
    for op, b, m, _ in OPS:
        for cls in block('coh-on', b, m, 500)['classes']:
            if cls not in classes:
                classes.append(cls)
    classes.sort(key=lambda c: (CATS.index(origin(c)[0]), c))
    done = set()
    for cls in classes:
        cat, why = origin(cls)
        lam = cls.startswith('conf.')
        if lam and 'lambda' in done:
            continue
        cells = []
        for op, b, m, _ in OPS:
            cl = block('coh-on', b, m, 500)['classes']
            v = next((x for c, x in cl.items() if c.startswith('conf.')), None) if lam else cl.get(cls)
            cells.append(f'{v[1]:.0f}' if v else '-')
        if lam:
            done.add('lambda')
        name = 'benchmark `$$Lambda`' if lam else f'`{short(cls)}`'
        a.append(f'|{name} |{cat} |' + ' |'.join(cells) + f' |{why}')
    tot = []
    for op, b, m, _ in OPS:
        bl = block('coh-on', b, m, 500)
        tot.append(f'*{bl["bytes"]:.0f}* ({bl["objs"]:.0f} obj)')
    a += ['2+h|Total per call |' + ' |'.join(tot) + ' |', '|===', '',
          f'.Bytes per call by origin. Every Eclipse Collections version allocates 0 B.',
          f'image::charts/{c1}.png[Bytes per JDK stream call by origin]',
          f'Mermaid source: link:charts/{c1}.mmd[{c1}.mmd]', '',
          '=== By origin, every match position (compact headers on)', '',
          '[cols="2,1,1,1,1,1,1,1",options="header"]', '|===',
          '|Operation |test |Stream source |filter stage |terminal op |caller lambda |Integer box |Total (B / objects)']
    for op, b, m, _ in OPS:
        for t in TESTS:
            d = by_cat('coh-on', b, m, t)
            bl = block('coh-on', b, m, t)
            a.append(f'|`{op}` |{t} |' + ' |'.join(f'{d[c]:.0f}' for c in CATS) + f' |{bl["bytes"]:.0f} / {bl["objs"]:.0f}')
    a += ['|===', '',
          'Only the `Integer` box changes with the match position (`Integer.valueOf(test)` is cached for 1, not for '
          '500 or 1000), and that box belongs to the benchmark\'s lambda, not to the Stream API. The Stream API\'s own '
          'cost is the same for a match at element 1 as at element 1000.', '',
          '== The terminal ops compared', '',
          '[cols="1,2,1,1,3",options="header"]', '|===',
          '|Operation |Terminal op class |New objects per call |Bytes |What could be avoided']
    term = {op: [(short(c), v) for c, v in block('coh-on', b, m, 500)['classes'].items() if origin(c)[0] == 'terminal op']
            for op, b, m, _ in OPS}
    notes = {'anyMatch': 'The op and the supplier lambda depend on the predicate, so they cannot be cached as written.',
             'findFirst': 'The op is already cached (`FindSink.OfRef.OP_FIND_FIRST`). The `Optional` is the result.',
             'count': '`ReduceOps$5` holds no per-call state, yet a new one is made each call (unlike FindOps). '
                      'Caching it would save 16 B a call (inferred from the bytecode; not tested).'}
    cls_of = {'anyMatch': 'MatchOps', 'findFirst': 'FindOps', 'count': 'ReduceOps'}
    for op, *_ in OPS:
        items = term[op]
        a.append(f'|`{op}` |{cls_of[op]} |' + ', '.join(f'`{c}`' for c, _ in items) +
                 f' |{sum(round(v[1]) for _, v in items)} |{notes[op]}')
    a += ['|===', '',
          '== Escape analysis does not help the JDK stream', '',
          '`gc.alloc.rate.norm` from the main runs, escape analysis on (normal) and off (`-XX:-DoEscapeAnalysis`), '
          'compact headers on:', '',
          '[cols="2,1,1,1",options="header"]', '|===', '|Operation |test |EA on (B per call) |EA off (B per call)']
    for op, b, m, _ in OPS:
        for t in TESTS:
            a.append(f'|`{op}` |{t} |{norm("coh-on", b, m, t)["bytes"]:.0f} |{norm("ea-off", b, m, t)["bytes"]:.0f}')
    a += ['|===', '',
          'The numbers are the same, so the JIT scalar-replaces none of the pipeline objects. They are linked to each '
          'other (head to spliterator, stage to stage, sink to sink) and passed through interface calls in '
          '`AbstractPipeline.evaluate`, so they escape (inferred: the usual reasons escape analysis gives up, not '
          'confirmed with JIT logs). Compare Eclipse Collections, where the same JIT removes every object, including the '
          '`Optional` from `detectOptional`: there the loop and the lambda are inlined into the caller.', '',
          '== Compact object headers', '',
          f'image::charts/{c2}.png[Object sizes, compact headers off vs on]',
          f'Mermaid source: link:charts/{c2}.mmd[{c2}.mmd]', '',
          '[cols="3,2,1,1,1",options="header"]', '|===', '|Class |Origin |Headers off (B) |Headers on (B) |Saved']
    for r in rows:
        a.append(f'|`{r[0]}` |{r[1]} |{r[3]} |{r[2]} |{r[3] - r[2] if r[3] != r[2] else "-"}')
    a += ['|===', '',
          '[cols="2,1,1,1,1",options="header"]', '|===', '|Operation |test |Headers off (B per call) |Headers on (B per call) |Saved']
    for op, b, m, _ in OPS:
        for t in TESTS:
            on, off = block('coh-on', b, m, t), block('coh-off', b, m, t)
            a.append(f'|`{op}` |{t} |{off["bytes"]:.0f} |{on["bytes"]:.0f} |{off["bytes"] - on["bytes"]:.0f} B '
                     f'({(1 - on["bytes"] / off["bytes"]) * 100:.0f}%)')
    a += ['|===', '',
          'Object-count run (exact per-class sizes). Compact headers turn a 12-byte header into 8 bytes, and objects '
          'are padded to a multiple of 8 bytes, so a class only gets smaller when the 4 bytes saved cross that '
          'boundary: the pipeline head, the MatchOps lambda, the filter sink and the find sink do; the spliterator, '
          'the ops, `Optional` and `Integer` do not. Not allocating at all saves the whole 160 to 224 B.', '',
          '== What it costs an application', '',
          'Derived from bytes per call (compact headers on): allocation rate = bytes per call × calls per second.', '',
          '[cols="2,1,1,1,1",options="header"]', '|===',
          '|Operation |Bytes per call (test=500) |MB/s at 1 M calls/s |MB/s at 10 M calls/s |GC runs per 1 billion calls (measured, G1 2 GB heap)']
    for op, b, m, _ in OPS:
        by = block('coh-on', b, m, 500)['bytes']
        a.append(f'|`{op}` |{by:.0f} |{by:.0f} |{by * 10:,.0f} |{norm("coh-on", b, m, 500)["gc_per_b"]:.0f}')
    a += ['|===', '',
          'The Eclipse Collections versions of the same three operations allocate 0 B per call and caused 0 GC runs.', '',
          '=== Time spent on the pipeline itself', '',
          'At match position 1 the loop does almost no work, so the time is mostly setting up and tearing down the call.', '',
          '[cols="2,1,1,1",options="header"]', '|===', '|Operation |JDK stream (ns per call) |Fastest EC version (ns per call) |Difference (ns)']
    for op, b, m, _ in OPS:
        ecs = [k.split('|')[1] for k in S[b] if k.startswith('coh-on|ec') and k.endswith('|1')]
        best = min(S[b][f'coh-on|{e}|1']['ns'] for e in ecs)
        j = norm('coh-on', b, m, 1)['ns']
        a.append(f'|`{op}` |{j:.1f} |{best:.1f} |{j - best:.1f}')
    a += ['|===', '',
          '`count` always scans all 1000 elements, so at position 1 it is not a setup measurement; the difference '
          'there includes the slower per-element path through the filter and counting sinks.', '']
    a += ec_doc()
    a += ['== Caveats', '',
          '* One machine (cloud VM), one JDK build (OpenJDK 25.0.4), microbenchmarks.',
          '* Origins of each class were read from the JDK 25 bytecode (`javap -c`); explanations of why escape '
          'analysis fails, and the saving from caching `ReduceOps$5`, are inferred.',
          '* `gc.alloc.rate.norm` can sit a few bytes below the object-count totals in some forks, where the JIT '
          'removed one object some of the time; the object-count run is the reference.',
          '* EC findings are for EC 13.0.0: the `Predicates.bind` path on `ImmutableList` may change in other versions.',
          '* Escape analysis off is a bound, not a typical JVM: it shows what the code creates, while a normal JVM '
          'removes what it can inline. Which real call sites get inlined depends on the application.', '',
          '== Reproduce', '', '....',
          f'python3 assets/runs/{RUN_TS}/tools/jdk_ops.py . {RUN_TS}', '....', '',
          f'Inputs: `assets/runs/{RUN_TS}/all-histo-coh-on.histo`, `all-histo-coh-off.histo`, `all-histo-ea-off.histo`, '
          '`summary.json`. `jdk_ops.py`, `charts.py` and `parse.py` are in the run\'s `tools/` folder.', '']
    path = f'{OUT}/{RUN_TS}-JdkStreamOpsAnalysis.adoc'
    open(path, 'w').write('\n'.join(a))
    print(path)


doc()
