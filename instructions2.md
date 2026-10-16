# Benchmark analysis instructions (v3: shakers and movers, numbered)

How to re-run the two parts of this project and turn their results into analysis docs and charts:

- **Shakers**: the JMH benchmarks in `src/test/java/conf/session/shakers/`, comparing the JDK Stream
  API with Eclipse Collections (EC) for throughput, garbage per call and object counts.
- **Movers**: the JOL footprint tests in `src/test/java/conf/session/movers/` (no JMH).

Every item has an ID so a change can be pinpointed: `G` = ground rules, `L` = file layout,
`S` = shakers, `A` = shakers asks checklist, `M` = movers, `C` = changes from `instructions.md`.
IDs are stable: when an item is removed, its ID is not reused.

This file sits next to `instructions.md` and does not replace it. Section C lists what differs.

---

## G. Ground rules (shakers and movers)

- **G1** Do not edit or delete any existing file. Only add new files: the outputs named in section L.
  An explicit request from the local Mac user overrides this for the file it names.
- **G2** Do not move existing outputs into the new layout (L). Files made before this layout stay
  where they are; only new runs use it.
- **G3** `pom-bench.xml` is a permanent project file, not a run output (S3.2).
- **G4** Before starting a run, get confirmation from the local Mac user. Shakers runs take several
  hours (S3.4.4) and need a quiet machine on mains power.
- **G5** If the local Mac user says "stop", halt the current step and do not copy any more files.
- **G6** Copy to the local Mac only the files the local Mac user names.
- **G7** Never use a person's name in these instructions or in any output (docs, charts, reports).
  Refer to the machine and its user as "local Mac".
- **G8** Java style, if a run ever needs a Java change (for example the HistoProfiler fix in S3.5.2):
  - **G8.1** The opening brace goes on its own line (Allman) for classes, records, methods,
    `if`/`try`/`catch` and block lambdas, matching every existing class.
  - **G8.2** `HistoProfiler` uses Eclipse Collections, not `java.util` collections.
  - **G8.3** Comment any non-obvious logic.
- **G9** Every number in an analysis doc must be traceable to a file in a run folder (L) and to a
  command (S3 or M2).
- **G10** After copying a file to the local Mac, read it back (md5) about 15 seconds later. If it
  differs, copy again. Then ask the local Mac user to reload it from disk, because an editor that
  has the file open (IntelliJ) can write the old content back.

---

## L. File layout

- **L1** Every output file name starts with the run's datetime, so runs sort by time:
  `RUN_TS` for shakers, `MOVERS_TS` for movers, both `$(date +%Y%m%d-%H%M)`, e.g. `20261015-2130`.
- **L2** Shakers paths (written by the tools in `assets/tools/shakers/`, see `layout.py`):

  | ID | What | Path |
  |---|---|---|
  | L2.1 | Raw JMH output, logs, `meta.txt`, `notes.json`, `summary.json`, `*.histo` | `assets/runs/shakers/${RUN_TS}/` |
  | L2.2 | Assembled results, one per class | `assets/runs/shakers/${RUN_TS}-<Class>Results2.adoc` |
  | L2.3 | Analysis doc, one per class | `assets/docs/shakers/${RUN_TS}-<Class>Analysis.adoc` |
  | L2.4 | Run summary | `assets/docs/shakers/${RUN_TS}-Summary.adoc` |
  | L2.5 | JDK Stream ops analysis | `assets/docs/shakers/${RUN_TS}-JdkStreamOpsAnalysis.adoc` |
  | L2.6 | Mermaid sources | `assets/docs/shakers/mermaid/${RUN_TS}-<Class>-<chart>.mmd` and `${RUN_TS}-JdkStreamOps-<chart>.mmd` |
  | L2.7 | Chart images (PNG) | `assets/images/shakers/${RUN_TS}-<Class>-<chart>.png` and `${RUN_TS}-JdkStreamOps-<chart>.png` |

- **L3** Movers paths (written by `assets/tools/movers/movers.py`):

  | ID | What | Path |
  |---|---|---|
  | L3.1 | Results files, Maven logs, `meta.txt` | `assets/runs/movers/${MOVERS_TS}/` |
  | L3.2 | Analysis doc, one per test | `assets/docs/movers/${MOVERS_TS}-<Test>Analysis.adoc` |
  | L3.3 | Mermaid sources | `assets/docs/movers/mermaid/${MOVERS_TS}-<Test>-<chart>.mmd` |
  | L3.4 | Chart images (PNG) | `assets/images/movers/${MOVERS_TS}-<Test>-<chart>.png` |

- **L4** Tools (permanent, shared by every run; copy a newer version in only as a new file name or
  when the local Mac user asks):

  | ID | Tool | Path |
  |---|---|---|
  | L4.1 | Paths for every shakers tool | `assets/tools/shakers/layout.py` |
  | L4.2 | Results2, analysis docs, charts, `summary.json` | `assets/tools/shakers/gen.py` (needs `charts.py`, `parse.py`) |
  | L4.3 | JDK Stream ops analysis | `assets/tools/shakers/jdk_ops.py` |
  | L4.4 | Movers analysis and charts | `assets/tools/movers/movers.py` |

- **L5** Docs link to their images and Mermaid sources relatively, so they render on GitHub and in
  IntelliJ: `image::../../images/<part>/<file>.png[...]` and `link:mermaid/<file>.mmd[...]`.
- **L6** `<Class>` is the benchmark class without `Benchmark` (S2.2). `<Test>` is the movers test
  class without `Test` (M1.2).
