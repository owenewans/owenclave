package io.nekohasekai.sagernet.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Linux per-app routing install/remove with a [FakeHost] command runner: the
 * cgroup slice, the nftables/iptables marks, the policy route and every failure
 * path (no root, no cgroup v2, no nft/iptables, empty list).
 */
class PerAppRoutingTest {

    private fun routing(host: FakeHost, log: MutableList<String> = mutableListOf()) =
        PerAppRouting(runner = host, log = { log += it })

    private fun commands(host: FakeHost): String = host.commands.joinToString("\n") { it.joinToString(" ") }

    @Test
    fun `install creates the slice, the nft mark and the policy route`() {
        val host = FakeHost().apply { pids["curl"] = "123" }
        val routing = routing(host)

        val message = routing.install(listOf("curl", "/usr/bin/firefox"), "owenclave-tun")
        assertTrue(routing.installed)
        assertTrue(message.contains("1 of 2"), message)

        val all = commands(host)
        assertTrue(all.contains("mkdir -p /sys/fs/cgroup/owenclave.slice"), all)
        assertTrue(all.contains("nft add table inet owenclave"), all)
        assertTrue(
            all.contains("meta cgroup 4242 meta mark set 0x1"),
            "the nft rule must mark the slice cgroup: $all",
        )
        assertTrue(all.contains("ip rule add fwmark 0x1 lookup 1188"), all)
        assertTrue(all.contains("ip route add default dev owenclave-tun table 1188"), all)
        assertTrue(
            host.cgroupWrites.any { it.contains("echo 123 > /sys/fs/cgroup/owenclave.slice/cgroup.procs") },
            "the matching pid is moved into the slice: ${host.cgroupWrites}",
        )
    }

    @Test
    fun `remove tears down the marks, the route and the slice`() {
        val host = FakeHost().apply { pids["curl"] = "123" }
        val routing = routing(host)
        routing.install(listOf("curl"), "owenclave-tun")
        host.commands.clear()

        routing.remove()
        assertFalse(routing.installed)

        val all = commands(host)
        assertTrue(all.contains("nft delete table inet owenclave"), all)
        assertTrue(all.contains("ip rule del fwmark 0x1 lookup 1188"), all)
        assertTrue(all.contains("ip route del default dev owenclave-tun table 1188"), all)
        assertTrue(all.contains("rmdir /sys/fs/cgroup/owenclave.slice"), all)

        // remove is idempotent.
        routing.remove()
    }

    @Test
    fun `iptables is used when nft is missing`() {
        val host = FakeHost().apply {
            nftAvailable = false
            iptablesAvailable = true
            pids["curl"] = "123"
        }
        val routing = routing(host)
        routing.install(listOf("curl"), "owenclave-tun")

        val all = commands(host)
        assertTrue(
            all.contains("iptables -t mangle -A OWENCLAVE -m cgroup --path owenclave.slice -j MARK --set-mark 0x1"),
            all,
        )
        assertTrue(all.contains("iptables -t mangle -A OUTPUT -j OWENCLAVE"), all)

        routing.remove()
        val teardown = commands(host)
        assertTrue(teardown.contains("iptables -t mangle -D OUTPUT -j OWENCLAVE"), teardown)
    }

    @Test
    fun `missing root is reported precisely`() {
        val host = FakeHost().apply { uid = "1000" }
        val error = assertFailsWith<PerAppRoutingException> {
            routing(host).install(listOf("curl"), "owenclave-tun")
        }
        assertTrue(error.message!!.contains("root"), error.message)
        assertTrue(error.message!!.contains("1000"), error.message)
    }

    @Test
    fun `missing cgroup v2 is reported precisely`() {
        val host = FakeHost().apply { cgroupV2 = false }
        val error = assertFailsWith<PerAppRoutingException> {
            routing(host).install(listOf("curl"), "owenclave-tun")
        }
        assertTrue(error.message!!.contains("cgroup v2"), error.message)
    }

    @Test
    fun `missing nft and iptables is reported precisely`() {
        val host = FakeHost().apply {
            nftAvailable = false
            iptablesAvailable = false
            pids["curl"] = "123"
        }
        val error = assertFailsWith<PerAppRoutingException> {
            routing(host).install(listOf("curl"), "owenclave-tun")
        }
        assertTrue(error.message!!.contains("neither nft nor iptables"), error.message)
    }

    @Test
    fun `an empty process list is rejected`() {
        val error = assertFailsWith<PerAppRoutingException> {
            routing(FakeHost()).install(emptyList(), "owenclave-tun")
        }
        assertTrue(error.message!!.contains("empty"), error.message)
    }

    @Test
    fun `a process that is not running is counted, not fatal`() {
        val host = FakeHost()
        val logs = mutableListOf<String>()
        val routing = routing(host, logs)
        val message = routing.install(listOf("not-running"), "owenclave-tun")
        assertTrue(routing.installed)
        assertTrue(message.contains("0 of 1"), message)
        assertTrue(logs.any { it.contains("no running process") }, logs.toString())
    }

    @Test
    fun `parseHostList trims blanks and comments`() {
        assertEquals(
            listOf("curl", "/usr/bin/firefox", "telegram"),
            parseHostList("curl\n  /usr/bin/firefox  \n# a comment\n\ntelegram,curl"),
        )
    }
}
