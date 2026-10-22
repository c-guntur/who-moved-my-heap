# Who Moved My Heap: instructions

The one authoritative set of instructions for this project, for people and for Claude. It covers
how to run the two parts of the project, turn their raw output into reports, charts and summaries,
and build the slides, all at once or one stage at a time from outputs already on disk.

- **Shakers**: the JMH benchmarks in `src/test/java/conf/session/shakers/`, comparing the JDK Stream
  API with Eclipse Collections (EC) for throughput, garbage per call and object counts.
- **Movers**: the JOL footprint tests in `src/test/java/conf/session/movers/` (no JMH).
- **Slides**: the slide sources in `assets/presentation-40min/` (the 40-minute talk); the build publishes the
  slide pages and the talk PDF to `assets/presentation/` (D2.5).

`HowTo.adoc` is the short command guide for other users of the project. Where it and this file
differ, this file wins; change both together.

Every item has an ID so a change can be pinpointed: `Q` = quick start, `G` = ground rules,
`L` = file layout, `P` = pipeline stages, `A` = asks checklist, `S` = shakers, `M` = movers,
`D` = slides, `W` = working notes. IDs are stable: when an item is removed, its ID is not reused.

---

## Q. Quick start

- **Q1** Everything runs from the project root with JDK 25 and Maven (P0). One launcher runs any stage:
  ```
  java src/main/java/tools/Pipeline.java <stage> [RUN_TS] [shakers|movers] [--dry-run] [--resume]
  ```
- **Q2** The stages:

  | ID | Stage | Needs | Writes | Section |
  |---|---|---|---|---|
  | Q2.1 | `run` | the source tree | raw output in `generated/runs/<RUN_TS>/<part>/raw/` | P1 |
  | Q2.2 | `reports` | the raw output of `RUN_TS` | reports, charts, Mermaid files and summaries of `RUN_TS` | P2 |
  | Q2.3 | `slides` | the slide sources | slide HTML pages and the talk PDF in `assets/presentation/` | P3 |
  | Q2.4 | `all` | the source tree | `run`, then `reports`, then `slides` | P4 |

- **Q3** Without a part, a stage does shakers and movers. Examples:

  | ID | Command | Does |
  |---|---|---|
  | Q3.1 | `java src/main/java/tools/Pipeline.java all` | everything, with a new `RUN_TS` |
  | Q3.2 | `java src/main/java/tools/Pipeline.java run` | both parts, new `RUN_TS`, no reports |
  | Q3.3 | `java src/main/java/tools/Pipeline.java run shakers` | only the JMH benchmarks |
  | Q3.4 | `java src/main/java/tools/Pipeline.java run 20261015-0406 movers` | adds movers to an existing run (keeps its `meta.txt`) |
  | Q3.5 | `java src/main/java/tools/Pipeline.java reports 20261015-0406` | reports from raw files already on disk |
  | Q3.6 | `java src/main/java/tools/Pipeline.java reports` | the same for the newest run folder |
  | Q3.7 | `java src/main/java/tools/Pipeline.java slides` | slide HTML pages and the talk PDF only |
  | Q3.8 | `java src/main/java/tools/Pipeline.java all --dry-run` | prints every command without running anything |
  | Q3.9 | `java src/main/java/tools/Pipeline.java all 20261017-1708 --resume` | continues an interrupted run: finished steps are skipped (P7) |
  | Q3.10 | `java src/main/java/tools/Pipeline.java slides --slides-dir=<folder>` | the same from another slide source folder (D3) |

- **Q4** Every stage also lists its commands for running by hand (S3, S5.4, S6.3.5, M2.4, M4.1, D2).
  `Pipeline.java` runs exactly those commands.

---

## G. Ground rules

- **G1** Hand-written files (Java sources, `pom.xml`, the slides, `HowTo.adoc`, this file) are changed
  only when the local Mac user asks. Everything under `generated/` is regenerated freely.
- **G2** Old outputs outside `generated/` stay where they are until the local Mac user deletes them.
- **G3** The `bench` and `slides` profiles in `pom.xml` are a permanent part of the project, not run
  outputs (S3.2, D2).
- **G4** Before starting a `run` stage, get confirmation from the local Mac user. Shakers runs take
  several hours (S3.4.4) and need a quiet machine on mains power. The `reports` and `slides` stages
  need no confirmation.
- **G5** If the local Mac user says "stop", halt the current step and do not copy any more files.
- **G6** Copy to the local Mac only the files the local Mac user names, or the outputs of a task
  they asked for.
- **G7** Never use a person's name in these instructions or in any output (docs, charts, reports,
  slides). Refer to the machine and its user as "local Mac".
- **G8** Java style:
  - **G8.1** The opening brace goes on its own line (Allman) for classes, records, methods,
    `if`/`try`/`catch` and block lambdas, in every class.
  - **G8.2** `HistoProfiler` uses Eclipse Collections, not `java.util` collections.
  - **G8.3** Comment any non-obvious logic.
- **G9** Every number in a report must be traceable to a file in a raw folder (L2.1, L2.2) and to a
  command (S3 or M2).
- **G10** After copying a file to the local Mac, read it back (md5) about 15 seconds later. If it
  differs, copy again. Then ask the local Mac user to reload it from disk, because an editor that
  has the file open (IntelliJ) can write the old content back.
- **G11** Claude never deletes files on the local Mac and never asks for delete permission. When
  files become obsolete, list them for the local Mac user to delete.
- **G12** For a large change, give a numbered plan and wait for an explicit go-ahead.
- **G13** Commands must run both on the local Mac and in the cloud sandbox (W1).

---

## L. File layout

- **L1** Every run has one datetime, `RUN_TS` (`yyyyMMdd-HHmm`, e.g. `20261015-2130`), shared by
  shakers and movers. Folders are named by it and every generated file name starts with it, so runs
  sort by time.