- **L7** Older files keep their places (G2): `assets/docs/*.adoc`, `assets/docs/charts/`,
  `assets/movers/`, `assets/runs/20261015-0406*`, and the `*Results*.md` files in the movers package.
  Exception: `assets/jdkops/` is merged into the shakers layout. The 20261015-0406 JDK Stream ops doc
  was regenerated into `assets/docs/shakers/` (Mermaid in `mermaid/`) with its PNGs in
  `assets/images/shakers/`, and `assets/jdkops/` is no longer used.
- **L8** To regenerate a run made before this layout, point the shakers tools at its raw folder
  with `SHAKERS_RUN_DIR=assets/runs/<RUN_TS>`; the docs and charts still go to L2.3 to L2.7.
  `gen.py` writes `summary.json` into that folder, so ask the local Mac user first (G1).

---

## Shakers (JMH benchmarks)

### A. Shakers asks checklist

Every request made for the shakers work so far and the item that covers it. Check a run against
this list before reporting (S8).

| ID | Ask | Covered in |
|---|---|---|
| A1 | Rerun only the `shakers` benchmarks; movers is a separate part | Scope, M |
| A2 | Every output file name starts with the run datetime, so runs sort by time | L1 |
| A3 | `pom-bench.xml` is permanent at the project root; never create or edit it during a run | G3, S3.2 |
| A4 | Separate compile, jar and run commands | S3.3 |
| A5 | Maven runs on JDK 25 (compact object headers) | S3.1.2 |
| A6 | Run with compact headers on and off, plus an escape-analysis-off run | S3.4 |
| A7 | Object counts per class with the HistoProfiler, compact headers on, off and escape analysis off | S3.5 |
| A8 | HistoProfiler sends `jcmd` output to a temp file (fixes the JDK 25 deadlock) | S3.5.2 |
| A9 | HistoProfiler uses Eclipse Collections, has comments on complex logic, uses Allman braces | G8 |
| A10 | Use the per-class `HISTO` lines, not the totals (profiler noise) | S3.5.6 |
| A11 | `HISTO` lines go to the console, not the `-o` file; capture them | S3.5.5 |
| A12 | Regenerated results go to `*Results2.adoc` files; the old `*Results.adoc` stay for reference | L2.2, S3.6 |
| A13 | One analysis doc per Results2 file | S4 |
| A14 | The story: fat-free lambdas make less garbage; memory footprint and heap headroom for spikes, not only throughput, and not "EC wins because of `*With`" | S1, S4.5 |
| A15 | Charts a viewer understands without the doc: plain title, "better" subtitle with the takeaway, legend, a value on every bar, grouped bars per match position | S5.1 |
| A16 | Colour-blind-safe colours only (Okabe-Ito, never red with green), checked by simulation when one is added | S5.1.6, S5.1.8, S6.5.6 |
| A17 | Every chart as a matplotlib PNG plus a separate Mermaid `.mmd` file | S5.3 |
| A18 | A JDK Stream ops analysis (Stream, FindOps, MatchOps, ReduceOps allocations) | S6 |
| A19 | EC numbers and comparisons next to every JDK number in the JDK Stream ops analysis | S6.4.9 |
| A20 | EC 13.0.0 `ImmutableList` `*With` methods (except `detectWithOptional`) go through `Predicates.bind` and are not fat-free; say so wherever they appear | S5.1.6, S6.5.4 |
| A21 | `asLazy().count` also allocates a `CountProcedure`; attribute it as an EC object | S6.5.4 |
| A22 | A run summary doc linking the six analyses | S7 |
| A23 | Shakers analysis docs, Summary and Mermaid files under `assets/docs/shakers/`, PNGs under `assets/images/shakers/` | L2 |
| A24 | Every instruction is a numbered item | this file |
| A25 | Never use a person's name; "local Mac" | G7 |
| A26 | Copy only named files to the local Mac; stop when told "stop" | G5, G6 |
| A27 | Do not edit or delete existing files; only add new ones | G1, G2 |
| A28 | After a copy, check the file on the local Mac and ask for a reload from disk | G10 |

### S1. Intent (read this first)

- **S1.1** The story is **fat-free lambdas make less garbage**. This is about memory, not only
  speed: an application that allocates less per call needs less heap headroom to absorb a traffic
  spike, collects less often, and does not have to be over-provisioned "just in case".
- **S1.2** *Fat-free lambda*: a lambda or method reference that captures nothing, so the JVM can
  reuse one instance forever. Example: `Integer::equals` in
  `ecList.anySatisfyWith(Integer::equals, this.test)`. EC's `*With` methods (`anySatisfyWith`,
  `countWith`, `detectWithOptional`, `detectWithIfNone`) exist so the extra value is passed as a
  parameter instead of being captured.
- **S1.3** *Capturing ("fat") lambda*: one that closes over a value, so a new object may be created
  per call. Example: `Integer.valueOf(this.test)::equals` captures the boxed `Integer`.
- **S1.4** *Compact object headers* (`-XX:+UseCompactObjectHeaders`, JDK 25) shrink every object
  header, so each unavoidable object costs fewer bytes. Show what the flag saves by comparing runs
  with it on and off.
- **S1.5** Keep two effects separate in every analysis, because they have different causes:
  - **S1.5.1** *Pipeline garbage*: the objects `stream()` builds on every call (spliterator,
    pipeline stages, sink, `Optional`, ...). EC's eager methods iterate directly and build none.
  - **S1.5.2** *Lambda garbage*: the captured lambda and any boxed `Integer` (values outside
    -128..127 are not cached, so `test = 500` and `test = 1000` box on every call).
