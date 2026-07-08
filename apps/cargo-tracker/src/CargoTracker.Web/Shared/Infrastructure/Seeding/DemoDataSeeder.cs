using CargoTracker.Estimation.Application.Internal.CommandServices;
using CargoTracker.Estimation.Application.Internal.QueryServices;
using CargoTracker.Estimation.Domain.Model;
using CargoTracker.Shared.Infrastructure.Auth;
using CargoTracker.Shipper.Application.Internal.CommandServices;
using CargoTracker.Shipper.Application.Internal.QueryServices;

namespace CargoTracker.Shared.Infrastructure.Seeding;

/// <summary>
/// デモ・開発環境向けのシードデータを投入する。シードユーザー（US26）に加え、
/// 空の状態を避けるためのデモ荷主・見積を冪等に登録する（既存データがあればスキップ）。
/// 本番では実行しない（<see cref="SeedOptions.Enabled"/> または Development でのみ起動）。
/// </summary>
public static class DemoDataSeeder
{
    public static async Task SeedAsync(IServiceProvider services, CancellationToken ct = default)
    {
        // 1. シードユーザー（ログインに必須）。AddIfNotExists のため冪等。
        await UserSeeder.SeedAsync(
            services.GetRequiredService<IUserRepository>(),
            services.GetRequiredService<IPasswordHasher>(),
            ct);

        // 2. デモ荷主（既に荷主が存在する場合はスキップ）。
        var shipperQuery = services.GetRequiredService<FindShipperQueryService>();
        if ((await shipperQuery.FindAllAsync(ct)).Count == 0)
        {
            var registerShipper = services.GetRequiredService<RegisterShipperCommandService>();
            await registerShipper.HandleAsync(new RegisterShipperCommand(
                IsCorporate: false, Name: "山田太郎", Email: "yamada@example.com",
                Phone: "03-1234-5678", Address: "東京都港区海岸1-2-3", ContractNumber: null, DiscountRate: null), ct);
            await registerShipper.HandleAsync(new RegisterShipperCommand(
                IsCorporate: true, Name: "サンプル物流株式会社", Email: "sales@sample-logistics.example.com",
                Phone: "06-9876-5432", Address: "大阪府大阪市住之江区南港北2-1-10", ContractNumber: "C-2026-001", DiscountRate: 0.15m), ct);
        }

        // 3. デモ見積（既に見積が存在する場合はスキップ）。
        var estimateQuery = services.GetRequiredService<FindEstimateQueryService>();
        if ((await estimateQuery.FindAllAsync(ct)).Count == 0)
        {
            var createEstimate = services.GetRequiredService<CreateEstimateCommandService>();
            await createEstimate.HandleAsync(new CreateEstimateCommand(
                "JPTYO", "DEHAM", new DateOnly(2026, 9, 30), CargoType.General, 1200m), ct);
        }
    }
}
