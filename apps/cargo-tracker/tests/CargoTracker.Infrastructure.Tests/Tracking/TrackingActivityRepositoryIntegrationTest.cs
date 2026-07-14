using CargoTracker.Shared.Infrastructure.Persistence;
using CargoTracker.Tracking.Application.Internal.CommandServices;
using CargoTracker.Tracking.Domain.Events;
using CargoTracker.Tracking.Domain.Model;
using CargoTracker.Tracking.Infrastructure.Repositories;
using FluentAssertions;
using MediatR;
using Moq;
using Testcontainers.PostgreSql;

namespace CargoTracker.Infrastructure.Tests.Tracking;

public sealed class TrackingActivityRepositoryIntegrationTest : IAsyncLifetime
{
    private readonly PostgreSqlContainer _postgres = new PostgreSqlBuilder("postgres:16-alpine").Build();
    private readonly Mock<IPublisher> _publisher = new();
    private DbConnectionFactory _connectionFactory = null!;
    private TrackingActivityRepository _repository = null!;
    private AssignTrackingNumberCommandService _commandService = null!;
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
        _repository = new TrackingActivityRepository(_connectionFactory, _ambient);
        _commandService = new AssignTrackingNumberCommandService(
            new UnitOfWorkFactory(_connectionFactory, _publisher.Object, _ambient), _repository);
    }

    public Task DisposeAsync() => _postgres.DisposeAsync().AsTask();

    [Fact]
    public async Task 追跡番号を発行すると受領待ちで保存され再構築される()
    {
        var trackingNumber = await _commandService.HandleAsync(new AssignTrackingNumberCommand("BKG-TRK-0001"));

        trackingNumber.Should().NotBeNull();
        trackingNumber!.Value.Should().Be("TRK-TRK-0001");

        var tracking = await _repository.FindByBookingIdAsync("BKG-TRK-0001");
        tracking.Should().NotBeNull();
        tracking!.TrackingNumber.Value.Should().Be("TRK-TRK-0001");
        tracking.CurrentStatus().Should().Be(TrackingStatus.NotReceived);
    }

    [Fact]
    public async Task 既に発行済みの予約には再発行しない()
    {
        await _commandService.HandleAsync(new AssignTrackingNumberCommand("BKG-TRK-0002"));

        var second = await _commandService.HandleAsync(new AssignTrackingNumberCommand("BKG-TRK-0002"));

        second.Should().BeNull();
        (await _repository.ExistsForBookingAsync("BKG-TRK-0002")).Should().BeTrue();
    }

    [Fact]
    public async Task 追跡イベントを追記すると状態が更新され往復保存される()
    {
        await _commandService.HandleAsync(new AssignTrackingNumberCommand("BKG-TRK-0003"));

        var tracking = await _repository.FindByBookingIdAsync("BKG-TRK-0003");
        tracking!.AddEvent(new TrackingActivityEvent(
            TrackingEventType.Receive, new TrackingLocation("JPTYO"), new DateTimeOffset(2026, 9, 1, 0, 0, 0, TimeSpan.Zero)));
        tracking.AddEvent(new TrackingActivityEvent(
            TrackingEventType.Load, new TrackingLocation("JPTYO"), new DateTimeOffset(2026, 9, 2, 0, 0, 0, TimeSpan.Zero), new TrackingVoyageNumber("V001")));
        await using (var uow = new UnitOfWorkFactory(_connectionFactory, _publisher.Object, _ambient).Begin())
        {
            await _repository.SaveAsync(tracking);
            await uow.CommitAsync();
        }

        var reloaded = await _repository.FindByBookingIdAsync("BKG-TRK-0003");
        reloaded!.Events.Should().HaveCount(2);
        reloaded.CurrentStatus().Should().Be(TrackingStatus.Loaded);
        reloaded.Events[1].VoyageNumber!.Number.Should().Be("V001");
    }

    [Fact]
    public async Task 例外を登録し解決すると往復保存され状態が復帰する()
    {
        await _commandService.HandleAsync(new AssignTrackingNumberCommand("BKG-TRK-EX01"));
        var tracking = await _repository.FindByBookingIdAsync("BKG-TRK-EX01");
        tracking!.AddEvent(new TrackingActivityEvent(
            TrackingEventType.Load, new TrackingLocation("JPTYO"), new DateTimeOffset(2026, 10, 1, 0, 0, 0, TimeSpan.Zero)));
        tracking.AddException(new TrackingExceptionEvent(
            ExceptionType.Lost, new TrackingLocation("USLAX"),
            new DateTimeOffset(2026, 10, 8, 14, 0, 0, TimeSpan.Zero), "紛失の疑い"));
        await using (var uow = new UnitOfWorkFactory(_connectionFactory, _publisher.Object, _ambient).Begin())
        {
            await _repository.SaveAsync(tracking);
            await uow.CommitAsync();
        }

        // 例外登録後は Exception 状態・エスカレーションフラグが往復保存される。
        var afterRegister = await _repository.FindByBookingIdAsync("BKG-TRK-EX01");
        afterRegister!.CurrentStatus().Should().Be(TrackingStatus.Exception);
        afterRegister.HasActiveException().Should().BeTrue();
        afterRegister.Exceptions.Should().ContainSingle()
            .Which.EscalationFlag.Should().BeTrue();

        // 解決すると例外発生前（Loaded）へ復帰し、resolved_at/notes が保存される。
        afterRegister.ResolveException(
            new DateTimeOffset(2026, 10, 9, 0, 0, 0, TimeSpan.Zero), "代替便を手配");
        await using (var uow = new UnitOfWorkFactory(_connectionFactory, _publisher.Object, _ambient).Begin())
        {
            await _repository.SaveAsync(afterRegister);
            await uow.CommitAsync();
        }

        var afterResolve = await _repository.FindByBookingIdAsync("BKG-TRK-EX01");
        afterResolve!.HasActiveException().Should().BeFalse();
        afterResolve.CurrentStatus().Should().Be(TrackingStatus.Loaded);
        afterResolve.Exceptions[^1].ResolutionNotes.Should().Be("代替便を手配");
    }

    [Fact]
    public async Task 追跡管理者が手動で状態を追記できる()
    {
        await _commandService.HandleAsync(new AssignTrackingNumberCommand("BKG-TRK-0004"));
        var trackingNumber = "TRK-TRK-0004";
        var addService = new AddTrackingEventCommandService(
            new UnitOfWorkFactory(_connectionFactory, _publisher.Object, _ambient), _repository);

        await addService.HandleAsync(new AddTrackingEventCommand(
            trackingNumber, "Unload", "SGSIN", new DateTimeOffset(2026, 9, 15, 0, 0, 0, TimeSpan.Zero)));

        var tracking = await _repository.FindByTrackingNumberAsync(trackingNumber);
        tracking!.Events.Should().ContainSingle();
        tracking.CurrentStatus().Should().Be(TrackingStatus.Unloaded);
    }

    [Fact]
    public async Task 存在しない追跡番号への手動追記は拒否される()
    {
        var addService = new AddTrackingEventCommandService(
            new UnitOfWorkFactory(_connectionFactory, _publisher.Object, _ambient), _repository);

        var act = () => addService.HandleAsync(new AddTrackingEventCommand(
            "TRK-NOT-EXIST", "Receive", "JPTYO", DateTimeOffset.UtcNow));

        await act.Should().ThrowAsync<InvalidOperationException>();
    }

    [Fact]
    public async Task 例外登録で追跡例外検知イベントがpostcommitで発行される()
    {
        await _commandService.HandleAsync(new AssignTrackingNumberCommand("BKG-TRK-EV01"));
        var registerService = new RegisterExceptionCommandService(
            new UnitOfWorkFactory(_connectionFactory, _publisher.Object, _ambient), _repository);

        await registerService.HandleAsync(new RegisterExceptionCommand(
            "TRK-TRK-EV01", "Lost", "USLAX",
            new DateTimeOffset(2026, 10, 8, 14, 0, 0, TimeSpan.Zero), "紛失の疑い"));

        // post-commit で TrackingExceptionDetectedEvent（エスカレーションフラグ付き）が発行される。
        _publisher.Verify(p => p.Publish(
            It.Is<INotification>(n => (n as TrackingExceptionDetectedEvent) != null
                && ((TrackingExceptionDetectedEvent)n).BookingId == "BKG-TRK-EV01"
                && ((TrackingExceptionDetectedEvent)n).EscalationFlag
                && ((TrackingExceptionDetectedEvent)n).ExceptionType == "LOST"),
            It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task 税関保留の手動登録は拒否される()
    {
        await _commandService.HandleAsync(new AssignTrackingNumberCommand("BKG-TRK-EV02"));
        var registerService = new RegisterExceptionCommandService(
            new UnitOfWorkFactory(_connectionFactory, _publisher.Object, _ambient), _repository);

        var act = () => registerService.HandleAsync(new RegisterExceptionCommand(
            "TRK-TRK-EV02", "CustomsHold", "USLAX", DateTimeOffset.UtcNow));

        await act.Should().ThrowAsync<ArgumentException>();
    }

    [Fact]
    public async Task 紛失例外の検知で荷主と管理職の通知が記録される()
    {
        var notificationRepository = new CargoTracker.Tracking.Infrastructure.Repositories.ExceptionNotificationRepository(
            _connectionFactory, _ambient);
        var handler = new CargoTracker.Tracking.Application.Internal.EventHandlers.NotifyOnTrackingExceptionDetectedHandler(
            new UnitOfWorkFactory(_connectionFactory, _publisher.Object, _ambient),
            notificationRepository,
            Microsoft.Extensions.Logging.Abstractions.NullLogger<
                CargoTracker.Tracking.Application.Internal.EventHandlers.NotifyOnTrackingExceptionDetectedHandler>.Instance);

        await handler.Handle(new TrackingExceptionDetectedEvent(
            "BKG-NOTIF-01", "TRK-NOTIF-01", "LOST", "USLAX", true,
            new DateTimeOffset(2026, 10, 8, 14, 0, 0, TimeSpan.Zero)), CancellationToken.None);

        var notifications = await notificationRepository.FindByTrackingNumberAsync("TRK-NOTIF-01");
        notifications.Should().HaveCount(2);
        notifications.Should().Contain(n => n.Recipient == NotificationRecipient.Shipper);
        notifications.Should().Contain(n => n.Recipient == NotificationRecipient.Management);
    }

    [Fact]
    public async Task 遅延例外の検知では荷主通知のみ記録される()
    {
        var notificationRepository = new CargoTracker.Tracking.Infrastructure.Repositories.ExceptionNotificationRepository(
            _connectionFactory, _ambient);
        var handler = new CargoTracker.Tracking.Application.Internal.EventHandlers.NotifyOnTrackingExceptionDetectedHandler(
            new UnitOfWorkFactory(_connectionFactory, _publisher.Object, _ambient),
            notificationRepository,
            Microsoft.Extensions.Logging.Abstractions.NullLogger<
                CargoTracker.Tracking.Application.Internal.EventHandlers.NotifyOnTrackingExceptionDetectedHandler>.Instance);

        await handler.Handle(new TrackingExceptionDetectedEvent(
            "BKG-NOTIF-02", "TRK-NOTIF-02", "DELAY", "USLAX", false,
            new DateTimeOffset(2026, 10, 8, 14, 0, 0, TimeSpan.Zero)), CancellationToken.None);

        var notifications = await notificationRepository.FindByTrackingNumberAsync("TRK-NOTIF-02");
        notifications.Should().ContainSingle()
            .Which.Recipient.Should().Be(NotificationRecipient.Shipper);
    }
}
