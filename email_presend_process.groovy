import javax.mail.Multipart
import javax.mail.internet.MimeBodyPart
import inm.macro.ide.ci.*
import static inm.macro.ide.ci.Logger.*
import static inm.macro.ide.ci.Utils.*

def msgBody = msg.getContent()
def bodyPart = msgBody.getBodyPart(0)
logger.println("Pre-processing email content...")
def newImVersion = build.buildVariableResolver.resolve(Config.instance.params.IDE_BUILD_PARAM_IM_VERSION)
def newSubmits = build.buildVariableResolver.resolve(Config.instance.params.IDE_BUILD_PARAM_NEW_SUBMIT)?.trim()
if(newImVersion || newSubmits) {
    def trigger = '<br><b>Triggered by: </b><br>'
    if(newSubmits) {
        trigger += "&nbsp;&nbsp;New submits:<o:p></o:p></span></p><ul>"
        newSubmits.split(Config.instance.params.NEW_SUBMIT_DELIMITER).each {
            trigger += "<li>${it}</li>"
        }
        trigger += "</ul>"
    }
    if(newImVersion) {
        trigger += "<p>&nbsp;&nbsp;New MACRO Info Model for: $newImVersion</p>"
    }    
    
    def newContent = bodyPart.getContent() + trigger

    bodyPart.setContent(newContent, "text/html")
}

logger.println("Pre-processing email content...done")