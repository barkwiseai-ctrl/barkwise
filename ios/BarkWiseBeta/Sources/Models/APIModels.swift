import Foundation

struct AuthLoginRequest: Encodable {
    let userId: String
    let password: String

    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case password
    }
}

struct AuthLoginResponse: Decodable {
    let accessToken: String
    let tokenType: String
    let userId: String
    let expiresAt: String

    enum CodingKeys: String, CodingKey {
        case accessToken = "access_token"
        case tokenType = "token_type"
        case userId = "user_id"
        case expiresAt = "expires_at"
    }
}

struct ServiceProvider: Decodable, Identifiable {
    let id: String
    let name: String
    let category: String
    let suburb: String
    let rating: Double
    let reviewCount: Int
    let priceFrom: Int
    let description: String

    enum CodingKeys: String, CodingKey {
        case id, name, category, suburb, rating, description
        case reviewCount = "review_count"
        case priceFrom = "price_from"
    }
}

struct CommunityGroup: Decodable, Identifiable {
    let id: String
    let name: String
    let suburb: String
    let memberCount: Int
    let membershipStatus: String

    enum CodingKeys: String, CodingKey {
        case id, name, suburb
        case memberCount = "member_count"
        case membershipStatus = "membership_status"
    }
}

struct GroupJoinRequest: Encodable {
    let userId: String

    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
    }
}

struct CommunityPost: Decodable, Identifiable {
    let id: String
    let type: String
    let title: String
    let body: String
    let suburb: String
    let createdAt: String?

    enum CodingKeys: String, CodingKey {
        case id, type, title, body, suburb
        case createdAt = "created_at"
    }
}

struct CommunityPostCreate: Encodable {
    let type: String
    let title: String
    let body: String
    let suburb: String
}

struct ChatRequest: Encodable {
    let userId: String
    let message: String
    let suburb: String?

    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case message
        case suburb
    }
}

struct ChatTurn: Decodable, Identifiable {
    let id = UUID()
    let role: String
    let content: String

    enum CodingKeys: String, CodingKey {
        case role
        case content
    }
}

struct ChatResponse: Decodable {
    let answer: String
    let conversation: [ChatTurn]
}

struct AppNotification: Decodable, Identifiable {
    let id: String
    let title: String
    let body: String
    let category: String
    let read: Bool
    let createdAt: String

    enum CodingKeys: String, CodingKey {
        case id, title, body, category, read
        case createdAt = "created_at"
    }
}

struct APIErrorResponse: Decodable {
    let detail: String?
}
