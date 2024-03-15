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
    DUMP_FILE = "${params.DumpFileName}"
	  TIER      = "${params.Environment}"
    SLACK_SECRET  = "cds_slack_url"
    PROJECT       = "cds"
 }
  stages{

  	stage('create inventory'){
 		steps {
 		  wrap([$class: 'AnsiColorBuildWrapper', colorMapName: "xterm"]) {
			    ansiblePlaybook( 
                playbook: '${WORKSPACE}/ansible/hostfile.yml',
                inventory: '${WORKSPACE}/ansible/hosts',
                extraVars: [
                  tier: "${params.Environment}",
						      project_name: "${PROJECT}",
                  workspace: "$WORKSPACE"
						    ],
                colorized: true)
		  }
 		}
    
  }
  stage("download dump"){
    steps{
      wrap([$class: 'AnsiColorBuildWrapper', colorMapName: "xterm"]) {
			    ansiblePlaybook( 
                playbook: '${WORKSPACE}/ansible/download-dump.yml',
                inventory: '${WORKSPACE}/ansible/hosts',
                // inventory: '${WORKSPACE}/inventory/hosts',
                extraVars: [
                  tier: "${params.Environment}",
						      project_name: "${PROJECT}",
                  workspace: "$WORKSPACE"
						    ],
                colorized: true)
		  }
    }
  }
	// stage('push to s3'){
	// 	steps{			
	// 		ansiblePlaybook( 
  //               playbook: '${WORKSPACE}/ansible/data-dump-push.yml',
  //               inventory: '${WORKSPACE}/ansible/hosts',
	// 			        credentialsId: 'commonsdocker',
  //               colorized: true)

	// 	}
	// }
	
  }
  post {
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
