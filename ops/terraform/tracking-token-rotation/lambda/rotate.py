"""
AWS Secrets Manager Rotation Lambda（IT9 A2.3 / ADR-0021 / US27）。

trackingms 公開追跡照会 JWT 署名 secret の自動回転を担う。AWS Secrets Manager の標準
4 ステップローテーション（createSecret / setSecret / testSecret / finishSecret）を実装し、
四半期 (90 日) ごとに新しい 256bit 相当の英数字シークレットを生成して AWSCURRENT に昇格させる。

trackingms 側の AwsSecretsManagerTrackingTokenSecretProvider は @Scheduled で 5 分ごとに
refresh するため、本 Lambda が AWSCURRENT を更新してから最大 5 分後に新シークレットでの
発行が開始される。既存トークンは AWSPREVIOUS でも検証されるため、ローテーション期間中の
検証連続性も担保される（最大トークン有効期間 + 5 分のグレース）。

参照: AWS Secrets Manager Rotation Lambda ベース実装
https://github.com/aws-samples/aws-secrets-manager-rotation-lambdas
"""

import logging
import secrets
import string

import boto3

logger = logging.getLogger()
logger.setLevel(logging.INFO)

# 32 バイト以上必須（StaticTrackingTokenSecretProvider / AwsSecretsManagerTrackingTokenSecretProvider 共通）
SECRET_LENGTH = 48
ALPHABET = string.ascii_letters + string.digits


def handler(event, context):
    """Secrets Manager Rotation Lambda エントリポイント。"""
    arn = event["SecretId"]
    token = event["ClientRequestToken"]
    step = event["Step"]

    client = boto3.client("secretsmanager")
    metadata = client.describe_secret(SecretId=arn)
    if not metadata.get("RotationEnabled"):
        raise ValueError(f"Secret {arn} は rotation 設定がありません")

    versions = metadata.get("VersionIdsToStages", {})
    if token not in versions:
        raise ValueError(f"Token {token} は secret {arn} のいずれの段階にも紐付いていません")
    if "AWSCURRENT" in versions[token]:
        logger.info("Token %s は既に AWSCURRENT、処理スキップ", token)
        return
    if "AWSPENDING" not in versions[token]:
        raise ValueError(f"Token {token} は AWSPENDING 段階ではありません")

    if step == "createSecret":
        _create_secret(client, arn, token)
    elif step == "setSecret":
        # 外部システムへの伝搬は trackingms 側の @Scheduled refresh が担うため no-op
        logger.info("setSecret: 外部更新は trackingms refresh で行うため no-op")
    elif step == "testSecret":
        _test_secret(client, arn, token)
    elif step == "finishSecret":
        _finish_secret(client, arn, token)
    else:
        raise ValueError(f"未知の rotation step: {step}")


def _create_secret(client, arn: str, token: str) -> None:
    """AWSPENDING に新シークレットを生成する。既に存在する場合はスキップ（冪等性）。"""
    try:
        client.get_secret_value(
            SecretId=arn, VersionId=token, VersionStage="AWSPENDING"
        )
        logger.info("AWSPENDING token=%s は既存、生成スキップ", token)
    except client.exceptions.ResourceNotFoundException:
        new_secret = "".join(secrets.choice(ALPHABET) for _ in range(SECRET_LENGTH))
        client.put_secret_value(
            SecretId=arn,
            ClientRequestToken=token,
            SecretString=new_secret,
            VersionStages=["AWSPENDING"],
        )
        logger.info("AWSPENDING secret 生成完了: arn=%s, length=%d", arn, len(new_secret))


def _test_secret(client, arn: str, token: str) -> None:
    """AWSPENDING シークレットの妥当性を検証する（32 バイト以上）。"""
    response = client.get_secret_value(
        SecretId=arn, VersionId=token, VersionStage="AWSPENDING"
    )
    value = response.get("SecretString", "")
    if len(value.encode("utf-8")) < 32:
        raise ValueError(
            f"AWSPENDING secret が 32 バイト未満です (現在: {len(value.encode('utf-8'))})"
        )
    logger.info("AWSPENDING secret 検証成功: arn=%s", arn)


def _finish_secret(client, arn: str, token: str) -> None:
    """AWSPENDING を AWSCURRENT に昇格させ、旧 AWSCURRENT を AWSPREVIOUS に降格させる。"""
    metadata = client.describe_secret(SecretId=arn)
    current_version = None
    for version_id, stages in metadata["VersionIdsToStages"].items():
        if "AWSCURRENT" in stages:
            current_version = version_id
            break

    client.update_secret_version_stage(
        SecretId=arn,
        VersionStage="AWSCURRENT",
        MoveToVersionId=token,
        RemoveFromVersionId=current_version,
    )
    logger.info(
        "AWSCURRENT 昇格完了: arn=%s, new=%s, previous=%s",
        arn,
        token,
        current_version,
    )
