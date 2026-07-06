module CargoTracker.IntegrationTests.PlaceholderTests

open Xunit

// Docker が必要な Testcontainers ベースのテストは今後追加する。
// 現時点では Integration カテゴリのプレースホルダーのみ配置する。
[<Fact>]
[<Trait("Category", "Integration")>]
let ``プレースホルダー統合テスト`` () = Assert.True(true)
