using System.Data;

namespace CargoTracker.Shared.Application.Persistence;

/// <summary>
/// DB 接続を生成する出力ポート（ADR-0001）。アプリケーション層はこのポートに依存し、
/// 具象アダプター（Infrastructure）は DI で結線する。
/// </summary>
public interface IDbConnectionFactory
{
    IDbConnection Create();
}