- **L2** Everything a run makes lives under `generated/runs/${RUN_TS}/` at the project root
  (paths in `Layout.java`). `mvn clean` does not touch `generated/`.

  | ID | What | Path |
  |---|---|---|
  | L2.1 | Shakers raw output, every file `.txt`: JMH tables `<Class>-<CFG>.txt`, console `<Class>-<CFG>-console.txt`, object counts `all-<CFG>-histo.txt`, `meta.txt` | `generated/runs/${RUN_TS}/shakers/raw/` |
  | L2.2 | Movers raw output, every file `.txt`: Maven output of each test `<Test>Test-<CFG>.txt`, `meta.txt` | `generated/runs/${RUN_TS}/movers/raw/` |
  | L2.3 | Data passed from one generator to the next (`summary.json`) | `generated/runs/${RUN_TS}/shakers/` |
  | L2.4 | Reports: results, analysis docs, JDK Stream ops analysis, part summary (`.adoc`) | `generated/runs/${RUN_TS}/{shakers,movers}/reports/` |
  | L2.5 | Chart images (PNG) | `generated/runs/${RUN_TS}/{shakers,movers}/reports/images/` |
  | L2.6 | Mermaid sources (`.mmd`) | `generated/runs/${RUN_TS}/{shakers,movers}/reports/mermaid/` |
  | L2.7 | Full run summary: both part summaries in one doc | `generated/runs/${RUN_TS}/${RUN_TS}-Summary.adoc` |

- **L3** Report file names: shakers `${RUN_TS}-<Class>Results.adoc`, `${RUN_TS}-<Class>Analysis.adoc`,
  `${RUN_TS}-JdkStreamOpsAnalysis.adoc`, `${RUN_TS}-Summary.adoc`; movers `${RUN_TS}-<Test>Analysis.adoc`,
  `${RUN_TS}-Summary.adoc`; charts `${RUN_TS}-<Class or Test>-<chart>.{png,mmd}` and
  `${RUN_TS}-JdkStreamOps-<chart>.{png,mmd}`.
- **L4** Tools (permanent, shared by every run), Java 25 source files under `src/main/java/tools/`:

  | ID | Tool | Path |
  |---|---|---|
  | L4.1 | Runs the stages (P) | `src/main/java/tools/Pipeline.java` |
  | L4.2 | Paths for every generator | `src/main/java/tools/analysis/Layout.java` |
  | L4.3 | Shakers results, analysis docs, charts, part summary, `summary.json` | `src/main/java/tools/analysis/ShakersDocs.java` (uses `ShakersCharts.java`, `JmhParse.java`) |
  | L4.4 | JDK Stream ops analysis | `src/main/java/tools/analysis/JdkOpsDocs.java` |
  | L4.5 | Movers analysis docs, charts and part summary | `src/main/java/tools/analysis/MoversDocs.java` |
  | L4.6 | Full run summary (L2.7) | `src/main/java/tools/analysis/RunSummary.java` |
  | L4.7 | Shared helpers: Python-compatible number formatting and JSON, PNG charts in Java2D | `src/main/java/tools/analysis/Py.java`, `Plot.java` |
  | L4.8 | Slide navigation, slide images, publishing, the talk PDF | `src/main/java/tools/presentation/SlideNav.java`, `SlideImages.java`, `SlidePublish.java`, `SlidesToPdf.java` |

  Run them straight from source with `java <file>.java` (no build, no Python); the launcher finds
  the other classes of the package. `mvn compile` also compiles them, except `SlidesToPdf.java`,
  which needs Playwright and is only run by the `slides` profile.
- **L5** Reports link to their images and Mermaid sources relatively (`image::images/<file>.png[...]`,
  `link:mermaid/<file>.mmd[...]`), and to each other by file name, so they render on GitHub and in
  IntelliJ. The full run summary prefixes the same links with `<part>/reports/`.
- **L6** `<Class>` is the benchmark class without `Benchmark` (S2.2). `<Test>` is the movers test
  class without `Test` (M1.3).
- **L7** Nothing is written by hand: every sentence in the reports, including each Summary, the part
  summaries and the full run summary, comes from the run's numbers.
- **L8** `SHAKERS_RUN_DIR=<folder>` (shakers tools) and `MOVERS_RESULTS=<folder>` (movers) read the raw
  files from another folder; the reports still go to L2.4 to L2.6.
- **L9** Runs made before this layout were copied into it: run 20261015-0406 (shakers; `.log` became
  `-console.txt`, `.histo` became `-histo.txt`) and run 20261015-1503 (movers; the
  `*Results-coh-{off,on}.md` files became `<Test>Test-<CFG>.txt`).

---

## P. Pipeline stages

- **P0** Prerequisites for every stage:
  - **P0.1** JDK 25 on `PATH`; `java` and `jcmd` must come from the same JDK (HistoProfiler calls `jcmd`).
  - **P0.2** Maven 3.9+ running on JDK 25: set `JAVA_HOME` to the JDK 25 home and check that
    `mvn -v` prints `Java version: 25...`. Maven on an older JDK cannot compile `release 25`.
  - **P0.3** No Python, Ruby or Node.js is needed. Optional: `npm install @mermaid-js/mermaid-cli@11`
    in a tools folder and `MMDC=<tools>/node_modules/.bin/mmdc` to parse-check every `.mmd`.
  - **P0.4** `CLASSES=AnyMatchUtility,...` limits the shakers classes in `run` and `reports`;
    `CHROME=<path>` picks the browser for the slide PDFs (D2.4).
- **P1** Stage `run`:
  - **P1.1** Picks `RUN_TS` (a new one, or the one given) and creates
    `generated/runs/${RUN_TS}/{shakers,movers}/raw/`.
  - **P1.2** Writes `meta.txt` into each raw folder (S3.1.3). A run that already has a `meta.txt` keeps
    it, so a part can be added to an existing run.
  - **P1.3** Shakers: builds the benchmark jar (S3.3), runs the matrix (S3.4), then the object counts (S3.5).
  - **P1.4** Movers: runs the four JOL tests with compact headers off, then on (M2).
  - **P1.5** A failing benchmark stops the stage; a failing movers test is reported and the rest go on,
    because its output file still shows what failed.
- **P2** Stage `reports` (from raw files already on disk):
  - **P2.1** Needs only `generated/runs/${RUN_TS}/<part>/raw/`. Without a `RUN_TS` it uses the newest run folder.
  - **P2.2** Shakers, when the raw folder has JMH tables: `ShakersDocs.java` (S5.4), then
    `JdkOpsDocs.java` (S6) when the object counts are there.
  - **P2.3** Movers, when the raw folder has test output: `MoversDocs.java` (M4).
  - **P2.4** Then `RunSummary.java` writes the full run summary (L2.7) from the part summaries.
  - **P2.5** A part without raw files is skipped and named as missing in the full run summary.
  - **P2.6** By hand, the same as:
    ```
    java src/main/java/tools/analysis/ShakersDocs.java . ${RUN_TS}
    java src/main/java/tools/analysis/JdkOpsDocs.java . ${RUN_TS}
    java src/main/java/tools/analysis/MoversDocs.java . ${RUN_TS}
    java src/main/java/tools/analysis/RunSummary.java . ${RUN_TS}
    ```
