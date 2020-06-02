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
                        values 'ch3-tcp', 'ch3-sock', 'ch4-ofi', 'ch4-ucx'
                    }
                }
                stages {
                    stage('Source') {
                        steps {
                            git(branch: "${params.gitBranch}", url: "https://github.com/hzhou/mpich/")
                        }
                    }
                    stage("Build") {
                        steps {
                            sh '''
                                git clean -fdx

                                export skip_test=true
                                export test_script=test_quick
                                export configOption="--disable-static --enable-strict --disable-fortran --disable-romio $param"
                                export N_MAKE_JOBS=16

                                rm -rf mymake
                                mkdir -p mymake
                                cp /nfs/gce/projects/pmrs/hzhou/mymake/*.* mymake
                                ln -sf /nfs/gce/projects/pmrs/hzhou/modules.tar.gz .
                                perl mymake/test_mymake.pl
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
                        slackSend channel: 'hzhou-build', color: 'good', message: "<${currentBuild.absoluteUrl}|${currentBuild.projectName}> *SUCCESS* after _${currentBuild.durationString}_\nbranch: ${params.gitBranch}\nconfig: ${config}"
                    }
                    failure {
                        slackSend channel: 'hzhou-build', color: 'danger', message: "<${currentBuild.absoluteUrl}|${currentBuild.projectName}> *FAILURE* after _${currentBuild.durationString}_\nbranch: ${params.gitBranch}\nconfig: ${config}"
                    }
                    unstable {
                        slackSend channel: 'hzhou-build', color: 'warning', message: "<${currentBuild.absoluteUrl}|${currentBuild.projectName}> *UNSTABLE* after _${currentBuild.durationString}_\nbranch: ${params.gitBranch}\nconfig: ${config}"
                    }
                }
            }
        }
    }
}
