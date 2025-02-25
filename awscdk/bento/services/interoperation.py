from aws_cdk import aws_elasticloadbalancingv2 as elbv2
from aws_cdk import aws_ecs as ecs
from aws_cdk import aws_ecr as ecr
from aws_cdk import aws_secretsmanager as secretsmanager

class interoperationService:
  def createService(self, config):

    ### Files Service ###############################################################################################################
    service = "interoperation"

    # Set container configs
    if config.has_option(service, 'command'):
        command = [config[service]['command']]
    else:
        command = None

    environment={
            # "NEW_RELIC_APP_NAME":"bento-cdk-interoperation",
            "AUTH_ENABLED":"false",
            "REDIS_AUTH_ENABLED":"false",
            "AUTH_URL":"/api/auth/authenticated",
            "AUTHORIZATION_ENABLED":"true",
            "BACKEND_URL":"/v1/graphql/",
            "DATE":"2024-07-09",
            #"MYSQL_PORT":"3306",
            #"MYSQL_SESSION_ENABLED":"true",
            #"NEO4J_URI":"bolt://{}:7687".format(config['db']['neo4j_ip']),
            "PROJECT":"BENTO",
            "URL_SRC":"CLOUD_FRONT",
            "VERSION":config[service]['image'],
        }

    secrets={
            "CLOUDFRONT_PRIVATE_KEY":ecs.Secret.from_secrets_manager(secretsmanager.Secret.from_secret_name_v2(self, "privatekey", secret_name="cloudfront"), 'privatekey'),
            "CLOUDFRONT_KEY_PAIR_ID":ecs.Secret.from_secrets_manager(secretsmanager.Secret.from_secret_name_v2(self, "key_group_id", secret_name='cloudfront'), 'key_group_id'),
            "CLOUDFRONT_DOMAIN":ecs.Secret.from_secrets_manager(secretsmanager.Secret.from_secret_name_v2(self, "domain_name", secret_name='cloudfront'), 'domain_name'),
            "FILE_MANIFEST_BUCKET_NAME":ecs.Secret.from_secrets_manager(secretsmanager.Secret.from_secret_name_v2(self, "file_manifest_bucket_name", secret_name='cloudfront'), 'file_manifest_bucket_name')
        }
    
    taskDefinition = ecs.FargateTaskDefinition(self,
        "{}-{}-taskDef".format(self.namingPrefix, service),
        family=f"{config['main']['resource_prefix']}-{config['main']['tier']}-interoperation",
        cpu=config.getint(service, 'cpu'),
        memory_limit_mib=config.getint(service, 'memory')
    )
    
    ecr_repo = ecr.Repository.from_repository_arn(self, "{}_repo".format(service), repository_arn=config[service]['repo'])
    
    taskDefinition.add_container(
        service,
        #image=ecs.ContainerImage.from_registry("{}:{}".format(config[service]['repo'], config[service]['image'])),
        image=ecs.ContainerImage.from_ecr_repository(repository=ecr_repo, tag=config[service]['image']),
        cpu=config.getint(service, 'cpu'),
        memory_limit_mib=config.getint(service, 'memory'),
        port_mappings=[ecs.PortMapping(container_port=config.getint(service, 'port'), name=service)],
        command=command,
        environment=environment,
        secrets=secrets,
        logging=ecs.LogDrivers.aws_logs(
            stream_prefix="{}-{}".format(self.namingPrefix, service)
        )
    )

    ecsService = ecs.FargateService(self,
        "{}-{}-service".format(self.namingPrefix, service),
        service_name=f"{config['main']['resource_prefix']}-{config['main']['tier']}-interoperation",
        cluster=self.ECSCluster,
        task_definition=taskDefinition,
        enable_execute_command=True,
        min_healthy_percent=50,
        max_healthy_percent=200,
        circuit_breaker=ecs.DeploymentCircuitBreaker(
            enable=True,
            rollback=True
        ),
    )
    # ecsService.connections.allow_to_default_port(self.auroraCluster)

    ecsTarget = self.listener.add_targets("ECS-{}-Target".format(service),
        port=int(config[service]['port']),
        target_group_name=f"{config['main']['resource_prefix']}-{config['main']['tier']}-interoperation",
        protocol=elbv2.ApplicationProtocol.HTTP,
        health_check = elbv2.HealthCheck(
            path=config[service]['health_check_path']),
        targets=[ecsService],)

    elbv2.ApplicationListenerRule(self, id="alb-{}-rule".format(service),
        conditions=[
            elbv2.ListenerCondition.path_patterns(config[service]['path'].split(','))
        ],
        priority=int(config[service]['priority_rule_number']),
        listener=self.listener,
        target_groups=[ecsTarget])
