buildLibPath = System.getenv("MACRO_IDE_CI_LIB_PATH")
Binding binding = new Binding()
binding.setVariable("msg", msg)
binding.setVariable("build", build)
binding.setVariable("logger", logger)
binding.setVariable("cancel", cancel)
GroovyShell shell = new GroovyShell(this.class.classLoader, binding)

return shell.evaluate(new File(buildLibPath + System.getProperty("file.separator") + "email_presend_process.groovy"));