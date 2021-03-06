import inm.macro.ide.ci.*
import static inm.macro.ide.ci.Logger.*
import static inm.macro.ide.ci.Utils.*

//Init logging...
logAppender = manager.listener.logger.&println
debugLevel = Config.instance.params.DEBUG_LOGGING
System.setOut manager.listener.logger
allErrorLogs = new StringBuilder()
inm.macro.ide.ci.Logger.metaClass.'static'.invokeMethod = { String name, args ->
    def metaMethod = inm.macro.ide.ci.Logger.metaClass.getStaticMetaMethod(name, args)
    def result = null
    if(metaMethod) {
        result = metaMethod.invoke(delegate, args)
        if(name == "error") {
            allErrorLogs.append(result).append('<br/>')
        }
    }    
    result
}


try {
    ideBuildJob = hudson.model.Hudson.getInstance().getItem(Config.instance.params.IDE_BUILD_JOB)
    info "Next IDE build number: ${ideBuildJob.nextBuildNumber}"
    
    Map checkScmResult = checkSubmits()
    if(checkScmResult.integrationConflict) {
        error "There is clearcase conflict, skip this build..."
        return false
    }
    Map updateIMResult = updateIM(checkScmResult.newSubmitsFound && checkScmResult.newSubmitsIntegrated)
    
    if( (checkScmResult.newSubmitsFound && checkScmResult.newSubmitsIntegrated)|| updateIMResult.newIMfound) {
        info "Ready to start build"
        if(Config.instance.params.NEW_BUILD_LABEL_FOR_ONLY_IDE_CHNAGES && 
            (checkScmResult.newSubmitsFound && checkScmResult.newSubmitsIntegrated) && 
            checkScmResult.newRebasedInmBuild &&
            !updateIMResult.newIMfound
        ) {
            //There is only IDE code changes, mark the IDE version with code baseline build label...
            updateBuildInfo(checkScmResult.newRebasedInmBuild)
        }
        //Populate cause...
        triggerIdeBuild(checkScmResult, updateIMResult)
        return true
    } else {
        info "No new submit nor new IM version, no build is needed..."
        return false
    }
    
    return true
} catch(Exception e) {
    if(e instanceof MacroIdeCheckedException) return false   //skip already reported errors...
    
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
String executeCmd(cmdStr, description="") {
    if(description != null && description.trim().size() > 0) info description + "..."
    def result = CmdExecutor.exec(
        cmdStr,                 //Command string
        Config.instance.params.FEATURE_STREAM_PATH,      //working directory
        {line -> debug line},   //standard output logging appender
        {line -> error line},   //error output logging appender
        {                       //Error handling logic
            if(description != null && description.trim().size() > 0) error description + "...Failed!"
            throw new MacroIdeCheckedException("Fatal error, exit...")
        }
    )
    if(description != null && description.trim().size() > 0) info description + "...OK"
    return result.trim()
}

void triggerIdeBuild(Map checkScmResult, Map updateIMResult) {
    if((checkScmResult.newSubmitsFound && checkScmResult.newSubmitsIntegrated) || updateIMResult.newIMfound) {
        def buildParameters = new ArrayList()        
        
        if(checkScmResult.newSubmitsFound && checkScmResult.newSubmitsIntegrated) {
            buildParameters << new hudson.model.TextParameterValue(
                                    Config.instance.params.IDE_BUILD_PARAM_NEW_SUBMIT,
                                    checkScmResult.changeLogs.join(Config.instance.params.NEW_SUBMIT_DELIMITER))
        }
        if(updateIMResult.newIMfound) {
            buildParameters << new hudson.model.StringParameterValue(
                                    Config.instance.params.IDE_BUILD_PARAM_IM_VERSION, 
                                    updateIMResult.newIMversion)            
        }
        info "Schedule IDE build..."
        
        result = triggerBuild(Config.instance.params.IDE_BUILD_JOB, manager, buildParameters)
    }
}

Map updateIM(boolean hasNewIdeCodeChange) {
    Map result = [
        newIMfound:     false,
        newIMversion:   ""
    ]
    
    try {
        info "Updating Info Model files..."
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
        if(allRemoteBuilds && !hasNewIdeCodeChange) {
            info "There are remote INM build directories under folder ${Config.instance.params.REMOTE_IM_DIR}, populate IDE build list..."
            allIdeBuildList = IdeBuildInfo.retrieveIdeBuilds(
                Config.instance.params.HTTP_PUBLISH_DIR + System.getProperty("file.separator") + "builds",
                {line -> debug line},
                {line -> error line},
                {
                    ideBuildManagable = false
                    error "Failed to retrive IDE build list, please check!"
                    notifyError()
                }
            ).sort().reverse()
            if(ideBuildManagable) {
                debug "Existing IDE build list: "
                allIdeBuildList.each {
                    debug "  ${it}, junction: ${it.junction}"
                }
            }
        }
        def newerBuildDirs = allRemoteBuilds.findAll { it > new InmBuildInfo(CurrentBuildVer) }.sort().reverse().collect { it.dir }
        def oldBuildDirs = allRemoteBuilds.findAll { it <= new InmBuildInfo(CurrentBuildVer) }.sort().reverse().collect { it.dir }
        
        if(oldBuildDirs) {
            oldBuildDirs.each { oldDir ->
                info "Found remote old build dir ${oldDir.absolutePath}, remove it..."
                executeAndJustNotifyError("deleting remote dir ${oldDir.absolutePath}") {
                    info "Deleting old dir ${oldDir.absolutePath}..."
                    notifyInfo "Found remote old build dir ${oldDir.absolutePath}, remove it..."
                    ant.delete dir:oldDir
                } 
                if(!hasNewIdeCodeChange && ideBuildManagable) {
                    checkAndCreateJunction(allIdeBuildList, oldDir.name)
                }
            }
        }
        
        if(!newerBuildDirs) {   
            info "No new INM build available, returning..."
            return result.asImmutable()
        }
        
        debug "Iterate all the newer build dir to get the latest build dir..."
        def latestBuildDir = null
        def latestBuildFinalDir = null
        def latestBuildDirHasUpdate = false
        newerBuildDirs.each{ buildDir -> 
            if(latestBuildDir) {
                //Newer build is found, for the older ones, now just simply remove it and create junction/link to the previous build, 
                //this may not be safe as there might be IM update in some older build dir, but just keep simple logic instead of initiate 
                //build for these....hopefully we don't meet this situation. 
                info "Newer build has been identified as ${latestBuildDir.name}, removing older one ${buildDir.absolutePath}..."
                executeAndJustNotifyError("deleting remote dir ${buildDir.absolutePath}") {
                    notifyInfo "Newer build has been identified as ${latestBuildDir.name}, removing older one ${buildDir.absolutePath}..."
                    ant.delete dir:buildDir
                } 
                if(!hasNewIdeCodeChange && ideBuildManagable) {
                    checkAndCreateJunction(allIdeBuildList, buildDir.name)
                }
            } else {
                if(remoteBuildIsStable(buildDir)) {
                    def remoteImFinalDir = unpackRemoteBuild(buildDir)
                    boolean missingFile = false
                    boolean hasUpdate = false
                    
                    imFileNames.each { imFile -> 
                        def remotefile = new File(remoteImFinalDir + System.getProperty("file.separator") + imFile)
                        def localfile = new File(locaIMabsolutePath + System.getProperty("file.separator") + imFile)
                        if(!remotefile.exists() || !remotefile.canRead()) {
                            error "IM file ${imFile} is missing or not readable in directory ${remoteImFinalDir}."
                            missingFile = true
                        } else if(hasUpdate == false){
                            if(localfile.exists()) {
                                ant.checksum(file:remotefile, property: "${imFile}_remote" )
                                ant.checksum(file:localfile, property: "${imFile}_local" )
                                if( ant.project.properties."${imFile}_remote" != ant.project.properties."${imFile}_local" ) {
                                    debug "There is update for ${imFile}, checksums are " + ant.project.properties."${imFile}_remote" + ", and " + ant.project.properties."${imFile}_local"
                                    hasUpdate = true
                                }
                            } else {
                                hasUpdate = true
                            }
                        }
                    }
                    if(missingFile) {
                        //Don't know what to do, just notify build manager and ignore it...
                        notifyError()
                    } else {
                        latestBuildDir = buildDir
                        latestBuildFinalDir = remoteImFinalDir
                        
                        if(hasUpdate) {                            
                            latestBuildDirHasUpdate = true                            
                            
                            info "There are IM changes, copying the latest IM files from dir ${latestBuildDir.absolutePath}..."
                            ant.copy (todir:locaIMabsolutePath, overwrite: true) {
                                fileset dir:latestBuildFinalDir
                            }
                            updateBuildInfo(latestBuildDir.name)
                            
                            info "Remove remote dir ${latestBuildDir.absolutePath} after copy..."
                            executeAndJustNotifyError("deleting remote dir ${latestBuildDir.absolutePath}") {
                                notifyInfo "Remove remote dir ${latestBuildDir.absolutePath} after copy..."
                                ant.delete dir:latestBuildDir
                            } 
                        } else {                            
                            info "No update in this build ${buildDir.absolutePath}, removing it from remote..."
                            updateBuildInfo(latestBuildDir.name)
                            executeAndJustNotifyError("deleting remote dir ${buildDir.absolutePath}") {
                                notifyInfo "No update in this build ${buildDir.absolutePath}, removing it from remote..."
                                ant.delete dir:buildDir
                            } 
                            if(!hasNewIdeCodeChange && ideBuildManagable) {
                                checkAndCreateJunction(allIdeBuildList, buildDir.name)
                            }
                        }
                    }                    
                }
            }
        }
        
        if(!latestBuildDirHasUpdate){
            info "No valid IM version update found, returning..."
            return result.asImmutable()
        }        
        
        result.newIMfound = true        
        result.newIMversion = latestBuildDir.name
        
        return result.asImmutable()
    } catch(ex) {
        error "Exception while updating IM file: ${ex.message}"
        notifyError()
        return result.asImmutable()
    }
}

void updateBuildInfo(String label) {
    new FileWriter(
        Config.instance.params.FEATURE_STREAM_PATH + 
        System.getProperty("file.separator") + 
        Config.instance.params.LOCAL_IM_DIR + 
        System.getProperty("file.separator") + 
        "buildinfo.dat"
    ).withWriter { 
        it.write(label)
    }
}

Map checkSubmits() {
    Map result = [
        newSubmitsFound:        false,
        newSubmitsIntegrated:   false,
        integrationConflict:    false,
        newRebasedInmBuild:     "",
        changeLogs:             []        
    ]
    
    try {
        //Get vob name
        def vobName = executeCmd(Config.instance.ccCmds.GET_VOB_NAME, "Get VOB name").split('@').toList().get(1)
        debug "VOB name: ${vobName}"

        //Check whether there is submitted changes from child streams...
        def allActsStr = executeCmd(Config.instance.ccCmds.GET_ALL_ACTIVITIES, "Get all activities list")
        if(allActsStr.trim()) {
            def allBaselineInfo = executeCmd(Config.instance.ccCmds.GET_BASELINE_INFO + vobName, "Get all baseline info")

            info "Check activities..."
            
            def activityList = allActsStr.split(System.getProperty("line.separator").toString()).toList()
            for(int i = 0; i < activityList.size(); i++) {
                def activity = activityList.get(i)
                debug "Checking #" + (i+1) + " activity: " + activity
                def checkActResult = executeCmd(Config.instance.ccCmds.CHECK_ACTIVITY + activity + "@" + vobName)
                    
                //def checkActResultList = checkActResult.split(',').toList()
                debug "  Check activity result: ${checkActResult}"
                if( checkActResult ==~ /.*,.*,,/ ) {
                    debug "  Activity not submitted, continue..."
                } else {
                    debug "  Submitted activity, skip..."
                    continue
                }
                
                def checkActChangesResult = executeCmd(Config.instance.ccCmds.GET_ACTIVITY_CHANGESET + activity + "@" + vobName)
                if(checkActChangesResult.isEmpty()) {
                    debug "  There is no change in this activity, skip..."
                    continue
                } else {
                    debug "  There is changes in this activity: " + checkActChangesResult
                }
                
                if(allBaselineInfo.contains("${activity}@${vobName}")) {
                    debug "  This activity has been included in existing baseline, skip..."
                    continue
                }
                
                def activityPtnMatcher = (activity =~ /^deliver.([^_]*)_.*/)
                if( activityPtnMatcher.matches() ) {
                    def contrib_acts = executeCmd(Config.instance.ccCmds.GET_CONTRIB_ACTIVITY + activity + "@" + vobName)
                    debug "  This activity is submitted from child streams, its contribution activities: " + contrib_acts
                    contrib_acts.split(',').eachWithIndex { contribAct, index ->
                        def contribHeadline = executeCmd(Config.instance.ccCmds.GET_ACTIVITY_HEADLINE + " ${contribAct}")
                        debug "  #${index} contrib activity headline: " + contribHeadline
                        if(contribHeadline ==~ /rebase .* on .*/) {
                            debug "  This is a rebase activity, ignore..."
                        } else {
                            result.changeLogs << "By " + activityPtnMatcher[0][1] + ": ${contribHeadline}"
                        }
                    }
                } else {
                    def headline = executeCmd(Config.instance.ccCmds.GET_ACTIVITY_HEADLINE + " activity:${activity}@${vobName}")
                    debug "  Headline: " + headline
                    result.changeLogs << headline
                }
            }
            
            info "Check activities...Finished"
            if(allErrorLogs.toString().contains("The specified integration activity is still in progress")) {
                error "There is some conflict with some inprogress pushacts, cancel further clearcase operations..."
                notifyError()
                result.integrationConflict = true
                return result.asImmutable()
            }
                
            if( result.changeLogs.size() > 0 ) {
                result.newSubmitsFound = true
                info "There are " + result.changeLogs.size() + " changes submitted: "
                for(int i = 0; i < result.changeLogs.size(); i++) {
                    info "  " + result.changeLogs[i]
                }
                info "Will trigger build... "
            } else {
                info "There is no change submitted, returning..."
                return result.asImmutable()
            }

            //check whether need to rebase to latest main build...
            def ccCmdGetOwnBuildLabelResult = executeCmd(Config.instance.ccCmds.GET_OWN_BUILD_BASELINE, "Get own stream INM build label")
            def buildLabelMatcher = (ccCmdGetOwnBuildLabelResult =~ /.*:(.*)@.*/)
            if(!buildLabelMatcher.matches()) {
                error "Couldn't parse own INM build baseline label, output: " + ccCmdGetOwnBuildLabelResult
                return result.asImmutable()
            }
            def ownBuildLabel = buildLabelMatcher[0][1]
            debug "Current own INM build label: " + ownBuildLabel

            def ccCmdGetMainbranchBuildLabelResult = executeCmd(Config.instance.ccCmds.GET_MAINBUILD_BASELINE + " ${Config.instance.params.MAINBUILD_STREAM_NAME}", "Get INM main build stream label")
            buildLabelMatcher = (ccCmdGetMainbranchBuildLabelResult =~ /.*:(.*)@.*/)
            if(!buildLabelMatcher.matches()) {
                error "Couldn't parse main build baseline label, output: " + ccCmdGetMainbranchBuildLabelResult
                return result.asImmutable()
            }
            def mainBuildLabel = buildLabelMatcher[0][1]
            debug "Current main INM build label: " + mainBuildLabel

            if(ownBuildLabel != mainBuildLabel) {
                info "Current own INM build label " + ownBuildLabel + " does not equal to main INM build label " +  mainBuildLabel + ", rebasing..."
                executeCmd(Config.instance.ccCmds.REBASE)
                executeCmd(Config.instance.ccCmds.REBASE_COMPLETE)
                info "Rebase done."
                result.newRebasedInmBuild = mainBuildLabel
            }           

            //Update view...
            tryTwice("Update view") {   //Sometimes it may fail but retry will make thru...
                executeCmd(Config.instance.ccCmds.UPDATE_VIEW)
            }

            //make baseline...
            def newBaseline = "${Config.instance.params.BASELINE_PREFIX}_${ideBuildJob.nextBuildNumber}"
            executeCmd(Config.instance.ccCmds.MKBASELINE +  "${vobName} ${newBaseline}", "Make new baseline")

            executeCmd(Config.instance.ccCmds.LABEL_BASELINE +  " ${newBaseline}@${vobName}", "Labeling new baseline")

            def streamName = executeCmd(Config.instance.ccCmds.GET_STREAM_NAME, "Get feature stream name")
            streamName = streamName.trim()

            executeCmd(Config.instance.ccCmds.RECOMMEND_BASELINE +  " ${newBaseline} ${streamName}@${vobName}", "Recommend new baseline" )
            
            result.newSubmitsIntegrated = true
            return result.asImmutable()
        } else {
            info "No activity at all, returning..."
            return result.asImmutable()
        }
    } catch(ex) {
        error "Exception while checking new submits: ${ex.message}"
        notifyError()
        return result.asImmutable()
    }
}

void executeAndJustNotifyError(description, Closure logic){
    try {
        logic()
    } catch(ignoreEx) { 
        error "Exception while ${description}: ${ignoreEx.message}, skipping..."
        notifyError()
    }
}

void tryTwice(description, Closure logic){
    try {
        info "${description}..."
        logic()
        info "${description}...Done"
    } catch(ignoreEx) { 
        error "Exception while ${description}: ${ignoreEx.message}, retry..."
        sleep 5000
        logic()
    }
}

boolean checkAndCreateJunction(List ideBuildList, String buildLabel) {
    def result = true
    def allRealIdeBuildList = ideBuildList.findAll { it.junction == false }
    if( !(ideBuildList.find { it.dir.name == buildLabel }) ) {
        info "No IDE build before for ${buildLabel}, try to link it to previous real one..."
        def previousRealIdeBuild = allRealIdeBuildList.find { it < new IdeBuildInfo(buildLabel, false) }

        previousRealIdeBuild?.createJunction(
            buildLabel,
            {line -> debug line},
            {line -> error line},
            {
                result = false
                error "Failed to create junction for build ${buildLabel}, please check!"
                notifyError()
            })
    }
    
    return result
}

String unpackRemoteBuild(File remoteDir) {
    info "Checking whether there is zip file..."
    File zipFile = null
    remoteDir.eachFileMatch(~/.*\.zip/) { zipFile = it }
    if(zipFile) {
        info "There is zip file ${zipFile.name}, unzip it..."
        def ant = new AntBuilder()
        ant.unzip(src:zipFile, dest:"${remoteDir.canonicalPath}")
        String resultDir = ""
        remoteDir.eachFileRecurse(groovy.io.FileType.FILES) {
            if(!resultDir && it.name =~ /.*\.json$/) {
                resultDir = it.parentFile.canonicalPath
            }
        }
        if(resultDir) {
            return resultDir
        } else {
            return remoteDir.canonicalPath
        }
    } else {
        info "No zip file, just return current dir..."
        return remoteDir.canonicalPath
    }
}

boolean remoteBuildIsStable(File remoteDir)  {
    info "Checking directory for build ${remoteDir.absolutePath}..."               
                
    if(Config.instance.params.REMOTE_IM_READY_FLAG_FILE) {
        //Use flag file to mark the build stable...
        def flagFile = new File(remoteDir.canonicalPath + System.getProperty("file.separator") + Config.instance.params.REMOTE_IM_READY_FLAG_FILE)
        return flagFile.exists()
    } else {   
        //Old  way...
        def dirSize = 0
        def retryNum = 3
        debug "Dir size: " + directorySize(remoteDir)
        while(dirSize != directorySize(remoteDir) && retryNum > 0) {
            dirSize = directorySize(remoteDir)
            sleep 5000 //sleep a while and check again the size to make sure it is stable...
            retryNum = retryNum -1
        }
        sleep 5000 //sleep another x secs to make sure it is really stable after at least 2 waits...
        if(dirSize != directorySize(remoteDir)) {
            info "${remoteDir.absolutePath} directory is not stable, skipping..."
            return false
        } else {
            return true
        }
    }
}

void notifyError() {
    notifyMonitor "Error log: <br/>" + allErrorLogs.toString(), manager.build.url
}

void notifyInfo(String msg) {
    notifyMonitor msg, manager.build.url
}
