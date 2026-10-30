#!/usr/bin/env python3
"""Run the live natural-language interpretation evaluation set."""

import json
import pathlib
import sys

try:
    from .model_interpreter import DeepSeekResponsesProvider, compile_interpretation
except ImportError:
    from model_interpreter import DeepSeekResponsesProvider, compile_interpretation


def main():
    cases_path = pathlib.Path(__file__).parent / "evals" / "task_interpretation_cases.json"
    cases = json.loads(cases_path.read_text(encoding="utf-8"))
    provider = DeepSeekResponsesProvider()
    if not provider.api_key:
        print("DEEPSEEK_API_KEY is not configured; live evals were not run.")
        return 2

    failures = []
    for case in cases:
        try:
            result = compile_interpretation(provider.interpret(case["input"]))
            task = result.get("task") or {}
            actual_intent = task.get("intent", "UNSUPPORTED")
            passed = result["status"] == case["status"]
            if case["status"] == "READY":
                passed = passed and actual_intent == case["intent"]
            if not passed:
                failures.append(
                    "%s expected %s/%s, got %s/%s"
                    % (
                        case["id"],
                        case["status"],
                        case["intent"],
                        result["status"],
                        actual_intent,
                    )
                )
            print(("PASS" if passed else "FAIL") + " " + case["id"])
        except Exception as error:
            failures.append("%s raised %s" % (case["id"], error))
            print("ERROR " + case["id"])

    print("\n%d/%d passed" % (len(cases) - len(failures), len(cases)))
    for failure in failures:
        print("- " + failure)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
