import hudson.model.*
import inm.macro.ide.ci.*
import static inm.macro.ide.ci.Logger.*

//Init logging...
logAppender = listener.getLogger().&println
debugLevel = Config.instance.params.DEBUG_LOGGING

inmBuildInParam = build.buildVariableResolver.resolve("INM_BUILD_LABEL")
toBeDeletedMarker = "TO_BE_DELETED"

info "INM_BUILD_LABEL: ${inmBuildInParam}"
ant = new AntBuilder()
if(debugLevel) {
    ant.project.getBuildListeners().firstElement().setMessageOutputLevel(3)
}

if(inmBuildInParam) {
    info "INM build passed in is: " + inmBuildInParam
    def allIdeBuildList = []
    def ideBuildManagable = true
    allIdeBuildList = IdeBuildInfo.retrieveIdeBuilds(
        Config.instance.params.HTTP_PUBLISH_DIR + System.getProperty("file.separator") + "builds",
        {line -> debug line},
        {line -> error line},
        {
            ideBuildManagable = false
            error "Failed to retrive IDE build list, please check!"
        }
    ).sort().reverse()
    def ideBuildMap = IdeBuildInfo.parseIdeBuildMapByRealBuild(allIdeBuildList)
    
    if(!ideBuildManagable) {
        build.setResult(Result.FAILURE)
        return false
    }
    
    def ideBuild = allIdeBuildList.find { it.dir.name == inmBuildInParam }
    if(ideBuild) {        
        if(ideBuild.junction) {
            info "Remove build junction directory ${ideBuild.dir.absolutePath}..."
            def result = true
            ideBuild.removeJunction(
                {line -> debug line},
                {line -> error line},
                {
                    result = false
                    error "Failed to remove junction directory ${ideBuild.dir.absolutePath}, please check!"
                }
            )
            if(result) {
                info "Remove build junction directory ${ideBuild.dir.absolutePath}...Done"
                def realIdeBuild4this = IdeBuildInfo.getRealIdeBuild(ideBuild, allIdeBuildList)
                if(realIdeBuild4this) {
                    debug "Check whether the corresponding real IDE build is ready to be deleted..."
                    def allJuncBuilds4sameBuild = ideBuildMap."${realIdeBuild4this.dir.name}"
                    if( !(allJuncBuilds4sameBuild?.any { it != ideBuild }) ) {
                        info "All corresponding junction directories for ${realIdeBuild4this.dir.name} have been deleted, check whether the real one needs to be deleted as well..."
                        if(new File(realIdeBuild4this.dir.absolutePath + System.getProperty("file.separator") + toBeDeletedMarker).exists()) {
                            info "${realIdeBuild4this.dir.name} is ready to be deleted, removing..."
                            ant.delete dir:realIdeBuild4this.dir
                        }
                    }
                }
            } else {
                build.setResult(Result.FAILURE)
                return false
            }
        } else {
            def allJuncBuilds4thisBuild = ideBuildMap."${ideBuild.dir.name}"
            if(allJuncBuilds4thisBuild) {
                info "There are still following junction directories pointing to this build:" 
                allJuncBuilds4thisBuild.each { info "  ${it.dir.name}" }
                info "Mark it as to be deleted and return..."
                ant.touch file:ideBuild.dir.absolutePath + System.getProperty("file.separator") + toBeDeletedMarker
            } else {
                info "There is no junction directory pointing to this build, directly delete it..."
                ant.delete dir:ideBuild.dir
            }
        }
        
        build.setResult(Result.SUCCESS)
        return true
    } else {
        error "Build ${inmBuildInParam} does not exist, exiting..."
        build.setResult(Result.ABORTED)
        return false
    }
} else {
    error "No INM build parameter passed in, exiting..."
    build.setResult(Result.NOT_BUILT)
    return true
}


