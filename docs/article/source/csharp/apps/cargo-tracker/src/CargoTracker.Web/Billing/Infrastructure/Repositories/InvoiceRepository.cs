using System.Data;
using CargoTracker.Billing.Domain.Model;
using CargoTracker.Billing.Domain.Repositories;
using CargoTracker.Shared.Application.Persistence;
using CargoTracker.Shared.Infrastructure.Persistence;
using Dapper;

namespace CargoTracker.Billing.Infrastructure.Repositories;

/// <summary>
/// Dapper による精算書リポジトリ（US21-23）。ヘッダを upsert し、割引根拠の明細（invoice_line_item）を
/// 全削除→再挿入で保存する（ADR-0001 の集約永続化パターン）。金額は最小通貨単位 + 通貨で保持。
/// </summary>
public sealed class InvoiceRepository(IDbConnectionFactory connectionFactory, AmbientTransaction ambient)
    : IInvoiceRepository
{
    public async Task SaveAsync(Invoice invoice, CancellationToken ct = default)
    {
        var now = DatabaseTimestamp.ToDatabase(DateTimeOffset.UtcNow);
        var tx = ambient.Require();
        var connection = tx.Connection!;

        var existingId = await connection.ExecuteScalarAsync<long?>(new CommandDefinition(
            "SELECT id FROM invoice WHERE booking_id = @BookingId",
            new { BookingId = invoice.BookingId.Value }, tx, cancellationToken: ct));

        long invoiceId;
        var parameters = new
        {
            InvoiceNumber = invoice.InvoiceId.Value,
            BookingId = invoice.BookingId.Value,
            ShipperId = invoice.ShipperId.Value,
            ShipperType = invoice.ShipperId.ShipperType,
            BaseValue = invoice.BaseAmount.Amount,
            BaseCurrency = invoice.BaseAmount.Currency,
            DiscountRate = invoice.DiscountRate.Value,
            FinalValue = invoice.FinalAmount.Amount,
            FinalCurrency = invoice.FinalAmount.Currency,
            PaymentStatus = EnumDbCodec.ToScreamingSnake(invoice.PaymentStatus),
            IssuedAt = DatabaseTimestamp.ToDatabase(invoice.IssuedAt),
            DueDate = DateOnly.FromDateTime(invoice.DueDate.UtcDateTime),
            PaidAt = invoice.PaidAt is { } p ? DatabaseTimestamp.ToDatabase(p) : (DateTime?)null,
            invoice.Version,
            Now = now,
        };

        if (existingId is null)
        {
            await connection.ExecuteAsync(new CommandDefinition(
                """
                INSERT INTO invoice
                    (invoice_number, booking_id, shipper_id, shipper_type,
                     base_amount_value, base_amount_currency, discount_rate,
                     final_amount_value, final_amount_currency, payment_status,
                     issued_at, due_date, paid_at, version, created_at, updated_at)
                VALUES
                    (@InvoiceNumber, @BookingId, @ShipperId, @ShipperType,
                     @BaseValue, @BaseCurrency, @DiscountRate,
                     @FinalValue, @FinalCurrency, @PaymentStatus,
                     @IssuedAt, @DueDate, @PaidAt, @Version, @Now, @Now)
                """,
                parameters, tx, cancellationToken: ct));
            invoiceId = await connection.ExecuteScalarAsync<long>(new CommandDefinition(
                "SELECT id FROM invoice WHERE booking_id = @BookingId",
                new { BookingId = invoice.BookingId.Value }, tx, cancellationToken: ct));
        }
        else
        {
            invoiceId = existingId.Value;
            // 楽観ロック（CargoRepository と同方針）: 集約が Version をインクリメント済みのため
            // WHERE は更新前 version、SET は新 version。影響行数 0 は並行更新競合として扱う。
            var affectedRows = await connection.ExecuteAsync(new CommandDefinition(
                """
                UPDATE invoice SET
                    base_amount_value = @BaseValue, base_amount_currency = @BaseCurrency,
                    discount_rate = @DiscountRate,
                    final_amount_value = @FinalValue, final_amount_currency = @FinalCurrency,
                    payment_status = @PaymentStatus, paid_at = @PaidAt,
                    version = @Version, updated_at = @Now
                WHERE id = @Id AND version = @ExpectedVersion
                """,
                new
                {
                    parameters.BaseValue,
                    parameters.BaseCurrency,
                    parameters.DiscountRate,
                    parameters.FinalValue,
                    parameters.FinalCurrency,
                    parameters.PaymentStatus,
                    parameters.PaidAt,
                    invoice.Version,
                    ExpectedVersion = invoice.Version - 1,
                    parameters.Now,
                    Id = invoiceId,
                },
                tx, cancellationToken: ct));
            if (affectedRows == 0)
            {
                throw new InvalidOperationException("精算書が並行更新されたため、保存できませんでした。");
            }
        }

        // 割引根拠の明細（invoice_line_item）を全削除→再挿入。基本料金と割引額を明細化する（US22 AC4）。
        await connection.ExecuteAsync(new CommandDefinition(
            "DELETE FROM invoice_line_item WHERE invoice_id = @Id", new { Id = invoiceId }, tx, cancellationToken: ct));

        await InsertLineItemAsync(connection, tx, invoiceId, 1, "輸送基本料金",
            invoice.BaseAmount, now, ct);
        var discount = invoice.DiscountAmount();
        if (discount.Amount > 0)
        {
            await InsertLineItemAsync(connection, tx, invoiceId, 2,
                $"法人割引（{invoice.DiscountRate.Value:P0}）", discount, now, ct);
        }
    }

    private static Task<int> InsertLineItemAsync(
        IDbConnection connection, IDbTransaction tx, long invoiceId, int seq,
        string description, Money amount, DateTime now, CancellationToken ct)
        => connection.ExecuteAsync(new CommandDefinition(
            """
            INSERT INTO invoice_line_item
                (invoice_id, description, amount_value, amount_currency, seq_number, created_at, updated_at)
            VALUES
                (@InvoiceId, @Description, @Value, @Currency, @Seq, @Now, @Now)
            """,
            new { InvoiceId = invoiceId, Description = description, Value = amount.Amount, Currency = amount.Currency, Seq = seq, Now = now },
            tx, cancellationToken: ct));

    public Task<Invoice?> FindByBookingIdAsync(string bookingId, CancellationToken ct = default)
        => QueryAsync("booking_id", bookingId, ct);

    public Task<Invoice?> FindByInvoiceNumberAsync(string invoiceNumber, CancellationToken ct = default)
        => QueryAsync("invoice_number", invoiceNumber, ct);

    public async Task<bool> ExistsForBookingAsync(string bookingId, CancellationToken ct = default)
    {
        using var connection = connectionFactory.Create();
        var count = await connection.ExecuteScalarAsync<long>(new CommandDefinition(
            "SELECT COUNT(*) FROM invoice WHERE booking_id = @BookingId",
            new { BookingId = bookingId }, cancellationToken: ct));
        return count > 0;
    }

    private async Task<Invoice?> QueryAsync(string column, string value, CancellationToken ct)
    {
        if (ambient.Current is not null)
        {
            return await LoadAsync(column, value, ambient.Current.Connection!, ambient.Current, ct);
        }
        using var connection = connectionFactory.Create();
        return await LoadAsync(column, value, connection, null, ct);
    }

    private static async Task<Invoice?> LoadAsync(
        string column, string value, IDbConnection connection, IDbTransaction? tx, CancellationToken ct)
    {
        var row = await connection.QuerySingleOrDefaultAsync<Row>(new CommandDefinition(
            $"""
            SELECT invoice_number AS InvoiceNumber, booking_id AS BookingId,
                   shipper_id AS ShipperId, shipper_type AS ShipperType,
                   base_amount_value AS BaseValue, base_amount_currency AS BaseCurrency,
                   discount_rate AS DiscountRate,
                   final_amount_value AS FinalValue, final_amount_currency AS FinalCurrency,
                   payment_status AS PaymentStatus, issued_at AS IssuedAt, due_date AS DueDate,
                   paid_at AS PaidAt, version AS Version
            FROM invoice WHERE {column} = @Value
            """,
            new { Value = value }, tx, cancellationToken: ct));
        return row?.ToInvoice();
    }

    private sealed class Row
    {
        public string InvoiceNumber { get; set; } = string.Empty;
        public string BookingId { get; set; } = string.Empty;
        public string ShipperId { get; set; } = string.Empty;
        public string ShipperType { get; set; } = string.Empty;
        public long BaseValue { get; set; }
        public string BaseCurrency { get; set; } = string.Empty;
        public decimal DiscountRate { get; set; }
        public long FinalValue { get; set; }
        public string FinalCurrency { get; set; } = string.Empty;
        public string PaymentStatus { get; set; } = string.Empty;
        public DateTime IssuedAt { get; set; }
        // due_date は DATE 列（PostgreSQL は DateOnly、SQLite は文字列 → DateOnly として受ける）。
        public DateOnly DueDate { get; set; }
        public DateTime? PaidAt { get; set; }
        public long Version { get; set; }

        public Invoice ToInvoice() => Invoice.Reconstruct(
            new InvoiceId(InvoiceNumber),
            new BillingBookingId(BookingId),
            new BillingShipperId(ShipperId, ShipperType),
            new Money(BaseValue, BaseCurrency),
            new DiscountRate(DiscountRate),
            new Money(FinalValue, FinalCurrency),
            EnumDbCodec.FromScreamingSnake<PaymentStatus>(PaymentStatus),
            new DateTimeOffset(DateTime.SpecifyKind(IssuedAt, DateTimeKind.Utc)),
            new DateTimeOffset(DueDate.ToDateTime(TimeOnly.MinValue), TimeSpan.Zero),
            PaidAt is { } p ? new DateTimeOffset(DateTime.SpecifyKind(p, DateTimeKind.Utc)) : null,
            Version);
    }
}
