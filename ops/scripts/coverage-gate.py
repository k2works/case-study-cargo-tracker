#!/usr/bin/env python3
"""カバレッジハードゲート（ドメイン層 85%）。

cobertura 形式のカバレッジレポートを解析し、ドメイン層（`CargoTracker.<BC>.Domain.` 名前空間）の
行カバレッジが閾値未満なら非 0 で終了して CI を失敗させる。

使い方:
    python3 ops/scripts/coverage-gate.py <cobertura.xml のグロブ> [--threshold 0.85]

プレゼンテーション層（Views/Controllers）は Web.Tests の統合テストで担保し単体対象外のため、
全体ではなくドメイン層に限定してゲートする（test_strategy・IT7 品質ゲート）。
"""
import argparse
import glob
import sys
import xml.etree.ElementTree as ET

DOMAIN_MARKER = ".Domain."


def collect(files):
    # 複数テストプロジェクトのレポートを (ファイル, 行番号) でマージし hits の和をとる
    # （同一行が各レポートに現れるため単純合算は二重計上になる）。
    line_hits = {}          # (filename, line_no) -> 合計 hits
    class_lines = {}        # クラス短名 -> set((filename, line_no))
    for path in files:
        root = ET.parse(path).getroot()
        for cls in root.iter("class"):
            name = cls.get("name", "")
            filename = cls.get("filename", "").replace("\\", "/")
            if DOMAIN_MARKER not in name and "/Domain/" not in filename:
                continue
            short = name.split(".")[-1]
            for line in cls.iter("line"):
                key = (filename, line.get("number"))
                line_hits[key] = line_hits.get(key, 0) + int(line.get("hits", "0"))
                class_lines.setdefault(short, set()).add(key)

    total = len(line_hits)
    covered = sum(1 for h in line_hits.values() if h > 0)
    classes = []
    for short, keys in class_lines.items():
        c = sum(1 for k in keys if line_hits[k] > 0)
        classes.append((short, c / len(keys) if keys else 0.0))
    return covered, total, classes


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("glob", help="cobertura XML のグロブパターン")
    ap.add_argument("--threshold", type=float, default=0.85)
    args = ap.parse_args()

    files = sorted(glob.glob(args.glob, recursive=True))
    if not files:
        print(f"[coverage-gate] カバレッジレポートが見つかりません: {args.glob}", file=sys.stderr)
        return 2

    covered, total, classes = collect(files)
    if total == 0:
        print("[coverage-gate] ドメイン層のカバレッジ行が見つかりません", file=sys.stderr)
        return 2

    rate = covered / total
    print(f"[coverage-gate] ドメイン層カバレッジ: {rate:.1%} ({covered}/{total} 行) / 閾値 {args.threshold:.0%}")
    low = sorted((c for c in classes if c[1] < args.threshold), key=lambda x: x[1])[:10]
    if low:
        print("[coverage-gate] 閾値未満のドメインクラス（上位）:")
        for cname, crate in low:
            print(f"    {cname}: {crate:.1%}")

    if rate < args.threshold:
        print(f"[coverage-gate] FAIL: ドメイン層カバレッジ {rate:.1%} < {args.threshold:.0%}", file=sys.stderr)
        return 1
    print(f"[coverage-gate] PASS: ドメイン層カバレッジ {rate:.1%} >= {args.threshold:.0%}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
