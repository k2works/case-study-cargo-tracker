#!/usr/bin/env bash
# HPC カバレッジしきい値検証 (T-10, IT2)
#
# `stack test --coverage` の出力から「expressions used」%を抽出し、
# しきい値を下回ったら exit 1 で fail させる。
#
# 環境変数:
# - COVERAGE_MIN_OVERALL  : 全体カバレッジ最低値 (デフォルト 60、IT3 で 70 に引き上げ予定)
# - COVERAGE_TARGET       : 目標値 (達成時に祝賀ログを出す、デフォルト 70)
#
# IT2 baseline (T-09 完了時): 62% expressions used。
# Domain ≥ 95% / 全体 ≥ 70% (iteration_plan-2.md §テスト戦略) は IT3 目標。

set -uo pipefail

MIN_OVERALL="${COVERAGE_MIN_OVERALL:-60}"
TARGET="${COVERAGE_TARGET:-70}"

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
