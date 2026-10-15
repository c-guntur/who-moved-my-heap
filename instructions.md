# Benchmark analysis instructions (v2, repeatable)

How to re-run the `shakers` JMH benchmarks and turn the results into one analysis
AsciiDoc per benchmark class, comparing the JDK Stream API with Eclipse Collections (EC), plus
one JDK Stream ops analysis per run that shows where the JDK stream garbage comes from and what
the EC versions of the same operations allocate instead (section 6).

> Scope: `src/test/java/conf/session/shakers/` only. The `movers` (JOL) material
> will get its own section later; ignore it here.

---

## 0. Intent (read this first)

The story is **fat-free lambdas make less garbage**. This is about memory, not only speed:
an application that allocates less per call needs less heap headroom to absorb a
traffic spike, collects less often, and does not have to be over-provisioned "just in case".

- **Fat-free lambda**: a lambda or method reference that captures nothing, so the JVM can
  reuse one instance forever. Example: `Integer::equals` in
  `ecList.anySatisfyWith(Integer::equals, this.test)`. EC's `*With` methods
  (`anySatisfyWith`, `countWith`, `detectWithOptional`, `detectWithIfNone`) exist so the
  extra value is passed as a parameter instead of being captured.
- **Capturing ("fat") lambda**: one that closes over a value, so a new object may be created
  per call. Example: `Integer.valueOf(this.test)::equals` captures the boxed `Integer`.
- **Compact object headers** (`-XX:+UseCompactObjectHeaders`, JDK 25) shrink every object
  header, so each unavoidable object costs fewer bytes. Show what the flag saves by comparing
  runs with it on and off.

Keep two effects separate in every analysis, because they have different causes:

1. **Pipeline garbage**: the objects `stream()` builds on every call (spliterator, pipeline
   stages, sink, `Optional`, ...). EC's eager methods iterate directly and build none of these.
2. **Lambda garbage**: the captured lambda and any boxed `Integer` (values outside
   -128..127 are not cached, so `test = 500` and `test = 1000` box on every call).

Be honest where the data does not show a difference. In these microbenchmarks escape
analysis (EA) often removes the captured lambda entirely, so EC capturing and EC `*With`
look the same. That is why section 3.4 includes a run with EA off: real applications
(megamorphic call sites, deep call chains, code that is not inlined) cannot rely on EA, and
the EA-off run shows the cost the lambda shape would carry there. Label it as an upper bound,
not as typical production behaviour.

## 1. Ground rules

- **Do not edit or delete any existing file.** Only add new files. The only new files are the
  per-run outputs named in section 2. `pom-bench.xml` is a permanent project file, not a run output.
- Before starting, get confirmation from the local Mac user. Runs take several hours (see section 3.4) and
  should run on a quiet machine on mains power.
- If the local Mac user says "stop", halt the current step and do not copy any more files.
- Never use a person's name in these instructions or in any output (docs, charts, reports).
  Refer to the machine and its user as "local Mac".
- Every number in an analysis doc must be traceable to a file in the run folder (section 2)
  and to a command in section 3.

## 2. Run identity and file layout

Set one timestamp per run and use it as the **prefix** of every output, so runs sort by time:

```
RUN_TS=$(date +%Y%m%d-%H%M)          # e.g. 20261015-0216
```

If the run spans several shell sessions, reuse the same `RUN_TS` (it is in the first line of
`assets/runs/${RUN_TS}/meta.txt`) instead of generating a new one.

| What | Path |
|---|---|
| Alternate build file | `pom-bench.xml` (project root, permanent; not per run) |
| Raw JMH output (logs, text, csv) | `assets/runs/${RUN_TS}/` |
| Run metadata | `assets/runs/${RUN_TS}/meta.txt` |
| Assembled results (analysis input) | `assets/runs/${RUN_TS}-<Class>Results2.adoc` |
| Analysis docs | `assets/docs/${RUN_TS}-<Class>Analysis.adoc` |
| Mermaid sources | `assets/docs/charts/${RUN_TS}-<Class>-<chart>.mmd` |
| Rendered charts | `assets/docs/charts/${RUN_TS}-<Class>-<chart>.png` |
| JDK Stream ops analysis (section 6) | `assets/jdkops/${RUN_TS}-JdkStreamOpsAnalysis.adoc` |
| JDK Stream ops charts | `assets/jdkops/charts/${RUN_TS}-JdkStreamOps-<chart>.{mmd,png}` |

