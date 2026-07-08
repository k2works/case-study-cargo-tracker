using CargoTracker.Shared.Domain.Model;
using CargoTracker.Shipper.Domain.Model.Events;

namespace CargoTracker.Shipper.Domain.Model;

/// <summary>
/// 荷主集約ルート（US02/US03）。個人・法人を種別で判別する単一集約。
/// data-model の単一 shipper テーブルに対応し、法人のみ契約番号・割引率を保持する。
/// </summary>
public sealed class Shipper : AggregateRoot
{
    public ShipperId Id { get; }
    public ShipperCode Code { get; }
    public ShipperName Name { get; private set; }
    public Email Email { get; private set; }
    public Phone? Phone { get; private set; }
    public Address? Address { get; private set; }
    public ShipperType Type { get; }
    public ContractNumber? ContractNumber { get; }
    public DiscountRate DiscountRate { get; }
    public long Version { get; }

    private Shipper(
        ShipperId id, ShipperCode code, ShipperName name, Email email, Phone? phone, Address? address,
        ShipperType type, ContractNumber? contractNumber, DiscountRate discountRate, long version)
    {
        Id = id;
        Code = code;
        Name = name;
        Email = email;
        Phone = phone;
        Address = address;
        Type = type;
        ContractNumber = contractNumber;
        DiscountRate = discountRate;
        Version = version;
    }

    /// <summary>個人荷主を登録する。割引の対象外（割引率 0）。</summary>
    public static Shipper RegisterIndividual(ShipperName name, Email email, Phone? phone, Address? address = null)
    {
        var shipper = new Shipper(
            ShipperId.New(), ShipperCode.Generate(), name, email, phone, address,
            ShipperType.Individual, null, DiscountRate.None, 0);
        shipper.AddDomainEvent(new ShipperRegisteredEvent(shipper.Id, shipper.Type));
        return shipper;
    }

    /// <summary>法人荷主を登録する。契約番号と割引率（0〜30%）が必須。</summary>
    public static Shipper RegisterCorporate(
        ShipperName name, Email email, Phone? phone, ContractNumber contractNumber, DiscountRate discountRate,
        Address? address = null)
    {
        var shipper = new Shipper(
            ShipperId.New(), ShipperCode.Generate(), name, email, phone, address,
            ShipperType.Corporate, contractNumber, discountRate, 0);
        shipper.AddDomainEvent(new ShipperRegisteredEvent(shipper.Id, shipper.Type));
        return shipper;
    }

    /// <summary>永続化データから集約を再構築する（イベントは発生させない）。</summary>
    public static Shipper Reconstruct(
        ShipperId id, ShipperCode code, ShipperName name, Email email, Phone? phone, Address? address,
        ShipperType type, ContractNumber? contractNumber, DiscountRate discountRate, long version)
        => new(id, code, name, email, phone, address, type, contractNumber, discountRate, version);
}
