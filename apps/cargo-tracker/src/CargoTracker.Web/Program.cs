using System.Text.Encodings.Web;
using System.Text.Unicode;
using CargoTracker.Shared.Application.Persistence;
using CargoTracker.Shared.Infrastructure.Auth;
using CargoTracker.Shared.Infrastructure.Persistence;
using CargoTracker.Shared.Infrastructure.Seeding;
using Microsoft.AspNetCore.Authentication.Cookies;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc.Authorization;
using Microsoft.Extensions.WebEncoders;

var builder = WebApplication.CreateBuilder(args);

// 日本語 UI のため、HTML エンコーダが日本語をエンティティ化せずそのまま出力するよう全 Unicode を許可する。
builder.Services.Configure<WebEncoderOptions>(options =>
    options.TextEncoderSettings = new TextEncoderSettings(UnicodeRanges.All));

// 既定で全 MVC エンドポイントに認証を要求する（US26 受入条件 3）。
// 未認証で保護ページにアクセスするとログイン画面へリダイレクトされる。
// [AllowAnonymous] を付けたログイン・公開追跡は除外される。/health と静的アセットは MVC 外のため影響しない。
builder.Services.AddControllersWithViews(options =>
{
    var policy = new AuthorizationPolicyBuilder().RequireAuthenticatedUser().Build();
    options.Filters.Add(new AuthorizeFilter(policy));
});
builder.Services.AddHealthChecks();

// Cookie 認証（US26 受入条件 1・5）。full Identity は導入せず Cookie スキームのみ利用する。
builder.Services
    .AddAuthentication(CookieAuthenticationDefaults.AuthenticationScheme)
    .AddCookie(options =>
    {
        options.LoginPath = "/login";
        options.LogoutPath = "/logout";
        options.AccessDeniedPath = "/forbidden";
        options.ExpireTimeSpan = TimeSpan.FromHours(8);
        options.SlidingExpiration = true;
    });
builder.Services.AddAuthorization();

// DB 接続設定（ADR-0003 二方言運用）と接続ファクトリ・MediatR・認証サービスを登録する。
var databaseOptions = builder.Configuration
    .GetSection(DatabaseOptions.SectionName)
    .Get<DatabaseOptions>() ?? new DatabaseOptions();
builder.Services.AddSingleton(databaseOptions);
builder.Services.AddSingleton<IDbConnectionFactory, DbConnectionFactory>();
builder.Services.AddScoped<AmbientTransaction>();
builder.Services.AddScoped<IUnitOfWorkFactory, UnitOfWorkFactory>();
builder.Services.AddMediatR(cfg => cfg.RegisterServicesFromAssembly(typeof(Program).Assembly));
builder.Services.AddSingleton<IPasswordHasher, IdentityPasswordHasher>();
builder.Services.AddScoped<IUserRepository, UserRepository>();
builder.Services.AddScoped<UserAuthenticator>();

// Shipper コンテキスト（US02/US03）。
builder.Services.AddScoped<CargoTracker.Shipper.Domain.Repositories.IShipperRepository,
    CargoTracker.Shipper.Infrastructure.Repositories.ShipperRepository>();
builder.Services.AddScoped<CargoTracker.Shipper.Application.Internal.CommandServices.RegisterShipperCommandService>();
builder.Services.AddScoped<CargoTracker.Shipper.Application.Internal.QueryServices.FindShipperQueryService>();

// Estimation コンテキスト（US01）。ルート算出は IT1 はスタブ（IT3 で WireMock.Net + 実サービス）。
builder.Services.AddScoped<CargoTracker.Estimation.Domain.Repositories.IEstimateRepository,
    CargoTracker.Estimation.Infrastructure.Repositories.EstimateRepository>();
builder.Services.AddScoped<CargoTracker.Estimation.Application.Internal.OutboundServices.IExternalRoutingServicePort,
    CargoTracker.Estimation.Infrastructure.Services.StubExternalRoutingService>();
builder.Services.AddScoped<CargoTracker.Estimation.Application.Internal.CommandServices.CreateEstimateCommandService>();
builder.Services.AddScoped<CargoTracker.Estimation.Application.Internal.QueryServices.FindEstimateQueryService>();

// Booking コンテキスト（US04）。
builder.Services.AddScoped<CargoTracker.Booking.Domain.Repositories.ICargoRepository,
    CargoTracker.Booking.Infrastructure.Repositories.CargoRepository>();
builder.Services.AddScoped<CargoTracker.Booking.Application.Internal.OutboundServices.IShipperExistenceChecker,
    CargoTracker.Booking.Infrastructure.Services.ShipperExistenceChecker>();
builder.Services.AddScoped<CargoTracker.Booking.Application.Internal.OutboundServices.ISelectedRouteLookup,
    CargoTracker.Booking.Infrastructure.Services.SelectedRouteLookup>();
