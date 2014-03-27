import com.jacob.activeX.*
import com.jacob.com.*

def cli = new CliBuilder(usage:'Convert MS Word to PDF')
cli.i( longOpt: 'input', argName: 'wordFile', required: true, args: 1, 'MS Word file to be converted' )
cli.o( longOpt: 'output', argName: 'pdfFile', required: true, args: 1, 'the destination pdf file' )

//--------------------------------------------------------------------------
def opt = cli.parse(args)
if (!opt) { System.exit 1 }

def wordFile = opt.i
def pdfFile = opt.o

def ant = new AntBuilder()

ant.delete file: pdfFile

def result = saveWordAsPDF wordFile, pdfFile

if(result) {
    System.exit 0
} else {
    System.exit 1
}

boolean saveWordAsPDF(String filePath,String outFile){
    ComThread.InitSTA()
    def wordCom=new ActiveXComponent("Word.Application")
    try{
        Dispatch wrdDocs=Dispatch.get(wordCom,"Documents").toDispatch()
        Dispatch wordDoc=Dispatch.call(wrdDocs,"Open",filePath, new Variant(true),new Variant(false)).toDispatch()
        Dispatch.invoke(wordDoc,"ExportAsFixedFormat",Dispatch.Method,
                   [outFile,new Variant(17),new Variant(false),new Variant(0),new Variant(0),
                    new Variant(0),new Variant(0),new Variant(false),new Variant(true),
                    new Variant(0),new Variant(false),new Variant(true),new Variant(false)] as Object[],new int[0])

        return true
    }catch(Exception es){
        es.printStackTrace()
        return false
    }finally{
        if(wordCom!=null) {
            println "Releasing resouce..."
            //wordCom.invoke("Quit",[] as Variant[])
            Dispatch.call(wordCom, "Quit")
            wordCom=null
            ComThread.Release()
        }
    }
}
