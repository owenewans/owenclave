#!/usr/bin/env bash
# Helpers for the desktop CI workflow.
#
# Downloading workflow logs requires an authenticated token, while check run
# annotations are readable anonymously. These helpers therefore turn the output of
# a failed Gradle/jpackage run into `::error::` annotations, so a broken packaging
# step can be diagnosed from the public GitHub API.
#
# Usage (inside a workflow step):
#   source bin/desktop/ci-report.sh
#   if ! ./gradlew :desktop:app:createDistributable --console=plain > /tmp/x.log 2>&1; then
#     report_gradle_failure "createDistributable" /tmp/x.log
#     dump_jpackage_logs desktop/app/build/compose/logs
#     dump_jpackage_args desktop/app/build/compose/tmp/createDistributable.args.txt
#     exit 1
#   fi

# Prints the interesting lines of a failed Gradle run as annotations.
report_gradle_failure() {
  local label="$1" log="$2"
  echo "::error::$label failed"
  [ -f "$log" ] || return 0
  grep -aE "What went wrong|Execution failed|Caused by|^> |FAILURE|error:|Error:|Exception" "$log" \
    | grep -avE "^> (Task |Configure project )" \
    | head -8 | while IFS= read -r line; do
      echo "::error::${line//$'\r'/}"
    done
}

# Dumps every jpackage stdout/stderr file below <root> (one level per task).
dump_jpackage_logs() {
  local root="${1:-desktop/app/build/compose/logs}"
  local f
  for f in "$root"/*/*-err.txt "$root"/*/*-out.txt; do
    [ -f "$f" ] || continue
    local tag
    tag="$(basename "$(dirname "$f")")/$(basename "$f")"
    while IFS= read -r line; do
      echo "::error::[$tag] $line"
    done < <(head -25 "$f")
  done
}

# Dumps a generated jpackage @args file, one argument per annotation.
dump_jpackage_args() {
  local file="$1"
  [ -f "$file" ] || return 0
  while IFS= read -r line; do
    echo "::error::jpackage-arg $line"
  done < <(tr -s ' ' '\n' < "$file" | grep -v '^$' | head -60)
}

# Surfaces JUnit XML failures, JVM hs_err crash logs, and log tails as annotations.
# Note: GitHub Actions caps ::error:: annotations at 10 per step, so multiline
# payloads are URL-encoded (%0A) into single annotations to avoid truncation.
report_test_failures() {
  local label="$1" log="$2"
  if command -v python3 >/dev/null 2>&1; then
    python3 - "$label" "$log" <<'PY' || true
import glob, os, sys, xml.etree.ElementTree as ET

label, log_path = sys.argv[1], sys.argv[2]

def esc(text: str) -> str:
    return text.replace("%", "%25").replace("\r", "").replace("\n", "%0A")

emitted = 0
for path in sorted(glob.glob("desktop/*/build/test-results/test/*.xml")):
    try:
        root = ET.parse(path).getroot()
    except Exception:
        continue
    for tc in root.findall("testcase"):
        for tag in ("failure", "error"):
            el = tc.find(tag)
            if el is not None and emitted < 5:
                cls = tc.attrib.get("classname", "")
                name = tc.attrib.get("name", "")
                msg = (el.attrib.get("message") or "").strip()
                body = "\n".join((el.text or "").splitlines()[:20])
                sys.stdout.write(f"::error::[JUnit {cls}.{name}] {esc(msg + chr(10) + body)[:3500]}\n")
                emitted += 1

for hs in sorted(glob.glob("**/hs_err_pid*.log", recursive=True))[:2]:
    try:
        with open(hs, "r", errors="replace") as f:
            head = "".join(f.readlines()[:35])
        sys.stdout.write(f"::error::[hs_err {hs}] {esc(head)[:3500]}\n")
    except Exception:
        pass

if os.path.isfile(log_path):
    with open(log_path, "r", errors="replace") as f:
        lines = [ln.rstrip("\r\n") for ln in f if not ln.startswith("> Task ") and not ln.startswith("> Configure project ")]
    interesting = [ln for ln in lines if any(k in ln for k in ("FAILED", "FAIL", "Exception", "Error", "error", " W/", " E/", "Caused by", "What went wrong", "non-zero exit value", "Process "))]
    summary = "\n".join((interesting[-30:] + ["--- tail ---"] + lines[-35:]))
    sys.stdout.write(f"::error::[{label}] {esc(summary)[:3800]}\n")
PY
    return 0
  fi
  report_gradle_failure "$label" "$log"
}
