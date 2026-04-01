---
name: java25_bytebuddy_fix
description: Java 25 + Mockito で Byte Buddy が ClassFileVersion を解決できない問題の対処法
type: project
---

Java 25 環境で Mockito（Byte Buddy）が `Unknown Java version: 0` エラーを出す場合は、
`build.gradle` のテストタスクに以下を追加する。

```gradle
tasks.named('test') {
    jvmArgs '-Dnet.bytebuddy.experimental=true'
}
```

**Why:** `net.bytebuddy.ClassFileVersion$VersionLocator$Unresolved.resolve(ClassFileVersion.java:667)` が Java 25 のクラスファイルバージョンを解決できないため。`experimental` フラグを立てることで回避できる。

**How to apply:** Java 25 以降の環境でテストが `Failed to resolve the class file version of the current VM` で落ちている場合に適用する。
