namespace CargoTracker.Estimation.Domain.Model;

/// <summary>貨物種別（Booking Context と共通の値）。</summary>
public enum CargoType
{
    General,
    Hazardous,
    Refrigerated,
}

/// <summary>見積状態。</summary>
public enum EstimateStatus
{
    Created,
    Expired,
}

/// <summary>見積識別子（業務キー・UUID）。</summary>
public sealed record EstimateId(Guid Value)
{
    public static EstimateId Generate() => new(Guid.NewGuid());
}

/// <summary>
/// ルート候補（値オブジェクト）。航海番号・経由港・輸送日数・見積コストを保持する。
/// IT1 ではスタブで生成し、IT3 で外部ルーティングサービス（IExternalRoutingServicePort）に差し替える。
/// </summary>
public sealed record RouteCandidate
{
    public string VoyageNumber { get; }
    public string TransitPort { get; }
    public int TransitDays { get; }
    public decimal EstimatedCost { get; }

    public RouteCandidate(string voyageNumber, string transitPort, int transitDays, decimal estimatedCost)
    {
        if (string.IsNullOrWhiteSpace(voyageNumber))
        {
            throw new ArgumentException("航海番号は必須です。", nameof(voyageNumber));
        }
        if (transitDays <= 0)
        {
            throw new ArgumentException("輸送日数は正の値でなければなりません。", nameof(transitDays));
        }
        if (estimatedCost <= 0)
        {
            throw new ArgumentException("見積コストは正の値でなければなりません。", nameof(estimatedCost));
        }
        VoyageNumber = voyageNumber;
        TransitPort = transitPort;
        TransitDays = transitDays;
        EstimatedCost = estimatedCost;
    }
}
