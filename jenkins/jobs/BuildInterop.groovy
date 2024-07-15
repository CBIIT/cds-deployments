@Library('datacommons-jenkins-shared-library@v1.2') _

def getLabelForEnvironment(environment) {
	if (environment == "stage" || environment == "prod"){
		return ""     // Set to use a Jenkins agent in your prod account
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

        extendedChoice(
            name: 'Environment',
            defaultValue: 'dev',
            description: 'Choose the environment to build',
            type: 'PT_SINGLE_SELECT',
            value: 'dev')

        gitParameter(branchFilter: 'origin/(.*)',
            defaultValue: '1.0.0',
            name: 'CodeBranch',
            type: 'GitParameterDefinition',
            quickFilterEnabled: true,
            selectedValue: 'DEFAULT',
            sortMode: 'ASCENDING_SMART',
            tagFilter: '*',
            useRepository: 'https://github.com/CBIIT/bento-cds-interoperation')

        booleanParam(
            defaultValue: true,
            name: 'UpdateDeploymentVersion')

        booleanParam(
            defaultValue: true,
            name: 'RunDeployStage')

  }

  options {
  	ansiColor('xterm')
	timestamps()
  }

  environment {

      PROJECT      = "cds"
	  SERVICE      = "interoperation"
      ECR_REPO     = "crdc-${env.PROJECT}-${env.SERVICE}"
	  DEPLOY_JOB   = "DeployInteroperation"
	  SLACK_SECRET = "cds_slack_url"
	  CODE_REPO    = "bento-${PROJECT}-${env.SERVICE}"
	  JOB_PATH     = "_default/_lower/CDS/OneClickDeployment/_jobs"
	  REGION       = "us-east-1"
	  ENV          = "${params.Environment}"
	  DEPLOY_REPO  = "${env.PROJECT}-deployments"
      CODE_BRANCH  = "${params.CodeBranch}"
      CODE_FOLDER  = "${env.PROJECT}-${env.SERVICE}"

  }

  stages{
  	stage('checkout'){
  		steps {

		checkout([$class: 'GitSCM',
			branches: [[name: "${env.CODE_BRANCH}"]],
			extensions: [[$class: 'SubmoduleOption', 
			recursiveSubmodules: true],
			[$class: 'RelativeTargetDirectory',
			relativeTargetDir: "${env.CODE_FOLDER}"]],
			userRemoteConfigs:
			[[url: "https://github.com/CBIIT/${env.CODE_REPO}"]]])

  		checkout([$class: 'GitSCM',
			branches: [[name: "${params.Environment}"]],
			extensions: [[$class: 'SubmoduleOption', 
			recursiveSubmodules: true],
			[$class: 'RelativeTargetDirectory',
			relativeTargetDir: "${env.PROJECT}-deployments"]],
			userRemoteConfigs:
			[[url: "https://github.com/CBIIT/${env.DEPLOY_REPO}"]]])

        }

  	}

  	stage('Set Environment Variables'){

 		steps {

 			script {

                // set ECR account number
				env.ECR_ACCOUNT = sh(label: 'Get ECR account', returnStdout: true, script: "aws secretsmanager get-secret-value --region $REGION --secret-id bento/$PROJECT/$ENV --query SecretString --output text | jq -r '.ecr_account'").trim()

				// set repo URL
				env.REPO_URL = "${ECR_ACCOUNT}.dkr.ecr.${REGION}.amazonaws.com/${ECR_REPO}"

			}

 		}

  	}

	stage('Build'){

 		steps {

 			script {

			    sh label: 'Docker-Build', script: '''#!/bin/bash

				# build Docker container
				echo "Building: $ECR_REPO:$CODE_BRANCH.$BUILD_NUMBER"

				cd $WORKSPACE/$CODE_FOLDER && DOCKER_BUILDKIT=1 docker build --no-cache -t $REPO_URL:$CODE_BRANCH.$BUILD_NUMBER .

				'''

			}

 		}

  	}

  	stage('Test'){
		when {
                expression { params.TrivyTestScan }
              }

 		steps {

 			script {

			    sh label: 'Trivy-Test', script: '''#!/bin/bash

				# Test image for vulnerabilities
                echo "Testing Image with Trivy: $ECR_REPO:$CODE_BRANCH.$BUILD_NUMBER"

				#docker run --rm --name trivy -u root -v /var/run/docker.sock:/var/run/docker.sock bitnami/trivy:latest image --exit-code 1 --timeout 15m --severity HIGH,CRITICAL $REPO_URL:$CODE_BRANCH.$BUILD_NUMBER
				docker run --rm --name trivy -u root -v /var/run/docker.sock:/var/run/docker.sock bitnami/trivy:latest image --timeout 15m --severity HIGH,CRITICAL $REPO_URL:$CODE_BRANCH.$BUILD_NUMBER

				'''

			}

 		}

  	}

  	stage('push to ECR'){

 		steps {

 			script {

			    sh label: 'Docker-Push', script: '''#!/bin/bash

				# push Docker container to ECR
				echo "Pushing: $ECR_REPO:$CODE_BRANCH.$BUILD_NUMBER"

				# login and push to ECR
				docker login -u AWS -p $(aws ecr get-login-password --region $REGION) $REPO_URL
				docker push $REPO_URL:$CODE_BRANCH.$BUILD_NUMBER

				'''

			}

 		}

  	}

  	stage('Update Deployment Manifest'){

 		steps {
 			
			script {
                if (params.UpdateDeploymentVersion) {
                    writeDeployment(
                        version: "${env.CODE_BRANCH}.${BUILD_NUMBER}",
                        image:  "${env.CODE_BRANCH}.${BUILD_NUMBER}",
                        service: "${env.APP}",
                        deploymentFile: "${env.PROJECT}-deployments/deployments.yaml",
                        deploymentRepoUrl: "https://github.com/CBIIT/${env.DEPLOY_REPO}",
                        deploymentCheckoutDirectory: "${env.PROJECT}-deployments"
                    )
                }
            }

 		}

  	}

  	stage('Update Code Tag'){

 		steps {
 			
			script {

                tagRepo ( 
                    gitTag: "${env.CODE_BRANCH}",
                    gitUrl: "github.com/CBIIT/${env.CODE_REPO}",
                    checkoutDirectory: "${env.CODE_FOLDER}"
                )

            }

 		}

  	}

  }

  post {

    success {

		script {
			if (params.RunDeployStage) {
				echo 'Run the deployment for this sevice'
				build job: "$JOB_PATH/$DEPLOY_JOB", parameters: [string(name: 'ImageTag', value: "$CODE_BRANCH.$BUILD_NUMBER"), extendedChoice(name: 'Environment', value: "$ENV")]
			}
		}
    }

    always {

        notify(
            secretPath: "notification/slack",
            secretName: "${env.SLACK_SECRET}"
        ) 

    }

    cleanup {

        cleanWs()

    }

  }

}