terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "4.67.0"
    }
  }

}

provider "aws" {
  region = var.region
  default_tags {
    tags = {
      EnvironmentTier = terraform.workspace
      Customer        = "nci od cbiit ods"
      DevLead         = "Amanda Bell"
      CreatedBy       = "vincent.donkor@gmail.com"
      FISMA           = "moderate"
      ManagedBy       = "terraform"
      OpsModel        = "cbiit managed hybrid"
      Program         = "crdc"
      PII             = "yes"
      Backup          = local.level
      PatchGroup      = local.level
      ApplicationName = "CDS"
      ProjectManager  = "Amanda Bell"
      Project         = "CRDC-CDS"
      Runtime         = local.runtime
    }
  }
}