`<Class>` is the benchmark class name without the `Benchmark` suffix. There are six:

| Benchmark class | `<Class>` | Compares |
|---|---|---|
| `AnyMatchUtilityBenchmark` | `AnyMatchUtility` | `stream().anyMatch` vs `Iterate.anySatisfy` / `anySatisfyWith` on a JDK `List` |
| `CountUtilityBenchmark` | `CountUtility` | `stream().filter().count()` vs `Iterate.count` / `countWith` |
| `FindFirstUtilityBenchmark` | `FindFirstUtility` | `stream().filter().findFirst()` vs `Iterate.detectOptional` / `detectWithOptional` / `detectIfNone` / `detectWithIfNone` |
| `AnyMatchEagerLazyBenchmark` | `AnyMatchEagerLazy` | as above on an EC `ImmutableList`, plus `asLazy()` |
| `CountEagerLazyBenchmark` | `CountEagerLazy` | as above on an EC `ImmutableList`, plus `asLazy()` |
| `FindFirstEagerLazyBenchmark` | `FindFirstEagerLazy` | as above on an EC `ImmutableList`, plus `asLazy()` |

The previous `*Results.adoc` files are kept for reference only. The analysis uses the new
`*Results2.adoc` files. If an analysis cites an old number, label it as from the earlier run.

## 3. Build and run

### 3.1 Prerequisites and metadata

- JDK 25 on `PATH` (`java`, `jcmd` must come from the **same** JDK; the object counter calls `jcmd`).
- Maven 3.9+, **running on JDK 25**: set `JAVA_HOME` to the JDK 25 home and check that `mvn -v`
  prints `Java version: 25...`. Maven on an older JDK cannot compile `release 25`.
- At least 16 GB RAM free for the object-count runs (12 GB heap).

Record the environment before running anything:

```
mkdir -p assets/runs/${RUN_TS}
{
  echo "run_ts: ${RUN_TS}"
  java -version 2>&1
  mvn -v
  sw_vers 2>/dev/null || uname -a
  sysctl -n machdep.cpu.brand_string hw.ncpu hw.memsize 2>/dev/null || lscpu
  java -XX:+UseCompactObjectHeaders -XX:+PrintFlagsFinal -version 2>/dev/null \
    | grep -E ' Use(G1|Parallel|Serial|Z|Shenandoah)GC |UseCompactObjectHeaders|DoEscapeAnalysis'
  git rev-parse HEAD; git status --short
} > assets/runs/${RUN_TS}/meta.txt
```

### 3.2 Alternate pom: `pom-bench.xml`

The benchmarks live under `src/test/java`, and the main `pom.xml` only declares the shade
plugin in `pluginManagement`, so `mvn package` does not produce a runnable JMH jar.
`pom-bench.xml` at the project root does that. It is a permanent project file: **do not create,
regenerate or edit it as part of a run**, and do not change `pom.xml`. If it is missing, stop and ask the local Mac user.

What it does:

- Compiles `src/test/java/conf/session/shakers/*Benchmark.java` and
  `src/test/java/prof/HistoProfiler.java` as main code (release 25) with the JMH annotation
  processor. Other test sources (`movers`, `AppTest`) are excluded and tests are skipped.
- Shades everything into `target-bench/microbenchmarks.jar` with main class `org.openjdk.jmh.Main`.
- Builds into `target-bench/`, so it never touches the main `target/`.
- Uses the same versions as `pom.xml` (Eclipse Collections 13.0.0, JMH 1.37). If `pom.xml`
  changes those versions, the local Mac user updates `pom-bench.xml` to match before the next run.

### 3.3 The three commands

1. **Compile**
   ```
   mvn -f pom-bench.xml clean compile
   ```
2. **Create the jar** (`target-bench/microbenchmarks.jar`)
   ```
   mvn -f pom-bench.xml package
   ```
   Sanity check: `java -jar target-bench/microbenchmarks.jar -l | grep shakers` lists 26 benchmarks.
   Use `microbenchmarks.jar`; the other jar in `target-bench/` is the unshaded build and will not run.
