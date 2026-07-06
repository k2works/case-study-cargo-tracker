#!/usr/bin/env bash
# lint-staged から呼ばれる F# ファイルのコミット前チェック。
# dotnet ツールマニフェスト（apps/cargo-tracker/.config）を解決するため
# apps/cargo-tracker に cd してから実行する。ファイルパスは絶対パスで渡される。
#
# FSharpLint はファイル単位で約 8 秒 / ソリューション単位で約 47 秒かかり
# pre-commit には重すぎるため、フックでは Fantomas のみ実行する。
# Lint は手動（dotnet fsharplint lint CargoTracker.sln）または CI で実行する。
set -euo pipefail

cd "$(dirname "$0")/../../apps/cargo-tracker"

# フォーマット（lint-staged が修正差分を自動で再ステージする）
dotnet fantomas "$@"
