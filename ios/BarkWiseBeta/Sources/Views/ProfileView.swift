import SwiftUI

struct ProfileView: View {
    @ObservedObject var vm: AppViewModel

    var body: some View {
        NavigationStack {
            List {
                Section("Session") {
                    Text("User: \(vm.session?.userId ?? "-")")
                    Text("Environment: \(AppConfig.environment)")
                    Text("API: \(AppConfig.apiBaseURL.absoluteString)")
                        .font(.caption)
                }

                Section("Notifications") {
                    ForEach(vm.notifications) { item in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(item.title)
                                .font(.headline)
                            Text(item.body)
                            Text(item.category)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }

                Section {
                    Button("Refresh") {
                        Task { await vm.refreshAll() }
                    }
                    Button("Log Out", role: .destructive) {
                        vm.logout()
                    }
                }
            }
            .navigationTitle("Profile")
        }
    }
}
