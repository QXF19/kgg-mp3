param([Parameter(Mandatory=$true)][string[]]$Executables)
$ErrorActionPreference = 'Stop'
if (-not ('KggManifestWriter' -as [type])) {
Add-Type -ReferencedAssemblies 'System.dll','System.Core.dll','System.Xml.dll' -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Text;
using System.Xml;
public static class KggManifestWriter {
    private delegate bool EnumLanguage(IntPtr module, IntPtr type, IntPtr name, ushort language, IntPtr parameter);
    [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)] private static extern IntPtr LoadLibraryExW(string path, IntPtr file, uint flags);
    [DllImport("kernel32.dll", SetLastError=true)] private static extern bool FreeLibrary(IntPtr module);
    [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)] private static extern IntPtr FindResourceW(IntPtr module, IntPtr name, IntPtr type);
    [DllImport("kernel32.dll", SetLastError=true)] private static extern uint SizeofResource(IntPtr module, IntPtr resource);
    [DllImport("kernel32.dll", SetLastError=true)] private static extern IntPtr LoadResource(IntPtr module, IntPtr resource);
    [DllImport("kernel32.dll", SetLastError=true)] private static extern IntPtr LockResource(IntPtr resource);
    [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)] private static extern bool EnumResourceLanguagesW(IntPtr module, IntPtr type, IntPtr name, EnumLanguage callback, IntPtr parameter);
    [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)] private static extern IntPtr BeginUpdateResourceW(string path, bool deleteExisting);
    [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)] private static extern bool UpdateResourceW(IntPtr update, IntPtr type, IntPtr name, ushort language, byte[] data, uint length);
    [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)] private static extern bool EndUpdateResourceW(IntPtr update, bool discard);
    public static void Patch(string path) {
        IntPtr type = new IntPtr(24), name = new IntPtr(1);
        byte[] original;
        List<ushort> languages = new List<ushort>();
        IntPtr module = LoadLibraryExW(path, IntPtr.Zero, 0x22);
        if (module == IntPtr.Zero) throw new Win32Exception(Marshal.GetLastWin32Error());
        try {
            IntPtr resource = FindResourceW(module, name, type);
            if (resource == IntPtr.Zero) throw new InvalidOperationException("Launcher manifest not found");
            uint size = SizeofResource(module, resource);
            IntPtr data = LockResource(LoadResource(module, resource));
            original = new byte[size]; Marshal.Copy(data, original, 0, original.Length);
            EnumLanguage callback = delegate(IntPtr m, IntPtr t, IntPtr n, ushort language, IntPtr p) { languages.Add(language); return true; };
            if (!EnumResourceLanguagesW(module, type, name, callback, IntPtr.Zero)) throw new Win32Exception(Marshal.GetLastWin32Error());
            GC.KeepAlive(callback);
        } finally { FreeLibrary(module); }
        XmlDocument document = new XmlDocument(); document.XmlResolver = null;
        document.LoadXml(Encoding.UTF8.GetString(original).TrimEnd('\0'));
        XmlElement root = document.DocumentElement;
        XmlElement application = root.SelectSingleNode("*[local-name()='application']") as XmlElement;
        if (application == null) { application = document.CreateElement("application", "urn:schemas-microsoft-com:asm.v3"); root.AppendChild(application); }
        XmlElement settings = application.SelectSingleNode("*[local-name()='windowsSettings']") as XmlElement;
        if (settings == null) { settings = document.CreateElement("windowsSettings", "urn:schemas-microsoft-com:asm.v3"); application.AppendChild(settings); }
        XmlElement codePage = settings.SelectSingleNode("*[local-name()='activeCodePage']") as XmlElement;
        if (codePage == null) { codePage = document.CreateElement("activeCodePage", "http://schemas.microsoft.com/SMI/2019/WindowsSettings"); settings.AppendChild(codePage); }
        codePage.InnerText = "UTF-8";
        byte[] modified = new UTF8Encoding(false).GetBytes(document.OuterXml);
        IntPtr update = BeginUpdateResourceW(path, false);
        if (update == IntPtr.Zero) throw new Win32Exception(Marshal.GetLastWin32Error());
        bool completed = false;
        try {
            foreach (ushort language in languages)
                if (!UpdateResourceW(update, type, name, language, modified, (uint)modified.Length)) throw new Win32Exception(Marshal.GetLastWin32Error());
            if (!EndUpdateResourceW(update, false)) throw new Win32Exception(Marshal.GetLastWin32Error());
            completed = true;
        } finally { if (!completed) EndUpdateResourceW(update, true); }
    }
}
'@
}
foreach ($Executable in $Executables) {
    $ResolvedExecutable = (Resolve-Path -LiteralPath $Executable).Path
    (Get-Item -LiteralPath $ResolvedExecutable).IsReadOnly = $false
    [KggManifestWriter]::Patch($ResolvedExecutable)
    Write-Host "UTF-8 launcher manifest applied: $ResolvedExecutable"
}
