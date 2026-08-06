using System.Data;
using System.Globalization;
using System.Runtime.CompilerServices;
using Dapper;

namespace CargoTracker.Shared.Infrastructure.Persistence;

/// <summary>
/// Dapper の共通設定。DateOnly は Dapper 標準ではパラメータに使えないため TypeHandler を登録する。
/// [ModuleInitializer] によりアセンブリ読み込み時に自動適用され、アプリ・テスト双方で有効になる。
/// </summary>
public static class DapperConfiguration
{
    [ModuleInitializer]
    public static void Initialize()
    {
        SqlMapper.AddTypeHandler(new DateOnlyTypeHandler());
        SqlMapper.AddTypeHandler(new GuidTypeHandler());
    }

    private sealed class GuidTypeHandler : SqlMapper.TypeHandler<Guid>
    {
        // 書き込みは Guid のまま渡す（PostgreSQL は uuid ネイティブ、SQLite は TEXT に保存）。
        public override void SetValue(IDbDataParameter parameter, Guid value) => parameter.Value = value;

        // 読み取りは SQLite が TEXT で返すため string→Guid を許容する（ADR-0003 二方言差異）。
        public override Guid Parse(object value) => value is Guid guid
            ? guid
            : Guid.Parse((string)value);
    }

    private sealed class DateOnlyTypeHandler : SqlMapper.TypeHandler<DateOnly>
    {
        public override void SetValue(IDbDataParameter parameter, DateOnly value)
        {
            // PostgreSQL の date は DateTime を要求し、SQLite は TEXT として保存する。
            // 深夜の DateTime + DbType.Date で双方に対応する（ADR-0003）。
            parameter.DbType = DbType.Date;
            parameter.Value = value.ToDateTime(TimeOnly.MinValue);
        }

        public override DateOnly Parse(object value) => value switch
        {
            DateOnly dateOnly => dateOnly,
            DateTime dateTime => DateOnly.FromDateTime(dateTime),
            // SQLite は TEXT（"2026-09-30" または時刻付き）で返すため DateTime.Parse で頑健に受ける。
            string text => DateOnly.FromDateTime(DateTime.Parse(text, CultureInfo.InvariantCulture)),
            _ => DateOnly.FromDateTime(Convert.ToDateTime(value, CultureInfo.InvariantCulture)),
        };
    }
}
