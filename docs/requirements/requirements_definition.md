---
title: 要件定義 - 国際貨物輸送管理システム
description: RDRA 2.0 に基づく要件定義書
published: true
date: 2026-03-31T00:00:00.000Z
tags: requirements, RDRA
editor: markdown
---

# 要件定義 - 国際貨物輸送管理システム

## システム価値

### システムコンテキスト

```plantuml
@startuml

title システムコンテキスト図 - 国際貨物輸送管理システム

left to right direction

actor 荷主
actor 荷受人
actor 営業担当者
actor 経路設計者
actor 追跡管理者
actor 荷役作業員

agent 通知システム
agent 外部経路システム
agent 港湾管理システム
agent 決済機関
agent 税関

usecase 国際貨物輸送管理システム
note top of 国際貨物輸送管理システム
  国際貨物輸送の予約から配送完了・精算までを
  一元管理するシステム。
  荷主が出発地・目的地・期限を指定するだけで
  最適ルートが提案され、リアルタイムで貨物を
  追跡できる透明性の高い輸送サービスを提供する。
end note

:荷主: -- (国際貨物輸送管理システム)
:荷受人: -- (国際貨物輸送管理システム)
:営業担当者: -- (国際貨物輸送管理システム)
:経路設計者: -- (国際貨物輸送管理システム)
:追跡管理者: -- (国際貨物輸送管理システム)
:荷役作業員: -- (国際貨物輸送管理システム)

(国際貨物輸送管理システム) -- 通知システム
(国際貨物輸送管理システム) -- 外部経路システム
(国際貨物輸送管理システム) -- 港湾管理システム
(国際貨物輸送管理システム) -- 決済機関
(国際貨物輸送管理システム) -- 税関

@enduml
```

### 要求モデル

```plantuml
@startuml

title 要求モデル図 - 国際貨物輸送管理システム

left to right direction

actor 荷主
note "最適ルートを簡単に見つけたい" as s_r1
note "いつでも貨物の状況を確認したい" as s_r2
note "迅速に予約・変更・キャンセルしたい" as s_r3
note as s_dr1 #Turquoise
  出発地・目的地・期限指定による
  ルート自動提案と予約完結機能。
  リアルタイム追跡と状態変更通知。
end note
:荷主: -- s_r1
:荷主: -- s_r2
:荷主: -- s_r3
s_r1 -- s_dr1
s_r2 -- s_dr1
s_r3 -- s_dr1

actor 営業担当者
note "見積・予約作業を効率化したい" as sa_r1
note "顧客情報を一元管理したい" as sa_r2
note as sa_dr1 #Turquoise
  荷主・貨物情報の登録と
  見積作成・予約確定の一元化。
end note
:営業担当者: -- sa_r1
:営業担当者: -- sa_r2
sa_r1 -- sa_dr1
sa_r2 -- sa_dr1

actor 経路設計者
note "最適経路を迅速に設計したい" as rd_r1
note "航海スケジュールを管理したい" as rd_r2
note as rd_dr1 #Turquoise
  ルート候補の自動算出と
  航海スケジュール管理。
end note
:経路設計者: -- rd_r1
:経路設計者: -- rd_r2
rd_r1 -- rd_dr1
rd_r2 -- rd_dr1

actor 追跡管理者
note "貨物状態をリアルタイムで更新したい" as tm_r1
note "例外発生時に迅速に対応したい" as tm_r2
note as tm_dr1 #Turquoise
  貨物状態の即時更新と
  例外処理ワークフローの管理。
end note
:追跡管理者: -- tm_r1
:追跡管理者: -- tm_r2
tm_r1 -- tm_dr1
tm_r2 -- tm_dr1

actor 荷役作業員
note "荷役作業を簡単に記録したい" as hw_r1
note as hw_dr1 #Turquoise
  モバイル対応の荷役作業記録機能。
end note
:荷役作業員: -- hw_r1
hw_r1 -- hw_dr1

@enduml
```

## システム外部環境

### ビジネスコンテキスト