- **P3** Stage `slides`: `mvn -B -Pslides process-resources` (D2). It does not depend on a run.
  `--slides-dir=<folder>` adds `-Dslides.dir=<folder>` and builds that slide folder instead (D3).
- **P4** Stage `all`: `run`, then `reports` for the same `RUN_TS`, then `slides`.
- **P5** `--dry-run` prints every command a stage would run and writes nothing. Use it to check a
  stage before a long run.
- **P6** Check before reporting a stage as done: every `link:` and `image::` target in the new reports
  resolves (W3), and every PNG has been looked at (S5.1.11, M4.7).
- **P7** Resuming an interrupted run (`--resume`, with the `RUN_TS` of that run). A killed process,
  a restarted container or a closed laptop stops `Pipeline.java` partway, and `run` alone starts over
  (it redoes every step even with a `RUN_TS`). `--resume` runs the same steps in the same order but skips
  the ones that finished:
  - **P7.1** The benchmark jar: skipped when `target-bench/microbenchmarks.jar` exists.
  - **P7.2** A JMH step (each class and configuration of S3.4, and each object-count run of S3.5): finished
    when its `-console.txt` has JMH's closing line `Benchmark result is saved to`. The result `.txt`
    file is not a test, because JMH creates it when the step starts.
  - **P7.3** A movers test: finished when its Maven output has `BUILD SUCCESS` or `BUILD FAILURE`.
  - **P7.4** Any other step, including one cut short, runs again from its start and overwrites its files.
    The log names each step as `skip (finished)` or `re-run (unfinished)`.
  - **P7.5** `meta.txt` is kept (P1.2). `reports` and `slides` do not depend on earlier steps and run as
    usual in `all --resume`.
  - **P7.6** Check first with `--dry-run --resume`: it prints only the steps that would run.
  - **P7.7** Without `--resume` nothing changes: `run` with a `RUN_TS` still adds a part to an existing
    run (Q3.4). `--resume` without a `RUN_TS` is an error.

---

## A. Asks checklist

Every request made for this project so far and the item that covers it. Check a run against this
list before reporting (S8, M6).

| ID | Ask | Covered in |
|---|---|---|
| A1 | Shakers and movers are separate parts; either can run on its own | P, Q3 |
| A2 | Every output file name starts with the run datetime, so runs sort by time | L1 |
| A3 | Benchmarks build with the permanent `bench` profile in `pom.xml`; never edit `pom.xml` during a run | G3, S3.2 |
| A4 | Separate compile, jar and run commands | S3.3 |
| A5 | Maven runs on JDK 25 (compact object headers) | P0.2 |
| A6 | Run with compact headers on and off, plus an escape-analysis-off run | S3.4 |
| A7 | Object counts per class with the HistoProfiler, compact headers on, off and escape analysis off | S3.5 |
| A8 | HistoProfiler sends `jcmd` output to a temp file (fixes the JDK 25 deadlock) | S3.5.2 |
| A9 | HistoProfiler uses Eclipse Collections, has comments on complex logic, uses Allman braces | G8 |
| A10 | Use the per-class `HISTO` lines, not the totals (profiler noise) | S3.5.6 |
| A11 | `HISTO` lines go to the console, not the `-o` file; capture them | S3.5.5 |
| A12 | Results files are generated into the run's `reports/` folder | L2.4, S3.6 |
| A13 | One analysis doc per results file | S4 |
| A14 | The story: fat-free lambdas make less garbage; memory footprint and heap headroom for spikes, not only throughput, and not "EC wins because of `*With`" | S1, S4.5 |
| A15 | Charts a viewer understands without the doc: plain title, "better" subtitle with the takeaway, legend, a value on every bar, grouped bars per match position | S5.1 |
| A16 | Colour-blind-safe colours only (Okabe-Ito, never red with green), checked by simulation when one is added | S5.1.6, S5.1.8, S6.5.6 |
| A17 | Every chart as a PNG (drawn with Java2D) plus a separate Mermaid `.mmd` file | S5.3 |
| A18 | A JDK Stream ops analysis (Stream, FindOps, MatchOps, ReduceOps allocations) | S6 |
| A19 | EC numbers and comparisons next to every JDK number in the JDK Stream ops analysis | S6.4.9 |
| A20 | EC 13.0.0 `ImmutableList` `*With` methods (except `detectWithOptional`) go through `Predicates.bind` and are not fat-free; say so wherever they appear | S5.1.6, S6.5.4 |
| A21 | `asLazy().count` also allocates a `CountProcedure`; attribute it as an EC object | S6.5.4 |
| A22 | A summary doc per part, generated from the data, linking every report | S7, M4.1 |
| A23 | One tree per run: raw files in `generated/runs/${RUN_TS}/<part>/raw/`, reports, charts and Mermaid files in `generated/runs/${RUN_TS}/<part>/reports/` | L2 |
| A24 | Every instruction is a numbered item | this file |
| A25 | Never use a person's name; "local Mac" | G7 |
| A26 | Copy only named files to the local Mac; stop when told "stop" | G5, G6 |
| A27 | Hand-written files change only on request; old outputs stay until the local Mac user deletes them | G1, G2 |
| A28 | After a copy, check the file on the local Mac and ask for a reload from disk | G10 |
| A29 | Run `CollectorsAndCollectors2` (JDK `Collectors` vs EC `Collectors2`, no `test` parameter) with the other classes and give it its own analysis doc | S2.2.7, S3.4, S4 |
| A30 | No hand-written notes: each class Summary and every summary doc are written from the numbers | L7, S4.1, S7 |
| A31 | Commands run both on the local Mac and in the cloud sandbox | G13, W1 |
| A32 | Movers are JOL footprint only: no JMH, no allocation, no speed; two baselines (compact headers off, on) | M1.2, M2.1 |
| A33 | One authoritative instructions file; `HowTo.adoc` stays as the short guide for other users | this file |
| A34 | Any stage can start from intermediate outputs on disk; one command runs both parts | P, Q |
| A35 | A full run summary of both parts at `generated/runs/${RUN_TS}/` | L2.7, P2.4 |
| A36 | Claude lists obsolete files for the local Mac user to delete; it never deletes them | G11 |
| A37 | Slides: one file per slide, no JavaScript, Oracle-only references, HTML and PDFs built with Maven | D |
| A38 | An interrupted run can be resumed from the finished steps instead of starting over | P7, Q3.9 |
| A39 | Diagram blocks and the cover QR code are rendered by Kroki; the talk has one cover page | D1.8, D2.8 |

