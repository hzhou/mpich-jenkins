@NonCPS
def get_label(text) {
    def m = text =~ /label[:=]\s*(\w+)/
    m ? m[0][1] : "centos64_review"
}
node('master') {
    env.label = get_label(ghprbCommentBody)
    node(env.label) {
        stage("Setup") {
            git(branch: ghprbSourceBranch, url: ghprbAuthorRepoGitUrl)
            sh 'git clean -fdx'
            env.PMRS  = '/nfs/gce/projects/pmrs'
            env.SCRIPT_DIR = "${env.PMRS}/hzhou/mpich-jenkins-scripts"
            sh 'hostname'
        }
        stage("Autogen") {
            sh '''
                PATH=$HOME/software/autotools/bin:$PATH
                git submodule update --init --recursive
                ./autogen.sh 2>&1 | tee autogen.log
            '''
        }
        stage("Configure") {
            sh '''
                perl $SCRIPT_DIR/custom.pl $SCRIPT_DIR/test-pipeline.sh -x configure
            '''
        }
        stage("Build") {
            sh '''
                make 2>&1 | tee m.txt
                make install 2>&1 | tee mi.txt
            '''
        }
        stage("Testing") {
            sh '''
                perl $SCRIPT_DIR/custom.pl $SCRIPT_DIR/test-pipeline.sh -x testing
            '''
        }
        stage("Post") {
            archiveArtifacts(artifacts: '**/config.log')
            junit('**/summary.junit.xml')
        }
    }
}
