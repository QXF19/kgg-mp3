using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

internal static class SingleFileLauncher
{
    [STAThread]
    private static int Main(string[] args)
    {
        AppContext.SetSwitch("Switch.System.IO.UseLegacyPathHandling", false);
        AppContext.SetSwitch("Switch.System.IO.BlockLongPaths", false);
        Application.EnableVisualStyles();
        try
        {
            string directory = null;
            if (args.Length == 0)
            {
                using (var splash = new Form())
                {
                    splash.Text = "kgg-mp3";
                    splash.ClientSize = new Size(390, 100);
                    splash.StartPosition = FormStartPosition.CenterScreen;
                    splash.FormBorderStyle = FormBorderStyle.FixedDialog;
                    splash.ControlBox = false;
                    splash.Controls.Add(new Label { Text = "正在准备启动，首次运行需要展开内置组件…", Dock = DockStyle.Top, Height = 55, TextAlign = ContentAlignment.MiddleCenter });
                    splash.Controls.Add(new ProgressBar { Style = ProgressBarStyle.Marquee, Dock = DockStyle.Bottom, Height = 12 });
                    Exception failure = null;
                    splash.Shown += async delegate
                    {
                        try { directory = await Task.Run(() => Prepare()); }
                        catch (Exception error) { failure = error; }
                        finally { splash.Close(); }
                    };
                    splash.ShowDialog();
                    if (failure != null) throw failure;
                }
            }
            else directory = Prepare();
            bool cli = Array.IndexOf(args, "--cli") >= 0 || Array.IndexOf(args, "--diagnose") >= 0;
            var info = new ProcessStartInfo(Path.Combine(directory, cli ? "kgg-mp3-cli.exe" : "kgg-mp3.exe"));
            info.UseShellExecute = false;
            info.WorkingDirectory = Environment.CurrentDirectory;
            info.Arguments = JoinArguments(args);
            info.CreateNoWindow = cli;
            info.RedirectStandardOutput = cli;
            info.RedirectStandardError = cli;
            using (var process = Process.Start(info))
            {
                if (!cli) return 0;
                process.OutputDataReceived += (sender, line) => { if (line.Data != null) Console.Out.WriteLine(line.Data); };
                process.ErrorDataReceived += (sender, line) => { if (line.Data != null) Console.Error.WriteLine(line.Data); };
                process.BeginOutputReadLine();
                process.BeginErrorReadLine();
                process.WaitForExit();
                return process.ExitCode;
            }
        }
        catch (Exception error)
        {
            if (args.Length == 0) MessageBox.Show("启动失败：" + error.Message, "kgg-mp3", MessageBoxButtons.OK, MessageBoxIcon.Error);
            else Console.Error.WriteLine(error.ToString());
            return 1;
        }
    }

    private static string Prepare()
    {
        var assembly = Assembly.GetExecutingAssembly();
        string identity;
        using (var reader = new StreamReader(assembly.GetManifestResourceStream("payload.sha256")))
            identity = reader.ReadToEnd().Trim();
        string localData = Environment.GetEnvironmentVariable("LOCALAPPDATA");
        if (String.IsNullOrEmpty(localData) || !Path.IsPathRooted(localData))
            localData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        string root = Path.Combine(localData, "kgg-mp3", "runtime");
        string target = Path.Combine(root, identity.Substring(0, 24));
        using (var mutex = new Mutex(false, @"Local\kgg-mp3-" + identity))
        {
            try { mutex.WaitOne(); } catch (AbandonedMutexException) { }
            try
            {
                if (Ready(target, identity)) return Path.Combine(target, "kgg-mp3");
                Directory.CreateDirectory(root);
                string staging = target + ".partial-" + Guid.NewGuid().ToString("N").Substring(0, 8);
                Directory.CreateDirectory(staging);
                using (var stream = assembly.GetManifestResourceStream("payload.zip"))
                using (var archive = new ZipArchive(stream, ZipArchiveMode.Read))
                    archive.ExtractToDirectory(ExtendedPath(staging));
                File.WriteAllText(Path.Combine(staging, "complete"), identity);
                if (!Ready(staging, identity)) throw new IOException("内置组件不完整，请重新下载 EXE。");
                // Preserve an incomplete cache rather than deleting files.
                if (Directory.Exists(target)) Directory.Move(target, target + ".previous-" + Guid.NewGuid().ToString("N"));
                Directory.Move(staging, target);
                return Path.Combine(target, "kgg-mp3");
            }
            finally { mutex.ReleaseMutex(); }
        }
    }

    private static bool Ready(string root, string identity)
    {
        root = ExtendedPath(root);
        string marker = Path.Combine(root, "complete");
        if (!File.Exists(marker) || File.ReadAllText(marker).Trim() != identity) return false;
        foreach (string file in new[] { "kgg-mp3.exe", "kgg-mp3-cli.exe", @"app\GkkAudioStudio.jar", @"runtime\bin\server\jvm.dll", @"app\tools\ffmpeg.exe", @"app\tools\ffprobe.exe", @"app\tools\sqlite3.exe" })
            if (!File.Exists(Path.Combine(root, "kgg-mp3", file))) return false;
        return true;
    }

    private static string ExtendedPath(string path)
    {
        string full = Path.GetFullPath(path);
        if (full.StartsWith(@"\\?\")) return full;
        return full.StartsWith(@"\\") ? @"\\?\UNC\" + full.Substring(2) : @"\\?\" + full;
    }

    private static string JoinArguments(string[] args)
    {
        var result = new StringBuilder();
        foreach (string arg in args)
        {
            if (result.Length > 0) result.Append(' ');
            result.Append('"');
            int slashes = 0;
            foreach (char c in arg)
            {
                if (c == '\\') { slashes++; continue; }
                result.Append('\\', c == '"' ? slashes * 2 + 1 : slashes);
                result.Append(c);
                slashes = 0;
            }
            result.Append('\\', slashes * 2);
            result.Append('"');
        }
        return result.ToString();
    }
}
