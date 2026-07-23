$logFile = "D:\code\project-demo\youkeda_pj\bot_output.log"
$errFile = "D:\code\project-demo\youkeda_pj\bot_output.err"
$p = Start-Process -FilePath "java" -ArgumentList "-jar D:\code\project-demo\youkeda_pj\target\youkeda_project-0.0.1-SNAPSHOT.jar" -WindowStyle Hidden -PassThru -RedirectStandardOutput $logFile -RedirectStandardError $errFile
$p.WaitForExit()
