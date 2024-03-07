

# S3 snapshot bucket
data "aws_iam_policy_document" "s3bucket_policy" {
  count  = terraform.workspace == "stage" ? 1 : 0
  statement {
      effect = "Allow"
      principals {
        type        = "AWS"
        identifiers = [
            "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root",
            "arn:aws:iam::${lookup(var.aws_nonprod_account_id,var.region,"us-east-1" )}:root",
        ]
      }
      actions = [
        "s3:ListBucket",
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject",
        "s3:ListBucketVersions",
        "s3:GetObjectVersion",
        "s3:PutObjectAcl"
      ]
      resources = [
        "arn:aws:s3:::${module.opensearch_s3_bucket[0].bucket_name}",
        "arn:aws:s3:::${module.opensearch_s3_bucket[0].bucket_name}/*"
      ]
    }
}

#Opensearch snapshot policy

data "aws_iam_policy_document" "trust" {
  count     = terraform.workspace == "dev" || terraform.workspace == "stage" ? 1 : 0
  statement {
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["es.amazonaws.com"]
    }
    actions = ["sts:AssumeRole"]
  }
}

resource "aws_iam_role" "opensearch_snapshot_role" {
  count                 = terraform.workspace == "dev" || terraform.workspace == "stage" ? 1 : 0
  name                  = "power-user-${var.program}-${terraform.workspace}-${var.project}-opensearch-snapshot"
  assume_role_policy    = data.aws_iam_policy_document.trust[0].json
  description           = "role that allows the opensearch service to create snapshots stored in s3"
  force_detach_policies = false
  permissions_boundary  = local.permissions_boundary
}

resource "aws_iam_policy" "opensearch_snapshot_policy" {
  count       = terraform.workspace == "dev" || terraform.workspace == "stage" ? 1 : 0
  name        = "power-user-${var.program}-${terraform.workspace}-${var.project}-opensearch-snapshot"
  description = "role that allows the opensearch service to create snapshots stored in s3"
  policy      = data.aws_iam_policy_document.opensearch_snapshot_policy_document[0].json
}

data "aws_iam_policy_document" "opensearch_snapshot_policy_document" {
  count     = terraform.workspace == "dev" || terraform.workspace == "stage" ? 1 : 0
  statement {
    effect    = "Allow"
    actions   = ["s3:ListBucket"]
    resources = ["arn:aws:s3:::${var.s3_opensearch_snapshot_bucket}",]
  }

  statement {
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject"
    ]
    resources = [
      "arn:aws:s3:::${var.s3_opensearch_snapshot_bucket}",
      "arn:aws:s3:::${var.s3_opensearch_snapshot_bucket}/*"
    ]
  }

  statement {
    effect = "Allow"
    actions = [
      "iam:PassRole",
      "iam:GetRole"
    ]
    resources = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/power-user*"]
  }

  statement {
    effect = "Allow"
    actions = ["es:*"]
    resources = [
      "${module.opensearch[0].opensearch_arn}/*"
    ]
  }
}

resource "aws_iam_role_policy_attachment" "opensearch_snapshot_policy_attachment" {
  count     = terraform.workspace == "dev" || terraform.workspace == "stage" ? 1 : 0
  role       = aws_iam_role.opensearch_snapshot_role[0].name
  policy_arn = aws_iam_policy.opensearch_snapshot_policy[0].arn
}

#role for cross account access

data "aws_iam_policy_document" "cross_account_trust" {
  count     = terraform.workspace == "stage" ? 1 : 0
  statement {
    effect = "Allow"

    principals {
          identifiers = ["arn:aws:iam::${lookup(var.aws_nonprod_account_id,var.region,"us-east-1" )}:root"]
          type        = "AWS"
    }
    actions = ["sts:AssumeRole"]
  }
}

resource "aws_iam_role" "s3_opensearch_cross_account_access_role" {
  count                 = terraform.workspace == "stage" ? 1 : 0
  name                  = "power-user-${var.program}-${terraform.workspace}-${var.project}-s3-opensearch-cross-account-access"
  assume_role_policy    = data.aws_iam_policy_document.cross_account_trust[0].json
  description           = "role that allows the opensearch service to access prod s3"
  force_detach_policies = false
}

resource "aws_iam_policy" "s3_opensearch_cross_account_access_policy" {
  count       = terraform.workspace == "stage" ? 1 : 0
  name        = "power-user-${var.program}-${terraform.workspace}-${var.project}-s3-opensearch-cross-account-access"
  description = "role that allows the opensearch service to access prod s3"
  policy      = data.aws_iam_policy_document.s3_opensearch_cross_account_access_policy_document[0].json
}

data "aws_iam_policy_document" "s3_opensearch_cross_account_access_policy_document" {
  count     = terraform.workspace == "stage" ? 1 : 0
  statement {
    effect = "Allow"
    actions = [
        "s3:PutObject",
        "s3:GetObject",
        "s3:ListBucket"
    ]
    resources = [
      "arn:aws:s3:::${var.s3_opensearch_snapshot_bucket}",
      "arn:aws:s3:::${var.s3_opensearch_snapshot_bucket}/*"
    ]
  }
}

resource "aws_iam_role_policy_attachment" "s3_opensearch_cross_account_access" {
  count                 = terraform.workspace == "stage" ? 1 : 0
  role                  = aws_iam_role.s3_opensearch_cross_account_access_role[0].name
  policy_arn            = aws_iam_policy.s3_opensearch_cross_account_access_policy[0].arn
}

resource "aws_s3_bucket_policy" "s3_snapshot_policy" {
  count  = terraform.workspace == "stage" ? 1 : 0
  bucket = module.opensearch_s3_bucket[0].bucket_id
  policy = data.aws_iam_policy_document.s3bucket_policy[0].json
}

#S3 bucket for storing OpenSearch Snapshots
module "opensearch_s3_bucket" {
  count  = terraform.workspace == "stage" ? 1 : 0
  source = "git::https://github.com/CBIIT/datacommons-devops.git//terraform/modules/s3?ref=main"
  bucket_name = local.s3_snapshot_bucket_name
  resource_prefix   = "${var.program}-${terraform.workspace}-${var.project}"
  env = terraform.workspace
  tags = var.tags
  s3_force_destroy = var.s3_force_destroy
  days_for_archive_tiering = 125
  days_for_deep_archive_tiering = 180
  s3_enable_access_logging = false
  s3_access_log_bucket_id = ""
}

module "data_backup_s3_bucket" {
  source = "git::https://github.com/CBIIT/datacommons-devops.git//terraform/modules/s3?ref=cds-rebuild"
  bucket_name = local. data_backup_bucket_name
  resource_prefix   = "${var.program}-${terraform.workspace}-${var.project}"
  env = terraform.workspace
  tags = var.tags
  s3_force_destroy = var.s3_force_destroy
  days_for_archive_tiering = 125
  days_for_deep_archive_tiering = 180
  s3_enable_access_logging = false
  s3_access_log_bucket_id = ""
}