- **S1.6** Be honest where the data does not show a difference. In these microbenchmarks escape
  analysis (EA) often removes the captured lambda, so EC capturing and EC `*With` look the same.
  The EA-off run (S3.4) shows the cost the lambda shape carries where EA cannot help (megamorphic
  call sites, deep call chains, code that is not inlined). Label it an upper bound, not typical
  production behaviour.

### S2. Benchmark classes

- **S2.1** Six classes, 26 benchmark methods, each at `test` = 1, 500 and 1000 (match position).
- **S2.2** The classes:

  | ID | Benchmark class | `<Class>` | Compares |
  |---|---|---|---|
  | S2.2.1 | `AnyMatchUtilityBenchmark` | `AnyMatchUtility` | `stream().anyMatch` vs `Iterate.anySatisfy` / `anySatisfyWith` on a JDK `List` |
  | S2.2.2 | `CountUtilityBenchmark` | `CountUtility` | `stream().filter().count()` vs `Iterate.count` / `countWith` |
  | S2.2.3 | `FindFirstUtilityBenchmark` | `FindFirstUtility` | `stream().filter().findFirst()` vs `Iterate.detectOptional` / `detectWithOptional` / `detectIfNone` / `detectWithIfNone` |
  | S2.2.4 | `AnyMatchEagerLazyBenchmark` | `AnyMatchEagerLazy` | as above on an EC `ImmutableList`, plus `asLazy()` |
  | S2.2.5 | `CountEagerLazyBenchmark` | `CountEagerLazy` | as above on an EC `ImmutableList`, plus `asLazy()` |
  | S2.2.6 | `FindFirstEagerLazyBenchmark` | `FindFirstEagerLazy` | as above on an EC `ImmutableList`, plus `asLazy()` |

- **S2.3** The analysis uses the `*Results2.adoc` files. The older `*Results.adoc` files are for
  reference only; an old number cited in an analysis is labelled as from the earlier run.

### S3. Build and run

#### S3.1 Prerequisites and metadata

- **S3.1.1** JDK 25 on `PATH`; `java` and `jcmd` must come from the same JDK (HistoProfiler calls `jcmd`).
- **S3.1.2** Maven 3.9+ running on JDK 25: set `JAVA_HOME` to the JDK 25 home and check that
  `mvn -v` prints `Java version: 25...`. Maven on an older JDK cannot compile `release 25`.
- **S3.1.3** At least 16 GB RAM free for the object-count runs (12 GB heap).
- **S3.1.4** Set the run timestamp once (L1). If the run spans several shell sessions, reuse it
  (first line of `meta.txt`) instead of making a new one.
  ```
  RUN_TS=$(date +%Y%m%d-%H%M)
  R=assets/runs/shakers/${RUN_TS}
  mkdir -p $R
  ```
- **S3.1.5** Record the environment before running anything. The `environment:` line is read by
  the tools and shown in every doc; describe the machine without a person's name (G7).
  ```
  {
    echo "run_ts: ${RUN_TS}"
    echo "environment: local Mac ($(sysctl -n machdep.cpu.brand_string 2>/dev/null || uname -m))"
    java -version 2>&1
    mvn -v
    sw_vers 2>/dev/null || uname -a
    sysctl -n machdep.cpu.brand_string hw.ncpu hw.memsize 2>/dev/null || lscpu
    java -XX:+UseCompactObjectHeaders -XX:+PrintFlagsFinal -version 2>/dev/null \
      | grep -E ' Use(G1|Parallel|Serial|Z|Shenandoah)GC |UseCompactObjectHeaders|DoEscapeAnalysis'
    git rev-parse HEAD; git status --short
  } > $R/meta.txt
  ```

#### S3.2 Alternate pom: `pom-bench.xml`

- **S3.2.1** The benchmarks live under `src/test/java` and the main `pom.xml` declares the shade
  plugin only in `pluginManagement`, so `mvn package` does not produce a runnable JMH jar.
  `pom-bench.xml` at the project root does that.
- **S3.2.2** Do not create, regenerate or edit `pom-bench.xml` during a run, and do not change
  `pom.xml`. If `pom-bench.xml` is missing, stop and ask the local Mac user.
- **S3.2.3** It compiles `src/test/java/conf/session/shakers/*Benchmark.java` and
  `src/test/java/prof/HistoProfiler.java` as main code (release 25) with the JMH annotation
  processor. Other test sources (movers, `AppTest`) are excluded and tests are skipped.
- **S3.2.4** It shades everything into `target-bench/microbenchmarks.jar` (main class
  `org.openjdk.jmh.Main`) and builds into `target-bench/`, never touching `target/`.
- **S3.2.5** It uses the same versions as `pom.xml` (Eclipse Collections 13.0.0, JMH 1.37). If
  `pom.xml` changes them, the local Mac user updates `pom-bench.xml` before the next run.

#### S3.3 The three commands

- **S3.3.1** Compile:
  ```
  mvn -f pom-bench.xml clean compile
  ```
- **S3.3.2** Create the jar:
  ```
  mvn -f pom-bench.xml package
  ```
- **S3.3.3** Sanity check: `java -jar target-bench/microbenchmarks.jar -l | grep shakers` lists
  26 benchmarks. Use `microbenchmarks.jar`; the other jar in `target-bench/` is unshaded and will not run.
