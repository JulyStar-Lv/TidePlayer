import AVFoundation
import MediaPlayer
import SwiftUI
import SharedKit

@MainActor
private final class NowPlayingCoordinator {
    private let infoCenter = MPNowPlayingInfoCenter.default()
    private let commandCenter = MPRemoteCommandCenter.shared()
    private var timer: Timer?
    private var currentMediaId: String?
    private var artworkMediaId: String?

    func start() {
        guard timer == nil else { return }
        sync()
        let timer = Timer(timeInterval: 1.0, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.sync()
            }
        }
        RunLoop.main.add(timer, forMode: .common)
        self.timer = timer
    }

    func stop(clearInfo: Bool) {
        timer?.invalidate()
        timer = nil
        if clearInfo {
            infoCenter.nowPlayingInfo = nil
            currentMediaId = nil
            artworkMediaId = nil
        }
    }

    func sync() {
        guard let snapshot = MainViewControllerKt.currentNowPlayingSnapshot() else {
            infoCenter.nowPlayingInfo = nil
            currentMediaId = nil
            artworkMediaId = nil
            updateCommandAvailability(hasItem: false, isPlaying: false, queueCount: 0, durationMs: 0)
            return
        }

        let mediaId = snapshot.mediaId
        let trackChanged = currentMediaId != mediaId
        currentMediaId = mediaId
        var info: [String: Any] = trackChanged ? [:] : (infoCenter.nowPlayingInfo ?? [:])

        info[MPMediaItemPropertyTitle] = snapshot.title
        if let artist = snapshot.artist, !artist.isEmpty {
            info[MPMediaItemPropertyArtist] = artist
        } else {
            info.removeValue(forKey: MPMediaItemPropertyArtist)
        }
        if let album = snapshot.album, !album.isEmpty {
            info[MPMediaItemPropertyAlbumTitle] = album
        } else {
            info.removeValue(forKey: MPMediaItemPropertyAlbumTitle)
        }

        let durationSeconds = max(0, Double(snapshot.durationMs) / 1_000.0)
        let elapsedSeconds = min(
            max(0, Double(snapshot.positionMs) / 1_000.0),
            durationSeconds > 0 ? durationSeconds : Double.greatestFiniteMagnitude
        )
        info[MPMediaItemPropertyPlaybackDuration] = durationSeconds
        info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = elapsedSeconds
        info[MPNowPlayingInfoPropertyPlaybackRate] = snapshot.isPlaying ? 1.0 : 0.0
        info[MPNowPlayingInfoPropertyDefaultPlaybackRate] = 1.0
        info[MPNowPlayingInfoPropertyExternalContentIdentifier] = mediaId
        info[MPNowPlayingInfoPropertyMediaType] = MPNowPlayingInfoMediaType.audio.rawValue
        info[MPNowPlayingInfoPropertyIsLiveStream] = false

        if snapshot.queueCount > 0 {
            info[MPNowPlayingInfoPropertyPlaybackQueueCount] = Int(snapshot.queueCount)
        } else {
            info.removeValue(forKey: MPNowPlayingInfoPropertyPlaybackQueueCount)
        }
        if snapshot.queueIndex >= 0 {
            info[MPNowPlayingInfoPropertyPlaybackQueueIndex] = Int(snapshot.queueIndex)
        } else {
            info.removeValue(forKey: MPNowPlayingInfoPropertyPlaybackQueueIndex)
        }

        if trackChanged {
            info.removeValue(forKey: MPMediaItemPropertyArtwork)
            artworkMediaId = nil
        }
        infoCenter.nowPlayingInfo = info

        updateCommandAvailability(
            hasItem: true,
            isPlaying: snapshot.isPlaying,
            queueCount: Int(snapshot.queueCount),
            durationMs: snapshot.durationMs
        )

        if artworkMediaId != mediaId {
            artworkMediaId = mediaId
            loadArtwork(for: mediaId)
        }
    }

    private func loadArtwork(for mediaId: String) {
        MainViewControllerKt.loadNowPlayingArtworkBase64(mediaId: mediaId) { [weak self] encoded in
            Task { @MainActor [weak self] in
                guard
                    let self,
                    self.currentMediaId == mediaId,
                    let encoded,
                    let data = Data(base64Encoded: encoded),
                    let image = UIImage(data: data)
                else {
                    return
                }
                var info = self.infoCenter.nowPlayingInfo ?? [:]
                info[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(
                    boundsSize: image.size,
                    requestHandler: { _ in image }
                )
                self.infoCenter.nowPlayingInfo = info
            }
        }
    }

    private func updateCommandAvailability(
        hasItem: Bool,
        isPlaying: Bool,
        queueCount: Int,
        durationMs: Int64
    ) {
        commandCenter.playCommand.isEnabled = hasItem && !isPlaying
        commandCenter.pauseCommand.isEnabled = hasItem && isPlaying
        commandCenter.stopCommand.isEnabled = hasItem
        commandCenter.togglePlayPauseCommand.isEnabled = hasItem
        commandCenter.nextTrackCommand.isEnabled = hasItem && queueCount > 1
        commandCenter.previousTrackCommand.isEnabled = hasItem && queueCount > 1
        commandCenter.skipForwardCommand.isEnabled = hasItem && durationMs > 0
        commandCenter.skipBackwardCommand.isEnabled = hasItem && durationMs > 0
        commandCenter.changePlaybackPositionCommand.isEnabled = hasItem && durationMs > 0
    }
}

