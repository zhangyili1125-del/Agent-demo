#!/usr/bin/env python3
"""Local-only planning and execution service for Parallel Agent."""

import base64
import json
import re
import subprocess
import threading
import time
import xml.etree.ElementTree as ET
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

try:
    from .model_interpreter import (
        ModelError,
        DeepSeekResponsesProvider,
        compile_interpretation,
    )
except ImportError:
    from model_interpreter import (
        ModelError,
        DeepSeekResponsesProvider,
        compile_interpretation,
    )

HOST = "127.0.0.1"
PORT = 8787
EMULATOR = "emulator-5554"
STANDARD_NOTES = "com.standardnotes/.MainActivity"
HELPER_RECEIVER = (
    "com.parallelagent.inputhelper/"
    ".ClipboardReceiver"
)
RESULTS = {}
EXECUTION_LOCK = threading.Lock()
MODEL_PROVIDER = DeepSeekResponsesProvider()


class WorkerError(RuntimeError):
    pass


def adb(*args, timeout=15, check=True):
    command = ["adb", "-s", EMULATOR, *args]
    completed = subprocess.run(
        command,
        capture_output=True,
        text=True,
        timeout=timeout,
    )
    if check and completed.returncode != 0:
        detail = completed.stderr.strip() or completed.stdout.strip()
        raise WorkerError("虚拟手机命令失败：" + detail)
    return completed.stdout


def dump_ui():
    adb("shell", "uiautomator", "dump", "/sdcard/parallel-agent.xml")
    xml_text = adb("shell", "cat", "/sdcard/parallel-agent.xml")
    return ET.fromstring(xml_text)


def bounds_center(bounds):
    values = [int(value) for value in re.findall(r"\d+", bounds)]
    if len(values) != 4:
        raise WorkerError("无法读取页面控件位置")
    return (values[0] + values[2]) // 2, (values[1] + values[3]) // 2


def find_node(root, *, resource_id=None, text_contains=None):
    matches = []
    for node in root.iter("node"):
        if resource_id and node.attrib.get("resource-id") != resource_id:
            continue
        searchable = " ".join(
            [node.attrib.get("text", ""), node.attrib.get("content-desc", "")]
        )
        if text_contains and text_contains not in searchable:
            continue
        matches.append(node)
    if not matches:
        return None
    # Standard Notes keeps some duplicate controls in off-screen/hidden layouts.
    # Prefer the matching control nearest the top of the visible screen.
    return min(
        matches,
        key=lambda node: bounds_center(node.attrib.get("bounds", ""))[1],
    )


def tap_node(node):
    if node is None:
        raise WorkerError("虚拟手机页面上没有找到需要的控件")
    x, y = bounds_center(node.attrib.get("bounds", ""))
    adb("shell", "input", "tap", str(x), str(y))


def wait_for_node(*, resource_id=None, text_contains=None, timeout=12):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        root = dump_ui()
        node = find_node(
            root,
            resource_id=resource_id,
            text_contains=text_contains,
        )
        if node is not None:
            return root, node
        time.sleep(0.7)
    raise WorkerError("等待 Standard Notes 页面超时")


def wait_for_note_page(timeout=6):
    deadline = time.monotonic() + timeout
    last_root = None
    while time.monotonic() < deadline:
        last_root = dump_ui()
        if (
            find_node(last_root, resource_id="note-title-editor") is not None
            or find_node(last_root, text_contains="Create a new note") is not None
        ):
            return last_root
        time.sleep(0.7)
    return last_root


def paste_unicode(text):
    encoded = base64.b64encode(text.encode("utf-8")).decode("ascii")
    adb(
        "shell",
        "am",
        "broadcast",
        "-n",
        HELPER_RECEIVER,
        "-a",
        "com.parallelagent.inputhelper.SET_TEXT",
        "--es",
        "text_base64",
        encoded,
    )
    adb("shell", "input", "keyevent", "KEYCODE_PASTE")


def create_note(title, body):
    devices = subprocess.run(
        ["adb", "-s", EMULATOR, "get-state"],
        capture_output=True,
        text=True,
    )
    if devices.returncode != 0 or devices.stdout.strip() != "device":
        raise WorkerError("电脑里的 Android 虚拟手机没有启动")

    packages = adb("shell", "pm", "list", "packages", "com.parallelagent.inputhelper")
    if "com.parallelagent.inputhelper" not in packages:
        raise WorkerError("虚拟手机缺少中文输入辅助组件")

    adb("shell", "am", "start", "-n", STANDARD_NOTES)
    root = wait_for_note_page()

    # A spelling suggestion or keyboard popup can temporarily hide the WebView tree.
    # Wait for normal app loading first. Close a transient layer only when neither the
    # note editor nor list controls become visible after that grace period.
    if (
        root is None
        or (
        find_node(root, resource_id="note-title-editor") is None
        and find_node(root, text_contains="Create a new note") is None
        )
    ):
        adb("shell", "input", "keyevent", "BACK")
        time.sleep(0.8)
        root = wait_for_note_page(timeout=3)
        if root is None:
            raise WorkerError("Standard Notes 页面没有加载完成")

    # Return to the item list when an existing note is open.
    if find_node(root, resource_id="note-title-editor") is not None:
        list_button = find_node(root, text_contains="Go to items list")
        if list_button is not None:
            tap_node(list_button)
            time.sleep(0.8)

    root, create_button = wait_for_node(text_contains="Create a new note")
    tap_node(create_button)
    time.sleep(0.8)

    _, title_field = wait_for_node(resource_id="note-title-editor")
    tap_node(title_field)
    paste_unicode(title)

    root = dump_ui()
    body_field = find_node(root, resource_id="note-text-editor")
    tap_node(body_field)
    paste_unicode(body)
    adb("shell", "input", "keyevent", "BACK")
    time.sleep(4)

    root = dump_ui()
    saved_title = find_node(root, resource_id="note-title-editor")
    saved_body = find_node(root, resource_id="note-text-editor")
    if saved_title is None or saved_body is None:
        raise WorkerError("笔记已输入，但无法重新读取页面进行核对")
    actual_title = saved_title.attrib.get("text", "")
    actual_body = saved_body.attrib.get("text", "")
    if actual_title != title or actual_body != body:
        raise WorkerError(
            "核对失败：Standard Notes 中的标题或正文与任务不一致"
        )
    return {
        "success": True,
        "message": "已在虚拟手机创建并核对笔记《%s》。" % title,
        "verifiedTitle": actual_title,
        "verifiedBody": actual_body,
    }