```plantuml
@startuml

title ビジネスコンテキスト図 - 国際貨物輸送管理システム

left to right direction

actor 荷主
actor 荷受人

node 国際貨物輸送会社 {
  rectangle 営業部門 {
    actor 営業担当者
  }

  rectangle 運用部門 {
    actor 経路設計者
    actor 追跡管理者
  }

  rectangle 現場部門 {
    actor 荷役作業員
  }

  rectangle 管理部門 {
    actor 経理担当者
  }

  usecase 貨物輸送予約
  usecase 経路設計
  usecase 荷役輸送管理
  usecase 貨物追跡監視
  usecase 精算管理

  artifact 貨物予約情報
  artifact 経路情報
  artifact 追跡情報
  artifact 精算情報
}

node 外部組織 {
  agent 通知システム
  agent 外部経路システム
  agent 港湾管理システム
  agent 決済機関
  agent 税関
}

:荷主: -- (貨物輸送予約)
:荷受人: -- (貨物追跡監視)

(貨物輸送予約) -- :営業担当者:
(経路設計) -- :経路設計者:
(荷役輸送管理) -- :荷役作業員:
(貨物追跡監視) -- :追跡管理者:
(精算管理) -- :経理担当者:

(貨物輸送予約) -- 貨物予約情報
(経路設計) -- 経路情報
(貨物追跡監視) -- 追跡情報
(精算管理) -- 精算情報

(貨物追跡監視) -- 通知システム
(経路設計) -- 外部経路システム
(荷役輸送管理) -- 港湾管理システム
(精算管理) -- 決済機関
(貨物輸送予約) -- 税関

@enduml
```

### ビジネスユースケース

#### 貨物輸送予約

```plantuml
@startuml

title ビジネスユースケース図 - 貨物輸送予約

left to right direction

actor 荷主
actor 営業担当者
actor 経路設計者

agent 国際貨物輸送会社

usecase "輸送見積依頼" as BUC01
usecase "見積作成" as BUC02
usecase "荷主・貨物登録" as BUC03
usecase "ルート設計・提案" as BUC04
usecase "予約確定" as BUC05
usecase "追跡番号発行" as BUC06

artifact "見積書" as af_01
artifact "貨物予約情報" as af_02
artifact "経路情報" as af_03
artifact "追跡番号" as af_04

:荷主: -- (BUC01)
:荷主: -- (BUC05)

:営業担当者: -- (BUC02)
:営業担当者: -- (BUC03)

:経路設計者: -- (BUC04)
:経路設計者: -- (BUC06)

(BUC01) -- 国際貨物輸送会社
(BUC02) -- 国際貨物輸送会社
(BUC03) -- 国際貨物輸送会社
(BUC04) -- 国際貨物輸送会社
(BUC05) -- 国際貨物輸送会社
(BUC06) -- 国際貨物輸送会社

(BUC02) -- af_01
(BUC03) -- af_02
(BUC04) -- af_03
(BUC06) -- af_04

@enduml
```

#### 荷役・追跡管理

```plantuml
@startuml

title ビジネスユースケース図 - 荷役・追跡管理

left to right direction

actor 荷役作業員
actor 追跡管理者
actor 荷主

agent 国際貨物輸送会社

usecase "荷役作業記録" as BUC07
usecase "貨物状態更新" as BUC08
usecase "追跡情報確認" as BUC09
usecase "例外処理" as BUC10

artifact "荷役記録" as af_05
artifact "追跡情報" as af_06

:荷役作業員: -- (BUC07)
:荷役作業員: -- (BUC10)

:追跡管理者: -- (BUC08)

:荷主: -- (BUC09)

(BUC07) -- 国際貨物輸送会社
(BUC08) -- 国際貨物輸送会社
(BUC09) -- 国際貨物輸送会社
(BUC10) -- 国際貨物輸送会社

(BUC07) -- af_05
(BUC08) -- af_06
(BUC09) -- af_06

@enduml
```

#### 精算管理

```plantuml
@startuml

title ビジネスユースケース図 - 精算管理

left to right direction

actor 荷主
actor 経理担当者

agent 国際貨物輸送会社

usecase "輸送料金算出" as BUC11
usecase "精算処理" as BUC12
usecase "割引適用" as BUC13

artifact "輸送料金" as af_07
artifact "精算実績" as af_08

:経理担当者: -- (BUC11)
:経理担当者: -- (BUC12)
:経理担当者: -- (BUC13)
:荷主: -- (BUC12)

(BUC11) -- 国際貨物輸送会社
(BUC12) -- 国際貨物輸送会社
(BUC13) -- 国際貨物輸送会社

(BUC11) -- af_07
(BUC12) -- af_08
(BUC13) -- af_07

@enduml
```

### 業務フロー

#### 貨物輸送予約の業務フロー

```plantuml
@startuml

title 業務フロー図 - 貨物輸送予約

|荷主|
start
:輸送依頼・見積依頼;

|営業担当者|
partition 受付フェーズ {
  :輸送要件確認;
  :見積作成;
}

|荷主|
:見積確認・承認;

|営業担当者|
partition 登録フェーズ {
  :荷主情報登録;
  :貨物仕様登録;
  :予約仮受付;
}

|経路設計者|
partition 経路設計フェーズ {
  :最適ルート算出;
  :航海スケジュール組み込み;
  :ルート提案;
}

|荷主|
:ルート選択・確認;
if (ルート承認?) then (yes)
  :予約確定依頼;
else (no)
  :条件変更・再見積依頼;
  stop
endif

|営業担当者|
partition 確定フェーズ {
  :予約確定処理;
}

|経路設計者|
:追跡番号発行;

|荷主|
:追跡番号受領;
stop

@enduml
```

