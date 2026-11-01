package com.wangzan101.mika.ui

import org.junit.Rule
import com.wangzan101.mika.i18n.ChineseTexts
import com.wangzan101.mika.core.shell.BuiltinShellFailure
import com.wangzan101.mika.core.shell.BuiltinShellStatus
import com.wangzan101.mika.settings.Problem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneSetupCopyTest {
    @get:Rule val texts = ChineseTexts()

    private val statuses = listOf(
        BuiltinShellStatus.NotPaired, BuiltinShellStatus.WirelessDebuggingOff, BuiltinShellStatus.Connecting, BuiltinShellStatus.Running,
    ) + BuiltinShellFailure.entries.map { BuiltinShellStatus.Failed(it) }

    @Test
    fun connectPortInputAcceptsOnlyValidPorts() {
        assertEquals(41235, ConnectPortInput.parse("41235"))
        assertEquals(41235, ConnectPortInput.parse(" 41 235 "))
        assertEquals(41235, ConnectPortInput.parse("４１２３５"))
        // 整段粘了「IP 地址和端口」也行
        assertEquals(41235, ConnectPortInput.parse("192.0.2.10:41235"))
        assertEquals(41235, ConnectPortInput.parse("192.0.2.10：41235"))
        assertEquals(1, ConnectPortInput.parse("1"))
        assertEquals(65535, ConnectPortInput.parse("65535"))
        for (bad in listOf("", " ", "0", "65536", "70000", "abc", "41a35", "-1", "192.0.2.10:")) assertNull(bad, ConnectPortInput.parse(bad))
    }

    @Test
    fun reconnectOnlyWhenRetryingIsTheFix() {
        val reconnectable = statuses.filter { PhoneSetupCopy.canReconnect(it) }
        assertEquals(
            listOf(BuiltinShellFailure.ConnectFailed, BuiltinShellFailure.HelperStartFailed, BuiltinShellFailure.Unknown),
            reconnectable.map { (it as BuiltinShellStatus.Failed).reason },
        )
        reconnectable.forEach { s ->
            assertEquals(Fix.RetryPhoneHelper, StatusCopy.directFix(Problem.PhoneHelper(s)))
        }
        // 没配对要走引导;无线调试关着要先去打开;没 Wi-Fi 会自己恢复;配对失效要重新配对
        listOf(
            BuiltinShellStatus.NotPaired, BuiltinShellStatus.WirelessDebuggingOff, BuiltinShellStatus.Connecting,
            BuiltinShellStatus.Running, BuiltinShellStatus.Failed(BuiltinShellFailure.NoWifi),
            BuiltinShellStatus.Failed(BuiltinShellFailure.AuthorizationRevoked),
        ).forEach { assertFalse(PhoneSetupCopy.canReconnect(it)) }
    }

    @Test
    fun bannerAndFixForPhoneHelperProblem() {
        statuses.filter { it != BuiltinShellStatus.Running }.forEach { s ->
            val p = Problem.PhoneHelper(s)
            val fix = when {
                PhoneSetupCopy.canReconnect(s) -> Fix.RetryPhoneHelper
                // 这个 Wi-Fi 还没允许无线调试:直接去亲手打开一次
                s == BuiltinShellStatus.Failed(BuiltinShellFailure.WifiNotAllowed) -> Fix.OpenWirelessDebugging
                else -> Fix.OpenPhoneSetup
            }
            assertEquals(fix, StatusCopy.directFix(p))
            assertEquals(Fix.OpenPhoneSetup, StatusCopy.explain(p, "14").fix)
        }
    }
}
