# frozen_string_literal: true

# 利用者認証（US26）。アカウントロック・監査ログを担う共通サービス。
class AuthenticationService
  INVALID_CREDENTIALS = "利用者 ID またはパスワードが正しくありません"
  ACCOUNT_LOCKED = "アカウントが一時ロックされています。管理者にお問い合わせください"
  ACCOUNT_DISABLED = "アカウントが無効です。管理者にお問い合わせください"

  # 認証結果を表す値オブジェクト。
  Result = Struct.new(:user, :error_message, :locked, keyword_init: true) do
    def success?
      user.present? && error_message.nil?
    end

    def locked?
      locked == true
    end
  end

  def initialize(logger: Rails.logger)
    @logger = logger
  end

  # 利用者 ID とパスワードで認証する。
  def authenticate(username, password)
    user = User.find_by(username: username)

    return failure(username) if user.nil?
    return disabled(user) unless user.active?
    return locked(user) if user.locked?

    if user.authenticate(password)
      succeed(user)
    else
      register_failure(user)
    end
  end

  private

  attr_reader :logger

  def succeed(user)
    user.reset_failures!
    logger.info("ログイン成功: #{user.username}")
    Result.new(user: user)
  end

  def register_failure(user)
    user.register_failure!
    logger.warn("ログイン失敗: #{user.username}（失敗回数 #{user.failed_attempts}）")
    return locked(user) if user.locked?

    Result.new(error_message: INVALID_CREDENTIALS)
  end

  def failure(username)
    logger.warn("ログイン失敗: #{username}（存在しない利用者）")
    Result.new(error_message: INVALID_CREDENTIALS)
  end

  def locked(user)
    logger.warn("ログイン拒否: #{user.username}（ロック済み）")
    Result.new(error_message: ACCOUNT_LOCKED, locked: true)
  end

  def disabled(user)
    logger.warn("ログイン拒否: #{user.username}（無効化済み）")
    Result.new(error_message: ACCOUNT_DISABLED)
  end
end