---

## Shakers (JMH benchmarks)

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
- **S1.7** Stream terminology: intermediate operations are lazy, terminal operations (`anyMatch`,
  `count`, `findFirst`) are eager. Never say "Streams are lazy" without that distinction.

### S2. Benchmark classes

- **S2.1** Seven classes, 28 benchmark methods. The six stream classes run each method at `test` =
  1, 500 and 1000 (match position); `CollectorsAndCollectors2` has no `test` parameter.
- **S2.2** The classes:

  | ID | Benchmark class | `<Class>` | Compares |
  |---|---|---|---|
  | S2.2.1 | `AnyMatchUtilityBenchmark` | `AnyMatchUtility` | `stream().anyMatch` vs `Iterate.anySatisfy` / `anySatisfyWith` on a JDK `List` |
  | S2.2.2 | `CountUtilityBenchmark` | `CountUtility` | `stream().filter().count()` vs `Iterate.count` / `countWith` |
  | S2.2.3 | `FindFirstUtilityBenchmark` | `FindFirstUtility` | `stream().filter().findFirst()` vs `Iterate.detectOptional` / `detectWithOptional` / `detectIfNone` / `detectWithIfNone` |
  | S2.2.4 | `AnyMatchEagerLazyBenchmark` | `AnyMatchEagerLazy` | as above on an EC `ImmutableList`, plus `asLazy()` |
  | S2.2.5 | `CountEagerLazyBenchmark` | `CountEagerLazy` | as above on an EC `ImmutableList`, plus `asLazy()` |
  | S2.2.6 | `FindFirstEagerLazyBenchmark` | `FindFirstEagerLazy` | as above on an EC `ImmutableList`, plus `asLazy()` |
  | S2.2.7 | `CollectorsAndCollectors2Benchmark` | `CollectorsAndCollectors2` | creating 7 collectors: JDK `Collectors` (`toList`, `toUnmodifiableList`, `toSet`, `toUnmodifiableSet`, `groupingBy` + `counting` as a bag, the same through `collectingAndThen(..., Map::copyOf)`, `joining`) vs EC `Collectors2` (`toList`, `toImmutableList`, `toSet`, `toImmutableSet`, `toBag`, `toImmutableBag`, `makeString`); no `test` parameter |

- **S2.3** A new benchmark class is added to `SHAKERS` in `Pipeline.java`, to `ALL_CLASSES` in
  `ShakersDocs.java`, to the S3.4.2 loop and to this table.

### S3. Build and run

#### S3.1 Run folder and metadata

- **S3.1.1** Prerequisites: P0, plus at least 16 GB RAM free for the object-count runs (12 GB heap).
- **S3.1.2** `Pipeline.java run` does S3.1 to S3.5. By hand, set the run timestamp once (L1). If the
  run spans several shell sessions, reuse it (first line of `meta.txt`) instead of making a new one.
  ```
  RUN_TS=$(date +%Y%m%d-%H%M)
  R=generated/runs/${RUN_TS}/shakers/raw
  MR=generated/runs/${RUN_TS}/movers/raw
  mkdir -p $R $MR
  ```
- **S3.1.3** Record the environment before running anything. The `environment:` line is read by the
  tools and shown in every doc; it says `local Mac` on macOS and `cloud sandbox` anywhere else, with
  no person's name (G7).
  ```
  {
    echo "run_ts: ${RUN_TS}"
    case "$(uname)" in Darwin) WHERE="local Mac" ;; *) WHERE="cloud sandbox" ;; esac
    CPU=$(sysctl -n machdep.cpu.brand_string 2>/dev/null || lscpu | sed -n 's/^Model name:[[:space:]]*//p')
    echo "environment: ${WHERE} (${CPU})"
    java -version 2>&1
    mvn -v
    sw_vers 2>/dev/null || uname -a
    sysctl -n machdep.cpu.brand_string hw.ncpu hw.memsize 2>/dev/null || lscpu
    java -XX:+UseCompactObjectHeaders -XX:+PrintFlagsFinal -version 2>/dev/null \
      | grep -E ' Use(G1|Parallel|Serial|Z|Shenandoah)GC |UseCompactObjectHeaders|DoEscapeAnalysis'
    git rev-parse HEAD; git status --short
  } > $R/meta.txt
  cp $R/meta.txt $MR/meta.txt
  ```
- **S3.1.4** The S3.1.3 block is the same as `writeMeta` in `Pipeline.java`; change both together.

#### S3.2 Benchmark build: the `bench` profile in `pom.xml`

- **S3.2.1** The benchmarks live under `src/test/java`, so the normal build compiles them as tests
  and `mvn package` does not produce a runnable JMH jar. The `bench` profile in `pom.xml` does that.
- **S3.2.2** Do not create, regenerate or edit `pom.xml` during a run. If the `bench` profile is
  missing, stop and ask the local Mac user.
- **S3.2.3** It adds `src/test/java` as a main source folder and compiles only
  `src/test/java/conf/session/shakers/*Benchmark.java` and `src/main/java/prof/HistoProfiler.java`
  (release 25) with the JMH annotation processor. Other sources (movers tests, `tools/`) are
  excluded and tests are skipped. `HistoProfiler` lives in `src/main/java` because it is a tool,
  not a test; it keeps package `prof`, so its JMH name stays `prof.HistoProfiler`.
- **S3.2.4** It shades everything except JOL into `target-bench/microbenchmarks.jar` (main class
  `org.openjdk.jmh.Main`) and builds into `target-bench/`, never touching `target/`. Any
  `mvn clean`, with or without `-Pbench`, removes both `target/` and `target-bench/`.
