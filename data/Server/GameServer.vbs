' GameServer.vbs - L2J4Team Game Server (atualizado)

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
args = " -Xms1500m -Xmx2g -server -XX:+UseZGC -XX:+UseCompactObjectHeaders"
args = args & " -cp """ & scriptDir & "\lib\*" & ";" & scriptDir & "\l2jserver.jar"""
args = args & " net.sf.l2j.gameserver.GameServer"

shell.Run "cmd /c title L2J4Team GameServer && " & javaCmd & args, 1, False