3. **Run the jar**, once per benchmark class and configuration (template; `CLASS`, `CONFIG`
   and `FLAGS` are set by the loop in 3.4):
   ```
   java -jar target-bench/microbenchmarks.jar "conf\.session\.shakers\.${CLASS}Benchmark\." \
     -prof gc \
     -jvmArgsAppend "-XX:+UseG1GC -Xms2g -Xmx2g ${FLAGS}" \
     -rf text -rff assets/runs/${RUN_TS}/${CLASS}-${CONFIG}.txt \
     -o assets/runs/${RUN_TS}/${CLASS}-${CONFIG}.log
   ```
   Forks, warmup and measurement come from the annotations (2 forks × 10 × 2 s warmup and
   measurement), so do not override them. GC and heap are pinned so runs are comparable across
   machines; the earlier run used the JDK default (G1) and an unrecorded heap size.

### 3.4 Run matrix

Run every class in each configuration. `CONFIG` names the file; `FLAGS` goes into the template.

| CONFIG | FLAGS | Purpose |
|---|---|---|
| `coh-on` | `-XX:+UseCompactObjectHeaders` | Main results |
| `coh-off` | `-XX:-UseCompactObjectHeaders` | What compact headers save |
| `ea-off` | `-XX:+UseCompactObjectHeaders -XX:-DoEscapeAnalysis` | Lambda garbage when EA cannot help (upper bound) |

```
for CONFIG in coh-on coh-off ea-off; do
  case $CONFIG in
    coh-on)  FLAGS="-XX:+UseCompactObjectHeaders" ;;
    coh-off) FLAGS="-XX:-UseCompactObjectHeaders" ;;
    ea-off)  FLAGS="-XX:+UseCompactObjectHeaders -XX:-DoEscapeAnalysis" ;;
  esac
  for CLASS in AnyMatchUtility CountUtility FindFirstUtility \
               AnyMatchEagerLazy CountEagerLazy FindFirstEagerLazy; do
    java -jar target-bench/microbenchmarks.jar "conf\.session\.shakers\.${CLASS}Benchmark\." \
      -prof gc -jvmArgsAppend "-XX:+UseG1GC -Xms2g -Xmx2g ${FLAGS}" \
      -rf text -rff assets/runs/${RUN_TS}/${CLASS}-${CONFIG}.txt \
      -o assets/runs/${RUN_TS}/${CLASS}-${CONFIG}.log
  done
done
```

Expected duration: 26 methods × 3 params × 2 forks × ~42 s ≈ 1.8 h per configuration,
so about 5.5 h for all three (the smoke test of `AnyMatchUtility`, 9 of the 78, took 12 minutes). Time is measured per configuration so a partial run is still usable.

### 3.5 Object counts (histogram profiler)

JMH `-prof gc` reports bytes per call, not objects. `prof.HistoProfiler`
(`src/test/java/prof/HistoProfiler.java`, already in the jar) runs
`jcmd <pid> GC.class_histogram -all` before and after each measurement iteration and divides
the per-class difference by the number of calls. It needs iterations with **no GC**, so use
short iterations and a big young generation. Run it with compact headers on and off: object
**counts** should match, while **bytes** show what compact headers save per class. Run it a third
time with escape analysis off (`histo-ea-off`): on a normal JVM the EC methods allocate nothing,
so only this run shows which objects their code creates (capturing lambda, `Predicates.bind`
wrapper, `asLazy()` adapter, `Optional`), which the JDK Stream ops analysis (section 6) compares.

```
for CONFIG in histo-coh-on histo-coh-off histo-ea-off; do
  case $CONFIG in
    histo-coh-on)  FLAGS="-XX:+UseCompactObjectHeaders" ;;
    histo-coh-off) FLAGS="-XX:-UseCompactObjectHeaders" ;;
    histo-ea-off)  FLAGS="-XX:+UseCompactObjectHeaders -XX:-DoEscapeAnalysis" ;;
  esac
  java -jar target-bench/microbenchmarks.jar "conf\.session\.shakers\." \
    -f 1 -wi 5 -w 1s -i 3 -r 100ms \
    -jvmArgs "-XX:+UseParallelGC -Xms12g -Xmx12g -Xmn10g ${FLAGS}" \
    -prof prof.HistoProfiler -prof gc \
    -rf text -rff assets/runs/${RUN_TS}/all-${CONFIG}.txt \
    -o assets/runs/${RUN_TS}/all-${CONFIG}.log \
    | grep '^HISTO' > assets/runs/${RUN_TS}/all-${CONFIG}.histo
done
```

