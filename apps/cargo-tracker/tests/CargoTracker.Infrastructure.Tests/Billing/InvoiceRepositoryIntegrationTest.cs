using CargoTracker.Billing.Domain.Model;
using CargoTracker.Billing.Infrastructure.Repositories;
using CargoTracker.Shared.Infrastructure.Persistence;
using FluentAssertions;
using MediatR;
using Moq;
using Testcontainers.PostgreSql;

namespace CargoTracker.Infrastructure.Tests.Billing;

public sealed class InvoiceRepositoryIntegrationTest : IAsyncLifetime
{
    private readonly PostgreSqlContainer _postgres = new PostgreSqlBuilder("postgres:16-alpine").Build();
    private readonly Mock<IPublisher> _publisher = new();
    private DbConnectionFactory _connectionFactory = null!;
    private InvoiceRepository _repository = null!;
    private AmbientTransaction _ambient = null!;

    public async Task InitializeAsync()
    {
        await _postgres.StartAsync();
        DatabaseMigrator.Migrate(DatabaseProvider.Postgres, _postgres.GetConnectionString()).Successful.Should().BeTrue();

        _connectionFactory = new DbConnectionFactory(new DatabaseOptions
        {
            Provider = DatabaseProvider.Postgres,
            ConnectionString = _postgres.GetConnectionString(),
        });
        _ambient = new AmbientTransaction();
        _repository = new InvoiceRepository(_connectionFactory, _ambient);
    }

    public Task DisposeAsync() => _postgres.DisposeAsync().AsTask();

    private static Invoice IssueFor(string bookingId, string shipperType) => Invoice.Issue(
        bookingId,
        new BillingShipperId("SHP-1", shipperType),
        new Money(10000, "JPY"),
        new DateTimeOffset(2026, 10, 1, 0, 0, 0, TimeSpan.Zero),
        new DateTimeOffset(2026, 10, 31, 0, 0, 0, TimeSpan.Zero));

    private async Task SaveAsync(Invoice invoice)
    {
        await using var uow = new UnitOfWorkFactory(_connectionFactory, _publisher.Object, _ambient).Begin();
        await _repository.SaveAsync(invoice);
        await uow.CommitAsync();
    }

    [Fact]
    public async Task 精算書を発行すると往復保存される()
    {
        var invoice = IssueFor("BKG-INV-0001", "Individual");
        await SaveAsync(invoice);

        var reloaded = await _repository.FindByBookingIdAsync("BKG-INV-0001");
        reloaded.Should().NotBeNull();
        reloaded!.InvoiceId.Value.Should().Be("INV-INV-0001");
        reloaded.BaseAmount.Should().Be(new Money(10000, "JPY"));
        reloaded.FinalAmount.Should().Be(new Money(10000, "JPY"));
        reloaded.PaymentStatus.Should().Be(PaymentStatus.Pending);
    }

    [Fact]
    public async Task 法人割引と入金確認が往復保存される()
    {
        var invoice = IssueFor("BKG-INV-0002", "Corporate");
        invoice.ApplyDiscount(new DiscountRate(0.10m));
        await SaveAsync(invoice);

        var afterDiscount = await _repository.FindByInvoiceNumberAsync("INV-INV-0002");
        afterDiscount!.DiscountRate.Value.Should().Be(0.10m);
        afterDiscount.FinalAmount.Should().Be(new Money(9000, "JPY"));

        // 入金確認 → 精算済が往復保存される。
        afterDiscount.ConfirmPayment(new DateTimeOffset(2026, 10, 20, 0, 0, 0, TimeSpan.Zero));
        await SaveAsync(afterDiscount);

        var afterPayment = await _repository.FindByBookingIdAsync("BKG-INV-0002");
        afterPayment!.PaymentStatus.Should().Be(PaymentStatus.Confirmed);
        afterPayment.PaidAt.Should().NotBeNull();
    }
}
