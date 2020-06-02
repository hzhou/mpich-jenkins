pipeline {
    parameters {
        string(name: 'gitBranch', defaultValue: 'INVALID', description: '')
    }
    agent none
    stages {
        stage('Matrix Build') {
            matrix {
                agent centos64_review
                axes {
                    axis {
                        name 'config'
                        values 'default', 'debug', 'am-only', 'strict', 'no-inline', 'external', 'hcoll'
                    }
                }
                stages {
                    stage('Source') {
                        steps {
                            git(branch: "${params.gitBranch}", url: "https://github.com/hzhou/mpich/")
                        }
                    }
                    stage("${params.config}") {
                        steps {
                            copyArtifacts(projectName: 'mpich-jenkins-scripts', target: 'jenkins-scripts')
                            sh '''
                                test_worker="jenkins-scripts/test-worker.sh"
                                netmod="ch4:ofi"
                                $test_worker -b $gitBranch -h $WORKSPACE -o $config -m $netmod
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
                        slackSend channel: 'hzhou-build', color: 'good', message: "<${currentBuild.absoluteUrl}|${currentBuild.projectName}> *SUCCESS* after _${currentBuild.durationString}_\nbranch: ${params.gitBranch}\nconfig: ${params.config}"
                    }
                    failure {
                        slackSend channel: 'hzhou-build', color: 'danger', message: "<${currentBuild.absoluteUrl}|${currentBuild.projectName}> *FAILURE* after _${currentBuild.durationString}_\nbranch: ${params.gitBranch}\nconfig: ${params.config}"
                    }
                    unstable {
                        slackSend channel: 'hzhou-build', color: 'warning', message: "<${currentBuild.absoluteUrl}|${currentBuild.projectName}> *UNSTABLE* after _${currentBuild.durationString}_\nbranch: ${params.gitBranch}\nconfig: ${params.config}"
                    }
                }
            }
        }
    }
}