@MainActor
private final class AppDelegate: NSObject, UIApplicationDelegate {
    private var remoteCommandTargets: [(command: MPRemoteCommand, target: Any)] = []
    private var audioSessionObservers: [NSObjectProtocol] = []
    private let nowPlaying = NowPlayingCoordinator()

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        configureAudioSession()
        configureRemoteCommands()
        configureAudioSessionObservers()
        application.beginReceivingRemoteControlEvents()
        nowPlaying.start()
        return true
    }

    func applicationDidBecomeActive(_ application: UIApplication) {
        nowPlaying.sync()
    }

    func applicationWillResignActive(_ application: UIApplication) {
        nowPlaying.sync()
    }

    func applicationDidEnterBackground(_ application: UIApplication) {
        nowPlaying.sync()
    }

    func applicationWillTerminate(_ application: UIApplication) {
        nowPlaying.stop(clearInfo: true)
        removeAudioSessionObservers()
        removeRemoteCommands()
        application.endReceivingRemoteControlEvents()
        try? AVAudioSession.sharedInstance().setActive(
            false,
            options: .notifyOthersOnDeactivation
        )
        MainViewControllerKt.shutdownApplication()
    }

    func application(
        _ application: UIApplication,
        handleEventsForBackgroundURLSession identifier: String,
        completionHandler: @escaping @Sendable () -> Void
    ) {
        MainViewControllerKt.handleEventsForBackgroundURLSession(
            identifier: identifier,
            completionHandler: completionHandler
        )
    }

    private func configureAudioSession() {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(
                .playback,
                mode: .default,
                options: [.allowAirPlay, .allowBluetoothA2DP]
            )
            try session.setActive(true)
        } catch {
            // AVPlayer reports playback failures through the shared controller.
        }
    }

    private func configureRemoteCommands() {
        let center = MPRemoteCommandCenter.shared()
        center.skipForwardCommand.preferredIntervals = [10]
        center.skipBackwardCommand.preferredIntervals = [10]

        register(center.playCommand) { [weak self] _ in
            self?.executeCommand { MainViewControllerKt.handlePlaybackPlayCommand() }
                ?? .commandFailed
        }
        register(center.pauseCommand) { [weak self] _ in
            self?.executeCommand { MainViewControllerKt.handlePlaybackPauseCommand() }
                ?? .commandFailed
        }
        register(center.stopCommand) { [weak self] _ in
            self?.executeCommand { MainViewControllerKt.handlePlaybackStopCommand() }
                ?? .commandFailed
        }
        register(center.togglePlayPauseCommand) { [weak self] _ in
            self?.executeCommand { MainViewControllerKt.handlePlaybackToggleCommand() }
                ?? .commandFailed
        }
        register(center.nextTrackCommand) { [weak self] _ in
            self?.executeCommand { MainViewControllerKt.handlePlaybackNextCommand() }
                ?? .commandFailed
        }
        register(center.previousTrackCommand) { [weak self] _ in
            self?.executeCommand { MainViewControllerKt.handlePlaybackPreviousCommand() }
                ?? .commandFailed
        }
        register(center.skipForwardCommand) { [weak self] event in
            let seconds = (event as? MPSkipIntervalCommandEvent)?.interval ?? 10
            return self?.executeCommand {
                MainViewControllerKt.handlePlaybackSeekByCommand(
                    deltaMs: Int64(seconds * 1_000)
                )
            } ?? .commandFailed
        }
        register(center.skipBackwardCommand) { [weak self] event in
            let seconds = (event as? MPSkipIntervalCommandEvent)?.interval ?? 10
            return self?.executeCommand {
                MainViewControllerKt.handlePlaybackSeekByCommand(
                    deltaMs: -Int64(seconds * 1_000)
                )
            } ?? .commandFailed
        }
        register(center.changePlaybackPositionCommand) { [weak self] event in
            guard let positionEvent = event as? MPChangePlaybackPositionCommandEvent else {
                return .commandFailed
            }
            return self?.executeCommand {
                MainViewControllerKt.handlePlaybackSeekToCommand(
                    positionMs: Int64(positionEvent.positionTime * 1_000)
                )
            } ?? .commandFailed
        }
    }

    private func executeCommand(_ command: @escaping () -> Bool) -> MPRemoteCommandHandlerStatus {
        var handled = false
        let work = {
            handled = command()
            if handled {
                self.nowPlaying.sync()
            }
        }
        if Thread.isMainThread {
            work()
        } else {
            DispatchQueue.main.sync(execute: work)
        }
        return handled ? .success : .noSuchContent
    }

    private func register(
        _ command: MPRemoteCommand,
        handler: @escaping (MPRemoteCommandEvent) -> MPRemoteCommandHandlerStatus
    ) {
        let target = command.addTarget(handler: handler)
        remoteCommandTargets.append((command, target))
    }

    private func removeRemoteCommands() {
        remoteCommandTargets.forEach { entry in
            entry.command.removeTarget(entry.target)
        }
        remoteCommandTargets.removeAll()
    }

    private func configureAudioSessionObservers() {
        let notifications = NotificationCenter.default
        let session = AVAudioSession.sharedInstance()

        audioSessionObservers.append(
            notifications.addObserver(
                forName: AVAudioSession.interruptionNotification,
                object: session,
                queue: .main
            ) { [weak self] notification in
                let rawType = notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt
                let rawOptions = notification.userInfo?[AVAudioSessionInterruptionOptionKey] as? UInt
                Task { @MainActor [weak self] in
                    self?.handleAudioSessionInterruption(
                        rawType: rawType,
                        rawOptions: rawOptions
                    )
                }
            }
        )
        audioSessionObservers.append(
            notifications.addObserver(
                forName: AVAudioSession.routeChangeNotification,
                object: session,
                queue: .main
            ) { [weak self] notification in
                let rawReason = notification.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt
                Task { @MainActor [weak self] in
                    self?.handleAudioRouteChange(rawReason: rawReason)
                }
            }
        )
        audioSessionObservers.append(
            notifications.addObserver(
                forName: AVAudioSession.mediaServicesWereResetNotification,
                object: session,
                queue: .main
            ) { [weak self] _ in
                Task { @MainActor [weak self] in
                    self?.configureAudioSession()
                    _ = MainViewControllerKt.handleAudioRouteChanged()
                    self?.nowPlaying.sync()
                }
            }
        )
    }

    private func removeAudioSessionObservers() {
        let notifications = NotificationCenter.default
        audioSessionObservers.forEach(notifications.removeObserver)
        audioSessionObservers.removeAll()
    }

    private func handleAudioSessionInterruption(rawType: UInt?, rawOptions: UInt?) {
        guard
            let rawType,
            let type = AVAudioSession.InterruptionType(rawValue: rawType)
        else {
            return
        }

        switch type {
        case .began:
            _ = MainViewControllerKt.handleAudioSessionInterruptionBegan()
            nowPlaying.sync()
        case .ended:
            let options = AVAudioSession.InterruptionOptions(rawValue: rawOptions ?? 0)
            let shouldResume = options.contains(.shouldResume)
            if shouldResume {
                try? AVAudioSession.sharedInstance().setActive(true)
            }
            _ = MainViewControllerKt.handleAudioSessionInterruptionEnded(
                shouldResume: shouldResume
            )
            nowPlaying.sync()
        @unknown default:
            break
        }
    }

    private func handleAudioRouteChange(rawReason: UInt?) {
        _ = MainViewControllerKt.handleAudioRouteChanged()
        guard
            let rawReason,
            let reason = AVAudioSession.RouteChangeReason(rawValue: rawReason)
        else {
            return
        }
        if reason == .oldDeviceUnavailable {
            _ = MainViewControllerKt.handleAudioRouteDisconnected()
        }
        nowPlaying.sync()
    }
}

