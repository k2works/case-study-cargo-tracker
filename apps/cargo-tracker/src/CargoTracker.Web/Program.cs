using CargoTracker.Shared.Infrastructure.Persistence;

var builder = WebApplication.CreateBuilder(args);

// Add services to the container.
builder.Services.AddControllersWithViews();
builder.Services.AddHealthChecks();

// DB 接続設定（ADR-0003 二方言運用）と接続ファクトリ・MediatR を登録する。
var databaseOptions = builder.Configuration
    .GetSection(DatabaseOptions.SectionName)
    .Get<DatabaseOptions>() ?? new DatabaseOptions();
builder.Services.AddSingleton(databaseOptions);
builder.Services.AddSingleton<IDbConnectionFactory, DbConnectionFactory>();
builder.Services.AddMediatR(cfg => cfg.RegisterServicesFromAssembly(typeof(Program).Assembly));

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

app.UseAuthorization();

app.MapStaticAssets();

app.MapHealthChecks("/health");

app.MapControllerRoute(
    name: "default",
    pattern: "{controller=Home}/{action=Index}/{id?}")
    .WithStaticAssets();


app.Run();
