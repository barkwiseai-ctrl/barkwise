import SwiftUI

struct ContentView: View {
    @StateObject private var vm = AppViewModel()

    var body: some View {
        Group {
            if vm.session == nil {
                LoginView(vm: vm)
            } else {
                MainTabView(vm: vm)
            }
        }
        .task {
            if vm.session != nil {
                await vm.refreshAll()
            }
        }
        .alert("Error", isPresented: Binding(get: { vm.errorMessage != nil }, set: { _ in vm.errorMessage = nil })) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(vm.errorMessage ?? "Unknown error")
        }
    }
}

private struct MainTabView: View {
    @ObservedObject var vm: AppViewModel

    var body: some View {
        TabView {
            ServicesView(vm: vm)
                .tabItem { Label("Services", systemImage: "wrench.and.screwdriver") }
            CommunityView(vm: vm)
                .tabItem { Label("Community", systemImage: "person.3") }
            BarkAIView(vm: vm)
                .tabItem { Label("BarkAI", systemImage: "sparkles") }
            ProfileView(vm: vm)
                .tabItem { Label("Profile", systemImage: "person.crop.circle") }
        }
    }
}
