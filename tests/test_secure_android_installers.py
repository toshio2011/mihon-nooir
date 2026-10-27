"""Regression tests for removing Android-wide extension APK installers."""
import importlib.util
import pathlib
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "secure_android_installers",
    ROOT / "tools/secure_android_installers.py",
)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class SecureAndroidInstallersTest(unittest.TestCase):
    def fixture(self, root):
        def write(name, content):
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
            return path

        manifest = write("app/src/main/AndroidManifest.xml", """<manifest xmlns:android="http://schemas.android.com/apk/res/android" xmlns:tools="http://schemas.android.com/tools">
  <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
  <uses-permission android:name="android.permission.REQUEST_DELETE_PACKAGES" />
  <uses-permission android:name="android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION" />
  <application>
        <activity
            android:name=".extension.util.ExtensionInstallActivity"
            android:exported="false"
            android:theme="@android:style/Theme.Translucent.NoTitleBar" />

        <service
            android:name=".extension.util.ExtensionInstallService"
            android:exported="false"
            android:foregroundServiceType="shortService" />

        <provider
            android:name="rikka.shizuku.ShizukuProvider"
            android:authorities="${applicationId}.shizuku"
            android:enabled="true"
            android:exported="true"
            android:multiprocess="false"
            android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />

    <meta-data android:name="android.webkit.WebView.EnableSafeBrowsing"
            android:value="false" />
  </application>
</manifest>
""")
        extension = write("app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionInstaller.kt", """import android.content.Intent
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.extension.installer.Installer
import eu.kanade.tachiyomi.util.storage.getUriCompat
class ExtensionInstaller {
    private val extensionInstaller = basePreferences.extensionInstaller

    private fun installApk(downloadId: Long, tempFile: File, packageInfo: PackageInfo, isUpdateForPrivatelyInstalled: Boolean) {
        if (isUpdateForPrivatelyInstalled) {
            installApkPrivately(downloadId, tempFile, packageInfo)
            return
        }

        when (val installer = extensionInstaller.get()) {
            else -> startAndroidInstaller(installer)
        }
    }

    private fun installApkPrivately(downloadId: Long, tempFile: File, packageInfo: PackageInfo) {}
    fun cancelInstall(pkgName: String) {
        Installer.cancelInstallQueue(pkgName.hashCode().toLong())
    }
    fun uninstallApk(pkgName: String) {
        Intent(Intent.ACTION_UNINSTALL_PACKAGE, "package:$pkgName".toUri())
    }
}
""")
        loader = write("app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt", """import java.io.File
class ExtensionLoader {
        val target = File(getPrivateExtensionDir(context), "${extension.packageName}.$PRIVATE_EXTENSION_EXTENSION")
        try {
            target.delete()
            file.copyAndSetReadOnlyTo(target, overwrite = true)
        } catch (e: Exception) {
            target.delete()
            throw e
        }
}
""")
        permissions_helper = write(
            "app/src/main/java/eu/kanade/presentation/util/Permissions.kt",
            "package eu.kanade.presentation.util\n"
            "@Composable\nfun rememberRequestPackageInstallsPermissionState(initialValue: Boolean = false): Boolean {\n"
            "    val value = context.packageManager.canRequestPackageInstalls()\n"
            "    return value\n}\n",
        )
        onboarding = write("app/src/main/java/eu/kanade/presentation/more/onboarding/PermissionStep.kt", """import eu.kanade.presentation.util.rememberRequestPackageInstallsPermissionState
import eu.kanade.tachiyomi.util.system.launchRequestPackageInstallsPermission
class PermissionStep {
        val installGranted = rememberRequestPackageInstallsPermissionState()

        Column {
            PermissionCheckbox(
                title = stringResource(MR.strings.onboarding_permission_install_apps),
                subtitle = stringResource(MR.strings.onboarding_permission_install_apps_description),
                granted = installGranted,
                onButtonClick = {
                    context.launchRequestPackageInstallsPermission()
                },
            )

            OtherPermission()
        }
}
""")
        settings = write("app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsAdvancedScreen.kt", """class SettingsAdvancedScreen {
    @Composable
    private fun getExtensionsGroup(
        basePreferences: BasePreferences,
    ): Preference.PreferenceGroup {
        return old()
    }
}
""")
        graph = write("app/src/main/java/mihon/app/di/AppGraph.kt",
                      "import eu.kanade.tachiyomi.extension.util.ExtensionInstallActivity\n"
                      "    fun inject(extensionInstallActivity: ExtensionInstallActivity)\n")

        to_delete = (
            "app/src/main/java/eu/kanade/tachiyomi/extension/installer/PackageInstallerInstaller.kt",
            "app/src/main/java/eu/kanade/tachiyomi/extension/installer/ShizukuInstaller.kt",
            "app/src/main/java/eu/kanade/tachiyomi/extension/installer/Installer.kt",
            "app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionInstallService.kt",
            "app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionInstallActivity.kt",
            "app/src/main/java/mihon/app/shizuku/ShellInterface.kt",
            "app/src/main/aidl/mihon/app/shizuku/IShellInterface.aidl",
        )
        for name in to_delete:
            write(name, "test source")
        return manifest, extension, settings, graph, to_delete

    def test_privileged_installers_removed_and_private_extensions_preserved(self):
        with tempfile.TemporaryDirectory() as d:
            root = pathlib.Path(d)
            manifest, extension, settings, graph, to_delete = self.fixture(root)
            MODULE.secure(root)
            self.assertFalse((root / "app/src/main/java/eu/kanade/presentation/util/Permissions.kt").exists())
            onboarding = (root / "app/src/main/java/eu/kanade/presentation/more/onboarding/PermissionStep.kt").read_text()
            self.assertNotIn("rememberRequestPackageInstallsPermissionState", onboarding)
            self.assertNotIn("launchRequestPackageInstallsPermission", onboarding)
            self.assertNotIn("onboarding_permission_install_apps", onboarding)
            self.assertIn("OtherPermission()", onboarding)
            xml = manifest.read_text()
            for name in ("REQUEST_INSTALL_PACKAGES", "REQUEST_DELETE_PACKAGES",
                         "UPDATE_PACKAGES_WITHOUT_USER_ACTION"):
                self.assertIn('android.permission.' + name + '" tools:node="remove"', xml)
            self.assertIn('android:name="rikka.shizuku.ShizukuProvider"\n            tools:node="remove"', xml)
            self.assertNotIn("ExtensionInstallService", xml)
            self.assertNotIn("ExtensionInstallActivity", xml)
            self.assertIn('android:value="true"', xml)
            loader_source = (root / "app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt").read_text()
            self.assertIn("import java.io.IOException", loader_source)
            self.assertIn("file.copyAndSetReadOnlyTo(staged, overwrite = true)", loader_source)
            self.assertIn("staged.renameTo(target)", loader_source)
            self.assertIn("staged.delete()", loader_source)
            self.assertNotIn("target.delete()", loader_source)
            source = extension.read_text()
            self.assertIn("installApkPrivately(downloadId, tempFile, packageInfo)", source)
            self.assertNotIn("startAndroidInstaller(", source)
            self.assertNotIn("Installer.cancelInstallQueue(", source)
            self.assertNotIn("ACTION_UNINSTALL_PACKAGE", source)
            self.assertIn("Settings.ACTION_APPLICATION_DETAILS_SETTINGS", source)
            for name in to_delete:
                self.assertFalse((root / name).exists(), name)
            self.assertNotIn("ExtensionInstallActivity", graph.read_text())
            self.assertNotIn("shizukuMissing", settings.read_text())
            self.assertIn("ext_revoke_trust", settings.read_text())

    def test_missing_upstream_permission_anchor_aborts(self):
        with tempfile.TemporaryDirectory() as d:
            root = pathlib.Path(d)
            manifest, _, _, _, _ = self.fixture(root)
            manifest.write_text(manifest.read_text().replace(
                '<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />', ''
            ))
            with self.assertRaises(RuntimeError):
                MODULE.secure(root)


if __name__ == "__main__":
    unittest.main()
