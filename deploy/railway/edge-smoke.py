#!/usr/bin/env python3
"""Bounded, loopback-only edge checks; never targets a public deployment.

--nginx /path/to/nginx runs the real configuration against an isolated HTTP
stub. --docker-image sha256:... does the same with a built Linux frontend image.
--base-url http://127.0.0.1:8082 checks a disposable Compose frontend.
Only the stub mode sends bursts/concurrent requests; no model is called.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
from contextlib import contextmanager
import http.client
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import ipaddress
import json
import os
from pathlib import Path
import re
import socket
import subprocess
import tempfile
import threading
import time
from urllib.parse import urlsplit


def request(port, path, method="GET", ip="192.0.2.1", body=None, headers=None):
    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=15)
    fields = {"Host": "localhost", "X-Real-IP": ip, "Connection": "close"}
    fields.update(headers or {})
    try:
        connection.request(method, path, body=body, headers=fields)
        response = connection.getresponse()
        return response.status, dict((k.lower(), v) for k, v in response.getheaders()), response.read()
    finally:
        connection.close()


def expect(port, path, status, **kwargs):
    result = request(port, path, **kwargs)
    assert result[0] == status, (path, result[0], status)
    if status == 429:
        assert int(result[1]["retry-after"]) >= 1, "429 must include Retry-After"
    if status == 405:
        assert result[1].get("allow"), "405 must include Allow"
    return result


class Stub(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *_):
        pass

    def handle_request(self):
        length = int(self.headers.get("Content-Length", "0"))
        if length:
            self.rfile.read(length)
        if self.headers.get("X-QA-Hold"):
            with self.server.condition:
                self.server.held += 1
                self.server.condition.notify_all()
            self.server.release.wait(12)
        payload = json.dumps({"status": "UP", "path": self.path,
                              "ip": self.headers.get("X-Real-IP"),
                              "forwarded": self.headers.get("X-Forwarded-For"),
                              "proto": self.headers.get("X-Forwarded-Proto")}).encode()
        status = 429 if self.path == "/limited" else 405 if self.path == "/method" else 200
        self.send_response(status)
        if status == 429:
            self.send_header("Retry-After", "77")
        if status == 405:
            self.send_header("Allow", "PUT")
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(payload)

    do_GET = do_HEAD = do_POST = do_PUT = do_DELETE = do_PATCH = do_OPTIONS = handle_request


@contextmanager
def local_edge(binary, docker_image=None):
    with tempfile.TemporaryDirectory(prefix="silverpilot-edge-") as directory:
        root = Path(directory)
        (root / "html").mkdir()
        (root / "html/index.html").write_text("<html>isolated frontend</html>")
        (root / "html/app.js").write_text("/* isolated asset */")
        (root / "logs").mkdir()
        (root / "mime.types").write_text("types { text/html html; application/javascript js; }")
        upstream = ThreadingHTTPServer(("127.0.0.1", 0), Stub)
        upstream.daemon_threads = True
        upstream.release = threading.Event()
        upstream.condition = threading.Condition()
        upstream.held = 0
        threading.Thread(target=upstream.serve_forever, daemon=True).start()
        with socket.socket() as listener:
            listener.bind(("127.0.0.1", 0))
            port = listener.getsockname()[1]
        template = Path(__file__).with_name("nginx.conf.template").read_text()
        config = (template.replace("@@BACKEND_HOST@@", "127.0.0.1")
                  .replace(":8083 resolve", f":{upstream.server_port} resolve")
                  .replace("@@PUBLIC_HOST@@", "public.example.test")
                  .replace("@@DNS_RESOLVERS@@", "127.0.0.1")
                  .replace("listen 8080;", f"listen 127.0.0.1:{port};")
                  .replace("listen [::]:8080;", "")
                  .replace("/etc/nginx/mime.types", (root / "mime.types").as_posix())
                  .replace("/usr/share/nginx/html", (root / "html").as_posix())
                  .replace("/tmp/", root.as_posix() + "/")
                  .replace("/dev/null", (root / "logs/error.log").as_posix())
                  .replace("/dev/stdout", (root / "logs/access.log").as_posix()))
        (root / "nginx.conf").write_text(config)
        config_args = ["-p", root.as_posix() + "/", "-c", "nginx.conf"]
        container_name = root.name
        if docker_image:
            assert os.name != "nt", "Docker host-network tests run on Linux CI"
            assert re.fullmatch(r"sha256:[a-f0-9]{64}", docker_image), "Use the already-built frontend image ID"
            args = ["docker", "run", "--rm", "--name", container_name, "--network", "host",
                    "--read-only", "--cap-drop", "ALL", "--security-opt", "no-new-privileges",
                    "--user", f"{os.getuid()}:{os.getgid()}", "--mount",
                    f"type=bind,source={root},target={root}", "--entrypoint", "nginx", docker_image] + config_args
        else:
            args = [str(Path(binary).resolve())] + config_args
        validation = subprocess.run(args + ["-t"], capture_output=True, text=True)
        assert validation.returncode == 0, validation.stderr
        process = subprocess.Popen(args + ["-g", "daemon off;"], stdout=subprocess.DEVNULL,
                                   stderr=subprocess.DEVNULL)
        try:
            for _ in range(100):
                try:
                    if request(port, "/healthz")[0] == 200:
                        break
                except (ConnectionError, OSError):
                    time.sleep(.05)
            else:
                raise AssertionError("Local Nginx failed to start")
            yield port, upstream, root
        except Exception:
            # This process sees synthetic fixtures only, never credentials.
            for log in ("error.log", "access.log"):
                logfile = root / "logs" / log
                if logfile.exists():
                    print(log + ":\n" + "\n".join(logfile.read_text(errors="replace").splitlines()[-12:]))
            raise
        finally:
            upstream.release.set()
            stop = (["docker", "stop", "--time", "3", container_name] if docker_image
                    else args + ["-s", "stop"])
            subprocess.run(stop, capture_output=True, timeout=8)
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
            upstream.shutdown()
            upstream.server_close()


def protocol_checks(port):
    expect(port, "/login", 200)
    expect(port, "/readyz", 200)
    expect(port, "/api/mcp", 404)
    expect(port, "/API/ACTUATOR/env", 404)
    expect(port, "/api/user;matrix/login/key", 400, method="POST")
    expect(port, "/api/user/%253blogin/key", 400, method="POST")
    expect(port, "/api/user%5clogin/key", 400, method="POST")
    expect(port, "/api/chat%3fignored", 400, method="POST")
    expect(port, "/api/chat%23ignored", 400, method="POST")
    expect(port, "/login", 400, headers={"Host": "untrusted.example"})
    expect(port, "/readyz", 200, headers={"Host": "healthcheck.railway.app"})
    expect(port, "/api/chat/status", 400, headers={"Host": "healthcheck.railway.app"})
    expect(port, "/login", 405, method="POST")
    expect(port, "/api/chat", 405, method="TRACE")
    expect(port, "/api/user/register", 405, method="POST")
    expect(port, "/api/user/getVerificationCode/key", 405, method="PUT")
    # Headers alone prove rejection before request-body buffering or backend IO.
    for route, size in [("/api/chat", 65537), ("/API/CHAT/care-plan/", 65537),
                        ("/api/report/analyze/1", 65537),
                        ("/api/activity/findAll", 262145), ("/api/upload/image", 10485761)]:
        expect(port, route, 413, method="POST", headers={"Content-Length": str(size)})


def run_local(binary, docker_image=None):
    checks = 0
    with local_edge(binary, docker_image) as (port, _, root):
        protocol_checks(port)
        result = expect(port, "/api/echo?password=synthetic-query-secret", 200,
                        headers={"X-Forwarded-For": "198.51.100.9", "X-Forwarded-Proto": "http"})
        echo = json.loads(result[2])
        assert echo["path"].startswith("/echo?")
        assert echo["ip"] == echo["forwarded"] == "192.0.2.1" and echo["proto"] == "https"
        assert "synthetic-query-secret" not in (root / "logs/access.log").read_text()
        assert expect(port, "/api/limited", 429)[1]["retry-after"] == "77"
        assert expect(port, "/api/method", 405)[1]["allow"] == "PUT"
        assert json.loads(expect(port, "/api/echo", 200, ip="a" * 300)[2])["ip"] == "127.0.0.1"
        assert json.loads(expect(port, "/api/echo", 200, ip="2001:db8::1")[2])["ip"] == "2001:db8::1"
        expect(port, "/api/chat", 200, method="POST", body=b"x" * 65536, ip="192.0.2.2")
        expect(port, "/api/activity/findAll", 200, method="POST", body=b"x" * 262144, ip="192.0.2.3")
        expect(port, "/api/upload/image", 200, method="POST", body=b"x" * 300000, ip="192.0.2.4")
        checks += 30
    groups = [
        ("login", "POST", ["/api/user/login/a", "/API/USER/LOGIN/b/", "/api//user/login/c", "/api/user/%6cogin/d"]),
        ("captcha", "GET", [f"/api/user/getVerificationCode/key-{i}" for i in range(6)]),
        ("registration", "PUT", ["/api/user/register", "/API/USER/REGISTER/", "/api/user/%72egister"]),
        ("admin", "GET", ["/api/chat/admin/overview"] * 10),
        ("upload", "POST", ["/api/upload/image", "/API/UPLOAD/file/", "/api/upload/video"]),
        ("api", "GET", ["/api/example"] * 14),
    ]
    for group, method, paths in groups:
        with local_edge(binary, docker_image) as (port, _, _):
            for route in paths[:-1]:
                expect(port, route, 200, method=method)
            expect(port, paths[-1], 429, method=method)
            # A different source keeps an independent per-IP bucket.
            if group in ("login", "captcha", "registration", "upload"):
                expect(port, paths[-1], 200, method=method, ip="192.0.2.55")
            checks += len(paths)
    with local_edge(binary, docker_image) as (port, _, _):
        expect(port, "/api/chat", 200, method="POST")
        expect(port, "/api/report/analyze/1", 200)
        expect(port, "/API/CHAT/care-plan/", 429, method="POST")
        checks += 3
    with local_edge(binary, docker_image) as (port, _, _):
        for i in range(4):
            expect(port, "/api/chat", 200, method="POST", ip=f"192.0.2.{i + 1}")
        expect(port, "/api/report/analyze/1", 429, ip="192.0.2.99")
        checks += 5
    for per_ip, count, zone in [(True, 70, "client_rate"), (False, 180, "site_rate")]:
        with local_edge(binary, docker_image) as (port, _, root), ThreadPoolExecutor(max_workers=8) as pool:
            results = list(pool.map(lambda i: request(port, "/app.js", ip="192.0.2.1" if per_ip else f"192.0.2.{i + 1}"), range(count)))
            assert all(r[0] in (200, 429) for r in results)
            assert any(r[0] == 429 for r in results)
            assert f'by zone "{zone}"' in (root / "logs/error.log").read_text()
            checks += 1
    # Concurrency tests retain requests only in this stub. The log proves the
    # connection limiter, rather than merely another rate bucket, rejected it.
    for count, route, same_ip in [(8, "/api/hold", True), (4, "/api/chat", False)]:
        with local_edge(binary, docker_image) as (port, upstream, root), ThreadPoolExecutor(max_workers=count) as pool:
            futures = [pool.submit(request, port, route, "POST", "192.0.2.1" if same_ip else f"192.0.2.{i + 1}",
                                   None, {"X-QA-Hold": "yes"}) for i in range(count)]
            with upstream.condition:
                assert upstream.condition.wait_for(lambda: upstream.held == count, timeout=5), "Stub requests did not arrive"
            if not same_ip:
                time.sleep(2.1)  # Refill the shared AI rate bucket to isolate concurrency.
            expect(port, route, 429, method="POST", ip="192.0.2.1" if same_ip else "192.0.2.99")
            assert "conn=REJECTED" in (root / "logs/access.log").read_text(), "Connection cap did not reject"
            upstream.release.set()
            assert all(f.result()[0] == 200 for f in futures)
            checks += 2
    with local_edge(binary, docker_image) as (port, _, root):
        def slow_request(body):
            with socket.create_connection(("127.0.0.1", port), timeout=14) as client:
                client.sendall((b"POST /api/slow HTTP/1.1\r\nHost: localhost\r\nContent-Length: 10\r\n\r\nx"
                                if body else b"GET /login HTTP/1.1\r\nHost: localhost\r\n"))
                start = time.monotonic()
                try:
                    data = client.recv(4096)
                except ConnectionResetError:
                    data = b""  # reset_timedout_connection deliberately sends RST.
                elapsed = time.monotonic() - start
                assert 8 <= elapsed <= 13, elapsed
                assert not data or b" 408 " in data, data[:100]
        with ThreadPoolExecutor(max_workers=2) as pool:
            list(pool.map(slow_request, (False, True)))
        assert (root / "logs/access.log").read_text().count(" 408 ") == 2
        checks += 2
    print(f"PASS: {checks} local Nginx edge checks (protocol, body bounds, IP/global rate, connection caps, slow requests)")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    targets = parser.add_mutually_exclusive_group(required=True)
    targets.add_argument("--nginx", help="Actual local nginx executable; creates an isolated loopback stub")
    targets.add_argument("--docker-image", help="Already-built frontend image ID; Linux Docker with a loopback stub")
    targets.add_argument("--base-url", help="Disposable Compose frontend; literal HTTP loopback only")
    args = parser.parse_args()
    if args.nginx or args.docker_image:
        run_local(args.nginx, args.docker_image)
    else:
        url = urlsplit(args.base_url)
        assert url.scheme == "http" and url.hostname and ipaddress.ip_address(url.hostname).is_loopback
        assert not url.username and not url.password and url.path in ("", "/") and not url.query and not url.fragment
        assert url.hostname == "127.0.0.1", "Integration checks only connect to 127.0.0.1"
        protocol_checks(url.port or 80)
        print("PASS: isolated Compose frontend Host/method/path/body protections; no model or login requests issued")


if __name__ == "__main__":
    main()
