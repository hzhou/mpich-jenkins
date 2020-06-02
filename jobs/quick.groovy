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
    env.label = get_label(params.param)
    node(env.label) {
        stage("Setup") {
            git(branch: params.gitBranch, url: "https://github.com/hzhou/mpich")
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
        stage("Configure") {
            sh '''
                . ./custom_import.sh
                perl $MYMAKE/mymake.pl --prefix=$PWD/_inst --with-device=$netmod --disable-fortran --disable-romio --enable-g=dbg $config_args
            '''
        }

        stage("Compile") {
            sh '''
                make install
                make hydra-install
                make test
            '''
        }

        stage("Test") {
            sh '''
                mpicc -o cpi examples/cpi.c
                mpirun -n 2 ./cpi

                . ./custom_import.sh
                zsh $SCRIPT_DIR/set-xfail.sh -o $config -q $label -m $netmod -f test/mpi/maint/jenkins/xfail.conf
                make -C test/mpi testing
            '''
        }

        stage("Post") {
            archiveArtifacts(artifacts: "config.log, src/include/mpichconf.h, Makefile, mymake/mpl/config.log, mymake/mpl/include/mplconfig.h")
            junit("**/summary.junit.xml")
            def colors = [SUCCESS:'good', UNSTABLE:'warning', FAILURE:'danger']
            slackSend(channel: 'hzhou-build', color: colors[currentBuild.currentResult], message: "<${currentBuild.absoluteUrl}|${currentBuild.projectName}> *${currentBuild.currentResult}* after _${currentBuild.durationString}_\nbranch: ${env.GIT_BRANCH}\nparam: ${params.param}")
        }
    }
}
