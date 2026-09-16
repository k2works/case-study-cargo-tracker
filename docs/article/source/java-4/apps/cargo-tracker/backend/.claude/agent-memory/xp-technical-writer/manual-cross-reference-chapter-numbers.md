---
name: manual-cross-reference-chapter-numbers
description: マニュアル本文の「（NN 章）」参照は章番号の入れ替わりで静かにずれる。index.md の目次と突き合わせて確かめる
metadata:
  type: feedback
---

本文中の「（NN 章）」表記は、必ず `docs/manual/index.md` の目次と番号を突き合わせて確かめる。

**Why:** IT17 で 20 章が要確認一覧を「18 章」と書いていた（実際は 04 章。18 章は輸送見積）。リンクではなく素の番号なので Lint もリンク切れ検査も捕まえない。

**How to apply:** 章のレビューで「NN 章」を全部拾い、目次の番号と照合する。可能なら素の番号ではなく相対リンクで書く。[[manual-entry-docs-go-stale]]
