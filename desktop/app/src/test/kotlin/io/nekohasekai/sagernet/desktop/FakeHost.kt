package io.nekohasekai.sagernet.desktop

/** A fake operating system for the host-command tests: no real command is run. */
internal class FakeHost : CommandRunner {

    val commands = mutableListOf<List<String>>()

    // ---- platform switches
    var gsettingsAvailable = true
    var kwriteconfigAvailable = false
    var kreadconfigAvailable = false
    var nftAvailable = true
    var iptablesAvailable = true
    var cgroupV2 = true
    var uid = "0"
    var failCommands = mutableSetOf<String>()
    var pids = mutableMapOf<String, String>()
    var cgroupWrites = mutableListOf<String>()

    // ---- state
    val gnome = linkedMapOf(
        "org.gnome.system.proxy/mode" to "'none'",
        "org.gnome.system.proxy.http/host" to "''",
        "org.gnome.system.proxy.http/port" to "0",
        "org.gnome.system.proxy.https/host" to "''",
        "org.gnome.system.proxy.https/port" to "0",
        "org.gnome.system.proxy.socks/host" to "''",
        "org.gnome.system.proxy.socks/port" to "0",
        "org.gnome.system.proxy/ignore-hosts" to "[]",
    )
    val kde = linkedMapOf(
        "ProxyType" to "0",
        "httpProxy" to "",
        "httpsProxy" to "",
        "socksProxy" to "",
        "NoProxyFor" to "",
    )
    val registry = linkedMapOf<String, String?>(
        "ProxyEnable" to "0x0",
        "ProxyServer" to "old.proxy:8080",
        "ProxyOverride" to null,
    )
    val mac = linkedMapOf(
        "web.Enabled" to "No",
        "web.Server" to "",
        "web.Port" to "0",
        "secureweb.Enabled" to "No",
        "secureweb.Server" to "",
        "secureweb.Port" to "0",
        "socks.Enabled" to "No",
        "socks.Server" to "",
        "socks.Port" to "0",
        "bypass" to "",
    )

    override fun run(command: List<String>): CommandResult {
        commands += command
        if (command.isNotEmpty() && failCommands.contains(command[0])) {
            return CommandResult(1, "", "simulated failure")
        }
        return when (command.firstOrNull()?.substringAfterLast('/')) {
            "gsettings" -> gsettings(command)
            "kwriteconfig6", "kwriteconfig5" -> kdeWrite(command)
            "kreadconfig6", "kreadconfig5" -> kdeRead(command)
            "sh" -> shell(command)
            "reg" -> reg(command)
            "powershell" -> ok()
            "route" -> ok("   route to: default\ninterface: en0\n")
            "networksetup" -> networksetup(command)
            "stat" -> ok("4242\n")
            "mkdir" -> ok()
            "cat" -> ok(if (cgroupV2) "cpu io memory\n" else "")
            "id" -> ok("$uid\n")
            "nft" -> if (nftAvailable) ok() else CommandResult(1, "", "nft: not found")
            "iptables" -> if (iptablesAvailable) ok() else CommandResult(1, "", "iptables: not found")
            "ip" -> ok()
            "pgrep" -> {
                val name = command.last()
                pids[name]?.let { ok("$it\n") } ?: CommandResult(1, "", "")
            }
            "rmdir" -> ok()
            else -> ok()
        }
    }

    private fun ok(stdout: String = "") = CommandResult(0, stdout, "")

    private fun gsettings(command: List<String>): CommandResult {
        if (!gsettingsAvailable) return CommandResult(1, "", "schema not found")
        val schema = command.getOrNull(2) ?: return CommandResult(1, "", "bad usage")
        val key = command.getOrNull(3) ?: return CommandResult(1, "", "bad usage")
        val id = "$schema/$key"
        return when (command[1]) {
            "get" -> ok((gnome[id] ?: "") + "\n")
            "set" -> {
                gnome[id] = command.getOrNull(4).orEmpty()
                ok()
            }
            "reset" -> {
                gnome[id] = when (key) {
                    "mode" -> "'none'"
                    "port" -> "0"
                    "ignore-hosts" -> "[]"
                    else -> "''"
                }
                ok()
            }
            else -> CommandResult(1, "", "bad usage")
        }
    }

    private fun kdeWrite(command: List<String>): CommandResult {
        if (!kwriteconfigAvailable) return CommandResult(1, "", "not found")
        val key = command.getOrNull(command.indexOf("--key") + 1) ?: return CommandResult(1, "", "no key")
        val value = command.getOrNull(command.indexOf("--key") + 2).orEmpty()
        kde[key] = value
        return ok()
    }

