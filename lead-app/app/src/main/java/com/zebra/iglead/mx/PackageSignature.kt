package com.zebra.iglead.mx

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Base64

/**
 * Reads this app's signing certificate.
 *
 * MX's `CallerSignature` parameter is the same certificate that StageNow asks you
 * to export with SigTools (`SigTools.jar getcert ... -outform der`), so the app
 * can supply it at runtime instead: [Signature.toByteArray] already returns the
 * DER encoded X.509 certificate.
 */
object PackageSignature {

    /**
     * Base64 (single line) of this app's signing certificate.
     *
     * @throws MxException when the certificate cannot be read, since an MX
     * allowlist entry with the wrong signature silently never matches.
     */
    fun base64(context: Context): String {
        val signature = signingCertificates(context).firstOrNull()
            ?: throw MxException("This app has no signing certificate to report to MX.")

        return Base64.encodeToString(signature.toByteArray(), Base64.NO_WRAP)
    }

    /** The certificates the APK is signed with, newest signing scheme first. */
    @SuppressLint("PackageManagerGetSignatures")
    private fun signingCertificates(context: Context): Array<Signature> = try {
        val packageManager = context.packageManager
        val packageName = context.packageName

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.apkContentsSigners ?: emptyArray()
        } else {
            @Suppress("DEPRECATION")
            val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            info.signatures ?: emptyArray()
        }
    } catch (e: PackageManager.NameNotFoundException) {
        throw MxException("Could not read this app's package info: ${e.message}", e)
    }
}
