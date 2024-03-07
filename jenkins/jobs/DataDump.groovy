import groovy.json.JsonOutput

def sendSlackMessage() {
  jenkins_image = ":jenkins:"
  beer_image = ":beer:"
  long epoch = System.currentTimeMillis()/1000
  def BUILD_COLORS = ['SUCCESS': 'good', 'FAILURE': 'danger', 'UNSTABLE': 'danger', 'ABORTED': 'danger']
  
  def slack = JsonOutput.toJson(
      [
            icon_emoji: jenkins_image,
            attachments: [[
              title: "Jenkins Job Alert - ${currentBuild.currentResult}",
              text:  "Job ${env.JOB_NAME} build ${env.BUILD_NUMBER}  ${beer_image}\n Details at: ${env.BUILD_URL}console",
              fallback: "Bento Jenkins Build",
              color: "${BUILD_COLORS[currentBuild.currentResult]}",
              footer: "bento devops",
              ts: epoch,
              mrkdwn_in: ["footer", "title"],
           ]]
        ]
    )
    try {
        sh "curl -X POST -H 'Content-type: application/json' --data '${slack}'  '${ICDC_SLACK_URL}'"
    } catch (err) {
        echo "${err} Slack notify failed"
    }
}

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
    extendedChoice( 
        name: 'Environment', 
        defaultValue: 'dev', 
        description: 'Choose the environment to build', 
        type: 'PT_SINGLE_SELECT',
        value: 'dev,dev2,qa,qa2,stage,prod' )
    string(defaultValue: "", 
        description: 'Name of the dump file to use', 
        name: 'DumpFileName')
    
    }

  tools {
  	maven 'Default' 
    jdk 'Default' 
  }
 environment {
    ICDC_SLACK_URL = "${ICDC_SLACK_URL}"
    env.DUMP_FILE = "${params.DumpFileName}"
	env.TIER      = "${params.Environment}"
 }
  stages{

  	stage('checkout'){
  		steps {
          checkout( changelog:false,
				poll: false,
				scm: [$class: 'GitSCM', 
				branches: [[name: '*/master']], 
				doGenerateSubmoduleConfigurations: false, 
				extensions: [[$class: 'DisableRemotePoll'],
				[$class: 'PathRestriction', excludedRegions: '*'], 
				[$class: 'RelativeTargetDirectory', 
				relativeTargetDir: 'icdc-devops']], 
				submoduleCfg: [], 
				userRemoteConfigs: 
				[[url: 'https://github.com/CBIIT/icdc-devops.git']]
				])

  		}
  	}
	
  	stage('dump data'){
 		steps {
 		  wrap([$class: 'AnsiColorBuildWrapper', colorMapName: "xterm"]) {
			
		      script {
                sh label: 'db-hosts', script: '''#!/bin/bash
                  echo "Creating inventory file"
                  echo "[icdc-neo4j]" > ${WORKSPACE}/icdc-devops/ansible/hosts
                  echo ${NEO4J_IP} >> ${WORKSPACE}/icdc-devops/ansible/hosts
                '''
              }
			    ansiblePlaybook( 
                playbook: '${WORKSPACE}/icdc-devops/ansible/icdc-data-dump.yml',
                inventory: '${WORKSPACE}/icdc-devops/ansible/hosts',
				credentialsId: 'commonsdocker',
                colorized: true)
		  }

 		}
    }
	
	stage('push to s3'){
		steps{

			script {
                sh label: 'db-hosts', script: '''#!/bin/bash
                  echo "Creating inventory file"
                  echo "[loader]" > ${WORKSPACE}/icdc-devops/ansible/hosts
                  echo "localhost" >> ${WORKSPACE}/icdc-devops/ansible/hosts

                '''

              }
			
			ansiblePlaybook( 
                playbook: '${WORKSPACE}/icdc-devops/ansible/icdc-data-dump-push.yml',
                inventory: '${WORKSPACE}/icdc-devops/ansible/hosts',
				        credentialsId: 'commonsdocker',
                colorized: true)

		}
	}
	
  }
  post {
    always {
      sendSlackMessage()
      }
    cleanup {
      cleanWs()
      }
  }
}
