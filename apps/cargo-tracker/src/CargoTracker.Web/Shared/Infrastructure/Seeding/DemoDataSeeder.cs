using CargoTracker.Booking.Application.Internal.CommandServices;
using CargoTracker.Booking.Domain.Model;
using CargoTracker.Estimation.Application.Internal.CommandServices;
using CargoTracker.Estimation.Application.Internal.QueryServices;
using CargoTracker.Estimation.Domain.Model;
using CargoTracker.Routing.Application.Internal.CommandServices;
using CargoTracker.Routing.Application.Internal.QueryServices;
using CargoTracker.Routing.Domain.Model;
using CargoTracker.Shared.Application.Persistence;
using CargoTracker.Shared.Infrastructure.Auth;
using CargoTracker.Shipper.Application.Internal.CommandServices;
using CargoTracker.Shipper.Application.Internal.QueryServices;
using Dapper;

namespace CargoTracker.Shared.Infrastructure.Seeding;

/// <summary>
/// デモ・開発環境向けのシードデータを投入する。シードユーザー（US26）に加え、
/// 空の状態を避けるためのデモ荷主・見積を冪等に登録する（既存データがあればスキップ）。
/// 本番では実行しない（<see cref="SeedOptions.Enabled"/> または Development でのみ起動）。
/// </summary>
public static class DemoDataSeeder
{
    public static async Task SeedAsync(
        IServiceProvider services, bool includeRoutingDemo = true, CancellationToken ct = default)
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
                "JPTYO", "DEHAM", new DateOnly(2026, 9, 30), Estimation.Domain.Model.CargoType.General, 1200m), ct);
        }

        // Routing のデモ（航海・経路設計依頼）はデータ分離のためテスト環境では投入しない。
        if (!includeRoutingDemo)
        {
            return;
        }

        // 4. デモ航海スケジュール（経路設計者ロールの航路管理・経路候補算出用。既に航海があればスキップ）。
        var voyageQuery = services.GetRequiredService<FindVoyageQueryService>();
        if ((await voyageQuery.FindAllAsync(ct)).Count == 0)
        {
            var registerVoyage = services.GetRequiredService<RegisterVoyageCommandService>();

            // 直行便: JPTYO → DEHAM
            await registerVoyage.HandleAsync(new RegisterVoyageCommand(
                "VYG-DEMO-001", "SAKURA MARU", "Pacific Ocean Lines",
                [SupportedCargoType.General, SupportedCargoType.Refrigerated],
                [
                    new RegisterCarrierMovementCommand("JPTYO", "DEHAM",
                        new DateTimeOffset(2026, 9, 1, 10, 0, 0, TimeSpan.Zero),
                        new DateTimeOffset(2026, 9, 20, 8, 0, 0, TimeSpan.Zero), 1),
                ]), ct);

            // 乗継便: JPTYO → SGSIN → DEHAM（寄港地経由）
            await registerVoyage.HandleAsync(new RegisterVoyageCommand(
                "VYG-DEMO-002", "FUJI MARU", "Asia Europe Express",
                [SupportedCargoType.General, SupportedCargoType.Hazardous],
                [
                    new RegisterCarrierMovementCommand("JPTYO", "SGSIN",
                        new DateTimeOffset(2026, 9, 2, 9, 0, 0, TimeSpan.Zero),
                        new DateTimeOffset(2026, 9, 7, 12, 0, 0, TimeSpan.Zero), 1),
                    new RegisterCarrierMovementCommand("SGSIN", "DEHAM",
                        new DateTimeOffset(2026, 9, 8, 15, 0, 0, TimeSpan.Zero),
                        new DateTimeOffset(2026, 9, 22, 8, 0, 0, TimeSpan.Zero), 2),
                ]), ct);

            // 別ルート: JPTYO → CNSHA → DEHAM
            await registerVoyage.HandleAsync(new RegisterVoyageCommand(
                "VYG-DEMO-003", "KISO MARU", "Nippon Global Carrier",
                [SupportedCargoType.General],
                [
                    new RegisterCarrierMovementCommand("JPTYO", "CNSHA",
                        new DateTimeOffset(2026, 9, 3, 8, 0, 0, TimeSpan.Zero),
                        new DateTimeOffset(2026, 9, 6, 10, 0, 0, TimeSpan.Zero), 1),
                    new RegisterCarrierMovementCommand("CNSHA", "DEHAM",
                        new DateTimeOffset(2026, 9, 7, 14, 0, 0, TimeSpan.Zero),
                        new DateTimeOffset(2026, 9, 25, 9, 0, 0, TimeSpan.Zero), 2),
                ]), ct);
        }

        // 5. 経路設計依頼（経路設計者ロールの依頼一覧用）。予約を登録し経路設計に引き渡した
        //    RouteProposed 状態の貨物を投入する（既に予約があればスキップ）。
        var connectionFactory = services.GetRequiredService<IDbConnectionFactory>();
        using (var connection = connectionFactory.Create())
        {
            var cargoCount = await connection.ExecuteScalarAsync<long>(
                new CommandDefinition("SELECT COUNT(1) FROM cargo", cancellationToken: ct));
            var shipperSurrogateId = await connection.ExecuteScalarAsync<long?>(
                new CommandDefinition("SELECT id FROM shipper ORDER BY id", cancellationToken: ct));

            if (cargoCount == 0 && shipperSurrogateId is not null)
            {
                var bookCargo = services.GetRequiredService<BookCargoCommandService>();
                var assignToRouting = services.GetRequiredService<AssignToRoutingCommandService>();

                // 到着期限は過去日ガード（H3）を避けるため相対的な未来日にする。
                var arrivalDeadline = DateOnly.FromDateTime(DateTime.UtcNow).AddMonths(3);
                var bookingId = await bookCargo.HandleAsync(new BookCargoCommand(
                    ShipperId: shipperSurrogateId.Value.ToString(System.Globalization.CultureInfo.InvariantCulture),
                    OriginUnLocode: "JPTYO", DestinationUnLocode: "DEHAM",
                    ArrivalDeadline: arrivalDeadline,
                    CargoType: Booking.Domain.Model.CargoType.General, Weight: 1500m,
                    Description: "デモ経路設計依頼貨物"), ct);

                await assignToRouting.HandleAsync(new AssignToRoutingCommand(bookingId), ct);
            }
        }
    }
}
