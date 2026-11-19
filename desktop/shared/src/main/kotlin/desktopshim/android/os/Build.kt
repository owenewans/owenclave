package android.os

object Build {

    @JvmField val ID: String = "Desktop"
    @JvmField val DISPLAY: String = System.getProperty("os.name", "Desktop")
    @JvmField val TYPE: String = "user"
    @JvmField val TAGS: String = "release-keys"
    @JvmField val MANUFACTURER: String = "PC"
    @JvmField val BRAND: String = "Owenclave"
    @JvmField val MODEL: String = System.getProperty("os.name", "Desktop")
    @JvmField val PRODUCT: String = "owenclave-desktop"
    @JvmField val BOARD: String = System.getProperty("os.arch", "amd64")
    @JvmField val HARDWARE: String = System.getProperty("os.arch", "amd64")
    @JvmField val DEVICE: String = "desktop"
    @JvmField val SUPPORTED_ABIS: Array<String> = arrayOf(System.getProperty("os.arch", "amd64"))

    object VERSION {
        const val SDK_INT = 34
        @JvmField val RELEASE: String = System.getProperty("os.version", "14")
        @JvmField val CODENAME: String = "REL"
        @JvmField val INCREMENTAL: String = "1"
    }

    object VERSION_CODES {
        const val LOLLIPOP = 21
        const val M = 23
        const val N = 24
        const val N_MR1 = 25
        const val O = 26
        const val O_MR1 = 27
        const val P = 28
        const val Q = 29
        const val R = 30
        const val S = 31
        const val S_V2 = 32
        const val TIRAMISU = 33
        const val UPSIDE_DOWN_CAKE = 34
        const val VANILLA_ICE_CREAM = 35
        const val BAKLAVA = 36
        const val CINNAMON_BUN = 37
    }

}
