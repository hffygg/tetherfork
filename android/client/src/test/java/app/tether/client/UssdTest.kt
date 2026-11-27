package app.tether.client

import app.tether.client.model.UssdMenu
import app.tether.client.model.ClientState
import app.tether.client.model.Problem
import app.tether.client.model.UssdCodes
import app.tether.core.T
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Carrier codes (USSD): the code rules, menu replies, and a request answered, lost and refused. */
class UssdTest {
    @Test fun codesAreWhatTheAndroidPhoneAccepts() {
        assertTrue(UssdCodes.valid("*123#")); assertTrue(UssdCodes.valid("*140*11#")); assertTrue(UssdCodes.valid("#31#"))
        assertFalse("no #", UssdCodes.valid("*123")); assertFalse("a number", UssdCodes.valid("+989121234567"))
        assertFalse(UssdCodes.valid("*12a#")); assertFalse(UssdCodes.valid(""))
        assertEquals("*123#", UssdCodes.clean(" *1 2-3# "))
    }

    @Test fun aMenuIsAnsweredWithANewCodeThatCarriesTheChoice() {
        assertEquals("*123*2#", UssdCodes.reply("*123#", "2"))
        assertEquals("*140*1*3#", UssdCodes.reply("*140*1#", " 3 "))
        assertNull("nothing chosen", UssdCodes.reply("*123#", ""))
        assertNull("letters are no choice", UssdCodes.reply("*123#", "ab"))
        assertNull("no code to answer", UssdCodes.reply("123", "1"))
    }

    @Test fun aMenuIsRecognised() {
        assertTrue(UssdCodes.isMenu("Choose:\n1. Balance\n2. Packages\n3) Help"))
        assertTrue("Persian menus number the same way", UssdCodes.isMenu("1-شارژ\n2-بسته"))
        assertFalse(UssdCodes.isMenu("Your balance is 12,000 Rials."))
        assertFalse(UssdCodes.isMenu(null))
    }

    @Test fun recentCodesComeFirstWithoutRepeats() {
        assertEquals(listOf("*1#", "*2#", "*3#"), UssdCodes.remember(listOf("*2#", "*1#", "*3#"), "*1#"))
        assertEquals(2, UssdCodes.remember(listOf("*2#", "*3#"), "*1#", max = 2).size)
    }

    private val link = ClientStateTest.FakeLink()
    private val fx = TestEffects()
    private val s = ClientState(link, fx)

    @Test fun theAnswerToThisClientsCodeIsShown() {
        s.received(T.STATUS, JSONObject().put("sims", JSONArray()).put("tools", JSONObject().put("ussd", true)), null)
        assertTrue(s.canUssd)
        s.sendUssd("*123#", 3)
        val sent = link.last(T.USSD_SEND)
        assertEquals("*123#", sent.b.getString("code")); assertEquals(3, sent.b.getInt("sub"))
        assertEquals("*123#", s.ussdPending)
        s.sendUssd("*124#", 3)
        assertEquals("one at a time, as on the Android phone", 1, link.sent.count { it.t == T.USSD_SEND })
        s.received(T.USSD_RESULT, JSONObject().put("ok", true).put("text", "other"), sent.id + 99)
        assertEquals("another request's answer is not ours", "*123#", s.ussdPending)
        s.received(T.USSD_RESULT, JSONObject().put("ok", true).put("text", "Balance: 5"), sent.id)
        assertNull(s.ussdPending)
        assertEquals(ClientState.UssdAnswer("*123#", true, "Balance: 5", null), s.ussdAnswer)
    }

    @Test fun aRefusalAndALostAnswerAreSaid() {
        s.sendUssd("*123#", null)
        assertFalse("no SIM given: the Android phone's default", link.last(T.USSD_SEND).b.has("sub"))
        s.received(T.USSD_RESULT, JSONObject().put("ok", false).put("err", "The carrier refused the code"), link.last(T.USSD_SEND).id)
        assertEquals("The carrier refused the code", s.ussdAnswer?.err)
        s.sendUssd("*124#", null)
        fx.timers.forEach { (_, run) -> run() }
        assertNull(s.ussdPending)
        assertEquals(Problem.NO_ANSWER, s.ussdAnswer?.err)
    }

    @Test fun anOpenMenuIsAnsweredAndCancelledOnTheAndroidPhone() {
        s.sendUssd("*140#", null)
        s.received(T.USSD_RESULT, JSONObject().put("ok", true).put("text", "1. Balance\n2. Packages").put("session", true), link.last(T.USSD_SEND).id)
        assertTrue(s.ussdAnswer!!.session)
        s.replyUssd("2")
        val reply = link.last(UssdMenu.REPLY)
        assertEquals("2", reply.b.getString("text"))
        assertEquals("*140#", s.ussdPending)
        s.received(T.USSD_RESULT, JSONObject().put("ok", true).put("text", "1. Daily\n2. Weekly").put("session", true), reply.id)
        assertEquals("1. Daily\n2. Weekly", s.ussdAnswer?.text)
        s.cancelUssd()
        assertTrue(link.sent.any { it.t == UssdMenu.CANCEL })
        assertNull(s.ussdAnswer)
    }

    @Test fun aFinalAnswerTakesNoMenuReply() {
        s.sendUssd("*123#", null)
        s.received(T.USSD_RESULT, JSONObject().put("ok", true).put("text", "Balance: 5"), link.last(T.USSD_SEND).id)
        s.replyUssd("1")
        assertTrue("no menu open: nothing to type into", link.sent.none { it.t == UssdMenu.REPLY })
        s.cancelUssd()
        assertTrue("nothing open to close", link.sent.none { it.t == UssdMenu.CANCEL })
    }

    @Test fun nothingIsSentForANonCodeOrWithoutTheLink() {
        s.sendUssd("12345", null)
        link.isConnected = false
        s.sendUssd("*123#", null)
        assertTrue(link.sent.none { it.t == T.USSD_SEND })
        assertNull(s.ussdPending)
    }
}
