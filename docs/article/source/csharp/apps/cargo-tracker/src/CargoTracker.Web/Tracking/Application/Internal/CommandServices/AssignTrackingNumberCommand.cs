namespace CargoTracker.Tracking.Application.Internal.CommandServices;

/// <summary>確定した予約に追跡番号を発行するコマンド（US14）。予約番号を指定する。</summary>
public sealed record AssignTrackingNumberCommand(string BookingId);