- **S3.3.4** Run the jar, once per class and configuration (`CLASS`, `CONFIG` and `FLAGS` come from S3.4):
  ```
  java -jar target-bench/microbenchmarks.jar "conf\.session\.shakers\.${CLASS}Benchmark\." \
    -prof gc \
    -jvmArgsAppend "-XX:+UseG1GC -Xms2g -Xmx2g ${FLAGS}" \
    -rf text -rff $R/${CLASS}-${CONFIG}.txt \
    -o $R/${CLASS}-${CONFIG}.log
  ```
- **S3.3.5** Forks, warmup and measurement come from the annotations (2 forks × 10 × 2 s warmup
  and measurement); do not override them. GC and heap are pinned so runs compare across machines.

#### S3.4 Run matrix

- **S3.4.1** Configurations:

  | ID | CONFIG | FLAGS | Purpose |
  |---|---|---|---|
  | S3.4.1.1 | `coh-on` | `-XX:+UseCompactObjectHeaders` | Main results |
  | S3.4.1.2 | `coh-off` | `-XX:-UseCompactObjectHeaders` | What compact headers save |
  | S3.4.1.3 | `ea-off` | `-XX:+UseCompactObjectHeaders -XX:-DoEscapeAnalysis` | Lambda garbage when EA cannot help (upper bound) |

- **S3.4.2** Loop:
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
        -rf text -rff $R/${CLASS}-${CONFIG}.txt \
        -o $R/${CLASS}-${CONFIG}.log
    done
  done
  ```
- **S3.4.3** Every configuration is complete on its own, so a partial run is still usable.
- **S3.4.4** Expected duration: 26 methods × 3 params × 2 forks × about 42 s ≈ 1.8 h per
  configuration, about 5.5 h for all three.

#### S3.5 Object counts (HistoProfiler)

- **S3.5.1** JMH `-prof gc` reports bytes per call, not objects. `prof.HistoProfiler` runs
  `jcmd <pid> GC.class_histogram -all` before and after each measurement iteration and divides the
  per-class difference by the number of calls. It needs GC-free iterations, so it uses short
  iterations and a big young generation.
- **S3.5.2** Prerequisite (one-time code fix): `HistoProfiler.histo()` sends `jcmd` output to a
  temp file (`ProcessBuilder.redirectOutput(file)`), not a pipe. The histogram is written while the
  benchmark VM is at a safepoint; once it is larger than the 64 KB pipe buffer, `jcmd` blocks and
  the VM never resumes, so the run hangs at `Iteration 1:` with no error. Confirm the fix is in
  place before running.
- **S3.5.3** Three configurations: compact headers on, off (counts should match, bytes show the
  saving per class) and escape analysis off (the only run that shows which objects the EC code
  creates: capturing lambda, `Predicates.bind` wrapper, `asLazy()` adapter, `CountProcedure`,
  `Optional`; S6 uses it).
- **S3.5.4** Loop:
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
      -rf text -rff $R/all-${CONFIG}.txt \
      -o $R/all-${CONFIG}.log \
      | grep '^HISTO' > $R/all-${CONFIG}.histo
  done
  ```
- **S3.5.5** The profiler prints from the forked VM, which JMH sends to the console, not to the
  `-o` file. The `grep` above captures the `HISTO` lines into `all-${CONFIG}.histo`.
- **S3.5.6** Use the per-class `HISTO` lines, not the totals. The `total` line and the
  `histo.objs.norm` / `histo.bytes.norm` rows include the profiler's own allocations (`jcmd`, heap
  filler) divided by the number of calls: hundreds to thousands of bytes per call of noise that
  makes EC look like it allocates. Objects per call = sum of the per-class counts; bytes per call =
  sum of the per-class bytes.
- **S3.5.7** Each block is headed `HISTO <benchmark> test=<n> clean=<true|false>` and lists every
  class at 0.5 objects per call or more. Use only `clean=true` blocks. If a benchmark has none,
  rerun just that benchmark with a larger `-Xmn`.
- **S3.5.8** Check: the per-class byte sum should equal `gc.alloc.rate.norm` from the main run of
  the same configuration (e.g. `jdkAnyMatch`, `test` = 1: 160 B). If not, say so in the analysis.
- **S3.5.9** ParallelGC is used only to get GC-free iterations; these numbers are for counting,
  not timing. Expect about 10 to 15 minutes per configuration.

#### S3.6 Results2 files

- **S3.6.1** `gen.py` (S5.4) writes `assets/runs/shakers/${RUN_TS}-<Class>Results2.adoc` for each class.
- **S3.6.2** Each contains: a header with `RUN_TS` and `meta.txt`; one section per configuration
  (`coh-on`, `coh-off`, `ea-off`) with the JMH text table verbatim in a `....` block; an
  object-count section per histo configuration (benchmark, test, objects and bytes per call
  summed from the per-class lines over clean iterations, plus the `HISTO` lines); the commands used.
- **S3.6.3** Numbers are copied verbatim, never rounded or reformatted.

### S4. Analysis docs (one per class)

- **S4.0** Write one doc per Results2 file: `assets/docs/shakers/${RUN_TS}-<Class>Analysis.adoc`
  (L2.3). `gen.py` writes the tables and charts; the narrative comes from `notes.json` (S5.4.3).
  Each doc covers S4.1 to S4.8 in order.

#### S4.1 Summary

- **S4.1.1** Three to five sentences at the top: garbage per call for each approach, what it means
  for heap headroom under a spike, and the throughput difference. Lead with memory.

