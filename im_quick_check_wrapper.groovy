import org.codehaus.groovy.control.CompilerConfiguration

buildLibPath = System.getenv("MACRO_IDE_CI_LIB_PATH")
CompilerConfiguration cc = new CompilerConfiguration()
cc.setClasspath(buildLibPath)
Binding binding = new Binding()
binding.setVariable("manager", manager)
GroovyShell shell = new GroovyShell(this.class.classLoader, binding, cc)

return shell.evaluate(new File(buildLibPath + System.getProperty("file.separator") + "im_quick_check_process.groovy"));