builder.Services.AddScoped<CargoTracker.Booking.Application.Internal.CommandServices.BookCargoCommandService>();
builder.Services.AddScoped<CargoTracker.Booking.Application.Internal.CommandServices.AssignToRoutingCommandService>();
builder.Services.AddScoped<CargoTracker.Booking.Domain.Repositories.IRouteNotificationRepository,
    CargoTracker.Booking.Infrastructure.Repositories.RouteNotificationRepository>();
builder.Services.AddScoped<CargoTracker.Booking.Application.Internal.CommandServices.RouteCargoCommandService>();
builder.Services.AddScoped<CargoTracker.Booking.Application.Internal.CommandServices.NotifyRouteToShipperCommandService>();
builder.Services.AddScoped<CargoTracker.Booking.Application.Internal.CommandServices.ConfirmBookingCommandService>();
builder.Services.AddScoped<CargoTracker.Booking.Application.Internal.CommandServices.ReturnToRoutingCommandService>();
builder.Services.AddScoped<CargoTracker.Booking.Application.Internal.CommandServices.CancelBookingCommandService>();
builder.Services.AddScoped<CargoTracker.Booking.Application.Internal.QueryServices.FindBookingQueryService>();

// Routing コンテキスト（US24）。
builder.Services.AddScoped<CargoTracker.Routing.Domain.Repositories.IVoyageRepository,
    CargoTracker.Routing.Infrastructure.Repositories.VoyageRepository>();
builder.Services.AddScoped<CargoTracker.Routing.Application.Internal.OutboundServices.IBookingLookup,
    CargoTracker.Routing.Infrastructure.Services.BookingLookup>();
builder.Services.AddScoped<CargoTracker.Routing.Domain.Repositories.ISelectedRouteRepository,
    CargoTracker.Routing.Infrastructure.Repositories.SelectedRouteRepository>();
builder.Services.AddScoped<CargoTracker.Routing.Application.Internal.CommandServices.RegisterVoyageCommandService>();
builder.Services.AddScoped<CargoTracker.Routing.Application.Internal.CommandServices.UpdateScheduleCommandService>();
builder.Services.AddScoped<CargoTracker.Routing.Application.Internal.CommandServices.SelectRouteCommandService>();
builder.Services.AddScoped<CargoTracker.Routing.Application.Internal.QueryServices.FindVoyageQueryService>();
builder.Services.AddScoped<CargoTracker.Routing.Application.Internal.QueryServices.RoutingRequestQueryService>();
builder.Services.AddScoped<CargoTracker.Routing.Application.Internal.QueryServices.SearchVoyagesQueryService>();
builder.Services.AddScoped<CargoTracker.Routing.Application.Internal.OutboundServices.IRouteCandidateService,
    CargoTracker.Routing.Infrastructure.Services.VoyageRouteCandidateService>();

// Tracking コンテキスト（US14）。
builder.Services.AddScoped<CargoTracker.Tracking.Domain.Repositories.ITrackingActivityRepository,
    CargoTracker.Tracking.Infrastructure.Repositories.TrackingActivityRepository>();
builder.Services.AddScoped<CargoTracker.Tracking.Application.Internal.CommandServices.AssignTrackingNumberCommandService>();

var app = builder.Build();

// 起動時に DbUp マイグレーションを適用する（forward-only・ADR-0003）。
// 接続文字列が未設定の環境（一部のテスト等）ではスキップする。
if (!string.IsNullOrWhiteSpace(databaseOptions.ConnectionString))
{
    var migration = DatabaseMigrator.Migrate(databaseOptions.Provider, databaseOptions.ConnectionString);
    if (!migration.Successful)
    {
        throw new InvalidOperationException("DB マイグレーションに失敗しました。", migration.Error);
    }

    // 開発環境、または Seed:Enabled が有効なデプロイ環境（deploy:dev の Heroku 等）で
    // デモデータ（シードユーザー + デモ荷主・見積）を投入する。本番では実行しない（US26 タスク 2.4）。
    var seedOptions = builder.Configuration.GetSection(SeedOptions.SectionName).Get<SeedOptions>() ?? new SeedOptions();
    if (app.Environment.IsDevelopment() || seedOptions.Enabled)
    {
        using var scope = app.Services.CreateScope();
        await DemoDataSeeder.SeedAsync(scope.ServiceProvider, seedOptions.IncludeRoutingDemo);
    }
}

// Configure the HTTP request pipeline.
if (!app.Environment.IsDevelopment())
{
    app.UseExceptionHandler("/Home/Error");
    // The default HSTS value is 30 days. You may want to change this for production scenarios, see https://aka.ms/aspnetcore-hsts.
    app.UseHsts();
}

app.UseHttpsRedirection();
app.UseRouting();

app.UseAuthentication();
app.UseAuthorization();

app.MapStaticAssets();

app.MapHealthChecks("/health");

app.MapControllerRoute(
    name: "default",
    pattern: "{controller=Home}/{action=Index}/{id?}")
    .WithStaticAssets();


app.Run();

// WebApplicationFactory<Program> による統合テストのためにエントリポイントを公開する。
public partial class Program;