#### S4.2 Head-to-head table (`coh-on`)

- **S4.2.1** One table grouped by `test` (1, 500, 1000).
- **S4.2.2** Columns: implementation and its kind (JDK stream / EC capturing / EC fat-free `*With`
  / EC lazy); throughput (M calls/s = 1000 ÷ ns per call); time per call (ns ± error); objects per
  call (`histo-coh-on`); bytes per call (`gc.alloc.rate.norm`); GC runs per billion calls; GC
  time per billion calls (ms).
- **S4.2.3** A comparison row per group, e.g. "EC `anySatisfyWith` vs JDK: _N_x faster, _k_ fewer
  objects and _b_ fewer bytes per call, 0 vs _g_ GC runs per billion calls", filled from this run only.

#### S4.3 Normalize, don't compare raw totals

- **S4.3.1** Compare per call or per fixed number of calls, never raw `gc.count` totals: a faster
  method fits more calls into the window, so its raw totals look worse.
- **S4.3.2** Calls measured ≈ measurement time ÷ ns per call; measurement time = 2 forks × 10
  iterations × 2 s = 40 s.
- **S4.3.3** Per-billion figures = (raw count ÷ calls measured) × 10⁹.
- **S4.3.4** JMH omits `gc.time` when it is 0 and prints `≈ 0` for tiny values; treat both as 0 and say so.
- **S4.3.5** Raw GC totals go in a separate, clearly labelled section further down.

#### S4.4 Garbage: where it comes from

- **S4.4.1** Objects per call with a per-class breakdown for each implementation (`HISTO` lines).
- **S4.4.2** Split them into pipeline garbage and lambda garbage (S1.5).
- **S4.4.3** Use the `ea-off` run to show lambda garbage EA hides (EC capturing vs EC `*With`),
  labelled as an upper bound.
- **S4.4.4** Explain mechanisms, each marked measured or inferred: stream pipeline objects; the
  `Integer` box outside -128..127 (`*With` methods also box `this.test` when passing it as
  `Object`); EA removing allocations; the `Optional` that `findFirst` and `detectOptional` return.

#### S4.5 Spikes and heap headroom (derived)

- **S4.5.1** For each implementation, at 1 M and 10 M calls/s: allocation rate (MB/s) = bytes per
  call × calls/s.
- **S4.5.2** Young collections per second for an example 512 MB young generation = allocation rate ÷ 512 MB.
- **S4.5.3** Extra young-gen headroom to absorb a 10 s spike = bytes per call × calls/s × 10 s.
- **S4.5.4** Label every figure as derived and show the formula next to the table.
- **S4.5.5** Explain that JMH's `gc.alloc.rate` (MB/s) scales with throughput and misleads on its
  own; `gc.alloc.rate.norm` (bytes per call) is the figure to plan capacity with.

#### S4.6 Compact object headers

- **S4.6.1** Compare `coh-on` vs `coh-off` for bytes per call and GC runs per billion calls.
- **S4.6.2** Compare `histo-coh-on` vs `histo-coh-off` per class; counts should match, flag it if not.
- **S4.6.3** Note any throughput effect and whether it is within the error bars.

#### S4.7 Supporting analysis

- **S4.7.1** Fixed vs per-element cost, from `test` = 1 vs 1000. `count` always scans all 1000
  elements, so its cost should be flat across `test`.
- **S4.7.2** Eager vs lazy EC (EagerLazy classes): what `asLazy()` adds in objects and time.
- **S4.7.3** Anything unusual, e.g. very wide error bars (check per-fork numbers in the `.log`
  before drawing conclusions).
- **S4.7.4** Caveats: one machine, one JDK build, microbenchmarks; mechanisms inferred unless the
  histogram shows them.

#### S4.8 Reproduce

- **S4.8.1** The exact commands (S3, variables expanded), the run folder and the Results2 file used.

### S5. Charts

#### S5.1 Rules for every chart

- **S5.1.1** Title in plain words: the operation, the collection and what is measured, e.g.
  "anyMatch on a JDK ArrayList (EC via Iterate): garbage left behind per call". No class names,
  config codes or abbreviations.
- **S5.1.2** Subtitle: which direction is better ("Lower is better") and a one-sentence takeaway
  with the measured numbers.
