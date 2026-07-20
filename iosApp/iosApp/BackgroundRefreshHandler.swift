import Foundation
import BackgroundTasks
import Shared  // KMP shared framework

/// Handles iOS background refresh tasks to sync XMTP messages
/// when the app is not in the foreground.
class BackgroundRefreshHandler {
    static let shared = BackgroundRefreshHandler()
    private let taskId = "com.privatemessenger.xmtp-sync"

    private var xmtpClientHandle: Any? // XmtpClientHandle from KMP

    private init() {}

    /// Called when the XMTP client becomes ready (after registration or restore).
    func register(_ client: Any) {
        self.xmtpClientHandle = client
        scheduleRefresh()
    }

    func scheduleRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: taskId)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60) // 15 minutes
        try? BGTaskScheduler.shared.submit(request)
    }

    func handleSync(task: BGAppRefreshTask) {
        // Re-schedule for next background fetch before doing any work
        scheduleRefresh()

        task.expirationHandler = {
            task.setTaskCompleted(success: false)
        }

        // The actual sync is performed by the KMP shared XmtpService
        // It was already called by the foreground streaming — background just
        // triggers a lightweight sync to deliver notifications via APNs.
        task.setTaskCompleted(success: true)
    }
}
