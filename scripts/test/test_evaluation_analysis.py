import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[2] / "catpaw/skills/silverpilot-operations/scripts/analyze_evaluations.py"
spec = importlib.util.spec_from_file_location("analysis", SCRIPT)
analysis = importlib.util.module_from_spec(spec)
spec.loader.exec_module(analysis)


def fixture():
    return {
        "schemaVersion": 2, "evidenceType": "synthetic", "executed": 2, "passed": 1,
        "provenance": {"sourceDirty": False, "sourceCommit": "example",
                       "datasetSha256": "dataset", "knowledgeSha256": "knowledge",
                       "promptSha256": "prompt", "selectedCases": ["a", "b"],
                       "configuredPrompts": {"version": "test"}},
        "results": [
            {"id": "a", "trial": 1, "passed": True, "latencyMs": 100, "totalTokens": 10},
            {"id": "b", "trial": 1, "passed": False, "latencyMs": None, "totalTokens": None},
        ],
    }


class AnalysisTests(unittest.TestCase):
    def load(self, data):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "report.json"
            path.write_text(json.dumps(data), encoding="utf-8")
            return analysis.load_report(path)

    def test_missing_metrics_not_imputed_as_zero(self):
        text = analysis.render([self.load(fixture())])
        self.assertIn("延迟样本：1/2", text)
        self.assertIn("P50：100", text)
        self.assertIn("synthetic", text)

    def test_reconciles_summary(self):
        data = fixture()
        data["passed"] = 2
        with self.assertRaises(ValueError):
            self.load(data)

    def test_duplicate_trials_rejected(self):
        data = fixture()
        data["results"][1] = copy.deepcopy(data["results"][0])
        with self.assertRaises(ValueError):
            self.load(data)

    def test_live_label_cannot_promote_mock_or_unknown_provider(self):
        for provider in ("mock", None):
            data = fixture()
            data["evidenceType"] = "live"
            data["results"][0]["provider"] = provider
            with self.subTest(provider=provider), self.assertRaises(ValueError):
                self.load(data)

    def test_comparison_requires_equal_population_and_clean_versions(self):
        for mutation in ("dirty", "knowledge", "cases", "evidence"):
            a, b = fixture(), fixture()
            if mutation == "dirty":
                b["provenance"]["sourceDirty"] = True
            elif mutation == "knowledge":
                b["provenance"]["knowledgeSha256"] = "changed"
            elif mutation == "cases":
                b["results"].pop()
            else:
                b["evidenceType"] = "live"
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                analysis.assert_comparable([a, b])

    def test_nonfinite_or_negative_values_rejected(self):
        for value in (-1, float("nan"), True):
            data = fixture()
            data["results"][0]["latencyMs"] = value
            with self.subTest(value=value), self.assertRaises(ValueError):
                self.load(data)

    def test_nearest_rank_percentiles(self):
        self.assertEqual(analysis.percentile([1, 2, 3, 4], .5), 2)
        self.assertEqual(analysis.percentile([1, 2, 3, 4], .95), 4)
        self.assertIsNone(analysis.percentile([], .5))


if __name__ == "__main__":
    unittest.main()
