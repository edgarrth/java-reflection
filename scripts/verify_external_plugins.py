#!/usr/bin/env python3
"""Prove that BETA can be built and installed after the host has already started."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import tempfile
import time
import zipfile
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from install_plugins import ROOT, install


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--maven", default="mvn.cmd" if os.name == "nt" else "mvn")
    parser.add_argument("--java", default="java")
    parser.add_argument("--maven-repo")
    args = parser.parse_args()
    creationflags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
    runtime_root = ROOT / "runtime"
    runtime_root.mkdir(exist_ok=True)
    runtime = Path(tempfile.mkdtemp(prefix="verification-", dir=runtime_root))

    def build(modules):
        command = [args.maven, "-B", "-ntp", "-pl", modules, "-am", "clean", "package", "-DskipTests"]
        if args.maven_repo:
            command.append(f"-Dmaven.repo.local={args.maven_repo}")
        subprocess.run(command, cwd=ROOT, check=True, creationflags=creationflags)

    build("payment-service,provider-alpha")
    jar = ROOT / "payment-service/target/payment-service-1.0.0.jar"
    before = hashlib.sha256(jar.read_bytes()).hexdigest()
    with zipfile.ZipFile(jar) as archive:
        assert not any("provider-alpha" in name or "provider-beta" in name or "/com/example/" in name
                       for name in archive.namelist()), "El host no debe empaquetar proveedores"
    install(runtime, ["ALPHA"])
    token = secrets.token_urlsafe(32)
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        port = probe.getsockname()[1]
    base = f"http://127.0.0.1:{port}"
    environment = dict(os.environ, PLUGIN_DIRECTORY=str(runtime / "plugins"),
                       PLUGIN_CATALOG=str(runtime / "payment-plugins.json"), PLUGIN_ADMIN_TOKEN=token)

    def request(path, payload=None, expected=200, authenticated=True):
        headers = {"Content-Type": "application/json"}
        if authenticated:
            headers["Authorization"] = f"Bearer {token}"
        data = None if payload is None else json.dumps(payload).encode()
        req = Request(base + path, data=data, headers=headers)
        try:
            with urlopen(req, timeout=5) as response:
                status, body = response.status, response.read()
        except HTTPError as error:
            status, body = error.code, error.read()
        if status != expected:
            raise AssertionError(f"{path}: expected {expected}, got {status}: {body!r}")
        return json.loads(body)

    with (runtime / "service.log").open("w", encoding="utf-8") as log:
        process = subprocess.Popen([args.java, "-jar", str(jar), f"--server.port={port}", "--server.address=127.0.0.1"],
                                   cwd=ROOT, env=environment, stdout=log, stderr=subprocess.STDOUT, creationflags=creationflags)
        try:
            deadline = time.monotonic() + 45
            while True:
                if process.poll() is not None:
                    raise RuntimeError(f"El servicio no arrancó; consulte {runtime / 'service.log'}")
                try:
                    request("/actuator/health")
                    break
                except (URLError, TimeoutError):
                    if time.monotonic() > deadline:
                        raise TimeoutError("El servicio no arrancó en 45 segundos")
                    time.sleep(0.2)
            plugins_path = "/api/v1/reflection/plugins"
            assert [p["code"] for p in request(plugins_path)] == ["ALPHA"]
            request(plugins_path, expected=401, authenticated=False)
            payment = json.loads((ROOT / "infrastructure/examples/payment-request.json").read_text(encoding="utf-8"))
            assert request("/api/v1/payments", payment)["processor"] == "ALPHA"
            payment["providerCode"] = "BETA"
            request("/api/v1/payments", payment, expected=404)

            # Crucially: the service module is absent from this second build.
            build("provider-beta")
            install(runtime, ["ALPHA", "BETA"])
            assert [p["code"] for p in request(plugins_path + "/reload", {})] == ["ALPHA", "BETA"]
            result = request("/api/v1/payments", payment)
            assert result["processor"] == "BETA" and result["status"] == "APPROVED"
            payment["pan"] = "4111111111110000"
            assert request("/api/v1/payments", payment)["status"] == "REJECTED"
            payment["amount"] = 15000
            assert request("/api/v1/payments", payment)["details"]["reason"] == "FRAUD_AMOUNT_LIMIT"
            request(plugins_path + "/BETA/inspect")
            request(plugins_path + "/BETA/inspect")
            metrics = request("/api/v1/reflection/metrics")
            assert metrics["metadataCacheHits"] >= 1
            assert metrics["providers"]["BETA"]["approved"] == 1
            assert metrics["providers"]["BETA"]["rejected"] == 1

            # A corrupt catalog must preserve generation 2 and both live providers.
            catalog = runtime / "payment-plugins.json"
            original = catalog.read_text(encoding="utf-8")
            broken = json.loads(original)
            broken["plugins"][0]["sha256"] = "0" * 64
            catalog.write_text(json.dumps(broken), encoding="utf-8")
            request(plugins_path + "/reload", {}, expected=409)
            catalog.write_text(original, encoding="utf-8")
            assert request("/api/v1/reflection/metrics")["generation"] == 2
            assert len(request(plugins_path)) == 2
            after = hashlib.sha256(jar.read_bytes()).hexdigest()
            assert before == after and process.poll() is None
            report = {"hostSha256Before": before, "hostSha256After": after, "sameProcessPid": process.pid,
                      "betaInstalledAfterStartup": True, "failedReloadPreservedRegistry": True, "metrics": metrics}
            (runtime / "verification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
            print(f"VERIFIED: same host JAR and process; external BETA installed. Evidence: {runtime}")
        finally:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()


if __name__ == "__main__":
    main()
