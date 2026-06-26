#!/usr/bin/env bash
# arch-check Phase 1 (シェルスクリプト実装)
#
# IT1 のシンプル実装: grep -E で Domain 層が許可されていないモジュールを
# 直接 import していないかを検査する。
#
# ADR 0002 で定めた 3 ルール:
# 1. Cargotracker.*.Domain.*       → Cargotracker.*.Infrastructure.* を import 不可
# 2. Cargotracker.*.Domain.*       → Servant / postgresql-simple / aeson 等を import 不可
# 3. Cargotracker.*.Application.*  → Cargotracker.*.Infrastructure.* を import 不可
#
# IT2 で haskell-src-exts ベースの AST 解析バイナリ (arch-check) に
# 置き換える予定だが、Phase 1 は確実性とシンプルさを優先。

set -uo pipefail

SRC_DIR="${1:-src}"
VIOLATIONS=0

is_domain_module() {
  grep -Eq '^module +Cargotracker\.[^.]+\.Domain(\.|$| )' "$1"
}

is_application_module() {
  grep -Eq '^module +Cargotracker\.[^.]+\.Application(\.|$| )' "$1"
}

# Rule 1: Domain → Infrastructure 禁止
check_rule1() {
  local file="$1"
  if is_domain_module "$file"; then
    local hits
    hits=$(grep -nE '^import +(qualified +)?Cargotracker\.[^.]+\.Infrastructure\.' "$file" || true)
    if [ -n "$hits" ]; then
      echo "❌ Rule 1 違反 (Domain → Infrastructure): $file"
      echo "$hits" | sed 's/^/   /'
      VIOLATIONS=$((VIOLATIONS + 1))
    fi
  fi
}

# Rule 2: Domain → フレームワーク・永続化 禁止
check_rule2() {
  local file="$1"
  if is_domain_module "$file"; then
    local hits
    hits=$(grep -nE '^import +(qualified +)?(Servant($|[. ])|Database\.PostgreSQL\.Simple|Data\.Aeson($|[. ])|Network\.HTTP\.(Types|Client)|Lucid($|[. ]))' "$file" || true)
    if [ -n "$hits" ]; then
      echo "❌ Rule 2 違反 (Domain → フレームワーク): $file"
      echo "$hits" | sed 's/^/   /'
      VIOLATIONS=$((VIOLATIONS + 1))
    fi
  fi
}

# Rule 3: Application → Infrastructure 禁止
check_rule3() {
  local file="$1"
  if is_application_module "$file"; then
    local hits
    hits=$(grep -nE '^import +(qualified +)?Cargotracker\.[^.]+\.Infrastructure\.' "$file" || true)
    if [ -n "$hits" ]; then
      echo "❌ Rule 3 違反 (Application → Infrastructure): $file"
      echo "$hits" | sed 's/^/   /'
      VIOLATIONS=$((VIOLATIONS + 1))
    fi
  fi
}

echo "arch-check Phase 1: $SRC_DIR を検査中..."
while IFS= read -r -d '' file; do
  check_rule1 "$file"
  check_rule2 "$file"
  check_rule3 "$file"
done < <(find "$SRC_DIR" -name "*.hs" -print0)

if [ "$VIOLATIONS" -gt 0 ]; then
  echo ""
  echo "🚫 $VIOLATIONS 件の規約違反 (ADR 0002)"
  exit 1
fi

echo "✅ アーキテクチャ規約遵守 (Rule 1/2/3 全て OK)"
