import inm.macro.ide.ci.*
import static inm.macro.ide.ci.Logger.*

//Init logging...
logAppender = manager.listener.logger.&println
debugLevel = Config.instance.params.DEBUG_LOGGING
System.setOut manager.listener.logger

try {
    def result = checkIM()
    
    info "Check IM result: $result"
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
        debug "Check whether ${Config.instance.params.REMOTE_IM_DIR} exists..."
        if(new File(Config.instance.params.REMOTE_IM_DIR).exists() == false) {
            error "Remote IM folder ${Config.instance.params.REMOTE_IM_DIR} doesn't exist. "
            notifyError()
            return result.asImmutable()
        }
        
        debug "Get list of newer build directories under folder ${Config.instance.params.REMOTE_IM_DIR}..."
        def allRemoteBuilds = []
        new File(Config.instance.params.REMOTE_IM_DIR).eachDir() { 
            def buildInfo = new InmBuildInfo(it)
            if(buildInfo.valid) {
                allRemoteBuilds << buildInfo
            }
        } 
        
        def allIdeBuildList = []
        def ideBuildManagable = true
    } catch (e) {
        error e.message
        return false
    }
    
    return true
}
