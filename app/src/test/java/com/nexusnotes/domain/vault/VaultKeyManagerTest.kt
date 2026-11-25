package com.nexusnotes.domain.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultKeyManagerTest {
    /** Stands in for Android Keystore. */
    private class FakeDeviceKey : DeviceKeyWrapper {
        var key: ByteArray? = VaultCrypto.newKey()
        override fun wrap(plain: ByteArray) = VaultCrypto.seal(key ?: throw VaultKeysLostException(), plain, "device")
        override fun unwrap(blob: ByteArray): ByteArray {
            val k = key ?: throw VaultKeysLostException()
            return try { VaultCrypto.open(k, blob, "device") } catch (e: VaultTamperedException) { throw VaultKeysLostException(e) }
        }
        override fun delete() { key = null }
    }

    private class MemoryStore : VaultMetaStore {
        var bytes: ByteArray? = null
        override fun read() = bytes?.let { VaultMeta.decode(it) }
        override fun write(meta: VaultMeta) { bytes = meta.encode() }
        override fun delete() { bytes = null }
    }

    private var time = 1_000_000L
    private val device = FakeDeviceKey()
    private val store = MemoryStore()
    private val manager = VaultKeyManager(device, store, { time }, iterations = 10_000)
    private val pin = "482915".toCharArray()

    @Test fun createThenUnlockGivesSameKey() {
        assertFalse(manager.isSetUp())
        val key = manager.create(pin)
        assertTrue(manager.isSetUp())
        val r = manager.unlock(pin) as UnlockResult.Success
        assertArrayEquals(key, r.key)
    }

    @Test fun pinIsNotStoredInPlaintext() {
        manager.create(pin)
        assertFalse(String(store.bytes!!).contains("482915"))
    }

    @Test fun wrongPinCountsDownThenLocksOut() {
        manager.create(pin)
        repeat(4) { i ->
            val r = manager.unlock("000111".toCharArray()) as UnlockResult.WrongPin
            assertEquals(4 - i, r.attemptsLeft)
            assertEquals(0, r.lockedUntil)
        }
        val fifth = manager.unlock("000111".toCharArray()) as UnlockResult.WrongPin
        assertEquals(time + 30_000, fifth.lockedUntil)
        // Even the right PIN is refused while locked out.
        assertTrue(manager.unlock(pin) is UnlockResult.LockedOut)
        time += 30_001
        assertTrue(manager.unlock(pin) is UnlockResult.Success)
        assertEquals(0, store.read()!!.failedAttempts)
    }

    @Test fun lockoutGrowsAndIsCapped() {
        assertEquals(0, VaultPolicy.lockoutMs(4))
        assertEquals(30_000, VaultPolicy.lockoutMs(5))
        assertEquals(60_000, VaultPolicy.lockoutMs(6))
        assertEquals(60L * 60 * 1000, VaultPolicy.lockoutMs(50))
    }

    @Test fun attemptIsCountedBeforeCheck() {
        manager.create(pin)
        // Simulate a crash during PBKDF2: the counter was already written.
        val before = store.read()!!.failedAttempts
        manager.unlock("999000".toCharArray())
        assertEquals(before + 1, store.read()!!.failedAttempts)
    }

    @Test fun deviceKeyLossIsReportedNotWrongPin() {
        manager.create(pin)
        device.delete()
        assertEquals(UnlockResult.KeysLost, manager.unlock(pin))
        assertEquals(0, store.read()!!.failedAttempts)
    }

    @Test fun copiedFilesUselessOnAnotherDevice() {
        manager.create(pin)
        val otherDevice = VaultKeyManager(FakeDeviceKey(), store, { time }, iterations = 10_000)
        assertEquals(UnlockResult.KeysLost, otherDevice.unlock(pin))
    }

    @Test fun changePinKeepsKey() {
        val key = manager.create(pin)
        val newPin = "739104".toCharArray()
        assertTrue(manager.changePin(pin, newPin) is UnlockResult.Success)
        assertTrue(manager.unlock(pin) is UnlockResult.WrongPin)
        assertArrayEquals(key, (manager.unlock(newPin) as UnlockResult.Success).key)
    }

    @Test fun changePinWithWrongOldPinFails() {
        manager.create(pin)
        assertTrue(manager.changePin("000999".toCharArray(), "739104".toCharArray()) is UnlockResult.WrongPin)
        assertTrue(manager.unlock(pin) is UnlockResult.Success)
    }

    @Test fun weakPinsRejected() {
        assertThrows(IllegalArgumentException::class.java) { manager.create("12345".toCharArray()) }
        assertThrows(IllegalArgumentException::class.java) { manager.create("123456".toCharArray()) }
        assertThrows(IllegalArgumentException::class.java) { manager.create("111111".toCharArray()) }
        assertNull(VaultPolicy.pinProblem("correct horse".toCharArray()))
    }

    @Test fun destroyRemovesEverything() {
        manager.create(pin)
        manager.destroy()
        assertFalse(manager.isSetUp())
        assertEquals(UnlockResult.NotSetUp, manager.unlock(pin))
    }

    @Test fun cannotCreateTwice() {
        manager.create(pin)
        assertThrows(IllegalStateException::class.java) { manager.create("739104".toCharArray()) }
    }

    @Test fun metaSurvivesEncodeDecode() {
        manager.create(pin)
        manager.setAutoLock(0)
        manager.setBiometricKey(byteArrayOf(1, 2, 3))
        val m = store.read()!!
        assertEquals(0L, m.autoLockMs)
        assertArrayEquals(byteArrayOf(1, 2, 3), m.biometricKey)
        assertThrows(IllegalArgumentException::class.java) { VaultMeta.decode("format=9".toByteArray()) }
    }

    @Test fun damagedSettingsFileIsKeysLostNotANewVault() {
        manager.create(pin)
        store.bytes = "garbage=1".toByteArray()
        assertTrue(manager.isSetUp())
        assertEquals(UnlockResult.KeysLost, manager.unlock(pin))
        assertThrows(IllegalStateException::class.java) { manager.create(pin) }
        manager.destroy()
        assertFalse(manager.isSetUp())
    }
}
