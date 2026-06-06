/**
 * trackingms 公開追跡照会トークン secret の自動回転 IaC（IT9 A2.3 / ADR-0021 / US27）。
 *
 * 構成要素:
 *   - AWS Secrets Manager Secret（tracking/public-token、AWSCURRENT/AWSPREVIOUS 段階管理）
 *   - Lambda Function（Python 3.12、4 ステップ rotation）
 *   - Secret Rotation 設定（90 日サイクル、四半期回転）
 *   - IAM Role / Policy（最小権限：secretsmanager + CloudWatch Logs）
 *
 * 適用方法:
 *   $ cd ops/terraform/tracking-token-rotation
 *   $ terraform init
 *   $ terraform plan
 *   $ terraform apply
 *
 * trackingms 側の連携:
 *   application-heroku.yml で以下を設定する（Heroku Config Vars でも可）:
 *     tracking.public-token.provider: aws-secrets-manager
 *     tracking.public-token.aws.secret-id: tracking/public-token
 *     tracking.public-token.aws.region: ap-northeast-1
 *     tracking.public-token.refresh-rate-ms: 300000
 *
 * LocalStack 検証は A2.4 で実施。
 */

terraform {
  required_version = ">= 1.6.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.70"
    }
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.4"
    }
  }
}

provider "aws" {
  region = var.region
}

variable "region" {
  description = "AWS region"
  type        = string
  default     = "ap-northeast-1"
}

variable "secret_name" {
  description = "AWS Secrets Manager の secret 名（trackingms 設定と一致必須）"
  type        = string
  default     = "tracking/public-token"
}

variable "rotation_days" {
  description = "自動 rotation の周期（日）。四半期 = 90 日。"
  type        = number
  default     = 90
}

variable "lambda_function_name" {
  description = "Rotation Lambda Function 名"
  type        = string
  default     = "tracking-public-token-rotation"
}

# ============================================================================
# Secrets Manager Secret
# ============================================================================

resource "aws_secretsmanager_secret" "tracking_public_token" {
  name        = var.secret_name
  description = "trackingms 公開追跡照会 JWT 署名 secret（IT9 / ADR-0021 / US27）"

  recovery_window_in_days = 7

  tags = {
    Application = "trackingms"
    Component   = "public-tracking-token"
    ManagedBy   = "terraform"
    ADR         = "0021"
  }
}

# ============================================================================
# Lambda Function（Rotation）
# ============================================================================

data "archive_file" "lambda_zip" {
  type        = "zip"
  source_dir  = "${path.module}/lambda"
  output_path = "${path.module}/build/rotate.zip"
}

resource "aws_lambda_function" "rotation" {
  function_name    = var.lambda_function_name
  description      = "trackingms public-token secret rotation Lambda (ADR-0021)"
  runtime          = "python3.12"
  handler          = "rotate.handler"
  role             = aws_iam_role.lambda_exec.arn
  filename         = data.archive_file.lambda_zip.output_path
  source_code_hash = data.archive_file.lambda_zip.output_base64sha256
  timeout          = 30
  memory_size      = 128

  environment {
    variables = {
      LOG_LEVEL = "INFO"
    }
  }

  tags = {
    Application = "trackingms"
    Component   = "public-tracking-token-rotation"
    ManagedBy   = "terraform"
  }
}

resource "aws_lambda_permission" "secrets_manager_invoke" {
  statement_id  = "AllowExecutionFromSecretsManager"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.rotation.function_name
  principal     = "secretsmanager.amazonaws.com"
}

# ============================================================================
# Secret Rotation 設定
# ============================================================================

resource "aws_secretsmanager_secret_rotation" "tracking_public_token" {
  secret_id           = aws_secretsmanager_secret.tracking_public_token.id
  rotation_lambda_arn = aws_lambda_function.rotation.arn

  rotation_rules {
    automatically_after_days = var.rotation_days
  }

  depends_on = [aws_lambda_permission.secrets_manager_invoke]
}

# ============================================================================
# IAM Role / Policy（最小権限）
# ============================================================================

data "aws_iam_policy_document" "lambda_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "lambda_exec" {
  name               = "${var.lambda_function_name}-role"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume.json
}

data "aws_iam_policy_document" "lambda_secrets_manager" {
  statement {
    sid    = "SecretsManagerRotation"
    effect = "Allow"
    actions = [
      "secretsmanager:DescribeSecret",
      "secretsmanager:GetSecretValue",
      "secretsmanager:PutSecretValue",
      "secretsmanager:UpdateSecretVersionStage",
    ]
    resources = [aws_secretsmanager_secret.tracking_public_token.arn]
  }

  statement {
    sid    = "GetRandomPassword"
    effect = "Allow"
    actions = [
      "secretsmanager:GetRandomPassword",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "CloudWatchLogs"
    effect = "Allow"
    actions = [
      "logs:CreateLogGroup",
      "logs:CreateLogStream",
      "logs:PutLogEvents",
    ]
    resources = ["arn:aws:logs:*:*:*"]
  }
}

resource "aws_iam_role_policy" "lambda_secrets_manager" {
  name   = "${var.lambda_function_name}-policy"
  role   = aws_iam_role.lambda_exec.id
  policy = data.aws_iam_policy_document.lambda_secrets_manager.json
}

# ============================================================================
# 出力
# ============================================================================

output "secret_arn" {
  description = "trackingms 公開トークン secret の ARN"
  value       = aws_secretsmanager_secret.tracking_public_token.arn
}

output "secret_name" {
  description = "trackingms 公開トークン secret 名"
  value       = aws_secretsmanager_secret.tracking_public_token.name
}

output "lambda_function_arn" {
  description = "Rotation Lambda Function の ARN"
  value       = aws_lambda_function.rotation.arn
}

output "rotation_period_days" {
  description = "自動 rotation 周期（日）"
  value       = var.rotation_days
}
