package inm.macro.ide.ci

class InmBuildInfo implements Comparable {
    String srNum
    String spNum
    String buildNum
    File dir
    static buildPattern = /INM_SR(\d+\.\d+)(-SP(\d+\.?\d?))?_(\d+)_build/
    
    InmBuildInfo(File theDir) {
        dir = theDir
        def matcher = (dir?.name =~ buildPattern)
        if(matcher.matches()) {
            srNum = matcher[0][1]
            spNum = matcher[0][3]
            buildNum = matcher[0][4]
        }
    }
    
    InmBuildInfo(String label) {
        dir = new File(label)
        def matcher = (label =~ buildPattern)
        if(matcher.matches()) {
            srNum = matcher[0][1]
            spNum = matcher[0][3]
            buildNum = matcher[0][4]
        }
    }
    
    int compareTo(other) {
        if((srNum <=> other?.srNum) != 0) {
            return srNum <=> other?.srNum
        } else if((spNum <=> other?.spNum) != 0) {
            return spNum <=> other?.spNum
        } else {
            return (buildNum as int) <=> (other?.buildNum as int)
        }
    }
    
    boolean isValid() {
        return (srNum != null) && (buildNum != null)
    }
    
    String toString() {
        if(isValid()) {
            return spNum ? "INM_SR${srNum}-SP${spNum}_${buildNum}_build" : "INM_SR${srNum}_${buildNum}_build"
        } else {
            return ""
        }
    }
}