- **S3.2.5** It uses the versions declared in `pom.xml` (Eclipse Collections 13.0.0, JMH 1.37),
  so there is nothing to keep in sync.

#### S3.3 The three commands

- **S3.3.1** Compile:
  ```
  mvn -Pbench clean compile
  ```
- **S3.3.2** Create the jar (`Pipeline.java` runs `mvn -B -Pbench clean package`, which does both):
  ```
  mvn -Pbench package
  ```
- **S3.3.3** Sanity check: `java -jar target-bench/microbenchmarks.jar -l | grep shakers` lists
  28 benchmarks. Use `microbenchmarks.jar`; the other jar in `target-bench/` is unshaded and will not run.
- **S3.3.4** Run the jar, once per class and configuration (`CLASS`, `CONFIG` and `FLAGS` come from S3.4):
  ```
  java -jar target-bench/microbenchmarks.jar "conf\.session\.shakers\.${CLASS}Benchmark\." \
    -prof gc \
    -jvmArgsAppend "-XX:+UseG1GC -Xms2g -Xmx2g ${FLAGS}" \
    -rf text -rff $R/${CLASS}-${CONFIG}.txt \
    -o $R/${CLASS}-${CONFIG}-console.txt
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
                 AnyMatchEagerLazy CountEagerLazy FindFirstEagerLazy \
                 CollectorsAndCollectors2; do
      java -jar target-bench/microbenchmarks.jar "conf\.session\.shakers\.${CLASS}Benchmark\." \
        -prof gc -jvmArgsAppend "-XX:+UseG1GC -Xms2g -Xmx2g ${FLAGS}" \
        -rf text -rff $R/${CLASS}-${CONFIG}.txt \
        -o $R/${CLASS}-${CONFIG}-console.txt
    done
  done
  ```
- **S3.4.3** Every configuration is complete on its own, so a partial run is still usable.
- **S3.4.4** Expected duration: 26 methods × 3 params × 2 forks × about 42 s ≈ 1.8 h per
  configuration, about 5.5 h for all three, plus about 3 minutes per configuration for the 2
  `CollectorsAndCollectors2` methods.

#### S3.5 Object counts (HistoProfiler)

- **S3.5.1** JMH `-prof gc` reports bytes per call, not objects. `prof.HistoProfiler` runs
  `jcmd <pid> GC.class_histogram -all` before and after each measurement iteration and divides the
  per-class difference by the number of calls. It needs GC-free iterations, so it uses short
  iterations and a big young generation.
- **S3.5.2** `HistoProfiler.histo()` sends `jcmd` output to a temp file
  (`ProcessBuilder.redirectOutput(file)`), not a pipe. The histogram is written while the benchmark
  VM is at a safepoint; once it is larger than the 64 KB pipe buffer, `jcmd` would block and the VM
  never resume, so the run would hang at `Iteration 1:` with no error. Keep it that way.
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
      -o $R/all-${CONFIG}-console.txt \
      | grep '^HISTO' > $R/all-${CONFIG}-histo.txt
  done
  ```
- **S3.5.5** The profiler prints from the forked VM, which JMH sends to the console, not to the
  `-o` file. The `grep` above (and `Pipeline.java`) captures the `HISTO` lines into `all-${CONFIG}-histo.txt`.
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

#### S3.6 Results files

- **S3.6.1** `ShakersDocs.java` (S5.4) writes `${RUN_TS}-<Class>Results.adoc` for each class into the
  reports folder (L2.4).
- **S3.6.2** Each contains: a header with `RUN_TS` and `meta.txt`; one section per configuration
  (`coh-on`, `coh-off`, `ea-off`) with the JMH text table verbatim in a `....` block; an
  object-count section per histo configuration (benchmark, test, objects and bytes per call
  summed from the per-class lines over clean iterations, plus the `HISTO` lines); the commands used.
- **S3.6.3** Numbers are copied verbatim, never rounded or reformatted.

### S4. Analysis docs (one per class)

- **S4.0** `ShakersDocs.java` writes one doc per results file: `${RUN_TS}-<Class>Analysis.adoc` in the
  reports folder (L2.4). It writes everything, tables, charts and text, from the run's numbers (L7).
  Each doc covers S4.1 to S4.8 in order.

#### S4.1 Summary

- **S4.1.1** Bullets at the top, written from the numbers: the JDK version's bytes and objects per
  call; each EC version's bytes per call; the allocation rate at 10 M calls/s (heap headroom for a
  spike); time per call, JDK vs the fastest EC version, per `test`; bytes per call with escape analysis
  off for every implementation; the JDK version with compact headers off vs on. Memory comes first.

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
- **S4.7.3** Anything unusual, e.g. very wide error bars (check per-fork numbers in the `-console.txt`
  before drawing conclusions).
- **S4.7.4** Caveats: one machine, one JDK build, microbenchmarks; mechanisms inferred unless the
  histogram shows them.

#### S4.8 Reproduce

- **S4.8.1** The exact commands (S3, variables expanded), the raw folder and the results file used.

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

- **S5.3.1** `<chart>.png` in `reports/images/` (L2.5), drawn with Java2D (`Plot.java`) by the S5.1
  rules. This is the image the doc embeds and GitHub shows.
- **S5.3.2** `<chart>.mmd` in `reports/mermaid/` (L2.6): Mermaid source with the same
  title, one horizontal bar per implementation and match position, labelled
  `<method> @<position> = <value>`, left-padded with non-breaking spaces (Mermaid under-measures
  label width). Mermaid `xychart-beta` cannot draw the PNG's legend or colours, which is why both exist.
- **S5.3.3** Each `.mmd` is parse-checked with `mmdc` when `MMDC` is set (P0.3).
- **S5.3.4** Embedding in a doc (written by the tools):
  ```
  .<caption>
  image::images/<RUN_TS>-<Class>-<chart>.png[<caption>]
  Mermaid source: link:mermaid/<RUN_TS>-<Class>-<chart>.mmd[...]
  ```

#### S5.4 Generating

- **S5.4.1** Needs only JDK 25 (P0).
- **S5.4.2** Command (writes the results files, analysis docs, charts, part summary and `summary.json`):
  ```
  MMDC=<tools>/node_modules/.bin/mmdc \
    java src/main/java/tools/analysis/ShakersDocs.java . ${RUN_TS}
  ```
- **S5.4.3** There is no hand-written input (L7).
- **S5.4.4** `CLASSES=AnyMatchUtility,...` limits the classes. Without it, `ShakersDocs.java` does every class
  that has raw files in the run, so runs made before a class was added still regenerate.
- **S5.4.5** `CollectorsAndCollectors2` has no `test` parameter. Its charts and tables
  show one group, "one call (no parameter)", instead of match positions, and the JDK bar is
  JDK `Collectors` (orange) against EC `Collectors2` (bluish green `#009E73`).

