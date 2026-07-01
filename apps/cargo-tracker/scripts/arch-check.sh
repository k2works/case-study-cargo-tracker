#!/usr/bin/env bash
# arch-check Phase 1 + Phase 2/3 (シェルスクリプト実装)
#
# IT1 のシンプル実装: grep -E で Domain 層が許可されていないモジュールを
# 直接 import していないかを検査する。
#
# ADR 0002 で定めた 3 ルール + IT2 T-06 で追加 Rule 4 + IT4 U-04 Phase 2 Rule 6
# + IT3 繰越 Phase 3 トランザクション境界 T-01 / T-02 / T-03:
#
# Phase 1 (IT1-IT2):
# 1. Cargotracker.*.Domain.*       → Cargotracker.*.Infrastructure.* を import 不可
# 2. Cargotracker.*.Domain.*       → Servant / postgresql-simple / aeson 等を import 不可
# 3. Cargotracker.*.Application.*  → Cargotracker.*.Infrastructure.* を import 不可
# 4. Cargotracker.<BC>.*           → 別 BC の Cargotracker.<OtherBC>.Domain.* を import 不可
#                                    (Shared.* は共有カーネル例外、それ以外は ACL 経由のみ許可)
#
# Phase 2 (IT4 U-04):
# 6. Cargotracker.*.Interfaces.*   → Cargotracker.*.Infrastructure.* を import 不可
#                                    (Servant ハンドラは Application Command 経由のみ I/O)
#
# Phase 3 (IT3 繰越、トランザクション境界):
# T-01. Cargotracker.*.Application.* のみが withTransaction / withDbTransaction を呼び出せる
# T-02. Cargotracker.*.Infrastructure.Repository.* は withTransaction を呼び出してはいけない
# T-03. Cargotracker.*.Domain.* は liftIO / IO 型を直接記述してはいけない
#       (Rule 2 で import 禁止済、追加で本文中の `liftIO` 使用を検出)
#
# IT5+ で haskell-src-exts ベースの AST 解析バイナリへの置き換えを検討するが、
# Phase 1/2/3 はシンプル & 確実性を優先して grep ベース継続。

set -uo pipefail

SRC_DIR="${1:-src}"
VIOLATIONS=0

# Rule 4 既知違反 ALLOWLIST (L-09 経緯リンク):
#
# IT1 完了時に Booking 系 7 ファイル (Cargo.hs / Ports.hs / 2 Infrastructure
# / 2 Views / RegisterBookingCommand) が Shipper.Domain.Model.Value.ShipperId
# を直接 import している既知違反として登録されていた。IT3 U-05 で全 7 件を
# Shared.Domain.Reference.ShipperRef VO 経由に移行し、本配列は空になった。
#
# 経緯ドキュメント:
# - docs/adr/0004-cross-bc-shipper-ref.md   (Cross-BC 参照 VO の規約 BCE-04)
# - docs/development/retrospective-2.md     (IT2 ふりかえり Try U-05)
# - docs/development/iteration_plan-3.md    (IT3 タスク 1.5 U-05)
# - docs/review/it2_code_review_20260627.md (M-04: ACL ADR 起票指示)
#
# 新規 BC 追加で同種の Cross-BC import を一時的に許容する場合の運用:
# 1. ADR-0004 BCE-04 規約を確認し、新 BC 用 <TargetBC>Ref VO を起票
# 2. 暫定で本配列に "<file>|<import 正規表現>" を追加 (target IT を ADR 起票で明示)
# 3. 解消した時点で配列から削除し、retrospective に経緯を記録
ALLOWLIST_RULE4=()