**Prerequisite (one-time code fix):** `HistoProfiler.histo()` must send `jcmd` output to a temp
file (`ProcessBuilder.redirectOutput(file)`), not read it from a pipe. The histogram is written
while the benchmark VM is at a safepoint; once it is larger than the pipe buffer (64 KB, which the
JMH + EC class list exceeds), `jcmd` blocks on the full pipe and the VM never resumes to drain it.
The run then hangs forever at `Iteration 1:` with no error (seen in the 2026-10-15 smoke test on
JDK 25). Confirm the fix is in place before running.

How to read the output:

- The profiler prints from the forked benchmark VM, and JMH sends that to the **console, not the
  `-o` file**, so the loop above captures the `HISTO` lines into `all-${CONFIG}.histo`.
- Per-class breakdowns are the `HISTO` lines in the `.histo` file: one block per measurement iteration,
  headed `HISTO <benchmark> test=<n> clean=<true|false>`, listing every class with at least
  0.5 objects per call.
- **Use the per-class lines, not the totals.** The `total` line and the `histo.objs.norm` /
  `histo.bytes.norm` summary rows include the profiler's own allocations (the `jcmd` call,
  heap filler objects) divided by the number of calls. With 100 ms iterations that noise is
  hundreds to thousands of bytes per call, and it makes EC look like it allocates when it does
  not. Objects per call = sum of the per-class counts; bytes per call = sum of the per-class bytes.
- Check: the per-class byte sum should equal `gc.alloc.rate.norm` from the main run of the same
  configuration (in the smoke test, `jdkAnyMatch` at test = 1: 32 + 48 + 16 + 16 + 24 + 24 = 160 B,
  matching 160 B/op). If it does not, say so in the analysis.
- `histo.clean` is the minimum over iterations. Use only `clean=true` blocks; if a benchmark has
  no clean iteration, rerun just that benchmark with a larger `-Xmn`.
- Expected duration: roughly 10 to 15 minutes per configuration for all 26 benchmarks.
- ParallelGC is used here only to make GC-free iterations easy. These numbers are for counting,
  not for timing.

### 3.6 Assemble the Results2 files

For each class, create `assets/runs/${RUN_TS}-<Class>Results2.adoc` (next to the
`assets/runs/${RUN_TS}/` folder, so results sort by run) containing:

1. A header with `RUN_TS` and the contents of `meta.txt`.
2. One section per configuration (`coh-on`, `coh-off`, `ea-off`) with the JMH text table
   from `<Class>-<CONFIG>.txt` copied verbatim in a `....` literal block.
3. An object-count section with a table per configuration (`histo-coh-on`, `histo-coh-off`,
   `histo-ea-off`):
   benchmark, test, objects per call and bytes per call (summed from the per-class lines,
   averaged over clean iterations, see 3.5), and the per-class `HISTO` lines themselves.
4. The exact commands used (copied from this file with variables expanded).

Copy numbers verbatim; do not round or reformat the raw tables.

## 4. Analysis docs

Write **one analysis doc per Results2 file** (six docs):
`assets/docs/${RUN_TS}-<Class>Analysis.adoc`. Each doc covers the following, in this order.

### 4.1 Summary (top of doc)

Three to five sentences: how much garbage each approach creates per call, what that means for
heap headroom under a spike, and the throughput difference. Lead with memory.

### 4.2 Head-to-head table (`coh-on`)

One combined table grouped by `test` (1, 500, 1000), with these columns:

- Implementation (also mark it: JDK stream / EC capturing / EC fat-free `*With` / EC lazy)
- Throughput (M calls/s) = 1000 ÷ ns per call
- Time per call (ns, ± error)
- Objects per call (from `histo-coh-on`)
- Bytes per call (`gc.alloc.rate.norm`)
- GC runs per 1 billion calls
- GC time per 1 billion calls (ms)

Add a comparison row per group, for example
"EC `anySatisfyWith` vs JDK: _N_x faster, _k_ fewer objects and _b_ fewer bytes per call, 0 vs _g_ GC runs per billion calls"
(fill in from the data; do not reuse numbers from the earlier run).

### 4.3 Normalize, don't compare raw totals

- Compare per call or per fixed number of calls, never raw `gc.count` totals: a faster method
  fits more calls into the same window, so its raw totals look worse than they are.
- Calls measured ≈ measurement time ÷ ns per call. Measurement time = forks × iterations ×
  iteration time = 2 × 10 × 2 s = **40 s** for the main runs.
