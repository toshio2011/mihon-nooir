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
mode = p.add_mutually_exclusive_group()
mode.add_argument("--server-only", action="store_true")
mode.add_argument("--installers-only", action="store_true")
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

def state_snapshot(root):
    # Verify the actual user files rather than just a sentinel.
    paths = ("local-library-v1.txt", "reader-bookmarks-v1.tsv",
             "conversion-queue-v1.tsv", "conversion-settings-v1.txt",
             "web-projects-v1.json", "exports/original-comic.xtc",
             "original-comic/001.png", "original-comic/002.png")
    return {name: sha256(root / name) for name in paths}

def state_test(java, jar, data, mode):
    cp = os.pathsep.join(str(x) for x in (package / "qa/viewer-smoke.jar",
        jar, jar.parent / "lib/*"))
    env = dict(os.environ, MIHONNOOIR_DATA_DIR=str(data))
    run(java, "-Djava.awt.headless=true", "-cp", cp,
        "dev.nooir.desktop.ui.DesktopInstalledDataTestKt", mode, env=env, timeout=300)

def verify_replacement(installed_app, installed_runtime, extracted_jar):
    # Disposable user root, not the runner's normal app data. Seed via the
    # native preview, then reopen via a separately extracted/copied app.
    # This validates portable replacement, not OS package-manager upgrades.
    with tempfile.TemporaryDirectory(prefix="nooir-replacement-") as folder:
        data = pathlib.Path(folder) / "user-data"
        data.mkdir()
        (data / "nooir-installed-qa.marker").write_text("disposable installer acceptance\n")
        state_test(runtime, app_jar, data, "seed")
        before = state_snapshot(data)
        state_test(installed_runtime, extracted_jar, data, "verify")
        assert state_snapshot(data) == before, "User state changed during replacement"
        print("PASS: library, bookmarks, queue, settings, original/export files and web project retained across app replacement")

def verify_installer_runtime(root):
    relative = app_jar.relative_to(app)
    # Linux packages may place the app under /opt or another package root.
    candidates = list(root.rglob(input_jar))
    assert len(candidates) == 1, candidates
    extracted_jar = candidates[0]
    assert sha256(extracted_jar) == sha256(app_jar)
    installed_app = extracted_jar
    for _ in relative.parts:
        installed_app = installed_app.parent
    installed_runtime = installed_app / runtime.relative_to(app)
    assert java_arch(installed_runtime)
    for name in ("pdfbox-app-3.0.8.jar", "imageio-webp-3.15.3.jar", "jsoup-1.21.2.jar", "json-20250517.jar"):
        original = app_jar.parent / "lib" / name
        actual = extracted_jar.parent / "lib" / name
        assert sha256(original) == sha256(actual), name
    cp = os.pathsep.join(str(x) for x in (package / "qa/viewer-smoke.jar",
        extracted_jar, extracted_jar.parent / "lib/*"))
    with tempfile.TemporaryDirectory(prefix="nooir-installer-qa-") as isolated:
        env = dict(os.environ, MIHONNOOIR_DATA_DIR=isolated)
        run(installed_runtime, "-Djava.awt.headless=true", "-cp", cp,
            "dev.nooir.desktop.ui.DesktopWebEpubTestKt", env=env, timeout=300)
    verify_replacement(installed_app, installed_runtime, extracted_jar)

def installers():
    assert app.is_dir() and runtime.is_file() and app_jar.is_file()
    assert args.platform in ("linux", "macos")
    version = pathlib.Path("desktop/windows-version.txt").read_text().strip()
    bundle_version = "1." + version.split(".", 1)[1] if args.platform == "macos" and version.startswith("0.") else version
    dist = package / "native-dist"
    assert dist.is_dir()
    stage = package / "native-installers"
    stage.mkdir()
    for kind in (("deb", "rpm") if args.platform == "linux" else ("dmg",)):
        out = stage / kind
        out.mkdir()
        cmd = [jdk / "bin/jpackage", "--type", kind, "--name", app_name,
               "--app-version", bundle_version, "--app-image", app, "--dest", out,
               "--vendor", "MihonNooir", "--description", "MihonNooir experimental desktop preview"]
        if args.platform == "linux":
            cmd += ["--linux-package-name", "mihonnooir-preview", "--linux-shortcut"]
        else:
            cmd += ["--mac-package-identifier", "dev.nooir.desktop.preview"]
        run(*cmd, timeout=600)
        files = list(out.glob("*." + kind))
        assert len(files) == 1 and files[0].stat().st_size > 0, files
        candidate = files[0]
        with tempfile.TemporaryDirectory(prefix="nooir-native-install-") as folder:
            dest = pathlib.Path(folder)
            if kind == "deb":
                info = subprocess.check_output(["dpkg-deb", "-f", str(candidate), "Architecture"], text=True).strip()
                assert info == ("amd64" if args.arch == "x64" else "arm64"), info
                run("dpkg-deb", "-x", candidate, dest)
                verify_installer_runtime(dest)
            elif kind == "rpm":
                info = subprocess.check_output(["rpm", "-qp", "--qf", "%{ARCH}", str(candidate)], text=True).strip()
                assert info == ("x86_64" if args.arch == "x64" else "aarch64"), info
                # Avoid shell interpolation and check BOTH extraction commands.
                with subprocess.Popen(["rpm2cpio", str(candidate)], stdout=subprocess.PIPE) as src:
                    extract = subprocess.run(["cpio", "-idm", "--quiet"], stdin=src.stdout, cwd=dest, check=True)
                    src.stdout.close()
                    assert src.wait() == 0
                verify_installer_runtime(dest)
            else:
                run("hdiutil", "attach", "-readonly", "-nobrowse", "-mountpoint", dest, candidate)
                try:
                    verify_installer_runtime(dest)
                finally:
                    run("hdiutil", "detach", dest)
        target = dist / ("MihonNooir-Desktop-" + version + "-" + args.platform + "-" + args.arch + "." + kind)
        shutil.move(candidate, target)
        (dist / (target.name + ".sha256")).write_text(sha256(target) + "  " + target.name + "\n", encoding="ascii")
        print("PASS: native installer payload, architecture and packaged web EPUB QA:", target.name)

if args.installers_only:
    installers()
elif args.server_only:
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
        else:
            # Native macOS runners must actually provide a usable display.
            # A headless or broken GUI is a release-blocking failure, not a skip.
            run(*gui, env=env, timeout=600)
    # Verify a separately copied portable app (including native executable permissions).
    with tempfile.TemporaryDirectory(prefix="nooir-portable-copy-") as folder:
        copied = pathlib.Path(folder) / app.name
        shutil.copytree(app, copied, symlinks=True)
        copied_launcher = copied / launcher.relative_to(app)
        assert copied_launcher.is_file() and (args.platform == "windows" or os.access(copied_launcher, os.X_OK))
        verify_replacement(copied, copied / runtime.relative_to(app), copied / app_jar.relative_to(app))
    # tar preserves Unix permissions and symlinks.
    dist = package / "native-dist"
    dist.mkdir()
    stem = "MihonNooir-Desktop-" + version + "-" + args.platform + "-" + args.arch
    archive = pathlib.Path(shutil.make_archive(str(dist / stem),
        "zip" if args.platform == "windows" else "gztar", native, app.name))
    (dist / (archive.name + ".sha256")).write_text(sha256(archive) + "  " + archive.name + "\n", encoding="ascii")
    (qa / "summary.txt").write_text("Native runtime: " + actual_arch + "\n"
        "Portable package and packaged conversion tests passed.\n"
        + "Swing GUI acceptance passed.\n")
    print("PASS:", archive.name)
