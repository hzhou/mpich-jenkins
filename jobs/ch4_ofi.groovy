pipeline {
    parameters {
        string(name: 'gitBranch', defaultValue: 'INVALID', description: '')
    }

    agent { label "centos64_review" }

    stages {
        stage('Matrix Build') {
            matrix {
                axes {
                    axis {
                        name 'jenkins_configure'
                        values 'default', 'debug'
                    }
                }
                stages {
                    stage('Build') {
                        steps {
                            sh '''
                            '''
                            copyArtifacts(projectName: 'mpich-jenkins-scripts', target: 'jenkins-scripts')
                            sh '''
                                git clone https://github.com/hzhou/mpich/ mpich
                                cd mpich
                                git checkout $gitBranch

                                test_worker=../jenkins-scripts/test-worker.sh
                                $test_worker -b $gitBranch -h $WORKSPACE -c $compiler -o $jenkins_configure -q $label -m ch4:ofi
                            '''
                        }
                    }
                }
                post {
                    always {
                        archiveArtifacts artifacts: 'filtered-make.txt,autogen.log,c.txt,**/config.log,m.txt,mi.txt,test/mpi/summary.junit.xml,apply-xfail.sh,test/mpi/basictypelist.txt'
                        junit 'test/mpi/summary.junit.xml'

                        success {
                            slackSend channel: 'hzhou-build', color: 'good', message: "${currentBuild.fullDisplayName} completed successfully."
                        }
                        failure {
                            slackSend channel: 'hzhou-build', color: 'RED', message: "${currentBuild.fullDisplayName} FAILED."
                        }

                    }
                }
            }
        }
    }
}
