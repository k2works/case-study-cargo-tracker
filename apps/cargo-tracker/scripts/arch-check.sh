#!/usr/bin/env bash
# arch-check Phase 1 (シェルスクリプト実装)
#
# IT1 のシンプル実装: grep -E で Domain 層が許可されていないモジュールを
# 直接 import していないかを検査する。
#
# ADR 0002 で定めた 3 ルール + IT2 T-06 で追加 Rule 4:
# 1. Cargotracker.*.Domain.*       → Cargotracker.*.Infrastructure.* を import 不可
# 2. Cargotracker.*.Domain.*       → Servant / postgresql-simple / aeson 等を import 不可
# 3. Cargotracker.*.Application.*  → Cargotracker.*.Infrastructure.* を import 不可
# 4. Cargotracker.<BC>.*           → 別 BC の Cargotracker.<OtherBC>.Domain.* を import 不可
#                                    (Shared.* は共有カーネル例外、それ以外は ACL 経由のみ許可)
#                                    IT1 由来の既知違反 6 件は ALLOWLIST_RULE4 で許容し、
#                                    新規違反のみ fail させる。完全解消は IT3 で実施。
#
# IT2 で haskell-src-exts ベースの AST 解析バイナリ (arch-check) に
# 置き換える予定だが、Phase 1 は確実性とシンプルさを優先。

set -uo pipefail

SRC_DIR="${1:-src}"
VIOLATIONS=0

# Rule 4 既知違反 ALLOWLIST (IT3 U-05 で Booking 系 7 件を ShipperRef VO に
# 解消済み / ADR-0004)。本配列は空。新規 BC 追加時に必要なら ADR で起票し
# 暫定エントリを追加する運用は今後も維持する。
ALLOWLIST_RULE4=()

is_rule4_allowed() {
  local file="$1" imp="$2"
  for entry in "${ALLOWLIST_RULE4[@]}"; do
    local af="${entry%%|*}" ap="${entry##*|}"
    if [[ "$file" == *"$af" ]] && [[ "$imp" =~ $ap ]]; then
      return 0
    fi
  done
  return 1
}

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

# Rule 4: BC Domain への直接 import 禁止 (Shared.* と同 BC は例外)
check_rule4() {
  local file="$1"
  # ファイルパスから BC 名を抽出 (例: src/Cargotracker/Booking/Domain/...) → "Booking"
  local own_bc
  own_bc=$(echo "$file" | sed -nE 's|.*/Cargotracker/([^/]+)/.*|\1|p')
  [ -z "$own_bc" ] && return
  # Shared 配下は対象外 (共有カーネル定義は別 BC 概念ではない)
  [ "$own_bc" = "Shared" ] && return
  # 全 Cargotracker.<X>.Domain.* import を列挙
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    local imp_bc imp_mod
    imp_mod=$(echo "$line" | sed -nE 's/^[0-9]+:import +(qualified +)?(Cargotracker\.[^ ]+).*/\2/p')
    imp_bc=$(echo "$imp_mod" | sed -nE 's|^Cargotracker\.([^.]+)\.Domain.*|\1|p')
    [ -z "$imp_bc" ] && continue
    # 同 BC または共有カーネル (Shared) は OK
    if [ "$imp_bc" = "$own_bc" ] || [ "$imp_bc" = "Shared" ]; then
      continue
    fi
    # ALLOWLIST 該当なら警告のみ
    if is_rule4_allowed "$file" "$imp_mod"; then
      continue
    fi
    echo "❌ Rule 4 違反 (BC=$own_bc が別 BC=$imp_bc の Domain を直接 import): $file"
    echo "$line" | sed 's/^/   /'
    VIOLATIONS=$((VIOLATIONS + 1))
  done < <(grep -nE '^import +(qualified +)?Cargotracker\.[^.]+\.Domain\.' "$file" || true)
}

echo "arch-check Phase 1: $SRC_DIR を検査中..."
while IFS= read -r -d '' file; do
  check_rule1 "$file"
  check_rule2 "$file"
  check_rule3 "$file"
  check_rule4 "$file"
done < <(find "$SRC_DIR" -name "*.hs" -print0)

if [ "$VIOLATIONS" -gt 0 ]; then
  echo ""
  echo "🚫 $VIOLATIONS 件の規約違反 (ADR 0002)"
  exit 1
fi

echo "✅ アーキテクチャ規約遵守 (Rule 1/2/3/4 全て OK、Rule 4 既知違反 ${#ALLOWLIST_RULE4[@]} 件は ALLOWLIST 化済)"
