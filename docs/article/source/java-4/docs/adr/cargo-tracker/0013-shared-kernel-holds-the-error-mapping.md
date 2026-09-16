---
type: ADR
title: "ADR-0013 共有カーネルが例外の対応表を持つ"
description: "ドメイン例外を HTTP へ写す対応表を shared.interfaces.rest に置く。共有カーネルにはドメインと契約だけを置く原則の、意図した例外である。"
tags: [adr]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-09T05:35:05Z }
---

# ADR-0013 共有カーネルが例外の対応表を持つ

ドメイン例外を HTTP の応答へ写す対応表（`AbstractApiExceptionHandler`）を、共有カーネルの `shared.interfaces.rest` に置く。

日付: 2026-09-09

## ステータス

2026-09-09 提案。**IT10 で実装済みの決定を、あとから明文化するもの**（[ADR-0001](0001-cqrs-es-with-axon-in-microservices.md) 決定 4「共有カーネルの範囲」の追補）。決定そのものは IT10 で入り、ADR が無いまま 1 イテレーション過ぎた（IT10 レビュー N4）。

## コンテキスト

**同じ対応表が 4 サービスに写されていました。** `BusinessRuleViolation` を 422、`IllegalTransition` を 409 へ写す規則と、**例外の包みを最後まで剥がす**手順（`containsMarker` / `deepestMessage`）が、bookingms・routingms・trackingms・handlingms に 139 行ずつ複製されていました。

包みを最後まで剥がす必要があることは IT3 の受け入れテストが出した知見です。Axon はコマンドハンドラの例外を `CommandExecutionException` で包み、その中身がさらに包まれることがあります。**1 枚しか見ないと 409 が 422 に化けます**——利用者には「業務規則で断られた」と「その順序では動かせない」の区別が付きません。

写しは少しずつ食い違います。IT9 の引き継ぎ H.2 として挙がった時点で、4 つのうち 1 つだけ包みの見方が違っていました。

## 決定

**決定 1. 対応表は `shared.interfaces.rest.AbstractApiExceptionHandler` が 1 つだけ持つ。** 各サービスの `ApiExceptionHandler` は `@RestControllerAdvice` を付けて継承するだけにする。

**決定 2. 共有カーネルに Web 層を置くのは、この 1 つに限る。** 共有カーネルはドメインと契約を置く場所で、技術的な層を持ち込むと「共有だから」を理由に何でも入る。ここで例外にするのは、**対応表が「ドメイン例外の意味」を決めているから**である——422 か 409 かは HTTP の都合ではなく、その例外が「規則に反する」のか「順序が違う」のかという業務の区別で、それは全 BC で同じでなければならない。

**決定 3. 写し戻したら赤になる検査を置く。** `EventSourcedServicesHaveTheSameShapeTest` が、各サービスの `ApiExceptionHandler` が共有を継承していること、**共有にあるものを写していないこと**の両方を見る。

**決定 4. 対応表を持たないサービスは、ドメイン例外を投げない。** authms は Event Sourcing ではなく（ADR-0001）この対応表を継承していない。**外にいること自体は正しいが、外にいるサービスがドメイン例外を投げ始めると 500 に化ける**ので、走査で導いた「対応表を持たないサービス」がドメイン例外を投げていないことを検査する（IT10 レビュー N5・IT11 で追加）。

## 検査

**決定の数だけ検査を用意する。** 検査に落とさなかった決定は守られない（ADR-0009 は 7 IT のあいだ半分しか守られなかった）。

| 決定 | 検査 |
| :--- | :--- |
| 1 | `EventSourcedServicesHaveTheSameShapeTest#servicesReuseTheSharedExceptionMapping`（各サービスが共有を継承している）。`AbstractApiExceptionHandlerTest`（対応表そのものの振る舞い。7 件） |
| 2 | `SharedKernelScopeTest`（置けるパッケージの名簿。`shared.interfaces.rest` はその 1 つ）。`SharedKernelScopeTest#discriminatesWhenScopeIsNarrowed`（名簿を狭めると赤になる——狭めても緑なら、この規則は何も守っていない） |
| 3 | `EventSourcedServicesHaveTheSameShapeTest#servicesReuseTheSharedExceptionMapping`（`containsMarker` / `deepestMessage` / `@ExceptionHandler(BusinessRuleViolation.class)` を写し戻すと赤）。`EventSourcedServicesHaveTheSameShapeTest#sharedMappingStillUnwrapsEveryLayer`（共有側が包みを 1 枚しか見ない形に戻っていない） |
| 4 | `EventSourcedServicesHaveTheSameShapeTest#servicesWithoutTheSharedMappingDoNotThrowDomainErrors`（対応表を持たないサービスがドメイン例外を投げていない。**対象は走査で導く**——名簿にすると載せ忘れたものほど漏れる） |

## 代替案

| 案 | 内容 | 却下の理由 |
| :--- | :--- | :--- |
| 各サービスに置いたまま、検査で同一性を見る | 写しは残すが、食い違ったら赤にする | 「同じであること」を検査するより「1 つであること」のほうが単純で、直す場所も 1 つになる |
| 別モジュール（`shared-web`）を作る | 共有カーネルに Web を入れない | モジュールが 1 つ増えるのに対し、置くものは 1 クラスである。範囲を ADR で縛るほうが軽い |
| 対応表を持たないサービスにも継承させる | authms にも `ApiExceptionHandler` を置く | 投げていないものへの備えになる。**読む側の無い配線を先に敷かない**（IT9・IT10 と同じ判断） |

## 結果

**よくなること。** 包みの見方が 1 か所になり、片方だけ直ることが無くなる。共有カーネルの範囲を広げる判断が、この ADR を書き換えることでしか起きなくなる。

**代償。** 共有カーネルが Spring Web に依存する。`shared` を Web を持たないサービスから使うときも、依存だけは付いて回る（実測では全サービスが Web を持つので、いまのところ実害は無い）。

**守りきれなくなる兆し。** `shared.interfaces` に 2 つ目のクラスを置きたくなったときは、この ADR を読み直す。「共有だから」以外の理由——全 BC で同じでなければならない業務上の理由——が言えなければ、置かない。

## 関連

- [ADR-0001](0001-cqrs-es-with-axon-in-microservices.md) 決定 4「共有カーネルの範囲」
- `apps/cargo-tracker/backend/shared/src/test/java/com/example/cargotracker/shared/archunit/SharedKernelScopeTest.java`（名簿と、名簿を狭めると赤になる検査）
- `apps/cargo-tracker/backend/shared/src/test/java/com/example/cargotracker/shared/conventions/EventSourcedServicesHaveTheSameShapeTest.java`（写し戻しと、対応表の外の検査）
