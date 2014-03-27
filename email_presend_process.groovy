import javax.mail.Multipart
import javax.mail.internet.MimeBodyPart

def msgBody = msg.getContent()
def bodyPart = msgBody.getBodyPart(0)
logger.println("Pre-processing email content...")
def newContent = bodyPart.getContent().replaceAll(
        "triggerCauseAction", 
        hudson.model.Hudson.getInstance().getRootUrl() + build.url + "triggerCauseAction"
    ).replaceAll("\\[ScriptTrigger\\] ", "")
bodyPart.setContent(newContent, "text/html")
logger.println("Pre-processing email content...done")