private final class KeyboardShortcutHostController: UIViewController {
    private let contentController: UIViewController

    init(contentController: UIViewController) {
        self.contentController = contentController
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        addChild(contentController)
        contentController.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(contentController.view)
        NSLayoutConstraint.activate([
            contentController.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            contentController.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            contentController.view.topAnchor.constraint(equalTo: view.topAnchor),
            contentController.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        contentController.didMove(toParent: self)
    }

    override var canBecomeFirstResponder: Bool { true }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        becomeFirstResponder()
    }

    override var keyCommands: [UIKeyCommand]? {
        [
            shortcut(" ", [], #selector(togglePlayPause), "Play or pause"),
            shortcut(UIKeyCommand.inputLeftArrow, .alternate, #selector(previousTrack), "Previous track"),
            shortcut(UIKeyCommand.inputRightArrow, .alternate, #selector(nextTrack), "Next track"),
            shortcut(UIKeyCommand.inputLeftArrow, .command, #selector(seekBackward), "Seek backward 10 seconds"),
            shortcut(UIKeyCommand.inputRightArrow, .command, #selector(seekForward), "Seek forward 10 seconds"),
            shortcut(UIKeyCommand.inputLeftArrow, [.command, .shift], #selector(seekBackwardLong), "Seek backward 30 seconds"),
            shortcut(UIKeyCommand.inputRightArrow, [.command, .shift], #selector(seekForwardLong), "Seek forward 30 seconds"),
            shortcut(UIKeyCommand.inputEscape, [], #selector(navigateBack), "Back"),
        ]
    }

    private func shortcut(
        _ input: String,
        _ modifiers: UIKeyModifierFlags,
        _ action: Selector,
        _ title: String
    ) -> UIKeyCommand {
        let command = UIKeyCommand(input: input, modifierFlags: modifiers, action: action)
        command.discoverabilityTitle = title
        return command
    }

    private var isEditingText: Bool {
        guard let responder = view.window?.firstResponderView else { return false }
        return responder is UITextField || responder is UITextView
    }

    @objc private func togglePlayPause() {
        guard !isEditingText else { return }
        _ = MainViewControllerKt.handlePlaybackToggleCommand()
    }

    @objc private func previousTrack() {
        guard !isEditingText else { return }
        _ = MainViewControllerKt.handlePlaybackPreviousCommand()
    }

    @objc private func nextTrack() {
        guard !isEditingText else { return }
        _ = MainViewControllerKt.handlePlaybackNextCommand()
    }

    @objc private func seekBackward() {
        guard !isEditingText else { return }
        _ = MainViewControllerKt.handlePlaybackSeekByCommand(deltaMs: -10_000)
    }

    @objc private func seekForward() {
        guard !isEditingText else { return }
        _ = MainViewControllerKt.handlePlaybackSeekByCommand(deltaMs: 10_000)
    }

    @objc private func seekBackwardLong() {
        guard !isEditingText else { return }
        _ = MainViewControllerKt.handlePlaybackSeekByCommand(deltaMs: -30_000)
    }

    @objc private func seekForwardLong() {
        guard !isEditingText else { return }
        _ = MainViewControllerKt.handlePlaybackSeekByCommand(deltaMs: 30_000)
    }

    @objc private func navigateBack() {
        guard !isEditingText else { return }
        _ = MainViewControllerKt.handlePlaybackBackCommand()
    }
}

private extension UIView {
    var firstResponderView: UIView? {
        if isFirstResponder {
            return self
        }
        for subview in subviews {
            if let responder = subview.firstResponderView {
                return responder
            }
        }
        return nil
    }
}

private struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        KeyboardShortcutHostController(
            contentController: MainViewControllerKt.MainViewController()
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
    }
}

@main
struct AppMain: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea()
                .onOpenURL { url in
                    guard
                        let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
                        let code = components.queryItems?.first(where: { $0.name == "code" })?.value,
                        let state = components.queryItems?.first(where: { $0.name == "state" })?.value
                    else {
                        return
                    }
                    MainViewControllerKt.handleOneDriveOAuthRedirect(code: code, state: state)
                }
        }
    }
}
