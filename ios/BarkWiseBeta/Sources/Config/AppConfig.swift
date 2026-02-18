import Foundation

enum AppConfig {
    static var apiBaseURL: URL {
        if let value = Bundle.main.object(forInfoDictionaryKey: "API_BASE_URL") as? String,
           let url = URL(string: value),
           !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return url
        }
        return URL(string: "https://staging-api.barkwise.app/")!
    }

    static var environment: String {
        (Bundle.main.object(forInfoDictionaryKey: "APP_ENV") as? String) ?? "staging"
    }
}
