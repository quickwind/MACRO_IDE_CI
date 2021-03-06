import inm.macro.ide.ci.*
import static inm.macro.ide.ci.Logger.*

//Init logging...
logAppender = manager.listener.logger.&println
debugLevel = Config.instance.params.DEBUG_LOGGING
System.setOut manager.listener.logger

try {
    buildResult = manager.build.result
    buildNumber = manager.build.number
    newImVersion = manager.build.buildVariableResolver.resolve(Config.instance.params.IDE_BUILD_PARAM_IM_VERSION)
    newSubmits = manager.build.buildVariableResolver.resolve(Config.instance.params.IDE_BUILD_PARAM_NEW_SUBMIT)?.trim()
    unstable = false
    
    if(buildResult.toString() == 'SUCCESS') {
        info "Determine whether there are test failure(s)..."        
        if(manager.build.log.find(/\[ERROR\] There are test failures\./)  || 
        manager.build.log.find(/Smoke test for MACRO syntax validation \- failed!/)) {
           info "Build passed but there are test failure(s), mark the build as unstable..."
           manager.addWarningBadge("Test Failed")
           manager.createSummary("warning.gif").appendText("<h3>There are test failures, please check console for details!</h3>", false, false, false, "red")
           unstable = true
           manager.buildUnstable()           
        }
        
        if(!newImVersion && !newSubmits) {
            info "This build is triggered manually, do not publish and submit..."
            return true
        }
        
        if(newImVersion) {
            manager.createSummary("star-gold.png").appendText("<h4>New MACRO IM version: $newImVersion.<h4/>", false, true, true, "green")
        }
        
        if(newSubmits) {
            def newSubmitsFormated = ""
            newSubmits.split(Config.instance.params.NEW_SUBMIT_DELIMITER).each {
                newSubmitsFormated += "<li><span>${it}</span></li>"
            }
            manager.createSummary("star-gold.png").appendText("<h4>New submits:<br/><ul>${newSubmitsFormated}</ul><h4/>", false, true, true, "green")
        }
        
        info "Publish build outputs..."
        if(! publish()) {
            error "Publish build failed"
            manager.addErrorBadge("Publish build failed")
            manager.createSummary("error.gif").appendText("<h4>Build failed to publish, see console for details.<h4/>", false, true, false, "red")
            manager.buildUnstable()
        } else {
            info "Publish build outputs...OK"
            manager.createSummary("installer.png").appendText("<h4>Build has been published.<h4/>", false, true, true, "green")
        }
        
        if(!unstable && newSubmits && Config.instance.params.CODE_SUBMIT) {
            info "Build is triggered by changelogs, submit them..."
            manager.createSummary("star.png").appendText("<h4>Feature stream baseline: ${Config.instance.params.BASELINE_PREFIX}_${buildNumber}<h4/>", false, true, true, "green")
            if(! submit()) {
                error "Submit failed"
                manager.addWarningBadge("Submit failed")
                manager.createSummary("error.gif").appendText("<h4>Failed to submit changes to main build, see console for details.<h4/>", false, true, false, "red")
            } else {
                manager.createSummary("star-gold.png").appendText("<h4>Changes have been submitted to main build.<h4/>", false, true, true, "green")
            }
        }
    }
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
boolean publish() {
    try {
        ant = new AntBuilder()
        if(debugLevel) {
            ant.project.getBuildListeners().firstElement().setMessageOutputLevel(3)
        }
        def pathDelimiter = System.getProperty("file.separator")
        def inmBuildInfoFile = new File("${Config.instance.params.FEATURE_STREAM_PATH}${pathDelimiter}${Config.instance.params.LOCAL_IM_DIR}${pathDelimiter}buildinfo.dat")
        
        ant.with {
            echo "Publish to latest directory..."
            delete dir:"${Config.instance.params.HTTP_PUBLISH_DIR}${pathDelimiter}latest"
            mkdir  dir: "${Config.instance.params.HTTP_PUBLISH_DIR}${pathDelimiter}latest${pathDelimiter}repository"            
            copy (todir:"${Config.instance.params.HTTP_PUBLISH_DIR}${pathDelimiter}latest") {
                fileset(dir:"${Config.instance.params.FEATURE_STREAM_PATH}${pathDelimiter}${Config.instance.params.STANDALONE_PRODUCT_PATH}", includes:'*.zip')
            }            
            copy (todir:"${Config.instance.params.HTTP_PUBLISH_DIR}${pathDelimiter}latest${pathDelimiter}repository") {
                fileset(dir:"${Config.instance.params.FEATURE_STREAM_PATH}${pathDelimiter}${Config.instance.params.REPOSITORY_PATH}", includes:'**/*')
            }
            if(Config.instance.params.IDE_USER_GUIDE_PDF_CONVERTION) {
                try {
                    convertWord2PDF(
                        "${Config.instance.params.FEATURE_STREAM_PATH}${pathDelimiter}${Config.instance.params.IDE_DOC_PATH}${pathDelimiter}${Config.instance.params.IDE_USER_GUIDE_DOC}",
                        "${Config.instance.params.HTTP_PUBLISH_DIR}${pathDelimiter}latest${pathDelimiter}${Config.instance.params.IDE_USER_GUIDE_PDF}"
                    )
                } catch(ex) {
                    error ex
                    error "User guide convertion from MS Word to PDF failed, publish MS Word version..."
                    copy (todir:"${Config.instance.params.HTTP_PUBLISH_DIR}${pathDelimiter}latest", 
                        file:"${Config.instance.params.FEATURE_STREAM_PATH}${pathDelimiter}${Config.instance.params.IDE_DOC_PATH}${pathDelimiter}${Config.instance.params.IDE_USER_GUIDE_DOC}") 
                }
            } else {  /**/
                copy (todir:"${Config.instance.params.HTTP_PUBLISH_DIR}${pathDelimiter}latest",
                    file:"${Config.instance.params.FEATURE_STREAM_PATH}${pathDelimiter}${Config.instance.params.IDE_DOC_PATH}${pathDelimiter}${Config.instance.params.IDE_USER_GUIDE_DOC}") 
            }
            echo "Publish to latest directory...OK"
        }
        
        if(inmBuildInfoFile.exists()) {
            def inmBuildInfo
            inmBuildInfoFile.withReader { inmBuildInfo = it.readLine() }
            if(inmBuildInfo != null && inmBuildInfo.trim() != "") {
                def allIdeBuildList = []
                def result = true
                allIdeBuildList = IdeBuildInfo.retrieveIdeBuilds(
                    Config.instance.params.HTTP_PUBLISH_DIR + System.getProperty("file.separator") + "builds",
                    {line -> debug line},
                    {line -> error line},
                    {
                        result = false
                        error "Failed to retrive IDE build list, please check!"
                    }
                ).sort().reverse()
                if(!result) return false //treat as error...
                
                def existingIdeBuild = allIdeBuildList.find { it.dir.name == inmBuildInfo }
                if(existingIdeBuild && existingIdeBuild.junction) {
                    info "There is existing junction IDE build, remove the junction link..."
                    existingIdeBuild.removeJunction(
                        {line -> debug line},
                        {line -> error line},
                        {
                            result = false
                            error "Failed to retrive IDE build list, please check!"
                        }
                    )
                    if(result) {
                        info "There is existing junction IDE build, remove the junction link...Done"
                    } else {
                        return false //treat as error...
                    }
                }
                
                ant.with {
                    echo "Publish to builds directory..."
                    mkdir  dir: "${Config.instance.params.HTTP_PUBLISH_DIR}${pathDelimiter}builds${pathDelimiter}${inmBuildInfo}"
                    delete (includeemptydirs: 'true') {
                        fileset(dir:"${Config.instance.params.HTTP_PUBLISH_DIR}${pathDelimiter}builds${pathDelimiter}${inmBuildInfo}", includes:'**/*')
                    }                                        
                    zip (
                        compress:'false',
                        destfile:"${Config.instance.params.HTTP_PUBLISH_DIR}${pathDelimiter}builds${pathDelimiter}${inmBuildInfo}${pathDelimiter}macro-ide-all-in-one-package.zip", 
                        basedir:"${Config.instance.params.HTTP_PUBLISH_DIR}${pathDelimiter}latest"
                    )
                    checksum (fileext:".md5" ){
                        fileset(dir:"${Config.instance.params.HTTP_PUBLISH_DIR}${pathDelimiter}builds${pathDelimiter}${inmBuildInfo}") {
                            include(name:"**/*")  
                            exclude(name:"**/*.md5")
                        }
                    } 
                    echo "Publish to builds directory...OK"
                }
            }
        }
    } catch (e) {
        error e.message
        return false
    }
    
    return true
}

boolean submit() {
    try {
        Process submitProcess = "cmd /K ${Config.instance.params.INMTOOLS_BAT}".execute(null, new File(Config.instance.params.FEATURE_STREAM_PATH))
        ExpectSession session = new ExpectSession(submitProcess)
        session.expect (["${Config.instance.params.FEATURE_STREAM_PATH}>":                                   {session.send("submit\n")}  ], 10*1000)
        session.expect (['Submit new activities? (yes/no) [YES]:':                                           {session.send("\n")}        ], 120*1000)
        session.expect (['- SET TO ONHOLD? (if this submit is waiting for another submit) (yes/no) [NO]:':   {session.send("\n")}        ], 60*1000)
        session.expect (['No changes since last baseline. You may have already baseline created.':           {}                          ], 60*1000)
        session.expect (['Is this OK? (yes/no) [YES]:':                                                      {session.send("\n")}        ], 60*1000)
        session.expect (['This program succeeded.':                                                          {}                          ], 160*1000)
        session.expect (["${Config.instance.params.FEATURE_STREAM_PATH}>":                                   {session.send("exit\n")}    ], 20*1000)
        submitProcess.waitForOrKill(20000)
    } catch (Exception e) {
        error e.message
        return false
    }
    return true
}

void convertWord2PDF(String wordFile, String pdfFile) {
    info "Converting ${wordFile} to ${pdfFile}..."
    def result = CmdExecutor.exec(
        "groovy Word2PDF.groovy -i \"${wordFile}\" -o \"${pdfFile}\"",                 //Command string
        System.env.MACRO_IDE_CI_LIB_PATH,      //working directory
        {line -> debug line},   //standard output logging appender
        {line -> error line},   //error output logging appender
        {                       //Error handling logic
            error "Converting ${wordFile} to ${pdfFile} failed!"
            throw new MacroIdeCheckedException("Fatal error, exit...")
        }
    )
    info "Converting ${wordFile} to ${pdfFile}...OK"
}

