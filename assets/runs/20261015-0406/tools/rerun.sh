#!/bin/bash
cd "$(dirname "$0")"; export PATH=/usr/lib/jvm/java-25-openjdk-amd64/bin:$PATH; unset JAVA_TOOL_OPTIONS
R=assets/runs/$(cat full_run_ts); CLASS=AnyMatchUtility; CONFIG=coh-on; FLAGS="-XX:+UseCompactObjectHeaders"
java -jar target-bench/microbenchmarks.jar "conf\.session\.shakers\.${CLASS}Benchmark\." \
  -prof gc -jvmArgsAppend "-XX:+UseG1GC -Xms2g -Xmx2g ${FLAGS}" \
  -rf text -rff $R/${CLASS}-${CONFIG}.txt -o $R/${CLASS}-${CONFIG}.log
