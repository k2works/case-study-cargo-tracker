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

# ヘルスチェック用に curl を導入
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/publish .

ENV ASPNETCORE_URLS=http://+:8080
EXPOSE 8080

# ヘルスチェック（ASP.NET Core Health Checks /health）
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -fsS http://localhost:8080/health || exit 1

ENTRYPOINT ["dotnet", "CargoTracker.Web.dll"]
