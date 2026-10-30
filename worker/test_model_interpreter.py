import json
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from worker.agent_worker import WorkerError, validate_task
from worker.model_interpreter import (
    DeepSeekResponsesProvider,
    MODEL_OUTPUT_SCHEMA,
    ModelError,
    build_create_note_plan,
    compile_interpretation,
    deepseek_http_error_message,
    extract_output_text,
)


def ready_output(title="旅行清单", body="护照和充电器"):
    return {
        "status": "READY",
        "intent": "CREATE_NOTE",
        "targetApp": "STANDARD_NOTES",
        "userGoal": "记录旅行用品",
        "summary": "创建旅行清单",
        "missingInfo": [],
        "clarificationQuestion": "",
        "slots": [
            {"name": "NOTE_TITLE", "value": title},
            {"name": "NOTE_BODY", "value": body},
        ],
        "proposedSteps": [
            {"stepId": "1", "action": "LAUNCH_APP", "target": "STANDARD_NOTES", "parameterRefs": []},
            {"stepId": "2", "action": "CREATE_NOTE", "target": "note", "parameterRefs": ["NOTE_TITLE", "NOTE_BODY"]},
            {"stepId": "3", "action": "VERIFY_NOTE", "target": "note", "parameterRefs": ["NOTE_TITLE", "NOTE_BODY"]},
        ],
    }


class ModelCompilerTest(unittest.TestCase):
    def test_insufficient_balance_has_a_user_friendly_message(self):
        self.assertEqual(
            "DeepSeek API 账户余额不足，请充值后重试",
            deepseek_http_error_message(402, "provider detail"),
        )

    def test_responses_provider_sends_json_schema_and_reads_output(self):
        captured = {}

        class FakeResponsesHandler(BaseHTTPRequestHandler):
            def log_message(self, format_, *args):
                pass

            def do_POST(self):
                length = int(self.headers["Content-Length"])
                captured["authorization"] = self.headers["Authorization"]
                captured["payload"] = json.loads(self.rfile.read(length))
                output = {
                    "output": [
                        {
                            "type": "message",
                            "content": [
                                {
                                    "type": "output_text",
                                    "text": json.dumps(ready_output(), ensure_ascii=False),
                                }
                            ],
                        }
                    ]
                }
                body = json.dumps(output, ensure_ascii=False).encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

        server = ThreadingHTTPServer(("127.0.0.1", 0), FakeResponsesHandler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            provider = DeepSeekResponsesProvider(
                api_key="test-key",
                model="test-model",
                api_url="http://127.0.0.1:%d/v1/responses" % server.server_port,
            )
            result = compile_interpretation(provider.interpret("帮我记一条旅行清单"))
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)

        self.assertEqual("Bearer test-key", captured["authorization"])
        self.assertEqual("test-model", captured["payload"]["model"])
        self.assertEqual("json_schema", captured["payload"]["text"]["format"]["type"])
        self.assertEqual(
            MODEL_OUTPUT_SCHEMA,
            captured["payload"]["text"]["format"]["schema"],
        )
        self.assertEqual("READY", result["status"])

    def test_example_placeholder_is_not_treated_as_a_real_key(self):
        provider = DeepSeekResponsesProvider(
            api_key="replace_with_your_deepseek_api_key",
        )

        with self.assertRaises(ModelError):
            provider.interpret("帮我记一下")

    def test_ready_output_is_compiled_to_canonical_safe_plan(self):
        result = compile_interpretation(ready_output())

        self.assertEqual("READY", result["status"])
        task = result["task"]
        self.assertTrue(task["confirmationRequired"])
        self.assertEqual(
            ["LAUNCH_APP", "CREATE_NOTE", "VERIFY_NOTE"],
            [step["action"] for step in task["steps"]],
        )
        self.assertIn("DELETE", task["forbiddenActions"])

    def test_missing_slot_becomes_clarification(self):
        output = ready_output()
        output["slots"] = [{"name": "NOTE_TITLE", "value": "旅行清单"}]

        result = compile_interpretation(output)

        self.assertEqual("NEEDS_CLARIFICATION", result["status"])
        self.assertIsNone(result["task"])

    def test_unsupported_output_never_compiles_to_task(self):
        output = ready_output()
        output.update({"status": "UNSUPPORTED", "intent": "UNSUPPORTED", "targetApp": "NONE"})

        result = compile_interpretation(output)

        self.assertEqual("UNSUPPORTED", result["status"])
        self.assertIsNone(result["task"])

    def test_duplicate_slots_are_rejected(self):
        output = ready_output()
        output["slots"].append({"name": "NOTE_TITLE", "value": "另一个标题"})

        with self.assertRaises(ModelError):
            compile_interpretation(output)

    def test_response_refusal_is_detected(self):
        response = {"output": [{"type": "message", "content": [{"type": "refusal", "refusal": "no"}]}]}

        with self.assertRaises(ModelError):
            extract_output_text(response)

    def test_worker_accepts_compiled_plan(self):
        task = build_create_note_plan("旅行清单", "护照和充电器", "记录旅行用品")
        task["confirmedTaskVersion"] = task["taskVersion"]

        title, body = validate_task(task)

        self.assertEqual("旅行清单", title)
        self.assertEqual("护照和充电器", body)

    def test_worker_rejects_tampered_confirmation(self):
        task = build_create_note_plan("旅行清单", "护照和充电器", "记录旅行用品")
        task["confirmedTaskVersion"] = "older-version"

        with self.assertRaises(WorkerError):
            validate_task(task)


if __name__ == "__main__":
    unittest.main()
