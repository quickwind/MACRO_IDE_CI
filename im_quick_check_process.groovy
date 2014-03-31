import inm.macro.ide.ci.*
import static inm.macro.ide.ci.Logger.*
import static inm.macro.ide.ci.Utils.*

//Init logging...
logAppender = manager.listener.logger.&println
debugLevel = Config.instance.params.DEBUG_LOGGING
System.setOut manager.listener.logger

try {
    def result = checkIM()
    
    info "Check IM result: $result"
    if(result) {
        result = triggerBuild(Config.instance.params.BUILD_TRIGGER_JOB, manager)
        info "Schedule result: $result"
    }
    return true
} catch(Exception e) {
    error "Exception caught: "
    error e.toString()
    e.getStackTrace().each {
        error it.toString()
    }
    manager.addErrorBadge("Post-build exception!")
    manager.createSummary("error.gif").appendText("<h4>Post-build processing exception, please check console log.<h4/>", false, true, false, "red")
    manager.buildUnstable()
    return false
}

//=============== function definition =====================
boolean checkIM() {
    try {
        def imFileNames = Config.instance.params.IM_FILE_LIST
        def remoteBaseDir = Config.instance.params.REMOTE_IM_DIR
        def CurrentBuildVer = ""
        def locaIMabsolutePath = Config.instance.params.FEATURE_STREAM_PATH + System.getProperty("file.separator") + Config.instance.params.LOCAL_IM_DIR
        def inmBuildInfoFile = new File(locaIMabsolutePath + System.getProperty("file.separator") + "buildinfo.dat")
                
        def ant = new AntBuilder()
        if(debugLevel) {
            ant.project.getBuildListeners().firstElement().setMessageOutputLevel(3)
        }
        
        if(inmBuildInfoFile.exists()){
            inmBuildInfoFile.withReader { CurrentBuildVer = it.readLine() }
        }        
        debug "Current IM version for IDE is $CurrentBuildVer"
        debug "Check whether ${remoteBaseDir} exists..."
        if(new File(remoteBaseDir).exists() == false) {
            fail "Remote IM folder ${remoteBaseDir} doesn't exist. "
            return false
        }
        
        debug "Get list of newer build directories under folder ${remoteBaseDir}..."
        def allRemoteBuilds = []
        new File(remoteBaseDir).eachDir() { 
            def buildInfo = new InmBuildInfo(it)
            if(buildInfo.valid) {
                allRemoteBuilds << buildInfo
            }
        } 
        
        def newerBuildDirs = allRemoteBuilds.findAll { it > new InmBuildInfo(CurrentBuildVer) }.sort().reverse().collect { it.dir }
        
        if(!newerBuildDirs) {   
            info "No new INM build available, returning..."
            return false
        } else {
            info "There are new INM build available, trigger further checking..."
            return true
        }
    } catch (e) {
        error e.message
        return false
    }
    
    return true
}

void fail(String reason) {
    error "Build failed: ${reason}"
    manager.createSummary("error.gif").appendText("<h4>${reason}, see console for details.<h4/>", false, true, false, "red")
    manager.buildFailure()  
}

void unstable(String reason) {
    error "${reason}"
    manager.createSummary("warning.gif").appendText("<h4>${reason}, please check console for details!</h4>", false, false, false, "red")
    manager.buildUnstable()  
}