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
                sh '''
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
            slackSend channel: 'hzhou-build', color: 'good', message: "<${currentBuild.absoluteUrl}|${currentBuild.projectName}> *SUCCESS* after _${currentBuild.durationString}_\nbranch: ${params.gitBranch}\nparam: ${param}"
        }
        failure {
            slackSend channel: 'hzhou-build', color: 'danger', message: "<${currentBuild.absoluteUrl}|${currentBuild.projectName}> *FAILURE* after _${currentBuild.durationString}_\nbranch: ${params.gitBranch}\nparam: ${param}"
        }
        unstable {
            slackSend channel: 'hzhou-build', color: 'warning', message: "<${currentBuild.absoluteUrl}|${currentBuild.projectName}> *UNSTABLE* after _${currentBuild.durationString}_\nbranch: ${params.gitBranch}\nparam: ${param}"
        }
    }
}
