import SwiftUI

struct LoginView: View {
    @ObservedObject var vm: AppViewModel
    @State private var userId = "user_2"
    @State private var password = "petsocial-demo"

    var body: some View {
        NavigationStack {
            Form {
                Section("Join Beta") {
                    TextField("User ID", text: $userId)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled(true)
                    SecureField("Password", text: $password)
                }

                Section {
                    Button(vm.isLoading ? "Signing In..." : "Sign In") {
                        Task { await vm.login(userId: userId, password: password) }
                    }
                    .disabled(vm.isLoading)
                }
            }
            .navigationTitle("BarkWise (test)")
        }
    }
}
