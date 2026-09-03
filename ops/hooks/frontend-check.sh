#!/usr/bin/env bash
# コミット前のフロントエンド検査。
#
# 型検査は tsc -b（npm run typecheck）で行う。tsc --noEmit は、このプロジェクトの
# プロジェクト参照構成（files: [] + references）では何も検査せず終了 0 を返す。
set -euo pipefail

cd "$(dirname "$0")/../../apps/cargo-tracker/frontend"
npm run typecheck
npm run lint
