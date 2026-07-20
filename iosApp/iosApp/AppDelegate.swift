import UIKit
import UserNotifications
import BackgroundTasks

class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // Request notification permissions
        UNUserNotificationCenter.current().delegate = self
        UNUserNotificationCenter.current().requestAuthorization(
            options: [.alert, .badge, .sound]
        ) { granted, error in
            if let error = error {
                print("[CryptoSub] Notification permission error: \(error)")
            }
        }

        // Register background refresh task for XMTP message sync
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: "com.privatemessenger.xmtp-sync",
            using: nil
        ) { task in
            BackgroundRefreshHandler.shared.handleSync(task: task as! BGAppRefreshTask)
        }

        return true
    }

    // Allow notification display while app is in foreground
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .badge, .sound])
    }
}
