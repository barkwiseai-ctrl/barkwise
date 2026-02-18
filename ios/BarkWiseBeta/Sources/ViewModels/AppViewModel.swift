import Foundation
import SwiftUI

@MainActor
final class AppViewModel: ObservableObject {
    @Published var session: UserSession?
    @Published var isLoading = false
    @Published var errorMessage: String?

    @Published var selectedSuburb = "Surry Hills"
    @Published var providers: [ServiceProvider] = []
    @Published var groups: [CommunityGroup] = []
    @Published var posts: [CommunityPost] = []
    @Published var notifications: [AppNotification] = []

    @Published var chatText = ""
    @Published var conversation: [ChatTurn] = []

    @Published var postDraft = CommunityDraft()

    private let api: APIClient
    private let tokenStore: SessionTokenStore

    init(
        api: APIClient = APIClient(baseURL: AppConfig.apiBaseURL),
        tokenStore: SessionTokenStore = SessionTokenStore()
    ) {
        self.api = api
        self.tokenStore = tokenStore
        self.session = tokenStore.load()
    }

    func login(userId: String, password: String) async {
        isLoading = true
        defer { isLoading = false }
        do {
            let response = try await api.login(userId: userId, password: password)
            let session = UserSession(userId: response.userId, accessToken: response.accessToken)
            self.session = session
            tokenStore.save(session)
            errorMessage = nil
            await refreshAll()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func logout() {
        session = nil
        providers = []
        groups = []
        posts = []
        notifications = []
        conversation = []
        tokenStore.clear()
    }

    func refreshAll() async {
        guard let session else { return }
        isLoading = true
        defer { isLoading = false }
        do {
            async let providersTask = api.listProviders(token: session.accessToken, suburb: selectedSuburb)
            async let groupsTask = api.listGroups(token: session.accessToken, userId: session.userId, suburb: selectedSuburb)
            async let postsTask = api.listPosts(token: session.accessToken, userId: session.userId, suburb: selectedSuburb)
            async let notificationsTask = api.listNotifications(token: session.accessToken, userId: session.userId)

            providers = try await providersTask
            groups = try await groupsTask
            posts = try await postsTask
            notifications = try await notificationsTask
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func sendChat() async {
        guard let session else { return }
        let text = chatText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }

        chatText = ""
        conversation.append(ChatTurn(role: "user", content: text))

        do {
            let response = try await api.chat(
                token: session.accessToken,
                payload: ChatRequest(userId: session.userId, message: text, suburb: selectedSuburb)
            )
            conversation.append(ChatTurn(role: "assistant", content: response.answer))
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func join(group: CommunityGroup) async {
        guard let session else { return }
        do {
            _ = try await api.joinGroup(token: session.accessToken, groupId: group.id, userId: session.userId)
            await refreshAll()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func createLostFoundPost() async {
        guard let session else { return }
        let title = postDraft.title.trimmingCharacters(in: .whitespacesAndNewlines)
        let body = postDraft.body.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !title.isEmpty, !body.isEmpty else { return }

        do {
            _ = try await api.createPost(
                token: session.accessToken,
                payload: CommunityPostCreate(type: "lost_found", title: title, body: body, suburb: selectedSuburb)
            )
            postDraft = CommunityDraft()
            await refreshAll()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

final class SessionTokenStore {
    private let userDefaults: UserDefaults
    private let userIdKey = "barkwise_user_id"
    private let tokenKey = "barkwise_access_token"

    init(userDefaults: UserDefaults = .standard) {
        self.userDefaults = userDefaults
    }

    func save(_ session: UserSession) {
        userDefaults.set(session.userId, forKey: userIdKey)
        userDefaults.set(session.accessToken, forKey: tokenKey)
    }

    func load() -> UserSession? {
        guard let userId = userDefaults.string(forKey: userIdKey),
              let token = userDefaults.string(forKey: tokenKey),
              !userId.isEmpty,
              !token.isEmpty else {
            return nil
        }
        return UserSession(userId: userId, accessToken: token)
    }

    func clear() {
        userDefaults.removeObject(forKey: userIdKey)
        userDefaults.removeObject(forKey: tokenKey)
    }
}
