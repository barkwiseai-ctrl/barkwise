import SwiftUI

struct BarkAIView: View {
    @ObservedObject var vm: AppViewModel

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                List(vm.conversation) { turn in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(turn.role.capitalized)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Text(turn.content)
                    }
                }

                HStack {
                    TextField("Ask BarkAI", text: $vm.chatText)
                        .textFieldStyle(.roundedBorder)
                    Button("Send") {
                        Task { await vm.sendChat() }
                    }
                }
                .padding(.horizontal)
                .padding(.bottom, 8)
            }
            .navigationTitle("BarkAI")
        }
    }
}
