# Neo4j Data Dump and Restore Pipelines

## Prerequisites
- Jenkins Agent
- Neo4j Database instance(s)
- S3 bucket to store the backups, e.g. `<program>-<project>-<env>-neo4j-data-backup`
- IAM policy that allows Jenkins Agent to read and put data to the bucket:
```json
{
    "Statement": [
        {
            "Action": [
                "s3:PutObject",
                "s3:ListBucketVersions",
                "s3:ListBucket",
                "s3:ListAllMyBuckets",
                "s3:GetObjectVersion",
                "s3:GetObjectAttributes",
                "s3:GetObject"
            ],
            "Effect": "Allow",
            "Resource": [
            "bucket_arn",
            "bucket_arn/*"
            ],
            "Sid": ""
        }
        
    ],
    "Version": "2012-10-17"
}
