using System.ComponentModel.DataAnnotations;

namespace CargoTracker.Shared.Infrastructure.Auth;

/// <summary>ログインフォーム（US26）。認証失敗時はユーザー名を保持しパスワードはクリアする。</summary>
public sealed class LoginForm
{
    [Required(ErrorMessage = "ユーザー名を入力してください")]
    [Display(Name = "ユーザー名")]
    public string Username { get; set; } = string.Empty;

    [Required(ErrorMessage = "パスワードを入力してください")]
    [DataType(DataType.Password)]
    [Display(Name = "パスワード")]
    public string Password { get; set; } = string.Empty;
}
