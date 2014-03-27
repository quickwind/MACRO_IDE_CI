package inm.macro.ide.ci

class Logger {
    static boolean debugLevel = false
    static boolean printTM = true
    static Closure logAppender
    
    static String getCurrentTimestamp(){
        //return printTM ? "["+String.format("%1\$tY-%1\$tm-%1\$td %1\$tH:%1\$tM:%1\$tS.%1\$tL", Calendar.instance)+"]" : ""
        return printTM ? "["+String.format("%1\$tH:%1\$tM:%1\$tS", Calendar.instance)+"]" : ""
    }
    
    private static Closure getLogAppender() {
        if(logAppender && logAppender.getParameterTypes().size() == 1) {
            return logAppender
        } else {
            return this.&println
        }
    }

    static String debug(msg) {
        def logMsg = getCurrentTimestamp() + "[DEBUG]: ${msg}"
        if(debugLevel) {
            getLogAppender()?.call logMsg
        }
        return logMsg
    }

    static String error(msg) {
        def logMsg = getCurrentTimestamp() + "[ERROR]: ${msg}"
        getLogAppender()?.call logMsg
        return logMsg
    }

    static String info(msg) {
        def logMsg = getCurrentTimestamp() + "[INFO ]: ${msg}"
        getLogAppender()?.call logMsg
        return logMsg
    }   
}