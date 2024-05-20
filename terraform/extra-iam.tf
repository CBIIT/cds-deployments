locals {
  iam_role_name = "power-user-${var.project}-${terraform.workspace}-ecs-task-execution-role"
  interoperation_bucket_name = terraform.workspace == "stage" || terraform.workspace == "prod" ? "crdc-cds-prod-interoperation-files" : "crdc-cds-nonprod-interoperation-files"
  interoperation_bucket_arn  = var.create_interoperation_bucket ? aws_s3_bucket.interoperation[0].arn : data.aws_s3_bucket.interoperation[0].arn
}
data "aws_iam_role" "role" {
  name       = local.iam_role_name
  depends_on = [module.ecs]
}

resource "aws_s3_bucket" "interoperation" {
    count = var.create_interoperation_bucket ? 1 : 0
    bucket  = local.interoperation_bucket_name
    tags = var.tags
}

resource "aws_s3_bucket_public_access_block" "interoperation" {
    count = var.create_interoperation_bucket ? 1 : 0
    bucket                  = aws_s3_bucket.interoperation[0].id
    block_public_acls       = true
    block_public_policy     = true
    ignore_public_acls      = true
    restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "interoperation" {
    count = var.create_interoperation_bucket ? 1 : 0
    bucket = aws_s3_bucket.interoperation[0].id

    rule {
        apply_server_side_encryption_by_default {
        sse_algorithm = "AES256"
        }
    }
}


resource "aws_s3_bucket_versioning" "interoperation" {
    count = var.create_interoperation_bucket ? 1 : 0
    bucket = aws_s3_bucket.interoperation[0].id
    versioning_configuration {
        status = "Enabled"
    }
}


resource "aws_iam_policy" "interoperation" {
  name   = "power-user-${terraform.workspace}-iam-extra-s3-policy"
  policy = data.aws_iam_policy_document.task_execution_s3.json
}

#attach the iam policy to the iam role
resource "aws_iam_policy_attachment" "attach" {
  name       = "iam-policy-attach"
  roles      = [data.aws_iam_role.role.name]
  policy_arn = aws_iam_policy.interoperation.arn
}

data "aws_iam_policy_document" "task_execution_s3" {
  statement {
    sid     = "AllowBucketAccess"
    effect  = "Allow"
    actions = [
      "s3:ListBucket"
    ]
    resources = [
      local.interoperation_bucket_arn
    ]
  }
  statement {
    sid     = "AllowObjectAccess"
    effect  = "Allow"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject"
    ]
    resources = [
      local.interoperation_bucket_arn,
      "${local.interoperation_bucket_arn}/*",
    ]
  }
}

data "aws_s3_bucket" "interoperation" {
    count = var.create_interoperation_bucket ? 0 : 1
    bucket = local.interoperation_bucket_name
}