- **S5.1.3** Grouped bars, never lines: one group per match position ("match at element 1 / 500 /
  1000"), one bar per implementation.
- **S5.1.4** Every bar is labelled with its value and unit, zeros included.
- **S5.1.5** Legend under the chart naming every bar: `<benchmark method>: <plain description>`.
- **S5.1.6** Okabe-Ito colours, one fixed colour per kind in every chart:
  - **S5.1.6.1** orange `#E69F00` = JDK stream
  - **S5.1.6.2** sky blue `#56B4E9` = EC eager, capturing
  - **S5.1.6.3** reddish purple `#CC79A7` = EC lazy (`asLazy`), capturing
  - **S5.1.6.4** dark blue `#0072B2` = EC `*With`, fat-free
  - **S5.1.6.5** black `#000000` = EC `*With` that is not fat-free (on an EC 13.0.0 `ImmutableList`,
    every `*With` except `detectWithOptional` goes through `Predicates.bind`)
  - **S5.1.6.6** never red with green, never default library colours.
- **S5.1.7** Colour is never the only cue: a second method of the same kind keeps the colour and
  gets a hatch (`//`, then `..`, `xx`); hatched bars get a background box behind the value.
- **S5.1.8** When a kind or colour is added, check the palette with a protanopia, deuteranopia and
  tritanopia simulation; every pair must stay at least 17 CIELAB ΔE apart (about 10 reads as
  clearly different).
- **S5.1.9** The y-axis label carries the unit and the flag where relevant, e.g. "bytes allocated
  per call (-XX:-DoEscapeAnalysis)".
- **S5.1.10** A footnote explains any bar that would otherwise surprise.
- **S5.1.11** Look at every PNG before reporting.

#### S5.2 Charts per class

| ID | `<chart>` | What it shows (title ends with) |
|---|---|---|
| S5.2.1 | `throughput` | "how many times faster than the JDK stream" (JDK = 1x; labels also give M calls/s) |
| S5.2.2 | `bytes-per-call` | "garbage left behind per call" (`gc.alloc.rate.norm`, `coh-on`) |
| S5.2.3 | `gc-per-billion` | "garbage collections per billion calls" |
| S5.2.4 | `ea-off-bytes` | "garbage per call when escape analysis is off" |
| S5.2.5 | `coh-bytes` | "bytes per call saved by compact object headers" (`coh-off` minus `coh-on`, with %) |
| S5.2.6 | `spike-headroom` | "heap needed to absorb a 10-second spike" (bytes per call × 1 M calls/s × 10 s, MB) |

#### S5.3 Two files per chart

- **S5.3.1** `<chart>.png` in `assets/images/shakers/` (L2.7), drawn with matplotlib by the S5.1
  rules. This is the image the doc embeds and GitHub shows.
- **S5.3.2** `<chart>.mmd` in `assets/docs/shakers/mermaid/` (L2.6): Mermaid source with the same
  title, one horizontal bar per implementation and match position, labelled
  `<method> @<position> = <value>`, left-padded with non-breaking spaces (Mermaid under-measures
  label width). Mermaid `xychart-beta` cannot draw the PNG's legend or colours, which is why both exist.
- **S5.3.3** Each `.mmd` is parse-checked with `mmdc` when `MMDC` is set.
- **S5.3.4** Embedding in a doc (written by the tools):
  ```
  .<caption>
  image::../../images/shakers/<RUN_TS>-<Class>-<chart>.png[<caption>]
  Mermaid source: link:mermaid/<RUN_TS>-<Class>-<chart>.mmd[...]
  ```

#### S5.4 Generating

- **S5.4.1** Once: `python3 -m pip install matplotlib`; optionally
  `npm install @mermaid-js/mermaid-cli@11` in a tools folder for the parse check.
- **S5.4.2** Command (writes Results2, analysis docs, charts and `summary.json`):
  ```
  MMDC=<tools>/node_modules/.bin/mmdc \
    python3 assets/tools/shakers/gen.py . ${RUN_TS} $R/notes.json
  ```
- **S5.4.3** `notes.json` holds the narrative per class (keys `summary`, `garbage`, `ea`, `spike`,
  `coh`, `supporting`, optional `caveats`). Start from the previous run's copy, save it as
  `$R/notes.json`, and rewrite every value from the new numbers.
- **S5.4.4** `CLASSES=AnyMatchUtility,...` limits the classes.

### S6. JDK Stream ops analysis

- **S6.1** Purpose: which objects one JDK `stream()` call allocates, which part of the Stream API
  makes each one and what it costs, with the EC versions of each operation beside every JDK number.
  It explains why the per-class numbers are what they are.
- **S6.2** Inputs: `$R/all-histo-coh-on.histo`, `all-histo-coh-off.histo`, `all-histo-ea-off.histo`
  (S3.5) and `$R/summary.json` (from S5.4, so run that first).
- **S6.3** Outputs:

  | ID | What | Path |
  |---|---|---|
  | S6.3.1 | Analysis doc | `assets/docs/shakers/${RUN_TS}-JdkStreamOpsAnalysis.adoc` |
  | S6.3.2 | Bytes per call by origin (stacked bars) | `${RUN_TS}-JdkStreamOps-bytes-by-origin.{png,mmd}` |
  | S6.3.3 | Size of each class, compact headers off vs on | `${RUN_TS}-JdkStreamOps-class-sizes-coh.{png,mmd}` |
  | S6.3.4 | JDK vs every EC version, bytes by origin, escape analysis off, one per operation | `${RUN_TS}-JdkStreamOps-vs-ec-{anyMatch,findFirst,count}.{png,mmd}` |

  PNGs go to `assets/images/shakers/`, `.mmd` files to `assets/docs/shakers/mermaid/`.
- **S6.3.5** Command:
  ```
  python3 assets/tools/shakers/jdk_ops.py . ${RUN_TS}
  ```
- **S6.4** What the doc contains (the script writes all of it from the data):
  - **S6.4.1** Summary: fixed objects and bytes per call, the Stream source share, how the terminal
    ops differ, the cost of `filter`, escape analysis, compact headers, setup time.
  - **S6.4.2** The three pipelines: the exact benchmark code for `anyMatch`, `findFirst`, `count`.
  - **S6.4.3** Every object one call allocates (compact headers on, `test` = 500): class, origin,
    bytes per operation, and why the Stream API allocates it.
  - **S6.4.4** Bytes by origin for every match position, with the stacked-bar chart.
  - **S6.4.5** The terminal ops compared (MatchOps, FindOps, ReduceOps): objects per call and what
    could be avoided.
  - **S6.4.6** Escape analysis on vs off for the JDK benchmarks.
  - **S6.4.7** Compact headers: per-class sizes off vs on (chart), bytes per call off vs on.
  - **S6.4.8** Cost to an application: MB/s at 1 M and 10 M calls/s, GC runs per billion calls,
    setup time at match position 1 (JDK vs fastest EC).
  - **S6.4.9** The same operations in EC:
    - **S6.4.9.1** where each JDK object goes in EC (Stream source, filter stage and terminal op
      have no EC counterpart);
    - **S6.4.9.2** on a normal JVM, one table per operation with every implementation (JDK,
      `Iterate` on the JDK list, `ImmutableList` eager and `asLazy()`): lambda shape, ns per call
      at elements 1 and 500, bytes per call, GC runs per billion calls;
    - **S6.4.9.3** with escape analysis off, one stacked-bar chart and one table per operation,
      with the normal-JVM bytes beside them;
    - **S6.4.9.4** every object the EC versions allocate and why;
    - **S6.4.9.5** fat-free vs capturing, including which `*With` methods are not fat-free.
  - **S6.4.10** Caveats and the reproduce command.
- **S6.5** Rules:
  - **S6.5.1** Every class in the JDK `HISTO` blocks maps to one origin (`ORIGIN` table in
    `jdk_ops.py`): Stream source, filter stage, terminal op, caller lambda or Integer box. A class
    shown as `other` must be traced and added to the table before reporting.
  - **S6.5.2** Check the origins against the JDK that ran, with `javap -p -c` on
    `java.util.stream.MatchOps`, `FindOps`, `ReduceOps`, `ReferencePipeline` (`src.zip` may be
    missing). If a JDK update changes what is allocated or cached, update `ORIGIN` and the
    terminal-op notes.
  - **S6.5.3** Use the `*Utility` runs for the JDK side. The `*EagerLazy` benchmarks run the same
    JDK code; check their JDK blocks match and say so if they do not.
  - **S6.5.4** EC classes map with the `EC_ORIGIN` table: caller lambda, Integer box or EC object
    (`Predicates$BindPredicate2`, `LazyIterableAdapter`, `CountProcedure` from `asLazy().count`,
    the `Optional` from `detectOptional`). Check against the EC jar that ran with
    `javap -c -cp eclipse-collections-<version>.jar` on `Iterate`, `ImmutableArrayList`,
    `RandomAccessListIterate`. If EC stops routing `ImmutableList` `*With` through
    `Predicates.bind`, update `EC_ORIGIN`, the doc text and `classify()` in `charts.py`.
  - **S6.5.5** The EC implementations and their code are the `IMPLS` table; update it when a
    benchmark method is added or renamed.
  - **S6.5.6** Origin colours (Okabe-Ito): orange = Stream source, sky blue = filter stage, dark
    blue = terminal op, reddish purple = caller lambda, black with `//` hatch = Integer box,
    vermillion `#D55E00` with `xx` hatch = EC object. All six stay at least 17 CIELAB ΔE apart
    under the three simulations.
  - **S6.5.7** Label anything not read from the data or the bytecode as inferred.

### S7. Run summary doc

- **S7.1** Write `assets/docs/shakers/${RUN_TS}-Summary.adoc` by hand after S4 to S6.
- **S7.2** It holds: what was run and where (from `meta.txt`), a link to each of the six analyses
  and the JDK Stream ops analysis, the inputs (Results2 files and run folder), and the findings
  as a numbered list, each with its number and the doc it comes from.
- **S7.3** Links are relative to `assets/docs/shakers/` (e.g. `link:${RUN_TS}-CountUtilityAnalysis.adoc[...]`).

### S8. Reporting back

- **S8.1** Report which files were created, the run folder, and any benchmark with no clean
  histogram iteration or suspicious error bars.
- **S8.2** Check the run against the asks checklist (A) and say which items, if any, it misses.
- **S8.3** Be ready to give the exact command behind any number (G9).
- **S8.4** Copy to the local Mac only what is named (G6), then check it (G10).

---

## Movers (JOL footprint baselines)

### M1. Scope

- **M1.1** The `movers` package (`src/test/java/conf/session/movers/`) holds JUnit tests that print
  JOL retained sizes (`GraphLayout.parseInstance(x).totalSize()`): what a filled collection or an
  unevaluated pipeline holds in memory.
- **M1.2** Footprint only: no JMH, no allocation rate, no speed.
- **M1.3** The tests:

  | ID | Test | `<Test>` | Compares |
  |---|---|---|---|
  | M1.3.1 | `ListMemoryTest` | `ListMemory` | `ArrayList` vs `FastList` vs `IntArrayList`, grown and presized, 100k to 1M |
  | M1.3.2 | `SetMemoryTest` | `SetMemory` | `HashSet` vs `UnifiedSet` vs `IntHashSet`, plus structure only (Integers subtracted) |
  | M1.3.3 | `MapMemoryTest` | `MapMemory` | `HashMap` vs `UnifiedMap` vs `IntObjectHashMap`, `ObjectIntHashMap`, `IntIntHashMap`, plus structure only |
  | M1.3.4 | `StreamVsLazyIterableMemoryTest` | `StreamVsLazyIterableMemory` | unevaluated `Stream` vs `LazyIterable` pipelines, 12 operations and one chain |

- **M1.4** `SweatingTheSmallStuffTest` is on hold; do not run it.

### M2. Run

- **M2.1** Run each test twice, compact headers off then on: two baselines.
- **M2.2** JOL attaches an agent to its own VM, so `-Djdk.attach.allowAttachSelf=true
  -XX:+EnableDynamicAgentLoading` are required; without them JOL fails under surefire.
- **M2.3** Set and Map take about 13 minutes per configuration.
- **M2.4** Loop (logs go to the run folder, L3.1):
  ```
  MOVERS_TS=$(date +%Y%m%d-%H%M)
  MR=assets/runs/movers/${MOVERS_TS}
  mkdir -p $MR
  for CFG in coh-off coh-on; do
    case $CFG in
      coh-off) COH="-XX:-UseCompactObjectHeaders" ;;
      coh-on)  COH="-XX:+UseCompactObjectHeaders" ;;
    esac
    for T in ListMemoryTest SetMemoryTest MapMemoryTest StreamVsLazyIterableMemoryTest; do
      mvn -B test -Dtest=$T -Dsurefire.failIfNoSpecifiedTests=false \
        -DargLine="${COH} -Djdk.attach.allowAttachSelf=true -XX:+EnableDynamicAgentLoading" \
        > $MR/$T-$CFG.mvn.log 2>&1
    done
  done
  ```
- **M2.5** The printed lines are in the `.mvn.log` files (surefire shows test output there).

### M3. Results files

- **M3.1** For each test and configuration, save the printed lines as
  `assets/runs/movers/${MOVERS_TS}/<Test>Results-<CFG>.md`, e.g. `MapMemoryResults-coh-on.md`.
- **M3.2** Size tests: the `... size: N Memory (bytes): B` lines, sorted by collection, then size.
  Pipeline test: its table as printed.
- **M3.3** Start each file with an HTML comment whose first line is
  `environment: local Mac (<CPU>)`, followed by `java -version`, the flags, the JOL and EC versions
  and JOL's `VM.current().details()`. `movers.py` shows the environment and JDK lines in every doc.
- **M3.4** The older results files in the movers package (`*Results.md` and `*Results-coh-{off,on}.md`)
  stay where they are (G2). To analyse them, set `MOVERS_RESULTS=src/test/java/conf/session/movers`.

### M4. Analysis docs and charts

- **M4.1** Command (one doc per test):
  ```
  MMDC=<tools>/node_modules/.bin/mmdc python3 assets/tools/movers/movers.py . ${MOVERS_TS}
  ```
- **M4.2** Docs go to `assets/docs/movers/` (L3.2), PNGs to `assets/images/movers/` (L3.4),
  Mermaid sources to `assets/docs/movers/mermaid/` (L3.3).
- **M4.3** Charts:

  | ID | `<chart>` | For |
  |---|---|---|
  | M4.3.1 | `footprint` | size at 100k, 500k and 1M; every test except the pipelines |
  | M4.3.2 | `growth-slack` | lists: grown vs presized, every size |
  | M4.3.3 | `structure-only` | sets and maps, Integers subtracted |
  | M4.3.4 | `coh` | compact headers off vs on; sets, maps and pipelines |
  | M4.3.5 | `pipeline-size` | the pipelines |

- **M4.4** Charts follow S5.1 (plain title, "better" subtitle with the takeaway, a value on every
  bar, legend, parse-checked `.mmd` next to each PNG).
- **M4.5** Colours by kind: orange = JDK; sky blue = EC boxed; dark blue = EC primitive; reddish
  purple = EC half-primitive maps and `LazyIterable`; presized and second members get a hatch;
  "structure only" gets a dotted hatch; compact headers off = grey `#999999`, on = dark blue.
- **M4.6** Map charts say "entries", not "elements".
- **M4.7** Look at every PNG before reporting.

### M5. Rules for the movers docs

- **M5.1** Label layout explanations that are arithmetic (e.g. node size × count + table size) as derived.
- **M5.2** Say in each doc that JOL sizes are footprint, not allocation or speed.
- **M5.3** Say that the pipeline sizes count shared lambda constants: they are the size of the
  pipeline graph, not bytes allocated per call.

### M6. Reporting back

- **M6.1** Report the files created and the run folder, and copy to the local Mac only what is named (G6, G10).

---

## C. Changes from `instructions.md`

- **C1** New file layout (L): shakers docs, Summary, JDK Stream ops doc and Mermaid files under
  `assets/docs/shakers/`; shakers PNGs under `assets/images/shakers/`; movers the same under
  `assets/docs/movers/` and `assets/images/movers/`. Mermaid files sit in a `mermaid/` subfolder
  so the doc folders list only docs.
- **C2** Raw runs split by part: `assets/runs/shakers/` and `assets/runs/movers/`. Movers results
  files now go to the movers run folder instead of the test package (M3.1, M3.4).
- **C3** `assets/jdkops/` is merged into `assets/docs/shakers/` + `assets/images/shakers/` (S6.3, L7),
  including the existing 20261015-0406 JDK Stream ops doc and charts.
- **C4** Tools moved to one permanent place, `assets/tools/shakers/` and `assets/tools/movers/`,
  instead of copying them into each run's `tools/` folder (L4). The new copies differ from the run
  copies only in paths, plus the environment line read from `meta.txt` (S3.1.5) instead of a fixed
  "cloud sandbox" text.
- **C5** `meta.txt` and the movers results headers gain an `environment:` line (S3.1.5, M3.3).
- **C6** The run summary doc is now a numbered step (S7); it was written by hand before but not described.
- **C7** The asks checklist (A) has IDs and new rows: A21 (`CountProcedure`), A22 (summary),
  A23 (layout), A24 (numbered items), A27 (no edits) and A28 (check copies).
- **C8** Every instruction is a numbered item.
