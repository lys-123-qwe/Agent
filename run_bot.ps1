$java = "C:\Program Files\Java\jdk-21.0.10\bin\java.exe"
$jar = "D:\code\project-demo\youkeda_pj\target\youkeda_project-0.0.1-SNAPSHOT.jar"
$logFile = "D:\code\project-demo\youkeda_pj\bot_output.log"
$errFile = "D:\code\project-demo\youkeda_pj\bot_output.err"
$p = Start-Process -FilePath $java -ArgumentList "--enable-native-access=ALL-UNNAMED -jar $jar" -WindowStyle Hidden -PassThru -RedirectStandardOutput $logFile -RedirectStandardError $errFile
$p.WaitForExit()
