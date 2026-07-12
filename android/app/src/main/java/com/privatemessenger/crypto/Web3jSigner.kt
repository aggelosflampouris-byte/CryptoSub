package com.privatemessenger.crypto

import org.web3j.crypto.Credentials
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import org.xmtp.android.library.SigningKey
import org.xmtp.android.library.messages.Signature
import org.xmtp.android.library.messages.SignatureOuterClass

class Web3jSigner(private val credentials: Credentials) : SigningKey {
    override val address: String
        get() = credentials.address

    override suspend fun sign(data: String): SignatureOuterClass.Signature? {
        val messageHash = org.web3j.crypto.Hash.sha3(data.toByteArray(Charsets.UTF_8))
        val signatureData = Sign.signPrefixedMessage(messageHash, credentials.ecKeyPair)
        
        val r = Numeric.toBytesPadded(Numeric.toBigInt(signatureData.r), 32)
        val s = Numeric.toBytesPadded(Numeric.toBigInt(signatureData.s), 32)
        val v = signatureData.v[0].toInt()
        
        // XMTP requires recovery param to be 0 or 1, Web3j gives 27 or 28
        val recoveryParam = if (v >= 27) v - 27 else v

        val ecdsaCompact = SignatureOuterClass.Signature.ECDSACompact.newBuilder()
            .setBytes(com.google.protobuf.ByteString.copyFrom(r + s))
            .setRecovery(recoveryParam)
            .build()
            
        return SignatureOuterClass.Signature.newBuilder()
            .setEcdsaCompact(ecdsaCompact)
            .build()
    }
    
    override suspend fun sign(data: ByteArray): SignatureOuterClass.Signature? {
        return sign(String(data, Charsets.UTF_8))
    }
}
