public_subnet_ids = [
  "subnet-9e9907fa",
  "subnet-ed3efab0"
]
private_subnet_ids = [
  "subnet-f334f0ae",
  "subnet-a69608c2"
]
vpc_id = "vpc-c29e1dba"
stack_name = "cds"

tags = {
  Project = "cds"
  CreatedWith = "Terraform"
  POC = "ye.wu@nih.gov"
  Environment = "stage"
}
region = "us-east-1"

#alb
internal_alb = false
certificate_domain_name = "*.datacommons.cancer.gov"
domain_name = "datacommons.cancer.gov"
alb_log_bucket_name = "ctos-nonprod-manager-alb-logs"

#ecr
create_ecr_repos = false
ecr_repo_names = ["backend","frontend","files"]

#ecs
add_opensearch_permission = true
application_subdomain = "dataservice"
microservices  = {
  frontend = {
    name = "frontend"
    port = 80
    health_check_path = "/"
    priority_rule_number = 22
    image_url = "cbiitssrepo/bento-frontend:latest"
    cpu = 256
    memory = 512
    path = ["/*"]
    number_container_replicas = 1
  },
  backend = {
    name = "backend"
    port = 8080
    health_check_path = "/ping"
    priority_rule_number = 20
    image_url = "cbiitssrepo/bento-backend:latest"
    cpu = 512
    memory = 1024
    path = ["/v1/graphql/*","/version"]
    number_container_replicas = 1
  },
  files = {
    name = "files"
    port = 8081
    health_check_path = "/api/files/ping"
    priority_rule_number = 19
    image_url = "cbiitssrepo/bento-auth:latest"
    cpu = 256
    memory = 512
    path = ["/api/files/*"]
    number_container_replicas = 1
  },
  interoperation = {
    name = "interoperation"
    port = 4030
    health_check_path = "/api/interoperation/ping"
    priority_rule_number = 18
    image_url = "cbiitssrepo/bento-interoperation:latest"
    cpu = 256
    memory = 512
    path = ["/api/interoperation/*"]
    number_container_replicas = 1
  }
}

#opensearch
create_opensearch_cluster = true
opensearch_ebs_volume_size = 200
opensearch_instance_type = "m5.xlarge.search"
opensearch_version = "OpenSearch_1.2"
allowed_ip_blocks = ["10.208.16.0/21","10.208.24.0/21"]
create_os_service_role = true
opensearch_instance_count = 1
create_cloudwatch_log_policy = true


#neo4j db is created by cloudone
create_db_instance = false

#dns is managed by cloudone
create_dns_record = false

#cloud platform
cloud_platform="cloudone"
target_account_cloudone = true
create_instance_profile = true

#cloudfront
create_cloudfront = true
create_files_bucket = false
cloudfront_distribution_bucket_name = "crdc-cds-prod-interoperation-files"
cloudfront_slack_channel_name = "cds-cloudfront-wafv2"
alarms = {
  error4xx = {
    name = "4xxErrorRate"
    threshold = 10
  }
  error5xx = {
    name = "5xxErrorRate"
    threshold = 10
  }
}
slack_secret_name = "cloudfront-slack"

#ecr replication
enable_ecr_replication = false
allow_ecr_replication = false



#destination_bucket_name = ""
create_destination_bucket = true
replication_role_arn = "arn:aws:iam::339649878709:role/power-user-cds-qa-s3-replication-role"
enable_replication = true
enable_s3_replication = true
destination_bucket_name = "cds-prod-metadata"


#metric pipeline
enable_metric_pipeline = true
project_name = "cds"
resource_prefix = "cds-stage"

#opensearch
s3_opensearch_snapshot_bucket =  "opensearch-snapshot-bucket"

#interoperation bucket
create_interoperation_bucket = true
                              


