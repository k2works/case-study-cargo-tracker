---
title: Part III - マルチタスキングとスケジューリング
description: ゲームループで学ぶマルチタスキング
published: true
date: 2025-12-31T10:00:00.000Z
tags: concurrency, multitasking, gameloop, scala
editor: markdown
dateCreated: 2025-12-31T10:00:00.000Z
---

# Part III: マルチタスキングとスケジューリング

## 概要

本章では、ゲームループを例にマルチタスキングとスケジューリングを学びます。

---

## ゲームタスク

```scala
case class GameTask(name: String, action: () => Unit):
  def run(): Unit = action()
```

---

## イベント同期

```scala
class ProcessorFreeEvent:
  private val lock = new Object
  @volatile private var signaled = false

  def waitForSignal(): Unit = lock.synchronized {
    while !signaled do lock.wait()
  }

  def signal(): Unit = lock.synchronized {
    signaled = true
    lock.notifyAll()
  }

  def reset(): Unit = lock.synchronized {
    signaled = false
  }
```

---

## ゲームループ

```scala
object GameLoop:
  /** Run all tasks in a single frame */
  def runOneFrame(tasks: List[GameTask]): Unit =
    tasks.foreach(_.run())

  /** Run game loop for specified number of frames */
  def run(tasks: List[GameTask], frames: Int): Unit =
    (1 to frames).foreach { frame =>
      println(s"Frame $frame")
      runOneFrame(tasks)
    }
```

---

## 使用例

```scala
val tasks = List(
  GameTask("Input", () => println("Processing input...")),
  GameTask("Update", () => println("Updating game state...")),
  GameTask("Render", () => println("Rendering frame..."))
)

GameLoop.run(tasks, 3)
```

---

## ポイント

- **case class**: イミュータブルなデータ構造
- **synchronized**: JVM レベルのロック
- **@volatile**: メモリ可視性の保証

---

## 次のステップ

[Part IV](part-4.md) では、Fork/Join パターンとパイプラインを学びます。
