#!/usr/bin/env python3
"""Install trusted demo artifacts and atomically publish their external catalog."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def install(runtime: Path, providers: list[str]) -> Path:
    definitions = json.loads((ROOT / "datasets/payment-plugins.json").read_text(encoding="utf-8"))
    available = {entry["code"]: entry for entry in definitions["providers"]}
    if not providers or len(providers) != len(set(providers)):
        raise ValueError("Seleccione al menos un proveedor, sin duplicados")
    plugin_dir = runtime.resolve() / "plugins"
    plugin_dir.mkdir(parents=True, exist_ok=True)
    entries = []
    for code in providers:
        definition = available[code]
        source = ROOT / definition["artifact"]
        # Stage bytes once, then hash and rename those exact bytes.
        handle, staged = tempfile.mkstemp(prefix=".install-", suffix=".tmp", dir=plugin_dir)
        os.close(handle)
        staged = Path(staged)
        try:
            shutil.copyfile(source, staged)
            staged.chmod(0o644)  # Artifacts are non-secret; readable by the non-root container user.
            digest = hashlib.sha256(staged.read_bytes()).hexdigest()
            name = f"{code.lower()}-{digest}.jar"
            destination = plugin_dir / name
            if destination.exists():
                if hashlib.sha256(destination.read_bytes()).hexdigest() != digest:
                    raise ValueError("Un artefacto existente no coincide con su hash")
            else:
                os.replace(staged, destination)
            entries.append({"jar": name, "className": definition["className"], "sha256": digest})
        finally:
            staged.unlink(missing_ok=True)
    catalog = runtime.resolve() / "payment-plugins.json"
    handle, staged = tempfile.mkstemp(prefix=".catalog-", suffix=".json", dir=catalog.parent)
    try:
        with os.fdopen(handle, "w", encoding="utf-8") as output:
            json.dump({"plugins": entries}, output, indent=2)
            output.write("\n")
        Path(staged).chmod(0o644)
        os.replace(staged, catalog)
    finally:
        Path(staged).unlink(missing_ok=True)
    return catalog


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime", type=Path, default=ROOT / "runtime")
    parser.add_argument("--providers", nargs="+", choices=["ALPHA", "BETA"], default=["ALPHA"])
    args = parser.parse_args()
    print(install(args.runtime, args.providers))