- Per-billion figures = (raw count ÷ calls measured) × 10⁹.
- JMH omits `gc.time` when it is 0, and prints `≈ 0` for tiny values; treat both as 0 and say so.
- Keep raw GC totals in a separate, clearly labelled section further down.

### 4.4 Garbage: where it comes from

- Objects per call with a breakdown by class for each implementation (from the `HISTO` lines).
- Split them into **pipeline garbage** and **lambda garbage** (section 0).
- Use the `ea-off` run to show lambda garbage that EA hides: compare EC capturing vs EC
  `*With` bytes per call with EA off. State clearly that this is an upper bound.
- Explain mechanisms, marking each as measured or inferred: stream pipeline objects created
  per call; the `Integer` box outside the -128..127 cache (note that `*With` methods also box
  `this.test`, an `int`, when passing it as `Object`); EA removing allocations; the `Optional`
  that `findFirst` and `detectOptional` must return.

### 4.5 Spikes and heap headroom (derived)

This is the section that makes the memory point. Using bytes per call from 4.2, compute for
each implementation at two example loads (1 M and 10 M calls/s):

- Allocation rate (MB/s) = bytes per call × calls/s.
- Young collections per second for an example 512 MB young generation = allocation rate ÷ 512 MB.
- Extra young-gen headroom needed to absorb a 10 s spike at that load without an extra collection
  = bytes per call × calls/s × 10 s.

Label every figure here as derived, not measured, and show the formula next to the table.
Explain that `gc.alloc.rate` (MB/s) from JMH is misleading on its own because it scales with
throughput; `gc.alloc.rate.norm` (bytes per call) is the figure to plan capacity with.

### 4.6 Compact object headers

Compare `coh-on` vs `coh-off` for bytes per call and GC runs per billion calls, and
`histo-coh-on` vs `histo-coh-off` per class. Object counts should match; if they do not, flag it.
Note the effect on throughput if any, and whether it is within the error bars.

### 4.7 Supporting analysis

- Fixed cost vs per-element cost, from `test = 1` vs `test = 1000`. Note that `count`
  always scans all 1000 elements, so its cost should be flat across `test`.
- Eager vs lazy EC (EagerLazy classes only): what `asLazy()` adds in objects and time.
- Anything unusual. Example from the earlier run: very wide error bars on `jdkCount` at
  `test = 1` and `500` (about ±35%), which suggests bimodal behaviour across forks; check the
  per-fork numbers in the `.log` before drawing conclusions.
- Caveats: one machine, one JDK build, microbenchmark not application; mechanisms inferred, not
  profiled, unless the histogram shows them.

### 4.8 Reproduce

List the exact commands (section 3, variables expanded), the run folder, and the Results2 file used.

## 5. Charts

Every chart must be understandable by someone who has not read the doc. Rules:

- **Title** says, in plain words, the operation, the collection and what is measured, e.g.
  "anyMatch on a JDK ArrayList (EC via Iterate): garbage left behind per call". No class names,
  config codes (`coh-on`) or abbreviations in titles.
- **Subtitle** says which direction is better ("Lower is better") and the one-sentence takeaway with
  the measured numbers (e.g. "The JDK stream leaves 160 to 192 B per call; every EC version leaves 0 B").
- **Grouped bars**, never lines: one group per match position ("match at element 1 / 500 / 1000"),
  one bar per implementation.
- **Every bar is labelled with its value and unit** (e.g. "192 B", "12.0x / 396 M/s", "2,240 MB"),
  including zeros, so nothing needs to be read off the axis.
- **Legend under the chart** naming every bar: `<benchmark method>: <plain description>`.
- **Colour-blind-safe colours only**, from the Okabe-Ito palette, one fixed colour per kind in
  every chart: orange `#E69F00` = JDK stream; sky blue `#56B4E9` = EC eager, capturing; reddish
  purple `#CC79A7` = EC lazy (asLazy), capturing; dark blue `#0072B2` = EC `*With`, fat-free;
  black `#000000` = EC `*With` that is not fat-free (on an EC 13.0.0 `ImmutableList`, every `*With`
  except `detectWithOptional` goes through `Predicates.bind`). Never use red and green together, and
  never default library colours. The Mermaid `.mmd` bars use dark blue `#0072B2`.
- **Colour is never the only cue.** A second method of the same kind keeps the colour and gets a
  hatch pattern (`//`, then `..`, `xx`); the legend and the value on every bar identify each bar.
