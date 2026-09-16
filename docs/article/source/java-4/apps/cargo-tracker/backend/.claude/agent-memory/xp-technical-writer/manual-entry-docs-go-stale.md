---
name: manual-entry-docs-go-stale
description: 章を追加しても docs/manual/00・01 の「担当と使える画面」「いま使える範囲」が更新されず、新章の読者を追い返す
metadata:
  type: feedback
---

新しいマニュアル章を書いたら、必ず `docs/manual/00-はじめに.md` の担当別「主に使う画面」表と `docs/manual/01-業務フロー.md` の「いま使える範囲」表も同じ変更で直す。

**Why:** IT17 で 21 章（荷主向け）を新設したが、00 章は「まだ画面が用意されていない担当（荷役作業員・荷主）もあります」のまま、01 章は「予約の確定より先の業務は順次追加されます」「見積・精算は未提供」のままだった。索引（index.md）と mkdocs.yml は毎回更新されるのに、この 2 つは見落とされる——読者が最初に開く場所なので、新章そのものが無いと読まれる。

**How to apply:** 章を足すレビューでは index.md / mkdocs.yml だけで「索引 OK」としない。00・01 の 2 表を開いて、その章のロールと業務の行があるか確かめる。[[manual-cross-reference-chapter-numbers]]
