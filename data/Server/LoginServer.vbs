' LoginServer.vbs - L2J4Team Login Server (atualizado)

Dim shell, fso, path, javaCmd, scriptDir
Set shell = WScript.CreateObject("WScript.Shell")
Set fso = WScript.CreateObject("Scripting.FileSystemObject")
scriptDir = fso.GetParentFolderName(WScript.ScriptFullName)

' Tenta JAVA_HOME primeiro, depois PATH
path = shell.Environment.Item("JAVA_HOME")
If path = "" Then
    javaCmd = "java"
Else
    If InStr(path, "\bin") = 0 Then
        path = path + "\bin\"
    Else
        path = path + "\"
    End If
    javaCmd = """" & Replace(path, "\\", "\") & "java.exe"""
End If

Dim args
args = " -Xms32m -Xmx64m -server -XX:+UseSerialGC"
args = args & " -cp """ & scriptDir & "\lib\*" & ";" & scriptDir & "\l2jserver.jar"""
args = args & " net.sf.l2j.loginserver.L2LoginServer"

shell.Run "cmd /c title L2J4Team LoginServer && " & javaCmd & args, 1, False
