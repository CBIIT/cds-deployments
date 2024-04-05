@Library('datacommons-jenkins-shared-library@v1.1') _

def getLabelForEnvironment(environment) {
	if (environment == "stage" || environment == "prod"){
		return "slave-ncias-s2979-c"
	}else {
		return "slave-ncias-d2943-c"
	}
}

pipeline {
	agent {
		node {
			label getLabelForEnvironment(params.Environment)
		}
	}

	parameters {

        string(
	        defaultValue: '',
		    description: 'The image tag to deploy',
		    name: 'InteroperationTag')

		extendedChoice(
            name: 'Environment',
            defaultValue: 'dev',
            description: 'Choose the environment to build',
            type: 'PT_SINGLE_SELECT',
            value: 'dev,dev2,qa,qa2,stage,prod')

  }

  options {
  	ansiColor('xterm')
	timestamps()
  }

  environment {

      PROJECT      = 'cds'
	  APP          = "interoperation"
	  ENV          = "${params.Environment}"
	  CLUSTER      = "${env.PROJECT}-${env.ENV}-ecs" 
	  SERVICE      = "${env.PROJECT}-${env.ENV}-${env.APP}"
	  REGION       = "us-east-1"
	  PROGRAM      = "crdc"
      ECR_REPO     = "${env.PROGRAM}-${env.PROJECT}-${env.APP}"
	  IMAGE_TAG    = "${params.InteroperationTag}"

  }

  stages{
  	stage('checkout'){
  		steps {

  		checkout([$class: 'GitSCM',
			branches: [[name: '*/main']],
			doGenerateSubmoduleConfigurations: false,
			extensions: [[$class: 'RelativeTargetDirectory',
			relativeTargetDir: "playbooks"]],
			submoduleCfg: [],
			userRemoteConfigs:
			[[url: 'https://github.com/CBIIT/cds-deployments']]])

        checkout([$class: 'GitSCM',
			branches: [[name: "${params.Environment}"]],
			doGenerateSubmoduleConfigurations: false,
			extensions: [[$class: 'RelativeTargetDirectory',
			relativeTargetDir: "${env.PROJECT}-deployments"]],
			submoduleCfg: [],
			userRemoteConfigs:
			[[url: 'https://github.com/CBIIT/cds-deployments']]])

        }

  	}

	 stage('Set Environment Variables'){
 		steps {
 			script {
                // set ECR account number
				env.ECR_ACCOUNT = sh(label: 'Get ECR account', returnStdout: true, script: "aws secretsmanager get-secret-value --region $REGION --secret-id ecr --query SecretString --output text | jq -r '.central_account_id'").trim()
				// set repo URL
				env.REGISTRY_URL = "${ECR_ACCOUNT}.dkr.ecr.${REGION}.amazonaws.com"
			}
 		}
  	}

      stage('Add Production Tag'){

 		when {

	        expression {

                ENV == 'prod'

            }

        }

		steps {

 			script {

			    sh label: 'Docker-Tag', script: '''#!/bin/bash

				# Tag image as production if deploying to prod tier
				echo "Tagging Image as Production: $ECR_REPO:$IMAGE_TAG-$DATE"
                                aws_account=$(aws sts get-caller-identity --query "Account" --output text)
                                repo_url="$REGISTRY_URL/$ECR_REPO"

                                #DATE
                                DATE="$DATE"
				# login and get manifest
				docker login -u AWS -p $(aws ecr get-login-password --region $REGION) $repo_url
				
				docker pull $repo_url:$IMAGE_TAG
				docker tag $repo_url:$IMAGE_TAG $repo_url:prod-$IMAGE_TAG-$DATE
				docker push $repo_url:prod-$IMAGE_TAG-$DATE
				'''
                
                IMAGE_TAG = "prod-$IMAGE_TAG"
				
                echo "updated image tag: $IMAGE_TAG"
			}

 		}

  	}

  	stage('Deploy'){
        agent {
            docker {
                image 'cbiitssrepo/cicd-ansible_4.0'
                args '--net=host -u root -v /var/run/docker.sock:/var/run/docker.sock'
                reuseNode true
            }
        }
	    environment {
            INTEROPERATION_VERSION = "${env.IMAGE_TAG}"
        }
 		steps {
			wrap([$class: 'AnsiColorBuildWrapper', colorMapName: "xterm"]) {
                ansiblePlaybook(
					playbook: "${WORKSPACE}/playbooks/ansible/deploy-interoperation-microservice.yml", 
            		inventory: "${WORKSPACE}/playbooks/ansible/hosts",
                    extraVars: [
                        tier: "${params.Environment}",
                        iam_prefix: "power-user",
                        subdomain: "dataservice",
                        domain_name: "datacommons.cancer.gov",
						project_name: "${PROJECT}",
						],
                    colorized: true)
 			}
 		}

  	}

	stage('verify deployment'){

		steps {

 			script {

			    sh label: 'Verify-Deploy', script: '''#!/bin/bash

                # wait untiil the service is stable
				aws ecs wait services-stable --cluster $CLUSTER --region $REGION --service $SERVICE

				'''

			}

 		}

  	}

  }

  post {

    always {

        notify(
            secretPath: "notifications/slack",
            secretName: "cds_slack_url"
        ) 

    }

    cleanup {

        cleanWs()

    }

  }

}
