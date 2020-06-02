@NonCPS
def get_label(text) {
    def m = text =~ /label[:=]\s*(\w+)/
    m ? m[0][1] : "centos07"
}
@NonCPS
def get_compiler(text) {
    def m = text =~ /compiler[:=]\s*(\w+)/
    m ? m[0][1] : "gcc"
}
@NonCPS
def get_config(text) {
    def m = text =~ /config[:=]\s*(\w+)/
    m ? m[0][1] : "default"
}
node('master') {
    env.label = get_label(ghprbCommentBody)
    node(env.label) {
        stage("Setup") {
            dir("mpich") {
                git(branch: ghprbSourceBranch, url: ghprbAuthorRepoGitUrl)
                sh 'git clean -fdx'
                env.PMRS = '/nfs/gce/projects/pmrs'
                env.SCRIPT_DIR = "${env.PMRS}/hzhou/mpich-jenkins-scripts"
                env.MYMAKE = "${env.PMRS}/hzhou/mymake"
                env.MODTARBALL = "${env.PMRS}/hzhou/modules.tar.gz"
                env.netmod = "ch3"
                env.compiler = get_compiler(params.param)
                env.config = get_config(params.param)
                def prefix = "${env.WORKSPACE}/_inst"
                env.PATH = "${prefix}/bin:${env.PATH}"
                env.LD_LIBRARY_PATH= "${prefix}/lib:${env.LD_LIBRARY_PATH}"
                env.CPATH= "${prefix}/include"
                sh '''
                    perl $MYMAKE/jenkins_custom.pl
                    cat custom_import.sh
                    printenv | sort
                '''
            }
        }
        stage("Configure") {
            dir("mpich") {
                sh '''
                    . ./custom_import.sh
                    perl $MYMAKE/mymake.pl --prefix=$PWD/_inst --with-device=$netmod --disable-fortran --disable-romio --enable-g=dbg $config_args
                '''
            }
        }

        stage("Compile") {
            dir("mpich") {
                sh '''
                    . ./custom_import.sh
                    t1=`date +%s`
                    make install 2>&1  | tee -a make.log
                    make hydra-install | tee -a make.log
                    t2=`date +%s`
                    dur=`expr t2 - t1`
                    perl $MYMAKE/report_make_log.pl make.log $compiler $dur
                '''
            }
        }

        stage("Post") {
            dir("mpich") {
                archiveArtifacts(artifacts: "config.log, src/include/mpichconf.h, Makefile, mymake/mpl/config.log, mymake/mpl/include/mplconfig.h")
                junit("**/summary.junit.xml")
            }
        }
    }
}
