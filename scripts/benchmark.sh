#!/usr/bin/env bash
# Roda os Macrobenchmarks num aparelho plugado e resume as métricas contra os
# orçamentos de docs/performance.md (§7).
#
#   scripts/benchmark.sh            # edição lite, StartupBenchmark + ScrollBenchmark
#   scripts/benchmark.sh full       # edição full
#   scripts/benchmark.sh lite Startup   # só uma classe (Startup | Scroll)
#
# O aparelho precisa estar destravado, em modo avião e com bateria > 30%.
# Não roda em emulador: os números só valem em hardware real.
set -euo pipefail

cd "$(dirname "$0")/.."

flavor="${1:-lite}"
only="${2:-}"
case "$flavor" in
  lite) Flavor=Lite ;;
  full) Flavor=Full ;;
  *) echo "edição desconhecida: $flavor (use lite ou full)" >&2; exit 2 ;;
esac

adb="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"
command -v adb >/dev/null && adb=adb
devices=$("$adb" devices | awk 'NR>1 && $2=="device"{print $1}')
if [ -z "$devices" ]; then
  echo "nenhum aparelho autorizado em 'adb devices'." >&2
  exit 1
fi
echo "aparelho: $devices"
echo "apps visíveis no aparelho: $("$adb" shell pm list packages -3 | wc -l) de terceiros (o orçamento de rolagem supõe ~300 no total)"

args=()
if [ -n "$only" ]; then
  args+=("-Pandroid.testInstrumentationRunnerArguments.class=app.cascata.launcher.baselineprofile.${only}Benchmark")
fi

./gradlew --no-daemon ":baselineprofile:connected${Flavor}BenchmarkReleaseAndroidTest" "${args[@]}"

json=$(find baselineprofile/build/outputs/connected_android_test_additional_output \
  -name '*benchmarkData.json' -newer app/build.gradle.kts 2>/dev/null | head -1)
if [ -z "$json" ]; then
  json=$(find baselineprofile/build/outputs -name '*benchmarkData.json' | head -1)
fi
[ -n "$json" ] || { echo "benchmarkData.json não encontrado." >&2; exit 1; }
echo
echo "resultado: $json"
python3 - "$json" <<'PY'
import json, sys
data = json.load(open(sys.argv[1]))
ctx = data.get("context", {})
build = ctx.get("build", {})
print(f"aparelho: {build.get('brand','?')} {build.get('model','?')}, Android {build.get('version',{}).get('sdk','?')}, "
      f"CPU trav.: {ctx.get('cpuLocked','?')}, sustained perf: {ctx.get('sustainedPerformanceModeEnabled','?')}")
print()
print(f"{'benchmark':<36} {'métrica':<26} {'mediana/P50':>12} {'P90':>8} {'P99':>8}  orçamento")
ok = True
for b in data.get("benchmarks", []):
    name = f"{b['className'].rsplit('.',1)[-1]}.{b['name']}"
    for m, v in b.get("metrics", {}).items():
        med = v.get("median"); note = ""
        if m == "timeToInitialDisplayMs" and b["name"] == "startupComPerfil":
            note = "< 500 ms " + ("✔" if med is not None and med < 500 else "✘"); ok &= bool(med is not None and med < 500)
        print(f"{name:<36} {m:<26} {med:>12.1f} {'':>8} {'':>8}  {note}")
    for m, v in b.get("sampledMetrics", {}).items():
        p50, p90, p99 = v.get("P50"), v.get("P90"), v.get("P99"); note = ""
        if m == "frameOverrunMs":
            note = "P99 ≤ 0 " + ("✔" if p99 is not None and p99 <= 0 else "✘ (ver frameDurationCpuMs)")
        if m == "frameDurationCpuMs":
            note = "P99 ≤ 16,6 ms (60 Hz) " + ("✔" if p99 is not None and p99 <= 16.6 else "✘")
            ok &= bool(p99 is not None and p99 <= 16.6)
        print(f"{name:<36} {m:<26} {p50:>12.1f} {p90:>8.1f} {p99:>8.1f}  {note}")
print()
print("orçamentos do plano:", "todos dentro ✔" if ok else "algum estourou ✘ — veja docs/performance.md §7")
PY
