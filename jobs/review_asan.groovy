@NonCPS
def get_netmod(text) {
    def m = text =~ /netmod[:=]\s*(\w+)/
    m ? m[0][1] : "ch3:tcp"
}
node("centos64_review") {
    stage("Setup") {
        git(branch: ghprbSourceBranch, url: ghprbAuthorRepoGitUrl)
        sh 'git clean -fdx'
        env.PMRS = '/nfs/gce/projects/pmrs'
        env.SCRIPT_DIR = "${env.PMRS}/hzhou/mpich-jenkins-scripts"
        env.MYMAKE = "${env.PMRS}/hzhou/mymake"
        env.MODTARBALL = "${env.PMRS}/hzhou/modules.tar.gz"
        env.netmod = get_netmod(ghprbCommentBody)
        env.PATH = "${env.PMRS}/opt/clang-8/bin:${env.PATH}"
        env.LD_LIBRARY_PATH = "${env.PMRS}/opt/gcc-8/lib64:${env.LD_LIBRARY_PATH}"
        env.CC = "clang"
        env.CXX = "clang++"
        env.CFLAGS = "-fsanitize=address -fno-omit-frame-pointer"
        env.CXXFLAGS = "-fsanitize=address -fno-omit-frame-pointer"
        env.LDFLAGS = "-fsanitize=address"
        def prefix = "${env.WORKSPACE}/_inst"
        env.PATH = "${prefix}/bin:${env.PATH}"
        env.LD_LIBRARY_PATH= "${prefix}/lib:${env.LD_LIBRARY_PATH}"
        env.CPATH= "${prefix}/include"
        sh 'printenv'
    }
    stage("Configure") {
        sh '''
            perl $MYMAKE/mymake.pl --prefix=$PWD/_inst --with-device=ch3 --disable-fortran --enable-g=dbg --disable-fast
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
            mpirun -n 2 ./cpi | tee -a asan.log || :

            zsh $SCRIPT_DIR/set-xfail.sh -j asan -c clang -o asan -q centos64 -m $netmod -f test/mpi/maint/jenkins/xfail.conf
            make -C test/mpi testing | tee -a asan.log || :
        '''
    }

    stage("Post") {
        sh '''
            perl $MYMAKE/report_make_log.pl asan.log clang 0
        '''
        archiveArtifacts(artifacts: "asan.log")
        junit("**/summary.junit.xml")
    }
}
