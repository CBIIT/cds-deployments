
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
			label getLabelForEnvironment(params.Environment) // set node lable according to your environment
		}
	}

	parameters {
    extendedChoice( 
        name: 'Environment', 
        defaultValue: 'dev', 
        description: 'Choose the environment to build', 
        type: 'PT_SINGLE_SELECT',
        value: 'dev,dev2,dev3,qa,qa2,stage,prod' )  // set according to project
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
    SLACK_SECRET  = "cds_slack_url"         // change this to your slack secret
    PROJECT       = "cds"                   // change this to you project
    S3_BUCKET     = "crdc-dev-cds-neo4j-data-backup"   // set this to your backup bucket
 }
  stages{

  	stage('create inventory'){
 		steps {
 		  wrap([$class: 'AnsiColorBuildWrapper', colorMapName: "xterm"]) {
			    ansiblePlaybook( 
                playbook: '${WORKSPACE}/ansible/hostfile.yml',   // set hotfile path per your project setup 
                inventory: '${WORKSPACE}/ansible/hosts',        // set hotfile path per your project setup 
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
                playbook: '${WORKSPACE}/ansible/download-dump.yml',         // set playbook path per your project setup 
                inventory: '${WORKSPACE}/ansible/hosts',                    // set host path per your project setup 
                colorized: true)
		  }
    }
  }
	stage('restore data dump'){
		steps{			
			ansiblePlaybook( 
                playbook: '${WORKSPACE}/ansible/dump-restore.yml',        // set playbook path per your project setup 
                inventory: '${WORKSPACE}/inventory/hosts',                // set host path per your project setup 
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
