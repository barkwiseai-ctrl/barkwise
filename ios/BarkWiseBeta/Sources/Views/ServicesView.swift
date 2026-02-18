import SwiftUI

struct ServicesView: View {
    @ObservedObject var vm: AppViewModel

    var body: some View {
        NavigationStack {
            List(vm.providers) { provider in
                VStack(alignment: .leading, spacing: 4) {
                    Text(provider.name)
                        .font(.headline)
                    Text("\(provider.suburb) • \(provider.category.replacingOccurrences(of: "_", with: " "))")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Text("$\(provider.priceFrom) • \(provider.rating, specifier: "%.1f")★")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Services")
            .refreshable { await vm.refreshAll() }
        }
    }
}
