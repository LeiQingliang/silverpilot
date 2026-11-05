"""Dependency-free, evidence-bounded summary of SilverPilot v2 evaluations."""
import argparse
from collections import Counter
import json
import math
from pathlib import Path


def percentile(values, fraction):
    values = sorted(values)
    return values[max(0, math.ceil(len(values) * fraction) - 1)] if values else None


def load_report(path):
    report = json.loads(Path(path).read_text(encoding="utf-8-sig"))
    if report.get("schemaVersion") != 2:
        raise ValueError("Only schemaVersion=2 reports are supported; rerun historical evaluations.")
    if report.get("evidenceType") not in {"live", "mock", "synthetic"}:
        raise ValueError("Missing or invalid evidenceType.")
    rows = report.get("results")
    if not isinstance(rows, list) or not rows:
        raise ValueError("Empty evaluation is not evidence.")
    seen = set()
    for row in rows:
        key = (row.get("id"), row.get("trial"))
        if not isinstance(key[0], str) or not key[0] or type(key[1]) is not int or key[1] < 1:
            raise ValueError("Each result needs a case ID and positive integer trial.")
        if key in seen:
            raise ValueError("Duplicate case/trial result.")
        seen.add(key)
        if type(row.get("passed")) is not bool:
            raise ValueError("passed must be a boolean.")
        provider = row.get("provider")
        if report["evidenceType"] == "live" and (not provider or provider == "mock"):
            raise ValueError("Live evidence cannot contain mock or unidentified providers.")
        if report["evidenceType"] == "mock" and provider not in ("mock", "unresolved"):
            raise ValueError("Mock evidence cannot mix real or unidentified providers.")
        for field in ("latencyMs", "totalTokens"):
            value = row.get(field)
            if value is not None and (type(value) not in (int, float) or not math.isfinite(value) or value < 0):
                raise ValueError("Invalid numeric metric: " + field)
    if report.get("executed") != len(rows) or report.get("passed") != sum(row["passed"] for row in rows):
        raise ValueError("Summary does not reconcile with individual trials.")
    return report


def assert_comparable(reports):
    if len(reports) < 2:
        return
    first = reports[0]
    for report in reports:
        provenance = report.get("provenance", {})
        if provenance.get("sourceDirty") is not False:
            raise ValueError("Comparisons require a clean, committed source snapshot.")
        for field in ("sourceCommit", "datasetSha256", "knowledgeSha256", "promptSha256"):
            if not provenance.get(field) or provenance[field] != first.get("provenance", {}).get(field):
                raise ValueError("Incomparable or missing provenance: " + field)
        for field in ("selectedCases", "configuredPrompts"):
            if not provenance.get(field) or provenance[field] != first.get("provenance", {}).get(field):
                raise ValueError("Incomparable runtime/case context: " + field)
        if report["evidenceType"] != first["evidenceType"]:
            raise ValueError("Cannot compare synthetic/mock and live evidence.")
        pairs = {(row["id"], row["trial"]) for row in report["results"]}
        if pairs != {(row["id"], row["trial"]) for row in first["results"]}:
            raise ValueError("Case/trial populations differ.")


def render(reports):
    assert_comparable(reports)
    lines = ["# SilverPilot 评测分析", "",
             "这是工程契约评测；不代表答案准确率、预约履约、用户增长或线上 A/B 效果。",
             "本地源码指纹不替代服务端构建核验；延迟仅覆盖返回运行计量的请求。", ""]
    for number, report in enumerate(reports, 1):
        rows = report["results"]
        latencies = [r["latencyMs"] for r in rows if r.get("latencyMs") is not None]
        tokens = [r["totalTokens"] for r in rows if r.get("totalTokens") is not None]
        models = sorted({str(r.get("model")) for r in rows if r.get("model")})
        statuses = Counter(r.get("status") or "UNRECORDED" for r in rows)
        failures = Counter(r.get("category") or "UNCLASSIFIED" for r in rows if not r["passed"])
        passed = sum(r["passed"] for r in rows)
        lines += [f"## 报告 {number}", "",
                  f"- 数据性质：{report['evidenceType']}；实际模型：{', '.join(models) or '未记录'}",
                  f"- 契约通过：{passed}/{len(rows)}（{passed / len(rows):.1%}），不等于业务任务完成率。",
                  f"- 延迟样本：{len(latencies)}/{len(rows)}；P50：{percentile(latencies, .5)} ms；P95：{percentile(latencies, .95)} ms。",
                  f"- Token 样本：{len(tokens)}/{len(rows)}；已记录总量：{sum(tokens)}；未估算费用。",
                  "- 运行状态：" + json.dumps(statuses, ensure_ascii=False),
                  "- 失败类别：" + json.dumps(failures, ensure_ascii=False), ""]
        if report["evidenceType"] != "live":
            lines += ["该报告仅供测试流程验证，不得据此判断真实模型优劣或写入业务业绩。", ""]
    lines += ["## 后续动作", "",
              "复核失败样本，区分知识、规划、工具和交互问题；记录修改版本与复测证据。人工质量评分与用户研究尚需独立执行。", ""]
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("reports", nargs="+", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    try:
        result = render([load_report(path) for path in args.reports])
    except (ValueError, OSError) as error:
        parser.error(str(error))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(result, encoding="utf-8")


if __name__ == "__main__":
    main()
