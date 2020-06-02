pipeline {
    agent { label "${params.label}" }
    parameters {
        string(name: 'gitBranch', defaultValue: 'INVALID', description: '')
        string(name: 'label', defaultValue: 'centos64_review', description: '')
        string(name: 'param', defaultValue: '', description: '')
    }
    stages {
        stage('Source') {
            steps {
                git(branch: "${params.gitBranch}", url: "https://github.com/hzhou/mpich/")
            }
        }
        stage("Build") {
            steps {
                copyArtifacts(projectName: 'mpich-jenkins-scripts', target: 'jenkins-scripts')
                sh '''
                    export PMRS=/nfs/gce/projects/pmrs
                    export GIT_BRANCH=$gitBranch
                    perl jenkins-scripts/custom.pl ./jenkins-scripts/test-worker.sh
                '''
            }
        }
    }
    post {
        always {
            archiveArtifacts(artifacts: '**/config.log')
            junit('**/summary.junit.xml')
        }
        success {
            slackSend channel: 'hzhou-build', color: 'good', message: "<${currentBuild.absoluteUrl}|${currentBuild.projectName}> *SUCCESS* after _${currentBuild.durationString}_\nbranch: ${params.gitBranch}\nparam: ${params.param}"
        }
        failure {
            slackSend channel: 'hzhou-build', color: 'danger', message: "<${currentBuild.absoluteUrl}|${currentBuild.projectName}> *FAILURE* after _${currentBuild.durationString}_\nbranch: ${params.gitBranch}\nparam: ${params.param}"
        }
        unstable {
            slackSend channel: 'hzhou-build', color: 'warning', message: "<${currentBuild.absoluteUrl}|${currentBuild.projectName}> *UNSTABLE* after _${currentBuild.durationString}_\nbranch: ${params.gitBranch}\nparam: ${params.param}"
        }
    }
}
