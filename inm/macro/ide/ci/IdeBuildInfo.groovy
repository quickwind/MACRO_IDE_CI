package inm.macro.ide.ci

class IdeBuildInfo extends InmBuildInfo {
    boolean junction
    static ideBuildPattern = /.*<([A-Z]+)>\s+(${buildPattern})$/
    
    IdeBuildInfo(File theDir, boolean junction) {
        super(theDir)
        this.junction = junction
    }
    
    IdeBuildInfo(String label, boolean junction) {
        super(label)
        this.junction = junction
    }
    
    int compareTo(other) {
        return super.compareTo(other)
    }
    
    boolean createJunction(String juncDir, Closure outputAppender=null, Closure errAppender=null, Closure errorHandler=null) {
        def success = true
        def result = CmdExecutor.exec(
            "cmd /C junction ${juncDir} ${dir?.name}",                 //Command string
            dir?.parent,      //working directory
            outputAppender,   //standard output logging appender
            errAppender,   //error output logging appender
            {
                success = false
                errorHandler?.call()                       //Error handling logic
            }
        )
        
        return success
    }
    
    boolean removeJunction(Closure outputAppender=null, Closure errAppender=null, Closure errorHandler=null) {
        def success = true
        def result = CmdExecutor.exec(
            "cmd /C junction -d ${dir?.name}",                 //Command string
            dir?.parent,      //working directory
            outputAppender,   //standard output logging appender
            errAppender,   //error output logging appender
            {
                success = false
                errorHandler?.call()                       //Error handling logic
            }
        )
        
        return success
    }
    
    static List retrieveIdeBuilds(String path, Closure outputAppender=null, Closure errAppender=null, Closure errorHandler=null) {
        def success = true
        def ideBuilds = []
        def result = CmdExecutor.exec(
            "cmd /C dir ${path}",                 //Command string
            path,      //working directory
            outputAppender,   //standard output logging appender
            errAppender,   //error output logging appender
            {
                success = false
                errorHandler()                       //Error handling logic
            }
        )
        
        if(success) {            
            result.eachLine {
                //println "DEBUG: |${it}|"
                def matcher = (it =~ ideBuildPattern)
                if(matcher.matches()) {
                    ideBuilds << new IdeBuildInfo(
                        new File(path + System.getProperty("file.separator") + matcher[0][2]),
                        (matcher[0][1] == "JUNCTION" ? true : false)
                       )
                }
            }
        }
        return ideBuilds
    }
    
    static Map parseIdeBuildMapByRealBuild(List ideBuilds) {
        def resultMap = [:]
        def currentRealBuild = null
        ideBuilds?.sort().each { 
            if(it.junction) {
                if(currentRealBuild) {
                    if( !(resultMap."$currentRealBuild") ) resultMap."$currentRealBuild" = []
                    resultMap."$currentRealBuild" << it
                } else {
                    if( !(resultMap.orphans) ) resultMap.orphans = []
                    resultMap.orphans << it 
                }
            } else {
                currentRealBuild = it.dir.name
            }
        }
        
        return resultMap
    }
    
    static IdeBuildInfo getRealIdeBuild(IdeBuildInfo ideBuild, List ideBuilds) {
        if(!ideBuild || ideBuild.junction == false) return ideBuild
        
        return ideBuilds?.sort().reverse().find {
            it.junction == false && it < ideBuild
        }
    }
}