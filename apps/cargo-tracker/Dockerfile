# CargoTracker.Web 本番用イメージ（マルチステージ）。
# ビルド: .NET SDK でソリューションを publish。実行: ASP.NET ランタイム + マイグレーションスクリプト。
# ビルドコンテキストは apps/cargo-tracker を想定する。

# ---- build stage ----
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

# Web プロジェクトと参照先（src 配下）をコピーして publish する（tests は不要）。
COPY src/ ./src/

RUN dotnet restore src/CargoTracker.Web/CargoTracker.Web.fsproj \
    && dotnet publish src/CargoTracker.Web/CargoTracker.Web.fsproj \
        -c Release -o /app/publish --no-restore

# ---- runtime stage ----
FROM mcr.microsoft.com/dotnet/aspnet:10.0 AS runtime
WORKDIR /app

# ヘルスチェック用に curl を導入する。
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# 公開成果物とマイグレーションスクリプト（PostgreSQL 方言を含む db/scripts 一式）を配置。
COPY --from=build /app/publish ./
COPY db/scripts /app/db/scripts

# 本番既定の環境変数（接続文字列・管理者認証情報は compose / secret で上書きする）。
ENV ASPNETCORE_ENVIRONMENT=Production \
    ASPNETCORE_URLS=http://0.0.0.0:8080 \
    Database__Provider=postgresql \
    Database__ScriptsRoot=/app/db/scripts

EXPOSE 8080

# 非 root 実行（aspnet イメージには app ユーザーが存在する）。
USER app

ENTRYPOINT ["dotnet", "CargoTracker.Web.dll"]