# IT4 U-04 Phase 2/3 既知違反 ALLOWLIST:
#
# IT1-IT3 で実装された Repository / Interfaces ファイルは新ルール導入前に
# 設計された経緯があり、Rule 6 + T-01 + T-02 を即時準拠させると大規模な
# リファクタが必要となるため、暫定 ALLOWLIST 化し IT5-IT6 で段階的に解消する。
#
# T4-16 (IT5): 全 ALLOWLIST エントリに sunset 日付コメントを必須化。
# sunset を過ぎたエントリは CI で警告 → エラーに昇格。
#
# 解消計画:
# - Rule 6: BookingPageApi / ShipperPageApi の Postgres 依存を Application Command 経由に移行
# - T-01/T-02: 既存 Repository から Tx 境界 API を取り除き、Application 層に Tx 境界を移動
ALLOWLIST_RULE6=(
  # sunset: 2026-09-30 (IT6 完了目標、handlerShow → QueryBookingDetail Command 経由化で解消)
  "Booking/Interfaces/BookingPageApi.hs"
  # sunset: 2026-09-30 (IT6 完了目標、handlerSearch → SearchShipper Command 経由化で解消)
  "Shipper/Interfaces/ShipperPageApi.hs"
)
ALLOWLIST_T01_T02=(
  # sunset: 2026-09-30 (IT6、AttachCustomsDeclarationCommand の Tx 境界を Application に移動で解消)
  "Booking/Infrastructure/PostgresCustomsDeclarationRepository.hs"
  # sunset: 2026-10-31 (IT7、CreateEstimateCommand の Tx 境界を Application に移動で解消)
  "Estimation/Infrastructure/PostgresEstimateRepository.hs"
  # sunset: 2026-10-31 (IT7、RegisterVoyageCommand の Tx 境界を Application に移動で解消)
  "Routing/Infrastructure/PostgresVoyageRepository.hs"
)

is_allowed_rule6() {
  local file="$1" entry
  for entry in "${ALLOWLIST_RULE6[@]}"; do
    [[ "$file" == *"$entry" ]] && return 0
  done
  return 1
}

is_allowed_t01_t02() {
  local file="$1" entry
  for entry in "${ALLOWLIST_T01_T02[@]}"; do
    [[ "$file" == *"$entry" ]] && return 0
  done
  return 1
}

# U-14 (IT3): ALLOWLIST 整合性検証。
# 登録ファイルが src/ 配下に実在しなければ stale エントリとして即 fail させる
# (リネーム漏れや誤登録の検出)。空配列のときは no-op。
validate_allowlist_entries() {
  local entry af missing=0
  for entry in "${ALLOWLIST_RULE4[@]}"; do
    af="${entry%%|*}"
    if [ ! -f "${SRC_DIR}/${af}" ]; then
      echo "❌ ALLOWLIST_RULE4 stale entry: ${af} (src ファイル不在)"
      missing=$((missing + 1))
    fi
  done
  if [ "$missing" -gt 0 ]; then
    echo "❌ ALLOWLIST_RULE4 に ${missing} 件の不在ファイル登録があります"
    exit 1
  fi
}

validate_allowlist_entries

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

is_interfaces_module() {
  grep -Eq '^module +Cargotracker\.[^.]+\.Interfaces(\.|$| )' "$1"
}

