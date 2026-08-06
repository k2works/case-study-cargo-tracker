# ビルドステージ
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

# 依存解決をレイヤーキャッシュするため csproj を先にコピー
COPY Directory.Build.props ./
COPY src/CargoTracker.Web/CargoTracker.Web.csproj src/CargoTracker.Web/
RUN dotnet restore src/CargoTracker.Web/CargoTracker.Web.csproj

COPY . .
RUN dotnet publish src/CargoTracker.Web/CargoTracker.Web.csproj -c Release -o /app/publish --no-restore

# 実行ステージ
FROM mcr.microsoft.com/dotnet/aspnet:10.0 AS runtime
WORKDIR /app

# ヘルスチェック用に curl を導入（Heroku Runtime では HEALTHCHECK は使用されない。ローカル用）
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/publish .

# EXPOSE はローカルテスト用途。Heroku は $PORT を注入する
EXPOSE 8080

# 非 root ユーザーで実行（aspnet イメージ同梱の app ユーザー）
USER app

# ヘルスチェック（ASP.NET Core Health Checks /health。ローカル Docker 用）
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -fsS http://localhost:${PORT:-8080}/health || exit 1

# Heroku が注入する $PORT で listen する（未設定時は 8080）
# Heroku Container Runtime は CMD をシェルでラップするため、シェル形式 CMD を使用する
CMD ASPNETCORE_URLS=http://+:${PORT:-8080} dotnet CargoTracker.Web.dll