def validate_task(payload):
    if payload.get("schemaVersion") != 2:
        raise WorkerError("不支持的任务单版本")
    if payload.get("taskVersion") != payload.get("confirmedTaskVersion"):
        raise WorkerError("确认已失效：任务内容在确认后发生了变化")
    if payload.get("intent") != "CREATE_NOTE":
        raise WorkerError("执行端目前只允许创建笔记")
    if payload.get("targetApp") != "Standard Notes":
        raise WorkerError("目标 App 不在允许范围内")
    if payload.get("riskLevel") != "MODIFIES_ACCOUNT":
        raise WorkerError("任务风险等级缺失或不正确")
    if payload.get("confirmationRequired") is not True:
        raise WorkerError("创建账号数据的任务必须经过用户确认")
    forbidden = payload.get("forbiddenActions")
    if not isinstance(forbidden, list) or not {
        "DELETE",
        "SHARE",
        "CHANGE_ACCOUNT_SETTINGS",
    }.issubset(set(forbidden)):
        raise WorkerError("任务安全边界缺失")

    steps = payload.get("steps")
    if not isinstance(steps, list) or len(steps) != 3:
        raise WorkerError("创建笔记任务必须包含三个标准步骤")
    actions = [step.get("action") for step in steps if isinstance(step, dict)]
    if actions != ["LAUNCH_APP", "CREATE_NOTE", "VERIFY_NOTE"]:
        raise WorkerError("任务步骤不符合已注册的创建笔记能力")
    if [step.get("order") for step in steps] != [1, 2, 3]:
        raise WorkerError("任务步骤顺序无效")

    create_parameters = steps[1].get("parameters")
    if not isinstance(create_parameters, list):
        raise WorkerError("创建笔记步骤缺少参数")
    parameters = {}
    for parameter in create_parameters:
        if not isinstance(parameter, dict):
            raise WorkerError("任务参数格式无效")
        key = parameter.get("key")
        value = parameter.get("value")
        if key in parameters:
            raise WorkerError("任务包含重复参数")
        parameters[key] = value
    title = parameters.get("NOTE_TITLE")
    body = parameters.get("NOTE_BODY")
    if not isinstance(title, str) or not title.strip() or len(title) > 120:
        raise WorkerError("笔记标题为空或过长")
    if not isinstance(body, str) or not body.strip() or len(body) > 2000:
        raise WorkerError("笔记正文为空或过长")
    return title.strip(), body.strip()


class Handler(BaseHTTPRequestHandler):
    def log_message(self, format_, *args):
        print("[worker] " + format_ % args, flush=True)

    def send_json(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/health":
            self.send_json(
                200,
                {
                    "ok": True,
                    "emulator": EMULATOR,
                    "modelConfigured": bool(MODEL_PROVIDER.api_key),
                    "model": MODEL_PROVIDER.model,
                    "capabilities": ["CREATE_NOTE"],
                },
            )
        else:
            self.send_json(404, {"success": False, "message": "not found"})

    def do_POST(self):
        if self.path not in {"/v1/interpret", "/v1/tasks"}:
            self.send_json(404, {"success": False, "message": "not found"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > 50_000:
                raise WorkerError("任务数据为空或过大")
            payload = json.loads(self.rfile.read(length).decode("utf-8"))
            if self.path == "/v1/interpret":
                request_text = payload.get("request")
                if not isinstance(request_text, str) or not request_text.strip():
                    raise WorkerError("自然语言请求不能为空")
                if len(request_text) > 4_000:
                    raise WorkerError("自然语言请求过长")
                model_output = MODEL_PROVIDER.interpret(request_text.strip())
                self.send_json(200, compile_interpretation(model_output))
                return

            title, body = validate_task(payload)
            task_version = payload["taskVersion"]
            with EXECUTION_LOCK:
                if task_version not in RESULTS:
                    RESULTS[task_version] = create_note(title, body)
                result = RESULTS[task_version]
            self.send_json(200, result)
        except ModelError as error:
            self.send_json(503, {"success": False, "message": str(error)})
        except (WorkerError, json.JSONDecodeError) as error:
            self.send_json(400, {"success": False, "message": str(error)})
        except Exception as error:
            self.send_json(
                500,
                {"success": False, "message": "执行端内部错误：%s" % error},
            )


if __name__ == "__main__":
    print("Parallel Agent worker listening on http://%s:%d" % (HOST, PORT), flush=True)
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