#### 荷役・追跡の業務フロー

```plantuml
@startuml

title 業務フロー図 - 荷役・追跡管理

|荷役作業員|
start
:荷役作業実施（積込/荷降し/受領/引取）;
:作業記録登録;

|追跡管理者|
:貨物状態更新;
if (例外発生?) then (yes)
  :例外処理（破損・紛失・遅延）;
  :関係者への報告;
else (no)
endif
:追跡情報更新;

|通知システム|
:荷主へ状況通知（メール/SMS）;

|荷主|
:追跡情報確認;
stop

@enduml
```

#### 精算の業務フロー

```plantuml
@startuml

title 業務フロー図 - 精算管理

|経路設計者|
start
:配送完了記録;

|経理担当者|
partition 料金計算フェーズ {
  :輸送料金算出;
  if (法人荷主?) then (yes)
    :割引適用;
  else (no)
  endif
  :精算書作成;
}

|荷主|
:精算書確認;
:支払い処理;

|経理担当者|
:入金確認;
:精算完了記録;
stop

@enduml
```

### 利用シーン

#### 貨物輸送予約の利用シーン

```plantuml
@startuml

title 利用シーン図 - 貨物輸送予約

left to right direction

actor 荷主
actor 営業担当者
actor 経路設計者

frame "新規輸送依頼" as sc01
note right of sc01
  荷主が初めて、または定期的に
  国際輸送を依頼する場面。
  出発地・目的地・期限・貨物種別を
  指定して最適ルートを確認・予約する。
  PC や営業担当者経由で利用。
end note

frame "ルート変更・再設計" as sc02
note right of sc02
  予約確定後のスケジュール変更や
  トラブルによるルート再設計の場面。
  追跡番号をもとに既存予約を参照し
  代替ルートに変更する。
end note

usecase "見積依頼"
usecase "ルート検索・選択"
usecase "予約登録・確定"
usecase "予約変更"

:荷主: -- sc01
:営業担当者: -- sc01
sc01 -- (見積依頼)
sc01 -- (ルート検索・選択)
sc01 -- (予約登録・確定)

:営業担当者: -- sc02
:経路設計者: -- sc02
sc02 -- (予約変更)

@enduml
```

#### 貨物追跡の利用シーン

```plantuml
@startuml

title 利用シーン図 - 貨物追跡

left to right direction

actor 荷主
actor 荷受人
actor 追跡管理者
actor 荷役作業員

frame "輸送中追跡確認" as sc03
note right of sc03
  荷主・荷受人が貨物の現在位置と
  状態をリアルタイムで確認する場面。
  追跡番号を使って Web アプリから参照。
  配送完了予定日も確認できる。
end note

frame "荷役作業記録" as sc04
note right of sc04
  荷役作業員が積込・荷降し等の
  作業完了時に記録を登録する場面。
  作業記録が即座に追跡情報に反映され
  荷主への通知がトリガーされる。
end note

usecase "追跡情報照会"
usecase "作業記録登録"
usecase "貨物状態更新"
usecase "状況通知確認"

:荷主: -- sc03
:荷受人: -- sc03
sc03 -- (追跡情報照会)
sc03 -- (状況通知確認)

:荷役作業員: -- sc04
:追跡管理者: -- sc04
sc04 -- (作業記録登録)
sc04 -- (貨物状態更新)

@enduml
```

### バリエーション・条件

#### 貨物種別

| 貨物種別 | 説明 |
|----------|------|
| 一般貨物 | 通常の取扱いで輸送可能な貨物 |
| 危険物 | 法規制に基づく特別な取扱いが必要な貨物 |
| 冷凍・冷蔵貨物 | 温度管理が必要な貨物 |

#### 荷主種別

| 荷主種別 | 説明 |
|----------|------|
| 個人荷主 | 個人として輸送を依頼する荷主 |
| 法人荷主 | 法人契約により割引が適用される荷主 |

#### 輸送例外種別

| 例外種別 | 説明 |
|----------|------|
| 遅延 | スケジュールより遅れが発生した状態 |
| 破損 | 輸送中に貨物が破損した状態 |
| 紛失 | 貨物の所在が確認できない状態 |

## システム境界

