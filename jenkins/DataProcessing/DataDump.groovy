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
    extendedChoice( 
        name: 'Environment', 
        defaultValue: 'dev', 
        description: 'Choose the environment to build', 
        type: 'PT_SINGLE_SELECT',
        value: 'dev,dev2,dev3,qa,qa2,stage,prod' )
    string(defaultValue: "", 
        description: 'Name of the dump file to use', 
        name: 'DumpFileName')
    
    }

  tools {
  	maven 'Default' 
    jdk 'Default' 
  }
 environment {
    DUMP_FILE     = "${params.DumpFileName}"
	  TIER          = "${params.Environment}"
    SLACK_SECRET  = "cds_slack_url"
    PROJECT       = "cds"
    S3_BUCKET     = "crdc-dev-cds-neo4j-data-backup"
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
  stage("take data dump"){
    steps{
      wrap([$class: 'AnsiColorBuildWrapper', colorMapName: "xterm"]) {
			    ansiblePlaybook( 
                playbook: '${WORKSPACE}/ansible/data-dump.yml',
                inventory: '${WORKSPACE}/inventory/hosts',
                // extraVars: [
                //   tier: "${params.Environment}",
						    //   project_name: "${PROJECT}",
                //   workspace: "$WORKSPACE"
						    // ],
                colorized: true)
		  }
    }
  }
	stage('push to s3'){
		steps{			
			ansiblePlaybook( 
                playbook: '${WORKSPACE}/ansible/data-dump-push.yml',
                inventory: '${WORKSPACE}/ansible/hosts',
				        credentialsId: 'commonsdocker',
                colorized: true)

		}
	}
	
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
