package com.tether.go

import android.app.Application
import android.util.Log
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Provider
import java.security.Security
import java.security.Signature

private const val TAG = "TetherSsh"
private const val BC_EDEC = "org.bouncycastle.jcajce.provider.asymmetric.edec."

/**
 * (serviceType, algorithm) pairs the SSH library wants to handle with its own
 * bundled pure-JVM crypto. On Android their platform JCA implementations resolve
 * to AndroidKeyStore, which can't do software/ephemeral keys. Removing them from
 * every provider makes the library's availability probes throw, so it falls back
 * to Google Tink (X25519) and Kyber-Kotlin (ML-KEM). curve25519-sha256 and the
 * post-quantum mlkem768x25519-sha256 hybrid are the modern OpenSSH KEX defaults.
 */
private val FORCE_FALLBACK = listOf(
  "KeyPairGenerator" to "X25519",
  "KeyPairGenerator" to "XDH",
  "KeyPairGenerator" to "ML-KEM-768",
  "KEM" to "ML-KEM",
)

/**
 * A minimal JCA provider exposing only BouncyCastle's Ed25519 KeyFactory and
 * Signature (plus KeyPairGenerator). Registered ahead of the platform so the SSH
 * library's Ed25519 host-key verification — `KeyFactory.getInstance("Ed25519")`
 * then `Signature.getInstance("Ed25519")`, with no provider specified — resolves
 * both to BouncyCastle, whose implementations interoperate. We deliberately do
 * NOT register the full BouncyCastle provider, because its X25519 KeyFactory
 * cannot satisfy the JDK `XECPrivateKeySpec` round-trip the library's X25519 path
 * needs, and BC cannot be cleanly stripped of X25519. Leaving X25519 off this
 * provider lets it fall through to the library's Tink fallback (see [FORCE_FALLBACK]).
 */
private class BcEd25519Provider : Provider("TetherBcEd25519", 1.0, "Ed25519 via BouncyCastle") {
  init {
    put("KeyFactory.Ed25519", BC_EDEC + "KeyFactorySpi\$Ed25519")
    put("Signature.Ed25519", BC_EDEC + "SignatureSpi\$Ed25519")
    put("KeyPairGenerator.Ed25519", BC_EDEC + "KeyPairGeneratorSpi\$Ed25519")
    put("Alg.Alias.KeyFactory.EdDSA", "Ed25519")
    put("Alg.Alias.Signature.EdDSA", "Ed25519")
    put("Alg.Alias.KeyFactory.1.3.101.112", "Ed25519")
    put("Alg.Alias.Signature.1.3.101.112", "Ed25519")
  }
}

/**
 * Application entry point. Works around crypto incompatibilities between the
 * bundled SSH library and modern Android's security providers: the library uses
 * unqualified `getInstance(...)` for its key exchange and host-key verification,
 * which on Android resolves to AndroidKeyStore and fails. Two fixes, applied
 * before any connection — register [BcEd25519Provider] for Ed25519, and strip the
 * [FORCE_FALLBACK] services so the library uses its bundled Tink/Kyber crypto.
 *
 * The app's own secrets keep using AndroidKeyStore — [com.tether.go.ssh.SecureStringStore]
 * requests that provider by name, so none of this affects it.
 */
class TetherApp : Application() {
  override fun onCreate() {
    super.onCreate()
    fixKexCryptoProviders()
  }

  private fun fixKexCryptoProviders() {
    if (Security.getProvider("TetherBcEd25519") == null) {
      val position = Security.insertProviderAt(BcEd25519Provider(), 1)
      Log.i(TAG, "Registered BcEd25519Provider at provider position $position")
    }

    // Provider.removeService(Service) is protected; reach it reflectively so we
    // can drop services registered via the modern putService() API (which are not
    // in the legacy property map, so Provider.remove alone can't drop them).
    val removeService = runCatching {
      Provider::class.java
        .getDeclaredMethod("removeService", Provider.Service::class.java)
        .apply { isAccessible = true }
    }.onFailure { Log.w(TAG, "Provider.removeService unavailable", it) }.getOrNull()

    for (provider in Security.getProviders()) {
      for ((type, algo) in FORCE_FALLBACK) {
        val service = provider.getService(type, algo) ?: continue
        runCatching { removeService?.invoke(provider, service) }
          .onSuccess { Log.i(TAG, "Removed $type.${service.algorithm} from '${provider.name}'") }
          .onFailure { Log.w(TAG, "Failed to remove $type/$algo from '${provider.name}'", it) }
        val key = "$type.$algo".uppercase()
        provider.keys.filterIsInstance<String>().filter {
          val u = it.uppercase()
          u == key || (u.startsWith("ALG.ALIAS.${type.uppercase()}.") && (provider[it] as? String)?.uppercase() == algo.uppercase())
        }.forEach { provider.remove(it) }
      }
    }

    logProbeResults()
  }

  /** Confirm each KEX primitive now resolves where we expect. */
  private fun logProbeResults() {
    val x25519 = runCatching { KeyPairGenerator.getInstance("X25519").provider.name }.getOrNull()
    Log.i(TAG, "X25519 KeyPairGenerator -> ${x25519 ?: "<none — library uses Tink>"}")
    val kemAvailable = runCatching {
      Class.forName("javax.crypto.KEM").getMethod("getInstance", String::class.java).invoke(null, "ML-KEM")
      true
    }.getOrDefault(false)
    Log.i(TAG, "javax.crypto.KEM 'ML-KEM' available=$kemAvailable (false => library uses Kyber-Kotlin)")
    val edKf = runCatching { KeyFactory.getInstance("Ed25519").provider.name }.getOrNull()
    val edSig = runCatching { Signature.getInstance("Ed25519").provider.name }.getOrNull()
    Log.i(TAG, "Ed25519 KeyFactory -> ${edKf ?: "<none>"}, Signature -> ${edSig ?: "<none>"}")
  }
}
