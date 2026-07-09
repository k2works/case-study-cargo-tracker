using CargoTracker.Shared.Application.Persistence;
using CargoTracker.Shared.Domain.Model;
using CargoTracker.Shipper.Domain;
using CargoTracker.Shipper.Domain.Model;
using CargoTracker.Shipper.Domain.Repositories;

namespace CargoTracker.Shipper.Application.Internal.CommandServices;

/// <summary>
/// 荷主登録ユースケース（US02/US03）。メール重複を検証し、種別に応じて集約を生成、
/// UoW のトランザクション内で永続化してコミット後にドメインイベントを発行する（ADR-0002）。
/// </summary>
public sealed class RegisterShipperCommandService(
    IUnitOfWorkFactory unitOfWorkFactory,
    IShipperRepository repository)
{
    public async Task<ShipperId> HandleAsync(RegisterShipperCommand command, CancellationToken ct = default)
    {
        await using var unitOfWork = unitOfWorkFactory.Begin();

        if (await repository.ExistsByEmailAsync(command.Email, ct))
        {
            throw new EmailAlreadyRegisteredException(command.Email);
        }

        var shipper = BuildShipper(command);
        unitOfWork.Track(shipper);
        await repository.SaveAsync(shipper, ct);
        await unitOfWork.CommitAsync(ct);

        return shipper.Id;
    }

    private static Domain.Model.Shipper BuildShipper(RegisterShipperCommand command)
    {
        var name = new ShipperName(command.Name);
        var email = new Email(command.Email);
        var phone = string.IsNullOrWhiteSpace(command.Phone) ? null : new Phone(command.Phone);
        var address = string.IsNullOrWhiteSpace(command.Address) ? null : new Address(command.Address);

        if (!command.IsCorporate)
        {
            return Domain.Model.Shipper.RegisterIndividual(name, email, phone, address);
        }

        if (string.IsNullOrWhiteSpace(command.ContractNumber))
        {
            throw new ArgumentException("法人荷主には契約番号が必要です。", nameof(command));
        }

        var discountRate = new DiscountRate(command.DiscountRate ?? 0m);
        return Domain.Model.Shipper.RegisterCorporate(name, email, phone, new ContractNumber(command.ContractNumber), discountRate, address);
    }
}
