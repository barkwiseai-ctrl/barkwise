import SwiftUI

struct CommunityView: View {
    @ObservedObject var vm: AppViewModel

    var body: some View {
        NavigationStack {
            List {
                Section("Groups") {
                    ForEach(vm.groups) { group in
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(group.name)
                                Text("\(group.suburb) • \(group.memberCount) members")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            if group.membershipStatus != "member" {
                                Button("Join") {
                                    Task { await vm.join(group: group) }
                                }
                            } else {
                                Text("Member")
                                    .font(.caption)
                                    .foregroundStyle(.green)
                            }
                        }
                    }
                }

                Section("Create Lost/Found") {
                    TextField("Title", text: $vm.postDraft.title)
                    TextField("Details", text: $vm.postDraft.body, axis: .vertical)
                        .lineLimit(3...6)
                    Button("Post") {
                        Task { await vm.createLostFoundPost() }
                    }
                }

                Section("Posts") {
                    ForEach(vm.posts) { post in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(post.title)
                                .font(.headline)
                            Text(post.body)
                                .font(.subheadline)
                            Text(post.suburb)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
            .navigationTitle("Community")
            .refreshable { await vm.refreshAll() }
        }
    }
}
