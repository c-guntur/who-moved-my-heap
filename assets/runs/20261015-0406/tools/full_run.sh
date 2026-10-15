#!/bin/bash
cd "$(dirname "$0")"
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 PATH=/usr/lib/jvm/java-25-openjdk-amd64/bin:$PATH
unset JAVA_TOOL_OPTIONS
mvn -q -f pom-bench.xml clean compile > build.log 2>&1 && mvn -q -f pom-bench.xml package >> build.log 2>&1 || { echo BUILD_FAILED > progress; exit 1; }
RUN_TS=$(date +%Y%m%d-%H%M); echo $RUN_TS > full_run_ts
R=assets/runs/${RUN_TS}; mkdir -p $R
{
  echo "run_ts: ${RUN_TS}"
  echo "environment: Claude cloud sandbox (KVM guest), not the local Mac"
  java -version 2>&1
  mvn -v 2>&1 | grep -v -e Unsafe -e HiddenClass -e JAVA_TOOL
  uname -a
  lscpu | grep -E 'Model name|^CPU\(s\)|Hypervisor|L3'
  free -g | head -2
  java -XX:+UseCompactObjectHeaders -XX:+PrintFlagsFinal -version 2>/dev/null \
    | grep -E ' Use(G1|Parallel|Serial|Z|Shenandoah)GC |UseCompactObjectHeaders|DoEscapeAnalysis'
  echo "sources: copied from the local Mac 2026-10-15 (not a git checkout); sha256:"
  sha256sum pom-bench.xml src/test/java/conf/session/shakers/*Benchmark.java src/test/java/prof/HistoProfiler.java
} > $R/meta.txt 2>&1
for CONFIG in coh-on coh-off ea-off; do
  case $CONFIG in
    coh-on)  FLAGS="-XX:+UseCompactObjectHeaders" ;;
    coh-off) FLAGS="-XX:-UseCompactObjectHeaders" ;;
    ea-off)  FLAGS="-XX:+UseCompactObjectHeaders -XX:-DoEscapeAnalysis" ;;
  esac
  for CLASS in AnyMatchUtility CountUtility FindFirstUtility \
               AnyMatchEagerLazy CountEagerLazy FindFirstEagerLazy; do
    echo "$(date -u +%H:%M) $CONFIG $CLASS" >> progress
    java -jar target-bench/microbenchmarks.jar "conf\.session\.shakers\.${CLASS}Benchmark\." \
      -prof gc -jvmArgsAppend "-XX:+UseG1GC -Xms2g -Xmx2g ${FLAGS}" \
      -rf text -rff $R/${CLASS}-${CONFIG}.txt \
      -o $R/${CLASS}-${CONFIG}.log
  done
done
for CONFIG in histo-coh-on histo-coh-off; do
  case $CONFIG in
    histo-coh-on)  COH="-XX:+UseCompactObjectHeaders" ;;
    histo-coh-off) COH="-XX:-UseCompactObjectHeaders" ;;
  esac
  echo "$(date -u +%H:%M) $CONFIG all" >> progress
  java -jar target-bench/microbenchmarks.jar "conf\.session\.shakers\." \
    -f 1 -wi 5 -w 1s -i 3 -r 100ms \
    -jvmArgs "-XX:+UseParallelGC -Xms12g -Xmx12g -Xmn10g ${COH}" \
    -prof prof.HistoProfiler -prof gc \
    -rf text -rff $R/all-${CONFIG}.txt \
    -o $R/all-${CONFIG}.log
done
echo "$(date -u +%H:%M) DONE" >> progress
touch FULL_DONE
