import Foundation
import XMTP
import BackgroundTasks

/// Swift-side XMTP wrapper that is ObjC-compatible so Kotlin/Native can
/// call it via cinterop.  All public methods and properties are annotated
/// with @objc and use only ObjC-bridgeable types (String, NSData, Bool, etc.)
@objc public class XmtpClientWrapper: NSObject {

    // ── Internal state ─────────────────────────────────────────────────────
    private let client: XMTP.Client
    private var streamTask: Task<Void, Never>?

    private init(client: XMTP.Client) {
        self.client = client
    }

    // ── Factory ────────────────────────────────────────────────────────────

    /// Creates and returns an authenticated XMTP client.
    /// Connects to the XMTP production network using MLS/V3 protocol.
    @objc public static func createWithPrivateKeyHex(
        privateKeyHex: String,
        dbEncryptionKey: NSData
    ) async throws -> XmtpClientWrapper {
        let keyBytes = stride(from: 0, to: privateKeyHex.count, by: 2).compactMap { i -> UInt8? in
            let start = privateKeyHex.index(privateKeyHex.startIndex, offsetBy: i)
            let end = privateKeyHex.index(start, offsetBy: 2, limitedBy: privateKeyHex.endIndex) ?? privateKeyHex.endIndex
            return UInt8(privateKeyHex[start..<end], radix: 16)
        }
        let privateKeyData = Data(keyBytes)
        let account = try PrivateKey(privateKeyData)

        let encKey = Data(referencing: dbEncryptionKey)
        let options = XMTP.ClientOptions(
            api: .init(env: .production, isSecure: true),
            dbEncryptionKey: encKey
        )
        let client = try await XMTP.Client.create(account: account, options: options)
        return XmtpClientWrapper(client: client)
    }

    // ── Properties ─────────────────────────────────────────────────────────

    @objc public var inboxId: String { client.inboxID }
    @objc public var publicAddress: String { client.address }

    // ── Methods ────────────────────────────────────────────────────────────

    @objc public func canMessage(_ address: String) async -> Bool {
        let result = try? await client.canMessage(addresses: [address])
        return result?[address.lowercased()] ?? false
    }

    @objc public func findOrCreateDm(_ address: String) async throws -> String {
        let dm = try await client.conversations.newDirectMessage(with: address)
        return dm.id
    }

    @objc public func newGroup(_ memberInboxIds: [String]) async throws -> String {
        let group = try await client.conversations.newGroup(with: memberInboxIds)
        return group.id
    }

    @objc public func sendMessage(_ conversationId: String, text: String) async throws -> String {
        let allConversations = try await client.conversations.list()
        guard let conversation = allConversations.first(where: { $0.id == conversationId }) else {
            throw NSError(
                domain: "XmtpClientWrapper",
                code: 404,
                userInfo: [NSLocalizedDescriptionKey: "Conversation not found: \(conversationId)"]
            )
        }
        return try await conversation.send(text: text)
    }

    @objc public func syncConversations() async throws {
        try await client.conversations.sync()
    }

    /// Streams all incoming messages, calling [callback] for each one.
    /// Returns a StreamHandle whose [cancel] method stops the stream.
    @objc public func streamAllMessages(
        callback: @escaping (
            _ id: String,
            _ conversationId: String,
            _ senderInboxId: String,
            _ body: String,
            _ sentAtMs: Int64
        ) -> Void
    ) -> StreamHandle {
        let task = Task {
            guard let stream = try? await client.conversations.streamAllMessages() else { return }
            for await message in stream {
                callback(
                    message.id,
                    message.topic,    // conversationId maps to topic in V3
                    message.senderInboxID,
                    message.body,
                    Int64(message.sentAt.timeIntervalSince1970 * 1000)
                )
            }
        }
        streamTask = task
        return StreamHandle(task: task)
    }
}

/// A cancellable handle to an active XMTP message stream.
@objc public class StreamHandle: NSObject {
    private let task: Task<Void, Never>

    init(task: Task<Void, Never>) {
        self.task = task
    }

    @objc public func cancel() {
        task.cancel()
    }
}