### ユースケース複合図

#### 貨物輸送予約

```plantuml
@startuml

title ユースケース複合図 - 貨物輸送予約

left to right direction

actor 荷主 as shipper
actor 営業担当者 as sales
actor 経路設計者 as route_designer

frame "新規輸送依頼" as f01
usecase "輸送見積作成" as UC01
usecase "荷主・貨物予約登録" as UC02
usecase "ルート検索・選択" as UC03
usecase "予約確定" as UC04
usecase "追跡番号発行" as UC05
boundary "見積・予約画面" as b01
boundary "ルート選択画面" as b02
entity "荷主情報" as e01
entity "貨物予約" as e02
entity "経路情報" as e03
control "貨物種別条件" as c01
interface "予約確定イベント" as i01

shipper -- f01
sales -- f01
route_designer -- f01
f01 -- UC01
f01 -- UC02
f01 -- UC03
f01 -- UC04
f01 -- UC05

b01 -- UC01
b01 -- UC02
UC01 -- e01
UC02 -- e02
UC02 -- c01

b02 -- UC03
UC03 -- e03
UC03 -- 外部経路システム

UC04 -- e02
UC04 -- i01

UC05 -- e02

@enduml
```

#### 荷役・追跡管理

```plantuml
@startuml

title ユースケース複合図 - 荷役・追跡管理

left to right direction

actor 荷役作業員 as handler
actor 追跡管理者 as tracker
actor 荷主 as shipper

frame "荷役・追跡操作" as f02
usecase "荷役作業記録" as UC06
usecase "貨物状態更新" as UC07
usecase "追跡情報照会" as UC08
usecase "例外処理" as UC09
boundary "荷役記録画面" as b03
boundary "追跡照会画面" as b04
entity "荷役情報" as e04
entity "追跡情報" as e05
control "例外種別条件" as c02
interface "状態変更通知イベント" as i02

handler -- f02
tracker -- f02
shipper -- f02
f02 -- UC06
f02 -- UC07
f02 -- UC08
f02 -- UC09

b03 -- UC06
UC06 -- e04
UC06 -- 港湾管理システム

UC07 -- e05
UC07 -- i02
i02 -- 通知システム

b04 -- UC08
UC08 -- e05

UC09 -- c02
UC09 -- e05

@enduml
```

#### 精算管理

```plantuml
@startuml

title ユースケース複合図 - 精算管理

left to right direction

actor 経理担当者 as accountant
actor 荷主 as shipper

frame "精算操作" as f03
usecase "輸送料金算出" as UC10
usecase "割引適用" as UC11
usecase "精算処理" as UC12
boundary "精算画面" as b05
entity "輸送料金" as e06
entity "精算実績" as e07
control "荷主種別条件" as c03
interface "精算完了イベント" as i03

accountant -- f03
shipper -- f03
f03 -- UC10
f03 -- UC11
f03 -- UC12

b05 -- UC10
b05 -- UC12
UC10 -- e06
UC11 -- e06
UC11 -- c03
UC12 -- e07
UC12 -- i03
i03 -- 決済機関

@enduml
```

## システム

### 情報モデル

```plantuml
@startuml

title 情報モデル図 - 国際貨物輸送管理システム

left to right direction

' 荷主関連
entity 荷主
entity 貨物予約
entity 貨物仕様

' 経路関連
entity 経路情報
entity 航海情報
entity 港湾情報

' 追跡・荷役関連
entity 追跡情報
entity 荷役情報
entity 追跡イベント

' 精算関連
entity 輸送料金
entity 精算実績

' 関連付け
荷主 "1" -- "0..*" 貨物予約 : 予約する
貨物予約 "1" -- "1" 貨物仕様 : 含む
貨物予約 "1" -- "0..1" 経路情報 : 指定する
経路情報 "1" -- "1..*" 航海情報 : 利用する
航海情報 "1..*" -- "1..*" 港湾情報 : 経由する
貨物予約 "1" -- "1" 追跡情報 : 追跡する
追跡情報 "1" -- "0..*" 追跡イベント : 記録する
追跡イベント "0..*" -- "1" 荷役情報 : 関連する
貨物予約 "1" -- "1" 輸送料金 : 算出する
輸送料金 "1" -- "0..1" 精算実績 : 精算する
荷主 "1" -- "0..*" 精算実績 : 支払う

@enduml
```

### 状態モデル

#### 貨物予約の状態遷移

