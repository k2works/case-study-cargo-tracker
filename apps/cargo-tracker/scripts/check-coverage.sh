#!/usr/bin/env bash
# HPC カバレッジしきい値検証 (T-10 IT2 / U-06 IT3)
#
# `stack test --coverage` の出力から「expressions used」%を抽出し、
# しきい値を下回ったら exit 1 で fail させる。
#
# 環境変数:
# - COVERAGE_MIN_OVERALL  : 全体カバレッジ最低値 (デフォルト 70 / IT3 U-06 で 60 → 70 引き上げ)
# - COVERAGE_TARGET       : 目標値 (達成時に祝賀ログを出す、デフォルト 70)
# - COVERAGE_REPORT_MODULES : 1 にすると Domain モジュール別の coverage を
#                             stack hpc report で個別出力 (CI 補助情報、gate 化はしない)
#
# IT3 baseline (U-06 完了時): 70% expressions used (unified)。
# IT4 以降: Domain ≥ 95% を per-module gate 化検討。HTML レポート
# (.stack-work/install/.../hpc/combined/custom/hpc_index.html) で
# 確認可能。

set -uo pipefail

MIN_OVERALL="${COVERAGE_MIN_OVERALL:-70}"
TARGET="${COVERAGE_TARGET:-70}"
REPORT_MODULES="${COVERAGE_REPORT_MODULES:-0}"

LOG=$(mktemp)
trap 'rm -f "$LOG"' EXIT

echo "==> stack test --coverage を実行..."
stack test --coverage 2>&1 | tee "$LOG"
TEST_EXIT=${PIPESTATUS[0]}

if [ "$TEST_EXIT" -ne 0 ]; then
  echo "❌ stack test 失敗 (exit=$TEST_EXIT)"
  exit "$TEST_EXIT"
fi

# 「Summary unified coverage report」直後の "% expressions used" 行を抽出
# (個別パッケージレポートにも同じ表現があるので unified 後だけ拾う)
overall=$(
  sed -n '/Summary unified coverage report/,$p' "$LOG" \
    | grep -m1 -oE '[0-9]+% expressions used' \
    | grep -oE '^[0-9]+'
)

if [ -z "$overall" ]; then
  echo "⚠️  unified coverage report から expressions % を抽出できませんでした"
  echo "    stack test --coverage の出力フォーマット変更の可能性あり"
  exit 1
fi

echo ""
echo "==================================================="
echo " HPC カバレッジ実績"
echo "==================================================="
echo "  全体 expressions used : ${overall}%"
echo "  しきい値              : ${MIN_OVERALL}% (gate)"
echo "  IT3 目標              : ${TARGET}%"
echo "==================================================="

if [ "$overall" -lt "$MIN_OVERALL" ]; then
  echo "❌ カバレッジ ${overall}% < しきい値 ${MIN_OVERALL}%"
  exit 1
fi

if [ "$overall" -ge "$TARGET" ]; then
  echo "🎉 目標 ${TARGET}% を達成"
else
  echo "✅ しきい値クリア (目標 ${TARGET}% へ向けて改善継続)"
fi

# U-06 IT3: Domain モジュール一覧の補助レポート (gate 化はしない)。
# `stack hpc report` はモジュール名引数を受け付けないため per-module 数値は
# unified の HTML レポート (hpc_index.html) を参照する。CI のレポート
# アーティファクトに含まれる。本セクションは「監視対象 Domain モジュール」
# の存在確認のみを行う (IT4 で per-module gate 化を ADR 起票する際の足場)。
if [ "$REPORT_MODULES" = "1" ]; then
  echo ""
  echo "==================================================="
  echo " Domain モジュール一覧 (HTML レポートで個別 % を確認)"
  echo "==================================================="
  count=0
  while IFS= read -r mod; do
    echo "  - $mod"
    count=$((count + 1))
  done < <(
    find src/Cargotracker -path '*/Domain/*' -name '*.hs' \
      | sed -e 's|^src/||' -e 's|\.hs$||' -e 's|/|.|g' \
      | sort
  )
  echo "---------------------------------------------------"
  echo "  Domain モジュール件数 : ${count}"
  echo "  HTML レポート         : .stack-work/install/.../hpc/combined/all/hpc_index.html"
  echo "==================================================="
fi