### S6. JDK Stream ops analysis

- **S6.1** Purpose: which objects one JDK `stream()` call allocates, which part of the Stream API
  makes each one and what it costs, with the EC versions of each operation beside every JDK number.
  It explains why the per-class numbers are what they are.
- **S6.2** Inputs: `$R/all-histo-coh-on-histo.txt`, `all-histo-coh-off-histo.txt`, `all-histo-ea-off-histo.txt`
  (S3.5) and `generated/runs/${RUN_TS}/shakers/summary.json` (from S5.4, so run that first).
- **S6.3** Outputs:

  | ID | What | Path |
  |---|---|---|
  | S6.3.1 | Analysis doc | `reports/${RUN_TS}-JdkStreamOpsAnalysis.adoc` |
  | S6.3.2 | Bytes per call by origin (stacked bars) | `${RUN_TS}-JdkStreamOps-bytes-by-origin.{png,mmd}` |
  | S6.3.3 | Size of each class, compact headers off vs on | `${RUN_TS}-JdkStreamOps-class-sizes-coh.{png,mmd}` |
  | S6.3.4 | JDK vs every EC version, bytes by origin, escape analysis off, one per operation | `${RUN_TS}-JdkStreamOps-vs-ec-{anyMatch,findFirst,count}.{png,mmd}` |

  PNGs go to `reports/images/`, `.mmd` files to `reports/mermaid/` (L2.4 to L2.6). The part summary links the doc.
- **S6.3.5** Command:
  ```
  java src/main/java/tools/analysis/JdkOpsDocs.java . ${RUN_TS}
  ```
- **S6.4** What the doc contains (the tool writes all of it from the data):
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
    `JdkOpsDocs.java`): Stream source, filter stage, terminal op, caller lambda or Integer box. A class
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
    `Predicates.bind`, update `EC_ORIGIN`, the doc text and `classify()` in `ShakersCharts.java`.
  - **S6.5.5** The EC implementations and their code are the `IMPLS` table; update it when a
    benchmark method is added or renamed.
  - **S6.5.6** Origin colours (Okabe-Ito): orange = Stream source, sky blue = filter stage, dark
    blue = terminal op, reddish purple = caller lambda, black with `//` hatch = Integer box,
    vermillion `#D55E00` with `xx` hatch = EC object. All six stay at least 17 CIELAB ΔE apart
    under the three simulations.
  - **S6.5.7** Label anything not read from the data or the bytecode as inferred.

### S7. Summary docs

- **S7.1** `ShakersDocs.java` writes the shakers part summary, `reports/${RUN_TS}-Summary.adoc`,
  with the analysis docs (S5.4.2); nothing is written by hand.
- **S7.2** It holds: where it ran (from `meta.txt`); one table row per class with the JDK method, its
  bytes and objects per call, the EC bytes per call and JDK time ÷ EC time; links to every analysis,
  results file and the JDK Stream ops analysis; the caveats.
- **S7.3** Links are file names in the same folder (e.g. `link:${RUN_TS}-CountUtilityAnalysis.adoc[...]`).
- **S7.4** `RunSummary.java` puts both part summaries into the full run summary (L2.7), one heading
  level down, with every link prefixed by `<part>/reports/`.

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
- **M1.5** A new test is added to `MOVERS` in `Pipeline.java`, to the M2.4 loop, to `MoversDocs.java`
  and to this table.

### M2. Run

- **M2.1** Run each test twice, compact headers off then on: two baselines.
- **M2.2** JOL attaches an agent to its own VM, so `-Djdk.attach.allowAttachSelf=true
  -XX:+EnableDynamicAgentLoading` are required; without them JOL fails under surefire.
- **M2.3** Set and Map take about 13 minutes per configuration.
- **M2.4** `Pipeline.java run movers` does this. By hand, with `RUN_TS`, `MR` and `meta.txt` from S3.1:
  ```
  for CFG in coh-off coh-on; do
    case $CFG in
      coh-off) COH="-XX:-UseCompactObjectHeaders" ;;
      coh-on)  COH="-XX:+UseCompactObjectHeaders" ;;
    esac
    for T in ListMemoryTest SetMemoryTest MapMemoryTest StreamVsLazyIterableMemoryTest; do
      mvn -B test -Dtest=$T -Dsurefire.failIfNoSpecifiedTests=false \
        -DargLine="${COH} -Djdk.attach.allowAttachSelf=true -XX:+EnableDynamicAgentLoading" \
        > $MR/$T-$CFG.txt 2>&1
    done
  done
  ```
- **M2.5** A plain `mvn test` runs every test, including these slow ones without their flags, so
  always name the test.

### M3. Raw files

- **M3.1** One file per test and configuration: `generated/runs/${RUN_TS}/movers/raw/<Test>Test-<CFG>.txt`,
  the Maven output, e.g. `MapMemoryTest-coh-on.txt`. Nothing is copied or sorted by hand.
- **M3.2** `MoversDocs.java` reads the `... size: N Memory (bytes): B` lines (size tests) and the
  printed table rows (pipeline test) from it, and the environment and JDK from `meta.txt` in the same folder.
- **M3.3** Older results files with an HTML comment header instead of `meta.txt` (run 20261015-1503)
  still work: the environment and JDK are read from that header.

### M4. Analysis docs and charts

- **M4.1** Command (one doc per test, plus the movers part summary `${RUN_TS}-Summary.adoc` with the
  Summary of every test):
  ```
  MMDC=<tools>/node_modules/.bin/mmdc java src/main/java/tools/analysis/MoversDocs.java . ${RUN_TS}
  ```
- **M4.2** Docs go to `generated/runs/${RUN_TS}/movers/reports/` (L2.4), PNGs to its `images/` (L2.5),
  Mermaid sources to its `mermaid/` (L2.6).
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

