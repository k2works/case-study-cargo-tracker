---
name: test_patterns
description: このプロジェクトのテスト書き方規約（JUnit5 + AssertJ + Mockito）
type: feedback
---

## テスト規約

- **フレームワーク**: JUnit 5 + AssertJ + Mockito (`spring-boot-starter-test` に含まれる)
- **命名**: テストメソッド名は日本語（例: `有効な値でRouteCandidateを生成できる`）
- **アノテーション**: `@DisplayName` で日本語の説明を付与
- **アサーション**: AssertJ の `assertThat()`、`assertThatThrownBy()` を使用
- **モック**: `@ExtendWith(MockitoExtension.class)` + `@Mock` + `@BeforeEach` で手動インスタンス化
- **配置**: `src/test/java/com/example/cargotracker/routing/` にフラットに置く（サブパッケージ不要）

## How to apply

新規ユニットテストを書く際にこのパターンに従う。既存の `CargoSpecificationTest.java` や `TransportConditionTest.java` が参考になる。
