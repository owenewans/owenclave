package android.content.pm

import android.content.Intent
import android.content.res.Resources
import android.os.Bundle

open class ComponentInfo {
    @JvmField var packageName: String = ""
    @JvmField var name: String = ""
    @JvmField var applicationInfo: ApplicationInfo = if (this is ApplicationInfo) this else ApplicationInfo()
    @JvmField var exported: Boolean = false
    @JvmField var metaData: Bundle = Bundle()
}

class ApplicationInfo : ComponentInfo() {
    companion object {
        const val FLAG_SYSTEM = 1
    }
    @JvmField var uid: Int = 0
    @JvmField var flags: Int = 0
    @JvmField var nativeLibraryDir: String = ""
    @JvmField var sourceDir: String = ""
    @JvmField var label: String = ""

    fun loadLabel(pm: PackageManager): CharSequence = label.ifEmpty { packageName }
    fun loadIcon(pm: PackageManager): android.graphics.drawable.Drawable? = null
}

class ProviderInfo : ComponentInfo() {
    @JvmField var authority: String = ""
}

class ServiceInfo : ComponentInfo()
class ActivityInfo : ComponentInfo()

class ResolveInfo {
    @JvmField var providerInfo: ProviderInfo = ProviderInfo()
    @JvmField var serviceInfo: ServiceInfo = ServiceInfo()
    @JvmField var activityInfo: ActivityInfo = ActivityInfo()
}

class PackageInfo {
    @JvmField var packageName: String = ""
    @JvmField var versionName: String? = "1.0"
    @JvmField var applicationInfo: ApplicationInfo = ApplicationInfo()
    @JvmField var requestedPermissions: Array<String>? = emptyArray()
    @JvmField var providers: Array<ProviderInfo>? = emptyArray()
}

class PackageManager {
    companion object {
        const val PERMISSION_GRANTED = 0
        const val PERMISSION_DENIED = -1
        const val GET_ACTIVITIES = 1
        const val GET_PROVIDERS = 8
        const val GET_SERVICES = 4
        const val GET_PERMISSIONS = 0x00001000
        const val GET_META_DATA = 0x00000080
        const val GET_SIGNATURES = 0x00000040
        const val GET_SIGNING_CERTIFICATES = 0x08000000
        const val GET_UNINSTALLED_PACKAGES = 0x00002000
        const val MATCH_UNINSTALLED_PACKAGES = 0x00002000
    }

    fun getPackagesHoldingPermissions(permissions: Array<String>, flags: Int): List<PackageInfo> = emptyList()
    fun getInstalledPackages(flags: Int): List<PackageInfo> = emptyList()
    fun getInstalledApplications(flags: Int): List<ApplicationInfo> = emptyList()
    fun getPackageInfo(packageName: String, flags: Int): PackageInfo? = PackageInfo().apply {
        this.packageName = packageName
    }
    fun queryIntentContentProviders(intent: Intent, flags: Int): List<ResolveInfo> = emptyList()
    fun queryIntentServices(intent: Intent, flags: Int): List<ResolveInfo> = emptyList()
    fun getResourcesForApplication(app: ApplicationInfo): Resources = Resources()
    fun getApplicationLabel(info: ApplicationInfo): CharSequence = info.label.ifEmpty { info.packageName }
    fun getApplicationIcon(info: ApplicationInfo): android.graphics.drawable.Drawable? = null
    fun canRequestPackageInstalls(): Boolean = true
}