## D. Slides

### D1. Sources

- **D1.1** The slides live in `assets/presentation-40min/` (the source; `assets/presentation/` is build
  output, D2.5). Each slide is its own file,
  `<series>_<deck>_<slide>.adoc` (e.g. `03_Definitions_01_02.adoc`). Start a new one from
  `slide-template.adoc`, which also says how much fits on one slide.
- **D1.2** One `==` heading is one 16:9 slide; no `===`. Budget per slide: a lead plus at most 4
  one-line bullets, or a diagram plus 2 bullets, or at most 14 code lines plus 2 bullets, or a table
  of at most 7 rows. Project numbers go in `[.notes]`.
- **D1.3** No `=` document title. The first slide of a deck is
  `[%notitle.title-slide,background-image=banner-NN.svg]` `== <title>` with the banner image; its
  heading becomes the tab title. In `02_ProblemStatement` and `03_Definitions` the last slide of a deck
  is References; the `01_Intro` references live in the appendix (D1.9).
- **D1.4** References link to Oracle pages only (for example the Java SE documentation on
  docs.oracle.com); no Eclipse Collections or other links.
- **D1.5** No JavaScript anywhere in the decks. Code colouring is done by rouge at build time.
- **D1.6** The deck files `<series>_<deck>.adoc` are generated include-assemblers, written by `SlideNav.java` into the work copy `target/slides/src` (never into the slide folder); do not edit them by hand.
- **D1.7** Every slide has a guarded header (`ifndef::deck[]`) and a navigation block after a marker
  comment; `SlideNav.java` rewrites both (D2.3).
- **D1.8** `index.adoc` is the cover of the whole talk (title, Duke image, QR code and link to the
  content, disclaimer), styled by the `cover` role in both docinfo files. It belongs to no series and
  opens a short run of opening pages `00_Index_NN.adoc`; `00_Index_01.adoc` is the audience question
  (role `question`, one large `[.ask]` line). `SlideNav.java` chains the opening pages in file-name
  order and sends the last one's Next to the first slide of `01_Intro`, whose Previous links back to it.
  The series follow each other: the last slide of a series links Next to the first slide of the next
  series (e.g. `01_Intro` ends on `02_ProblemStatement_00_00.adoc`), and that slide links Previous back.
  The last slide of the last series (the end of the talk) has no Next link.
  An opening page with the attribute line `:talk-index:` is the index of the whole talk: every Index
  link (opening pages included) points to it instead of the series agenda or the cover
  (`assets/presentation-40min/00_Index_03.adoc`, one link per section).
  `SlidesToPdf.java` prints each opening page as `pdf/index.pdf, 00_Index_NN.pdf`.
  Every slide carries one running number for the whole talk (cover = 1, then the opening pages, then the
  series in file-name order): `SlideNav.java` writes it as a `[.slide-no]` paragraph at the top of the
  generated block, so it shows in the plain HTML, the reveal.js slides and the PDF, and it is recomputed on
  every build (renaming or moving slides renumbers them). reveal.js's own per-deck number is off
  (`:revealjs_slideNumber: false` in `_deck-settings.adoc`).
  A slide with the attribute line `:slide-hidden:` (above `ifndef::deck[]`) is hidden: `SlideNav.java` skips
  it (no links to it, no number, not in the deck files or the PDF) and `SlidePublish.java` does not publish
  its page; Previous/Next of its neighbours skip over it. Delete the line to show the slide again.
  The slide sources folder (`assets/presentation-40min`) is never deleted or changed by any build step: `SlideNav.java`
  only reads it, copies it to `target/slides/src` and writes the navigation, slide numbers and deck files into that
  copy, which is what gets converted; the profile only cleans `target/slides` and empties the output folder
  (`assets/presentation`), and `SlidePublish.java` refuses an output folder that is, or contains, the slide sources.
  The navigation blocks inside the source files are therefore no longer updated (the published pages get the current ones).
- **D1.9** `07_Appendix` collects reference links at the end of the talk: `07_Appendix_00_00.adoc` (title,
  `banner-appendix-00.svg`) and one references slide per series, several sections on one slide as a
  table (Section | Reference, at most 7 rows, D1.2). `07_Appendix_00_01.adoc` holds the `01_Intro`
  references (jmap, jcmd, jps, jstat, JOL); each section name links back to that tool's first slide.

### D2. Build (stage `slides`)

- **D2.1** Commands:
  ```
  mvn -Pslides process-resources                            # what Pipeline.java slides runs
  mvn -Pslides process-resources -Dslides.dir=<folder>       # another slide source folder (D3)
  ```
- **D2.2** Needs only Java 25 and Maven (no Python, Ruby gems or Node.js), plus network access to the
  Kroki server for diagram blocks (D2.8).
- **D2.3** `generate-resources` runs `SlideNav.java` (rewrites each slide's Previous / Index / Next
  links and the deck files) and `SlideImages.java` (draws the banners, the
  `diagram-*.svg` images and the screenshot placeholders into `assets/images/`), then converts the AsciiDoc with AsciidoctorJ into work files in
  `target/slides/` (`plain/`: every page as plain HTML; `slides/`: the reveal.js decks, with reveal.js
  from its WebJar). The work folder is emptied first (`initialize`), so pages of removed slides never
  reach the output. `generate-resources` alone stops here and publishes nothing.
- **D2.4** `process-resources` then publishes: `SlidePublish.java` empties `assets/presentation/` and
  copies in the slide pages (`<series>_<deck>_<slide>.html`, `index.html`, `00_Index_NN.html`; not the deck files or
  `slide-template.html`); the pages show the images from `assets/images/` (D2.5.2). `SlidesToPdf.java` prints each reveal.js deck in
  headless Chrome through Playwright for Java, one slide per page, in memory. Browser: `CHROME`, else an
  installed Google Chrome or Chromium (macOS or Linux), else the Chromium under
  `PLAYWRIGHT_BROWSERS_PATH` (the cloud sandbox has one); without any, Playwright downloads its own
  Chromium once. It then joins the deck PDFs in talk order (file name order) into one PDF of the whole
  talk with Apache PDFBox (D2.5.3). The PDF hides the Previous / Index / Next links
  (`html.print-pdf .reveal .deck-nav` in `docinfo-revealjs.html`), since they are for reading and handing out.
