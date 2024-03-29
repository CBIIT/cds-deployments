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
      ApplicationName = "CDS"
      Project         = "CRDC-CDS"
      CreatedBy       = "vincent.donkor@nih.gov"
      Runtime         = "24hours"
      CreateDate      = "11/22/2021"
    }
  }
}