    private fun kdeRead(command: List<String>): CommandResult {
        if (!kreadconfigAvailable) return CommandResult(1, "", "not found")
        val key = command.getOrNull(command.indexOf("--key") + 1) ?: return CommandResult(1, "", "no key")
        return ok((kde[key] ?: "") + "\n")
    }

    private fun shell(command: List<String>): CommandResult {
        val script = command.getOrNull(2).orEmpty()
        return when {
            script.startsWith("command -v") -> {
                val name = script.removePrefix("command -v").trim()
                if (onPath(name)) ok("/usr/bin/$name\n") else CommandResult(1, "", "not found")
            }
            script.contains("cgroup.procs") -> {
                cgroupWrites += script
                ok()
            }
            else -> ok()
        }
    }

    private fun onPath(name: String): Boolean = when (name) {
        "gsettings" -> gsettingsAvailable
        "kwriteconfig6", "kwriteconfig5" -> kwriteconfigAvailable
        "kreadconfig6", "kreadconfig5" -> kreadconfigAvailable
        "nft" -> nftAvailable
        "iptables" -> iptablesAvailable
        else -> true
    }

    private fun reg(command: List<String>): CommandResult {
        // reg query <path> [/v <name>]
        val nameIndex = command.indexOf("/v")
        if (nameIndex < 0) return ok("HKEY_CURRENT_USER\\Software\\...\n")
        val name = command.getOrNull(nameIndex + 1) ?: return CommandResult(1, "", "")
        if (command[1] == "add") {
            val value = command.getOrNull(command.indexOf("/d") + 1).orEmpty()
            registry[name] = value
            return ok()
        }
        if (command[1] == "delete") {
            registry.remove(name)
            return ok()
        }
        val value = registry[name] ?: return CommandResult(1, "", "value not found")
        val type = if (name == "ProxyEnable") "REG_DWORD" else "REG_SZ"
        return ok("    $name    $type    $value\n")
    }

    private fun networksetup(command: List<String>): CommandResult {
        if (command.size < 2) return CommandResult(1, "", "bad usage")
        return when (command[1]) {
            "-listnetworkserviceorder" -> ok(
                "An asterisk (*) denotes that a network service is disabled.\n" +
                    "(1) Wi-Fi\n(Hardware Port: Wi-Fi, Device: en0)\n\n" +
                    "(2) Ethernet\n(Hardware Port: Ethernet, Device: en1)\n"
            )
            "-getwebproxy" -> ok(getMac("web"))
            "-getsecurewebproxy" -> ok(getMac("secureweb"))
            "-getsocksfirewallproxy" -> ok(getMac("socks"))
            "-getproxybypassdomains" -> ok(
                mac["bypass"].orEmpty().ifEmpty { "There aren't any bypass domains set on Wi-Fi." } + "\n"
            )
            "-setwebproxy" -> setMac("web", command)
            "-setsecurewebproxy" -> setMac("secureweb", command)
            "-setsocksfirewallproxy" -> setMac("socks", command)
            "-setwebproxystate" -> setState("web", command)
            "-setsecurewebproxystate" -> setState("secureweb", command)
            "-setsocksfirewallproxystate" -> setState("socks", command)
            "-setproxybypassdomains" -> {
                if (command.size < 4) {
                    return CommandResult(4, "", "** Error: The amount of parameters was not correct.")
                }
                val args = command.drop(3)
                mac["bypass"] = if (args.size == 1 && args[0].equals("Empty", ignoreCase = true)) "" else args.joinToString(" ")
                ok()
            }
            else -> CommandResult(1, "", "unsupported: ${command[1]}")
        }
    }

    private fun getMac(kind: String): String = buildString {
        append("Enabled: ${mac["$kind.Enabled"]}\n")
        append("Server: ${mac["$kind.Server"]}\n")
        append("Port: ${mac["$kind.Port"]}\n")
        append("Authenticated Proxy Enabled: 0\n")
    }

    private fun setMac(kind: String, command: List<String>): CommandResult {
        mac["$kind.Server"] = command.getOrNull(3).orEmpty()
        mac["$kind.Port"] = command.getOrNull(4).orEmpty()
        return ok()
    }

    private fun setState(kind: String, command: List<String>): CommandResult {
        mac["$kind.Enabled"] = if (command.getOrNull(3) == "on") "Yes" else "No"
        return ok()
    }
}
