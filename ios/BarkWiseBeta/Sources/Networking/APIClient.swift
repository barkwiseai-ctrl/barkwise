import Foundation

final class APIClient {
    private let baseURL: URL
    private let session: URLSession

    init(baseURL: URL, session: URLSession = .shared) {
        self.baseURL = baseURL
        self.session = session
    }

    func login(userId: String, password: String) async throws -> AuthLoginResponse {
        let payload = AuthLoginRequest(userId: userId, password: password)
        return try await send(path: "auth/login", method: "POST", body: payload, token: nil)
    }

    func listProviders(token: String, suburb: String?) async throws -> [ServiceProvider] {
        var queryItems: [URLQueryItem] = []
        if let suburb, !suburb.isEmpty {
            queryItems.append(URLQueryItem(name: "suburb", value: suburb))
        }
        return try await send(path: "services/providers", method: "GET", queryItems: queryItems, token: token)
    }

    func listGroups(token: String, userId: String, suburb: String?) async throws -> [CommunityGroup] {
        var queryItems = [URLQueryItem(name: "user_id", value: userId)]
        if let suburb, !suburb.isEmpty {
            queryItems.append(URLQueryItem(name: "suburb", value: suburb))
        }
        return try await send(path: "community/groups", method: "GET", queryItems: queryItems, token: token)
    }

    func joinGroup(token: String, groupId: String, userId: String) async throws -> CommunityGroup {
        try await send(
            path: "community/groups/\(groupId)/join",
            method: "POST",
            body: GroupJoinRequest(userId: userId),
            token: token
        )
    }

    func listPosts(token: String, userId: String, suburb: String?) async throws -> [CommunityPost] {
        var queryItems = [URLQueryItem(name: "user_id", value: userId)]
        if let suburb, !suburb.isEmpty {
            queryItems.append(URLQueryItem(name: "suburb", value: suburb))
        }
        return try await send(path: "community/posts", method: "GET", queryItems: queryItems, token: token)
    }

    func createPost(token: String, payload: CommunityPostCreate) async throws -> CommunityPost {
        try await send(path: "community/posts", method: "POST", body: payload, token: token)
    }

    func chat(token: String, payload: ChatRequest) async throws -> ChatResponse {
        try await send(path: "chat", method: "POST", body: payload, token: token)
    }

    func listNotifications(token: String, userId: String) async throws -> [AppNotification] {
        let queryItems = [URLQueryItem(name: "user_id", value: userId)]
        return try await send(path: "notifications", method: "GET", queryItems: queryItems, token: token)
    }

    private func send<T: Decodable>(
        path: String,
        method: String,
        queryItems: [URLQueryItem] = [],
        token: String?
    ) async throws -> T {
        let request = try buildRequest(path: path, method: method, queryItems: queryItems, token: token, body: Optional<Data>.none)
        let (data, response) = try await session.data(for: request)
        return try decodeResponse(data: data, response: response)
    }

    private func send<T: Decodable, Body: Encodable>(
        path: String,
        method: String,
        body: Body,
        token: String?
    ) async throws -> T {
        let encoded = try JSONEncoder().encode(body)
        let request = try buildRequest(path: path, method: method, queryItems: [], token: token, body: encoded)
        let (data, response) = try await session.data(for: request)
        return try decodeResponse(data: data, response: response)
    }

    private func buildRequest(
        path: String,
        method: String,
        queryItems: [URLQueryItem],
        token: String?,
        body: Data?
    ) throws -> URLRequest {
        let normalized = path.hasPrefix("/") ? String(path.dropFirst()) : path
        guard var components = URLComponents(url: baseURL.appendingPathComponent(normalized), resolvingAgainstBaseURL: false) else {
            throw URLError(.badURL)
        }
        if !queryItems.isEmpty {
            components.queryItems = queryItems
        }
        guard let url = components.url else {
            throw URLError(.badURL)
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let body {
            request.httpBody = body
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        if let token, !token.isEmpty {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        return request
    }

    private func decodeResponse<T: Decodable>(data: Data, response: URLResponse) throws -> T {
        guard let http = response as? HTTPURLResponse else {
            throw URLError(.badServerResponse)
        }

        let decoder = JSONDecoder()
        if (200..<300).contains(http.statusCode) {
            return try decoder.decode(T.self, from: data)
        }

        let apiError = try? decoder.decode(APIErrorResponse.self, from: data)
        let detail = apiError?.detail ?? HTTPURLResponse.localizedString(forStatusCode: http.statusCode)
        throw NSError(domain: "BarkWiseAPI", code: http.statusCode, userInfo: [NSLocalizedDescriptionKey: detail])
    }
}
