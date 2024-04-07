resource "aws_s3_bucket_policy" "alb_bucket_policy" {
  bucket = module.s3.bucket_id
  policy = data.aws_iam_policy_document.s3_alb_policy.json
}

module "data_backup_s3_bucket" {
  count = var.create_backup_bucket ? 1 : 0
  source = "git::https://github.com/CBIIT/datacommons-devops.git//terraform/modules/s3?ref=cds-rebuild"
  bucket_name = local.data_backup_bucket_name
  resource_prefix   = "${var.program}-${terraform.workspace}-${var.project}"
  env = terraform.workspace
  tags = var.tags
  s3_force_destroy = var.s3_force_destroy
  days_for_archive_tiering = 125
  days_for_deep_archive_tiering = 180
  s3_enable_access_logging = false
  s3_access_log_bucket_id = ""
  stack_name = var.stack_name
}