- **D2.5** Output in `assets/presentation/` (property `slides.out`), rewritten by every build; nothing else
  is written there:
  - **D2.5.1** `<series>_<deck>_<slide>.html` and `index.html`, `00_Index_NN.html`: one page per slide, no JavaScript.
    Present from here, starting at `index.html`.
  - **D2.5.2** No images are written here. Every image of the talk (SVG or PNG) exists once, in
    `assets/images/`; the pages refer to it by relative path (`../images/`), which resolves from
    `assets/presentation/` and from the slide folder alike, and the slide sources use `:imagesdir: ../images` (D3.3).
  - **D2.5.3** `Who-Moved-My-Heap.pdf` (property `slides.pdf.name`): the whole talk in one PDF to
    distribute, every slide once with its running number. Links do not work in it.
- **D2.6** After adding, removing or renaming a slide, or changing a title or diagram, run the
  build (or `java src/main/java/tools/presentation/SlideNav.java . [slide folder]` and
  `SlideImages.java . [slide folder]` by hand). A new title slide needs a row in `SlideImages.java`: `SECTIONS` for `03_Definitions`
  (`banner-NN.svg`), `INTRO_SECTIONS` for `01_Intro` (`banner-intro-NN.svg`), `PROBLEM_SECTIONS` for
  `02_ProblemStatement` (`banner-problem-NN.svg`), `APPENDIX_SECTIONS` for `07_Appendix`
  (`banner-appendix-NN.svg`); a new series gets its own array and banner prefix.
- **D2.6.1** Screenshot slides use `image::<file>.png[<alt>,role=screenshot]` (a larger image area than
  `role=diagram`) with one `[.lead]` line and no bullets. Until a screenshot exists the slide shows a
  `placeholder-*.svg` drawn by `SlideImages.java`; the comment above the image line names the PNG to add.
- **D2.7** Check: following Next from the first slide reaches every slide, and no slide scrolls at
  the usual window sizes.
- **D2.8** Diagram blocks (`[plantuml]`, `[mermaid]`, `[graphviz]`, ...) are rendered by Kroki through
  `asciidoctorj-diagram` in the `slides` profile (`diagram-server-url` = property `kroki.url`, default
  `https://kroki.io`; `diagram-server-type` = `kroki_io`). Kroki has no QR code type, so a QR code is a
  `[plantuml]` block with `<qrcode:URL{scale=6}>` in a legend. The cover `index.adoc` shows the
  committed `assets/images/qrcode.png` in the slide build (no network needed) and, on GitHub (`env-github`), the
  same PlantUML QR code as an image URL served by kroki.io.
  `plantuml-preprocess` is `off` so no local PlantUML is needed. A local server:
  `docker run -p 8000:8000 yuzutech/kroki`, then `mvn -Pslides process-resources -Dkroki.url=http://localhost:8000`.
  If the server cannot be reached the build still succeeds and the slide shows "Failed to generate image".

### D3. Other slide folders

- **D3.1** The slide build works on any source folder with the D1 file naming: property `slides.dir` in
  the `slides` profile (relative to the project root, default `assets/presentation-40min`), or
  `Pipeline.java slides --slides-dir=<folder>`. `SlideNav.java` and `SlideImages.java` take the folder as
  their second argument (same default); the output still goes to `assets/presentation/` (D2.5).
- **D3.2** A folder needs its own `_deck-settings.adoc`, `docinfo.html`, `docinfo-revealjs.html` and
  and `:imagesdir: ../images`. `SlideImages.java` writes all drawn images (banners, diagrams, placeholders) to the one shared `assets/images/`.
- **D3.3** Banners: with a `_banners.txt` in the folder, `SlideImages.java` draws the banners listed there
  instead of the built-in ones (D2.6). One line per banner,
  `file | kicker | number | title | subtitle | accent | track`; `backticked` words in the subtitle are drawn in the code font; the track is `label=#colour` pills separated
  by commas, `*` before the label marks the current section; lines starting with `#` are comments. A banner
  file is named after its series (`01_Intro.svg` for the `01_Intro_00_01.adoc` title slide) and is written
  once, to the shared `assets/images/`. Slides refer to images by bare name (`image::01_Intro.svg[]`), resolved
  through `imagesdir`: `../images` in the sources and in the built pages. No other folder holds a
  copy (the build copies the shared images into `target/slides/images/` only for its work files).
- **D3.4** `assets/presentation-40min/` is the 40-minute talk (sections Intro, Problem, Definitions,
  Solutions, AI skills, Wrap-up, Appendix; timing in each slide's notes), cut from the original long
  deck, which is archived outside the project. It has no Kroki diagram blocks, so it builds offline.

---

## W. Working notes (Claude and the local Mac)

- **W1** The same commands run on the local Mac and in the cloud sandbox. `meta.txt` records which
  (S3.1.3), and every doc shows it.
- **W2** When Claude works in the cloud with the local Mac folder connected, the Mac's shell may not
  have JDK 25. Then Claude runs the `reports` stage on a copy in the cloud and copies the outputs to
  the local Mac (G6, G10). Benchmark runs belong on the local Mac.
- **W3** Link check for a run: every `link:` and `image::` target in
  `generated/runs/${RUN_TS}/**/*.adoc` resolves relative to its doc.
- **W4** A copy made right after writing to a synced folder can carry the previous content; always
  compare md5s after copying (G10). PNGs that pass through such a folder can differ in bytes but not
  in pixels: compare pixels, not bytes. SVGs can gain a provenance block; compare them without it.
- **W5** The HistoProfiler deadlock (S3.5.2) shows as a run stuck at `Iteration 1:` with no error.
- **W6** JOL fails under surefire without the two attach flags (M2.2).
- **W7** Files open in IntelliJ can be written back with old content after a copy; ask for a reload
  from disk (G10).
- **W8** A restarted container kills the `Pipeline.java` process but keeps the files on disk. Check
  that no benchmark process is left running, then continue with `--resume` (P7). A background watcher
  job in the cloud sandbox stops after 2 hours; start it again, the pipeline itself is not affected.
- **W9** The cloud sandbox cannot reach `kroki.io` (blocked by its network policy). Build the slides
  there against a local Kroki-compatible server (D2.8), or build them on the local Mac.
