package inm.macro.ide.ci

class Utils {
    static long directorySize(File dir) {
        long size = 0;
        dir.eachFileRecurse { size += it.size() }
        return size
    } 
    
    static def triggerBuild(String jobName, def manager, def params=null) {
        def job = hudson.model.Hudson.getInstance().getItem(jobName)
        if(params) 
            return job.scheduleBuild2(1, new hudson.model.Cause.UpstreamCause(manager.build), new hudson.model.ParametersAction(params))
        else 
            return job.scheduleBuild2(1, new hudson.model.Cause.UpstreamCause(manager.build))
    }
    
    static void notifyMonitor(String msg, def location) {
        def monitorJob = hudson.model.Hudson.getInstance().getItem(Config.instance.params.MONITOR_JOB)
        def cause = new hudson.model.Cause.RemoteCause(
            hudson.model.Hudson.getInstance().getRootUrl() + location, msg)
        def causeAction = new hudson.model.CauseAction(cause)
        
        hudson.model.Hudson.getInstance().getQueue().schedule2(monitorJob, 0, causeAction)
    }
}