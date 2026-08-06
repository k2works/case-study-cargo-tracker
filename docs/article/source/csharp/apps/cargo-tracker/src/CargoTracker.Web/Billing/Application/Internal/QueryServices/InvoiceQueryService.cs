using CargoTracker.Shared.Application.Persistence;
using Dapper;

namespace CargoTracker.Billing.Application.Internal.QueryServices;

/// <summary>精算明細 1 行（読取・US22 割引根拠）。</summary>
public sealed class InvoiceLineItemView
{
    public string Description { get; set; } = string.Empty;
    public long AmountValue { get; set; }
    public string AmountCurrency { get; set; } = string.Empty;
}

/// <summary>精算書一覧の 1 件（読取・US21/US23）。</summary>
public sealed class InvoiceSummaryView
{
    public string InvoiceNumber { get; set; } = string.Empty;
    public string BookingId { get; set; } = string.Empty;
    public long FinalAmountValue { get; set; }
    public string FinalAmountCurrency { get; set; } = string.Empty;
    public string PaymentStatus { get; set; } = string.Empty;
    public DateTime DueDate { get; set; }
}

/// <summary>精算書詳細（読取・US22/US23）。</summary>
public sealed class InvoiceDetailView
{
    public string InvoiceNumber { get; set; } = string.Empty;
    public string BookingId { get; set; } = string.Empty;
    public string ShipperType { get; set; } = string.Empty;
    public long BaseAmountValue { get; set; }
    public decimal DiscountRate { get; set; }
    public long FinalAmountValue { get; set; }
    public string Currency { get; set; } = string.Empty;
    public string PaymentStatus { get; set; } = string.Empty;
    public DateTime IssuedAt { get; set; }
    public DateTime DueDate { get; set; }
    public DateTime? PaidAt { get; set; }
    public IReadOnlyList<InvoiceLineItemView> LineItems { get; set; } = [];
}

/// <summary>精算書の読取サービス（US21-23）。一覧・詳細を取得する。</summary>
public sealed class InvoiceQueryService(IDbConnectionFactory connectionFactory)
{
    public async Task<IReadOnlyList<InvoiceSummaryView>> ListAsync(CancellationToken ct = default)
    {
        using var connection = connectionFactory.Create();
        var rows = await connection.QueryAsync<InvoiceSummaryView>(new CommandDefinition(
            """
            SELECT invoice_number AS InvoiceNumber, booking_id AS BookingId,
                   final_amount_value AS FinalAmountValue, final_amount_currency AS FinalAmountCurrency,
                   payment_status AS PaymentStatus, due_date AS DueDate
            FROM invoice ORDER BY id DESC
            """, cancellationToken: ct));
        return rows.ToList();
    }

    public async Task<InvoiceDetailView?> FindByInvoiceNumberAsync(string invoiceNumber, CancellationToken ct = default)
    {
        using var connection = connectionFactory.Create();
        var detail = await connection.QuerySingleOrDefaultAsync<InvoiceDetailView>(new CommandDefinition(
            """
            SELECT invoice_number AS InvoiceNumber, booking_id AS BookingId, shipper_type AS ShipperType,
                   base_amount_value AS BaseAmountValue, discount_rate AS DiscountRate,
                   final_amount_value AS FinalAmountValue, final_amount_currency AS Currency,
                   payment_status AS PaymentStatus, issued_at AS IssuedAt, due_date AS DueDate, paid_at AS PaidAt
            FROM invoice WHERE invoice_number = @InvoiceNumber
            """,
            new { InvoiceNumber = invoiceNumber }, cancellationToken: ct));
        if (detail is null)
        {
            return null;
        }

        detail.LineItems = (await connection.QueryAsync<InvoiceLineItemView>(new CommandDefinition(
            """
            SELECT description AS Description, amount_value AS AmountValue, amount_currency AS AmountCurrency
            FROM invoice_line_item
            WHERE invoice_id = (SELECT id FROM invoice WHERE invoice_number = @InvoiceNumber)
            ORDER BY seq_number
            """,
            new { InvoiceNumber = invoiceNumber }, cancellationToken: ct))).ToList();
        return detail;
    }
}
