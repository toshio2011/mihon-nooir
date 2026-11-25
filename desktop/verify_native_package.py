"""Build and verify a host-native portable app; never release or sign it."""
import argparse
import hashlib
import os
import pathlib
import re
import shutil
import subprocess
import tempfile

p = argparse.ArgumentParser()
p.add_argument("--platform", choices=("linux", "macos", "windows"), required=True)
p.add_argument("--arch", choices=("x64", "arm64"), required=True)
p.add_argument("--server-only", action="store_true")
args = p.parse_args()
package = pathlib.Path("package").resolve()
app_name = "MihonNooir-Preview"
suffix = ".exe" if args.platform == "windows" else ""
jdk = pathlib.Path(os.environ["JAVA_HOME"])
native = package / "native-output"
app = native / (app_name + (".app" if args.platform == "macos" else ""))
qa = package / "native-qa"
qa.mkdir(parents=True, exist_ok=True)
input_jar = "mihonnooir-desktop-preview.jar"
if args.platform == "macos":
    runtime = app / "Contents/runtime/Contents/Home/bin/java"
    app_jar = app / "Contents/app" / input_jar
    launcher = app / "Contents/MacOS" / app_name
elif args.platform == "linux":
    runtime = app / "lib/runtime/bin/java"
    app_jar = app / "lib/app" / input_jar
    launcher = app / "bin" / app_name
else:
    runtime = app / "runtime/bin/java.exe"
    app_jar = app / "app" / input_jar
    launcher = app / (app_name + ".exe")

def run(*cmd, env=None, timeout=300):
    cmd = [str(a) for a in cmd]
    print("+", subprocess.list2cmdline(cmd), flush=True)
    subprocess.run(cmd, env=env, check=True, timeout=timeout)

def java_arch(binary):
    result = subprocess.run([str(binary), "-XshowSettings:properties", "-version"],
                            capture_output=True, text=True, check=True, timeout=30)
    actual = re.search(r"^\s*os\.arch\s*=\s*(\S+)", result.stderr + result.stdout, re.M)
    assert actual, result.stderr
    allowed = {"x64": {"amd64", "x86_64"}, "arm64": {"aarch64", "arm64"}}
    assert actual[1].lower() in allowed[args.arch], (actual[1], args.arch)
    return actual[1]

def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()

if args.server_only:
    server = package / "Suwayomi-Server-v2.4.2366.jar"
    assert sha256(server) == "af9feb20af9d7ebe9e30769e6c6ebc7fc7ab1c447388d42e0280b1da5fe07bfd"
    java_arch(runtime)
    run(runtime, "-Djava.awt.headless=true", "-cp", app_jar,
        "dev.nooir.desktop.suwayomi.RealSuwayomiServerSmoke", server, timeout=420)
    print("PASS: real pinned Suwayomi on native bundled runtime")
else:
    java_arch(jdk / "bin" / ("java" + suffix))
    full = package / "native-runtime"
    run(jdk / "bin" / ("jlink" + suffix), "--add-modules", "ALL-MODULE-PATH",
        "--output", full, "--strip-debug", "--no-man-pages", "--no-header-files")
    version = pathlib.Path("desktop/windows-version.txt").read_text().strip()
    # macOS CFBundleVersion requires a positive first component.
    bundle_version = "1." + version.split(".", 1)[1] if args.platform == "macos" and version.startswith("0.") else version
    cmd = [jdk / "bin" / ("jpackage" + suffix), "--type", "app-image", "--name", app_name,
           "--app-version", bundle_version, "--input", package / "input",
           "--main-jar", input_jar, "--main-class", "dev.nooir.desktop.ui.MangaNooirDesktop",
           "--runtime-image", full, "--dest", native, "--java-options", "-Dfile.encoding=UTF-8"]
    if args.platform == "macos":
        cmd += ["--mac-package-identifier", "dev.nooir.desktop.preview"]
    run(*cmd)
    assert launcher.is_file() and app_jar.is_file() and runtime.is_file()
    for library in ("pdfbox-app-3.0.8.jar", "imageio-webp-3.15.3.jar", "jsoup-1.21.2.jar", "json-20250517.jar"):
        assert (app_jar.parent / "lib" / library).is_file(), library
    actual_arch = java_arch(runtime)
    cp = os.pathsep.join(str(x) for x in (package / "qa/viewer-smoke.jar", app_jar, app_jar.parent / "lib/*"))
    with tempfile.TemporaryDirectory(prefix="nooir-native-qa-") as root:
        env = dict(os.environ)
        env["MIHONNOOIR_DATA_DIR"] = root
        assert env["MIHONNOOIR_DATA_DIR"] == root and not os.listdir(root)
        for test in ("DesktopConversionQueueTestKt", "DesktopDocumentImportTestKt", "DesktopWebEpubTestKt"):
            run(runtime, "-Djava.awt.headless=true", "-cp", cp, "dev.nooir.desktop.ui." + test, env=env, timeout=900)
        gui = [runtime, "-Djava.awt.headless=false", "-Dnooir.test.offlineLibrary=true",
               "-Dnooir.test.screenshotDir=" + str(qa / "screenshots"),
               "-cp", cp, "dev.nooir.desktop.ui.DesktopXtcViewerSwingSmokeTestKt"]
        if args.platform == "linux":
            run("xvfb-run", "-a", "-s", "-screen 0 1366x768x24", *gui, env=env, timeout=600)
        elif args.platform == "windows":
            run(*gui, env=env, timeout=600)
    # tar preserves Unix permissions and symlinks.
    dist = package / "native-dist"
    dist.mkdir()
    stem = "MihonNooir-Desktop-" + version + "-" + args.platform + "-" + args.arch
    archive = pathlib.Path(shutil.make_archive(str(dist / stem),
        "zip" if args.platform == "windows" else "gztar", native, app.name))
    (dist / (archive.name + ".sha256")).write_text(sha256(archive) + "  " + archive.name + "\n", encoding="ascii")
    (qa / "summary.txt").write_text("Native runtime: " + actual_arch + "\n"
        "Portable package and packaged conversion tests passed.\n"
        + ("GUI acceptance deferred on macOS.\n" if args.platform == "macos" else "Swing GUI acceptance passed.\n"))
    print("PASS:", archive.name)
