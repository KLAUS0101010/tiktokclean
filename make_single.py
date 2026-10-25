#!/usr/bin/env python3
"""Makes ONE file (build-single.yml) that contains the whole project, so on a phone you only have to create
one file on GitHub instead of uploading dozens. Run on a computer: python3 make_single.py"""
import base64, io, re, tarfile
from pathlib import Path

root = Path(__file__).resolve().parent
wf = (root / ".github/workflows/build.yml").read_text()

buf = io.BytesIO()
skip_dirs = {".github", "__pycache__", "stubsrc", "stubout", "chk", "chk2", "out", "tests/stubs"}
with tarfile.open(fileobj=buf, mode="w:gz", compresslevel=9) as tar:
    for p in sorted(root.rglob("*")):
        rel = p.relative_to(root).as_posix()
        if p.is_dir() or any(rel == s or rel.startswith(s + "/") for s in skip_dirs):
            continue
        if rel in ("build-single.yml", "BUILD-WORKFLOW.txt", "make_single.py") or rel.endswith(".zip"):
            continue
        ti = tar.gettarinfo(str(p), arcname=rel)
        ti.uid = ti.gid = 0; ti.uname = ti.gname = ""; ti.mtime = 0
        with open(p, "rb") as f:
            tar.addfile(ti, f)
b64 = base64.b64encode(buf.getvalue()).decode()
lines = [b64[i:i + 100] for i in range(0, len(b64), 100)]
ind = " " * 10
blob = "\n".join(ind + l for l in lines)

unpack = f"""      - name: Unpack the project (all files are inside this one file)
        run: |
          base64 -d > project.tgz <<'EOF_PROJECT'
{blob}
          EOF_PROJECT
          tar xzf project.tgz
          rm project.tgz
          ls

"""
wf = wf.replace("      - uses: actions/checkout@v4\n", "")
wf = wf.replace("      - name: Tests\n", unpack + "      - name: Tests\n", 1)
wf = wf.replace('gh release create "tiktok-clean-${{ github.run_number }}"', 'gh release create -R "${{ github.repository }}" "tiktok-clean-${{ github.run_number }}"')
(root / "build-single.yml").write_text(wf)
print("build-single.yml:", len(wf) // 1024, "KB")
