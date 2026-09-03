#!/usr/bin/env bash
# コミット前のバックエンド検査。
#
# 引数として渡された変更ファイルは使わない。Checkstyle も SpotBugs も
# モジュール単位でしか走らないため、変更ファイルだけを検査することはできない。
# lint-staged からは「Java が変わったこと」の合図として呼ばれる。
set -euo pipefail

cd "$(dirname "$0")/../../apps/cargo-tracker/backend"
./gradlew --quiet checkstyleMain checkstyleTest spotbugsMain spotbugsTest