is_repository_module() {
  grep -Eq '^module +Cargotracker\.[^.]+\.Infrastructure\.(Postgres|Repository)' "$1"
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

# Rule 6 (Phase 2): Interfaces → Infrastructure 禁止
# Servant ハンドラは Application Command 経由のみ I/O を行うべき。
# 例外: Main.hs / app/ のエントリポイントは Env 構築のため Infrastructure を import する。
# 本ルールは src/Cargotracker/<BC>/Interfaces/ 配下のみを対象とする。
check_rule6() {
  local file="$1"
  if is_interfaces_module "$file"; then
    if is_allowed_rule6 "$file"; then
      return
    fi
    local hits
    hits=$(grep -nE '^import +(qualified +)?Cargotracker\.[^.]+\.Infrastructure\.' "$file" || true)
    if [ -n "$hits" ]; then
      echo "❌ Rule 6 違反 (Interfaces → Infrastructure): $file"
      echo "$hits" | sed 's/^/   /'
      VIOLATIONS=$((VIOLATIONS + 1))
    fi
  fi
}

# T-01 (Phase 3): withTransaction は Application 層のみで張る
# Domain / Infrastructure / Interfaces で withTransaction / withDbTransaction を
# 使ってはいけない。Application 層のみ Tx 境界を持つ責務。
check_t01() {
  local file="$1"
  if is_application_module "$file" || is_interfaces_module "$file" || is_domain_module "$file"; then
    : # Application は OK、それ以外でも本ルールは Infrastructure 側で検査するため skip
  fi
  # 実装: Application 以外で withTransaction / withDbTransaction を使ってはいけない
  if ! is_application_module "$file"; then
    if is_allowed_t01_t02 "$file"; then
      return
    fi
    local hits
    hits=$(grep -nE '\b(withTransaction|withDbTransaction)\b' "$file" || true)
    if [ -n "$hits" ]; then
      # Infrastructure / Interfaces / Domain での Tx 境界張りを違反として検出
      if is_infrastructure_module "$file" || is_interfaces_module "$file" || is_domain_module "$file"; then
        echo "❌ T-01 違反 (Application 以外で Tx 境界を張っている): $file"
        echo "$hits" | sed 's/^/   /'
        VIOLATIONS=$((VIOLATIONS + 1))
      fi
    fi
  fi
}

is_infrastructure_module() {
  grep -Eq '^module +Cargotracker\.[^.]+\.Infrastructure(\.|$| )' "$1"
}

# T-02 (Phase 3): Repository は IO のみで Tx 開始禁止
# postgresql-simple の Connection を受け取って SQL を実行するだけで、
# トランザクション境界は Application 層に委譲する。
check_t02() {
  local file="$1"
  if is_repository_module "$file"; then
    if is_allowed_t01_t02 "$file"; then
      return
    fi
    local hits
    hits=$(grep -nE '\b(withTransaction|withDbTransaction|begin\b|BEGIN;|COMMIT;|ROLLBACK;)' "$file" || true)
    if [ -n "$hits" ]; then
      echo "❌ T-02 違反 (Repository で Tx 境界を張っている): $file"
      echo "$hits" | sed 's/^/   /'
      VIOLATIONS=$((VIOLATIONS + 1))
    fi
  fi
}

# T-03 (Phase 3): Domain は IO 完全排除
# Rule 2 で import 禁止済だが、追加で `liftIO` 呼び出しを検出する
# (DomainError 等で IO を直接記述するパターン防止)。
check_t03() {
  local file="$1"
  if is_domain_module "$file"; then
    local hits
    hits=$(grep -nE '\b(liftIO|unsafePerformIO|performIO)\b' "$file" || true)
    if [ -n "$hits" ]; then
      echo "❌ T-03 違反 (Domain で IO を直接使用): $file"
      echo "$hits" | sed 's/^/   /'
      VIOLATIONS=$((VIOLATIONS + 1))
    fi
  fi
}

# H-01 (IT5 task 3.9): TransportStatus SSoT 統合規約
# TransportStatus (Shared 公開語彙、9 値) の直接書き込みは Tracking Context の
# trackingStatusToTransportStatus 変換関数のみに集約する。
# Handling / Booking / Billing 等の他 BC が TransportStatus 値を直接構築するのは禁止
# (SSoT 二重化を防止)。TrackingStatus は Tracking Context 内部型として維持し、
# 出口で変換関数を経由する。
check_h01_transport_status_ssot() {
  local file="$1"
  # Tracking Context 自身と Shared Domain 定義は除外
  if grep -Eq '^module +Cargotracker\.Tracking\.' "$file"; then
    return
  fi
  if grep -Eq '^module +Cargotracker\.Shared\.Domain\.' "$file"; then
    return
  fi
  # TransportStatus のコンストラクタ (Ts... 8 値) を直接使用しているかを検出
  local hits
  hits=$(grep -nE '\bTs(NotReceived|Received|Loaded|OnboardCarrier|Unloaded|AwaitingClaim|Claimed|InException|Unknown)\b' "$file" || true)
  if [ -n "$hits" ]; then
    echo "⚠️  H-01 注意 (Tracking Context 外で TransportStatus コンストラクタを直接使用): $file"
    echo "$hits" | sed 's/^/   /'
    echo "   → 推奨: TrackingActivity.currentStatus + trackingStatusToTransportStatus 経由で取得"
    # 現段階では警告のみ (VIOLATIONS を増やさない、IT6 で強制化検討)
  fi
}

echo "arch-check Phase 1+2+3: $SRC_DIR を検査中..."
while IFS= read -r -d '' file; do
  check_rule1 "$file"
  check_rule2 "$file"
  check_rule3 "$file"
  check_rule4 "$file"
  check_rule6 "$file"
  check_t01 "$file"
  check_t02 "$file"
  check_t03 "$file"
  check_h01_transport_status_ssot "$file"
done < <(find "$SRC_DIR" -name "*.hs" -print0)

if [ "$VIOLATIONS" -gt 0 ]; then
  echo ""
  echo "🚫 $VIOLATIONS 件の規約違反 (ADR 0002)"
  exit 1
fi

echo "✅ アーキテクチャ規約遵守 (Rule 1/2/3/4/6 + T-01/T-02/T-03 全て OK)"
echo "   ALLOWLIST: Rule 4=${#ALLOWLIST_RULE4[@]} / Rule 6=${#ALLOWLIST_RULE6[@]} / T-01+T-02=${#ALLOWLIST_T01_T02[@]} (IT5 段階解消)"
echo "   H-01 (IT5): TransportStatus SSoT は警告のみ、IT6 で強制化検討"
