#!/usr/bin/env python3
"""Remove Android-wide APK installation pathways from the user-test build.

MihonNooir supports user-selected, store-signature-verified PRIVATE extensions,
but does not need to request installation/deletion of other Android apps.
This is substantive capability removal, not suppression of security warnings.
Run after apply_overlay.py and before Gradle compilation.
"""
from pathlib import Path
import sys

EXT = Path("app/src/main/java/eu/kanade/tachiyomi/extension")
APP = Path("app/src/main/java/mihon/app")


def replace_once(path: Path, old: str, new: str) -> None:
    src = path.read_text(encoding="utf-8")
    if src.count(old) != 1:
        raise RuntimeError(f"Unexpected upstream anchor in {path}: {old[:100]!r}")
    path.write_text(src.replace(old, new, 1), encoding="utf-8")


def secure(upstream: Path) -> None:
    manifest = upstream / "app/src/main/AndroidManifest.xml"

    for permission in (
        "REQUEST_INSTALL_PACKAGES",
        "REQUEST_DELETE_PACKAGES",
        "UPDATE_PACKAGES_WITHOUT_USER_ACTION",
    ):
        original = f'<uses-permission android:name="android.permission.{permission}" />'
        # tools:node remove also rejects the same permission from library manifests.
        changed = f'<uses-permission android:name="android.permission.{permission}" tools:node="remove" />'
        replace_once(manifest, original, changed)

    replace_once(
        manifest,
        '''        <activity
            android:name=".extension.util.ExtensionInstallActivity"
            android:exported="false"
            android:theme="@android:style/Theme.Translucent.NoTitleBar" />

''',
        "",
    )
    replace_once(
        manifest,
        '''        <service
            android:name=".extension.util.ExtensionInstallService"
            android:exported="false"
            android:foregroundServiceType="shortService" />

''',
        "",
    )
    replace_once(
        manifest,
        '''        <provider
            android:name="rikka.shizuku.ShizukuProvider"
            android:authorities="${applicationId}.shizuku"
            android:enabled="true"
            android:exported="true"
            android:multiprocess="false"
            android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />
''',
        '''        <!-- Privileged Shizuku service is not available in this safer fork build. -->
        <provider
            android:name="rikka.shizuku.ShizukuProvider"
            tools:node="remove" />
''',
    )
    # Restore Android WebView Safe Browsing rather than disabling it.
    replace_once(
        manifest,
        'android:name="android.webkit.WebView.EnableSafeBrowsing"\n            android:value="false"',
        'android:name="android.webkit.WebView.EnableSafeBrowsing"\n            android:value="true"',
    )

    ext_installer = upstream / EXT / "util/ExtensionInstaller.kt"
    src = ext_installer.read_text(encoding="utf-8")
    start = '        if (isUpdateForPrivatelyInstalled) {\n'
    finish = '\n    private fun installApkPrivately('
    if src.count(start) != 1 or src.count(finish) != 1:
        raise RuntimeError("Mihon extension installer layout has changed")
    i = src.index(start)
    j = src.index(finish, i)
    src = (
        src[:i]
        + '''        // Android-wide APK installs, background package sessions and privileged
        // Shizuku installs are intentionally unavailable. Downloads from a store
        // remain user-initiated, are checked against that store's signing key
        // above, and are stored only in this app's private extension directory.
        installApkPrivately(downloadId, tempFile, packageInfo)
    }
'''
        + src[j:]
    )
    src = src.replace(
        '        Installer.cancelInstallQueue(pkgName.hashCode().toLong())\n', ''
    )
    if 'Installer.cancelInstallQueue' in src:
        raise RuntimeError("System installer cancellation reference remained")
    src = src.replace(
        'import eu.kanade.tachiyomi.extension.installer.Installer\n', ''
    )
    src = src.replace(
        'import androidx.core.content.ContextCompat\n', ''
    )
    src = src.replace(
        'import eu.kanade.tachiyomi.util.storage.getUriCompat\n', ''
    )
    src = src.replace(
        '    private val extensionInstaller = basePreferences.extensionInstaller\n', ''
    )
    # OS-installed extensions can still be managed manually in Android Settings.
    # Private extensions continue to be removed directly by MihonNooir.
    src = src.replace(
        'import android.content.Intent\n',
        'import android.content.Intent\nimport android.provider.Settings\n',
        1,
    )
    src = src.replace(
        'Intent(Intent.ACTION_UNINSTALL_PACKAGE, "package:$pkgName".toUri())',
        'Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$pkgName".toUri())',
    )
    if 'Intent.ACTION_UNINSTALL_PACKAGE' in src:
        raise RuntimeError("Uninstall-package call remained")
    ext_installer.write_text(src, encoding="utf-8")

    # Remove installer UI that would otherwise expose unsupported unsafe modes.
    settings = upstream / "app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsAdvancedScreen.kt"
    settings_source = settings.read_text(encoding="utf-8")
    marker = '    @Composable\n    private fun getExtensionsGroup('
    if settings_source.count(marker) != 1:
        raise RuntimeError("Extension settings anchor changed")
    start = settings_source.index(marker)
    if not settings_source.rstrip().endswith('    }\n}'):
        raise RuntimeError("AdvancedSettings extensions group no longer last")
    settings.write_text(
        settings_source[:start]
        + '''    @Composable
    private fun getExtensionsGroup(
        basePreferences: BasePreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val trustExtension = remember { context.appGraph.trustExtension }
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_extensions),
            preferenceItems = listOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.ext_revoke_trust),
                    onClick = { trustExtension.revokeAll() },
                ),
            ),
        )
    }
}
''',
        encoding="utf-8",
    )

    # Remove the obsolete dependency-injection route to a removed activity.
    graph = upstream / APP / "di/AppGraph.kt"
    replace_once(
        graph,
        'import eu.kanade.tachiyomi.extension.util.ExtensionInstallActivity\n',
        '',
    )
    replace_once(
        graph,
        '    fun inject(extensionInstallActivity: ExtensionInstallActivity)\n',
        '',
    )

    # No compiled package installer, Shizuku privileged shell or OS installer
    # activity remains in this distribution.
    for relative in (
        EXT / "installer/PackageInstallerInstaller.kt",
        EXT / "installer/ShizukuInstaller.kt",
        EXT / "installer/Installer.kt",
        EXT / "util/ExtensionInstallService.kt",
        EXT / "util/ExtensionInstallActivity.kt",
        APP / "shizuku/ShellInterface.kt",
        Path("app/src/main/aidl/mihon/app/shizuku/IShellInterface.aidl"),
    ):
        path = upstream / relative
        if not path.is_file():
            raise RuntimeError(f"Expected unsafe legacy installer file is missing: {relative}")
        path.unlink()

    print("Hardened MihonNooir: disabled Android-wide app installation, privileged Shizuku installation and unsafe install permissions; private extensions kept")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: secure_android_installers.py UPSTREAM_DIRECTORY")
    secure(Path(sys.argv[1]).resolve())
