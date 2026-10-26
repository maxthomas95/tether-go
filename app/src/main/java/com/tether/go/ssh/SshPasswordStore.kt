package com.tether.go.ssh

import android.content.Context

private const val PASSWORD_PREFS_NAME = "tether_go_ssh_passwords"
private const val PASSWORD_KEYSTORE_ALIAS = "tether_go_ssh_passwords_v1"
private const val SAVED_PASSWORD_HOSTS_KEY = "saved_password_hosts_v1"
private const val PASSWORD_KEY_PREFIX = "password_v1_"

/**
 * Optional per-host SSH passwords, so a saved host can browse chats and
 * reconnect without retyping. Passwords are encrypted with an Android
 * Keystore key; only the set of host ids that have one is kept in the clear,
 * so the UI can ask "is there a saved password?" without decrypting.
 */
interface SshPasswordStore {
  fun hasPassword(hostId: String): Boolean
  fun loadPassword(hostId: String): String?
  fun savePassword(hostId: String, password: String)
  fun deletePassword(hostId: String)
}

open class PreferenceBackedSshPasswordStore(
  private val secrets: StringPreferenceStore,
  private val index: StringPreferenceStore,
) : SshPasswordStore {
  override fun hasPassword(hostId: String): Boolean = hostId in savedHostIds()

  override fun loadPassword(hostId: String): String? =
    if (hasPassword(hostId)) secrets.getString(PASSWORD_KEY_PREFIX + hostId) else null

  override fun savePassword(hostId: String, password: String) {
    secrets.putString(PASSWORD_KEY_PREFIX + hostId, password)
    writeHostIds(savedHostIds() + hostId)
  }

  override fun deletePassword(hostId: String) {
    secrets.removeString(PASSWORD_KEY_PREFIX + hostId)
    writeHostIds(savedHostIds() - hostId)
  }

  private fun savedHostIds(): Set<String> =
    index.getString(SAVED_PASSWORD_HOSTS_KEY)
      ?.split('\n')
      ?.filter { it.isNotBlank() }
      ?.toSet()
      .orEmpty()

  private fun writeHostIds(ids: Set<String>) {
    index.putString(SAVED_PASSWORD_HOSTS_KEY, ids.sorted().joinToString("\n"))
  }
}

class AndroidSshPasswordStore(
  context: Context,
) : PreferenceBackedSshPasswordStore(
  secrets = EncryptedStringPreferenceStore(
    backingStore = passwordPrefs(context),
    cipher = AndroidKeystoreAesGcmStringCipher(PASSWORD_KEYSTORE_ALIAS),
  ),
  index = passwordPrefs(context),
)

private fun passwordPrefs(context: Context): StringPreferenceStore =
  SharedPreferencesStringStore(
    context.applicationContext.getSharedPreferences(PASSWORD_PREFS_NAME, Context.MODE_PRIVATE),
  )

/**
 * Resolve stored credentials for a saved host: its private key if one is
 * linked, else a saved password. Returns null when the user must be asked, or
 * when secure storage can't be read right now (e.g. the Keystore key needs a
 * recent device unlock); callers then fall back to a password prompt.
 */
class SshStoredAuthResolver(
  private val privateKeyStore: SshPrivateKeyStore,
  private val passwordStore: SshPasswordStore,
) {
  fun resolve(hostId: String?, privateKeyId: String?): SshAuthMaterial? {
    privateKeyId?.let { keyId ->
      val material = runCatching { privateKeyStore.loadPrivateKeyMaterial(keyId) }.getOrNull()
      if (material != null) return SshAuthMaterial.PrivateKey(material.privateKeyData, material.passphrase)
    }
    val id = hostId ?: return null
    return runCatching { passwordStore.loadPassword(id) }.getOrNull()?.let { SshAuthMaterial.Password(it) }
  }

  /** Whether [resolve] is expected to succeed without decrypting anything. */
  fun hasStoredAuth(hostId: String?, privateKeyId: String?): Boolean =
    privateKeyId != null || (hostId != null && passwordStore.hasPassword(hostId))
}