```plantuml
@startuml

title 貨物予約の状態遷移図

[*] --> 仮受付 : UC02 荷主・貨物予約登録

仮受付 --> 経路提案中 : UC03 ルート設計・提案

state 経路提案中 {
  [*] --> ルート検討中
  ルート検討中 --> ルート選択済 : UC03 ルート選択
  ルート選択済 --> ルート検討中 : ルート変更依頼
}

経路提案中 --> 予約確定 : UC04 予約確定
予約確定 --> 追跡番号発行済 : UC05 追跡番号発行
追跡番号発行済 --> 輸送中 : UC06 荷役作業開始

輸送中 --> 配送完了 : UC07 最終荷役作業完了
配送完了 --> 精算済 : UC12 精算処理完了
精算済 --> [*]

仮受付 --> キャンセル : キャンセル依頼
経路提案中 --> キャンセル : キャンセル依頼
予約確定 --> キャンセル : キャンセル依頼
キャンセル --> [*]

@enduml
```

#### 追跡情報（貨物状態）の状態遷移

```plantuml
@startuml

title 追跡情報（貨物状態）の状態遷移図

[*] --> 受領待ち : UC05 追跡番号発行済

受領待ち --> 受領済 : UC06 荷役作業記録（受領）
受領済 --> 積込済 : UC06 荷役作業記録（積込）
積込済 --> 輸送中 : 出港
輸送中 --> 荷降し済 : UC06 荷役作業記録（荷降し）
荷降し済 --> 引取待ち : 最終港着港
引取待ち --> 引取済 : UC06 荷役作業記録（引取）
引取済 --> [*]

受領済 --> 例外発生 : UC09 例外処理（破損・紛失）
積込済 --> 例外発生 : UC09 例外処理（遅延）
輸送中 --> 例外発生 : UC09 例外処理（破損・紛失・遅延）
荷降し済 --> 例外発生 : UC09 例外処理

例外発生 --> 対応中 : 例外対応開始
対応中 --> 輸送中 : 正常復帰
対応中 --> 引取済 : 例外解決・引取完了

@enduml
```

---

## 記入ガイド（参照）

本ドキュメントは `docs/template/要件定義.md` テンプレートをベースに、
`docs/strategy/business_architecture.md` のビジネスアーキテクチャ分析書を入力として作成しました。

### アクター一覧

| 種別 | アクター | 役割 |
| :--- | :--- | :--- |
| ヒューマン | 荷主 | 貨物の輸送を依頼し、見積確認・ルート選択・予約確定・追跡確認を行う |
| ヒューマン | 荷受人 | 貨物の到着を待ち、追跡情報を確認する |
| ヒューマン | 営業担当者 | 見積作成・荷主情報登録・貨物予約登録・予約確定を行う |
| ヒューマン | 経路設計者 | 最適ルート設計・航海スケジュール管理・追跡番号発行を行う |
| ヒューマン | 追跡管理者 | 貨物状態更新・追跡情報管理・例外対応を行う |
| ヒューマン | 荷役作業員 | 積込・荷降し・受領・引取等の荷役作業を実施し記録する |
| ヒューマン | 経理担当者 | 輸送料金算出・割引適用・精算処理を行う |
| コンピュータ | 通知システム | 荷主・荷受人への状況変更通知（メール/SMS）を送信する |
| コンピュータ | 外部経路システム | ルート候補の算出を支援する |
| コンピュータ | 港湾管理システム | 港湾施設の利用状況を提供する |
| コンピュータ | 決済機関 | 精算処理の決済を実行する |
| コンピュータ | 税関 | 輸出入申告情報の連携を受け付ける |

### ユースケース一覧

| ID | ユースケース名 | 対応 BUC | 主要アクター |
| :--- | :--- | :--- | :--- |
| UC01 | 輸送見積作成 | 貨物輸送予約 | 営業担当者 |
| UC02 | 荷主・貨物予約登録 | 貨物輸送予約 | 営業担当者 |
| UC03 | ルート検索・選択 | 貨物輸送予約 | 経路設計者、荷主 |
| UC04 | 予約確定 | 貨物輸送予約 | 営業担当者、荷主 |
| UC05 | 追跡番号発行 | 貨物輸送予約 | 経路設計者 |
| UC06 | 荷役作業記録 | 荷役・追跡管理 | 荷役作業員 |
| UC07 | 貨物状態更新 | 荷役・追跡管理 | 追跡管理者 |
| UC08 | 追跡情報照会 | 荷役・追跡管理 | 荷主、荷受人 |
| UC09 | 例外処理 | 荷役・追跡管理 | 追跡管理者、荷役作業員 |
| UC10 | 輸送料金算出 | 精算管理 | 経理担当者 |
| UC11 | 割引適用 | 精算管理 | 経理担当者 |
| UC12 | 精算処理 | 精算管理 | 経理担当者、荷主 |
