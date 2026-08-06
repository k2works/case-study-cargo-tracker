namespace CargoTracker.Shipper.Domain;

/// <summary>同一メールアドレスの荷主が既に登録されている場合の例外（domain-model 規則 2）。</summary>
public sealed class EmailAlreadyRegisteredException(string email)
    : Exception($"メールアドレス {email} は既に登録されています。")
{
    public string Email { get; } = email;
}