- If a kind or colour is added, check the palette with a colour-blindness simulation (protanopia,
  deuteranopia, tritanopia) and keep every pair clearly apart. The current five stay at least
  17 CIELAB ΔE apart under all three (above about 10 reads as clearly different).
- **Y-axis label carries the unit** and the flag where relevant (e.g. "bytes allocated per call
  (-XX:-DoEscapeAnalysis)").
- A footnote under the chart explains any bar that would otherwise surprise (e.g. the orange bars).

| `<chart>` | What it shows (title ends with) |
|---|---|
| `throughput` | "how many times faster than the JDK stream" (JDK = 1x; labels also give M calls/s). Relative because absolute M calls/s span 1000x across match positions |
| `bytes-per-call` | "garbage left behind per call" (`gc.alloc.rate.norm`, `coh-on`) |
| `gc-per-billion` | "garbage collections per billion calls" |
| `ea-off-bytes` | "garbage per call when escape analysis is off" (clearest view of capturing vs fat-free) |
| `coh-bytes` | "bytes per call saved by compact object headers" (`coh-off` minus `coh-on`, with %) |
| `spike-headroom` | "heap needed to absorb a 10-second spike" (bytes per call x 1 M calls/s x 10 s, in MB) |

Mermaid `xychart-beta` cannot do this (no legend, one colour per series, bar series overlap,
labels get clipped), so each chart is written twice from the same numbers:

- `<chart>.png`: drawn with matplotlib following the rules above. This is the image the analysis
  doc embeds and GitHub shows.
- `<chart>.mmd`: Mermaid source with the same title, as one horizontal bar per
  implementation and match position, each bar labelled `<method> @<position> = <value>`.
  Labels are left-padded with non-breaking spaces because Mermaid under-measures their width.

Generate both, plus the Results2 files and analysis docs, with the generator (Python 3 with
matplotlib; Node with `@mermaid-js/mermaid-cli@11` is only used to check that each `.mmd` parses):

```
python3 -m pip install matplotlib        # once
npm install @mermaid-js/mermaid-cli@11   # once, in a tools folder; optional
MMDC=<tools>/node_modules/.bin/mmdc \
  python3 assets/runs/${RUN_TS}/tools/gen.py . ${RUN_TS} assets/runs/${RUN_TS}/tools/notes.json
```

`gen.py` and `charts.py` (copy them from the previous run's `tools/` folder) hold the chart rules;
`notes.json` holds the per-class narrative and must be rewritten from the new numbers each run.
`CLASSES=AnyMatchUtility,...` limits the classes. Look at every PNG before reporting.

In the analysis doc, embed the PNG with a one-line caption and link the source:
```
.<caption>
image::charts/<RUN_TS>-<Class>-<chart>.png[<caption>]
Mermaid source: link:charts/<RUN_TS>-<Class>-<chart>.mmd[...]
```

## 6. JDK Stream ops analysis

One extra doc per run: which objects one JDK `stream()` call allocates, which part of the Stream
API makes each one, and what that costs; then the same numbers for the Eclipse Collections (EC)
versions of each operation, so every JDK number sits next to its EC equivalent. It explains *why*
the numbers in the per-class docs are what they are.

**Inputs:** `assets/runs/${RUN_TS}/all-histo-coh-on.histo`, `all-histo-coh-off.histo`,
`all-histo-ea-off.histo` (section 3.5) and `summary.json` (written by `gen.py`, so run section 5
first).

**Outputs:**

| What | Path |
|---|---|
| Analysis doc | `assets/jdkops/${RUN_TS}-JdkStreamOpsAnalysis.adoc` |
| Bytes per call by origin (stacked bars) | `assets/jdkops/charts/${RUN_TS}-JdkStreamOps-bytes-by-origin.{mmd,png}` |
| Size of each class, compact headers off vs on | `assets/jdkops/charts/${RUN_TS}-JdkStreamOps-class-sizes-coh.{mmd,png}` |
| JDK vs every EC version, bytes by origin with escape analysis off, one per operation | `assets/jdkops/charts/${RUN_TS}-JdkStreamOps-vs-ec-{anyMatch,findFirst,count}.{mmd,png}` |

**Command** (copy `jdk_ops.py` from the previous run's `tools/` folder; it needs `parse.py` and
`charts.py` next to it; output goes to `assets/jdkops/` by default, or to a folder given as a
third argument):

```
python3 assets/runs/${RUN_TS}/tools/jdk_ops.py . ${RUN_TS}
```

**What the doc contains** (the script writes all of it from the data):

1. Summary: fixed objects and bytes per call, the Stream source share, how the terminal ops
   differ, the cost of `filter`, escape analysis, compact headers, setup time.
2. The three pipelines: the exact benchmark code for `anyMatch`, `findFirst` and `count`.
3. Every object one call allocates (compact headers on, `test` = 500): class, origin, bytes for
   each operation, and why the Stream API allocates it.
4. Bytes by origin for every match position, and a stacked-bar chart of it.
5. The terminal ops compared: MatchOps, FindOps and ReduceOps, new objects per call, and what
   could be avoided.
6. Escape analysis on vs off for the JDK benchmarks.
7. Compact headers: per-class sizes off vs on (chart), and bytes per call off vs on.
8. Cost to an application: MB/s at 1 M and 10 M calls/s, measured GC runs per billion calls, and
   the setup time at match position 1 (JDK vs fastest EC).
9. The same three operations in Eclipse Collections:
   - where each JDK object goes in EC (a mapping table: Stream source, filter stage and terminal
     op have no EC counterpart);
   - on a normal JVM, one table per operation with every implementation (JDK, `Iterate` on the
     JDK list, `ImmutableList` eager and `asLazy()`): lambda shape, ns per call at elements 1
     and 500, bytes per call, GC runs per billion calls;
   - with escape analysis off, one stacked-bar chart and one table per operation: the objects
     each implementation creates, by origin, with its normal-JVM bytes beside it;
   - every object the EC versions allocate and why;
   - fat-free vs capturing, including which `*With` methods are not fat-free.
10. Caveats and the reproduce command.

**Rules:**

- Every class in the JDK `HISTO` blocks is attributed to one origin: *Stream source*
  (`stream()`: pipeline head, spliterator), *filter stage*, *terminal op* (MatchOps, FindOps,
  ReduceOps sinks, ops and results), *caller lambda* (the benchmark's `$$Lambda`) or *Integer
  box*. The mapping and the reason for each class are the `ORIGIN` table in `jdk_ops.py`. A class
  the table does not know shows as `other`: check where it comes from and add it to the table
  before reporting.
- Check the origins against the JDK that ran the benchmarks, with
  `javap -p -c java.util.stream.MatchOps` (and `FindOps`, `ReduceOps`, `ReferencePipeline`); the
  JDK's `src.zip` may be missing. If a JDK update changes what is allocated or cached (for
  example, if `ReduceOps.makeRefCounting()` starts returning a shared op), update the `ORIGIN`
  table and the terminal-op notes in `jdk_ops.py`.
- EC classes are attributed with the `EC_ORIGIN` table in `jdk_ops.py`: *caller lambda*,
  *Integer box* or *EC object* (`Predicates$BindPredicate2`, `LazyIterableAdapter`, `CountProcedure`, the
  `Optional` from `detectOptional`). Check them against the EC jar that ran, with
  `javap -c -cp eclipse-collections-<version>.jar` on `Iterate`, `ImmutableArrayList` and
  `RandomAccessListIterate`. If an EC update overrides the `*With` methods on `ImmutableList`
  (so they no longer go through `Predicates.bind`), update `EC_ORIGIN`, the doc text and the
  `classify()` rule in `charts.py`.
- The EC implementations and their code are the `IMPLS` table in `jdk_ops.py`; when a benchmark
  method is added or renamed, update it.
- Use the `*Utility` runs for the JDK side. The `*EagerLazy` benchmarks run the same JDK code;
  check that their JDK blocks match and say so in the doc if they do not.
- Label anything not read from the data or the bytecode as inferred (for example, why escape
  analysis gives up, or the saving from caching an op).
- Charts follow section 5. Origin colours (Okabe-Ito): orange `#E69F00` = Stream source, sky blue
  `#56B4E9` = filter stage, dark blue `#0072B2` = terminal op, reddish purple `#CC79A7` = caller
  lambda, black with a hatch pattern = Integer box, vermillion `#D55E00` with a cross-hatch = EC
  object. These six stay at least 17 CIELAB ΔE apart under protanopia, deuteranopia and
  tritanopia simulation.

## 7. Reporting back

- Report which files were created (including the JDK Stream ops doc), the run folder, and any benchmarks with no clean
  histogram iteration or suspicious error bars.
- Be ready to give the exact command behind any number.
- Do not copy files to the local Mac unless they are named explicitly.
