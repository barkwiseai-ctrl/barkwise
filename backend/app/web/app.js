const state = {
  apiBase: localStorage.getItem("bw_api_base") || window.location.origin,
  userId: localStorage.getItem("bw_user_id") || "user_1",
  token: localStorage.getItem("bw_token") || "",
};

const el = (id) => document.getElementById(id);

function log(message, payload) {
  const line = payload ? `${message}\n${JSON.stringify(payload, null, 2)}` : message;
  const box = el("log");
  box.textContent = `${new Date().toISOString()} ${line}\n\n${box.textContent}`;
}

function setSessionLabel() {
  el("sessionState").textContent = state.token
    ? `Signed in as ${state.userId}`
    : `Using user ${state.userId} without token (works when AUTH_REQUIRED=false)`;
}

function authHeaders() {
  if (!state.token) return {};
  return { Authorization: `Bearer ${state.token}` };
}

async function api(path, options = {}) {
  const response = await fetch(`${state.apiBase}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...authHeaders(),
      ...(options.headers || {}),
    },
  });
  const text = await response.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = text;
    }
  }
  if (!response.ok) {
    throw new Error(`${response.status}: ${typeof data === "object" ? JSON.stringify(data) : data}`);
  }
  return data;
}

function renderList(containerId, items, itemRenderer) {
  const container = el(containerId);
  if (!items.length) {
    container.innerHTML = '<div class="item">No items</div>';
    return;
  }
  container.innerHTML = items.map(itemRenderer).join("");
}

async function loadProviders() {
  const category = el("serviceCategory").value.trim();
  const suburb = el("serviceSuburb").value.trim();
  const params = new URLSearchParams();
  if (category) params.set("category", category);
  if (suburb) params.set("suburb", suburb);
  const query = params.toString() ? `?${params}` : "";
  const providers = await api(`/services/providers${query}`);
  renderList("providersList", providers, (p) =>
    `<div class="item"><h4>${p.name}</h4><p>${p.category} in ${p.suburb}</p><p>Rating ${p.rating} (${p.review_count}) · from $${p.price_from}</p><p><code>${p.id}</code></p></div>`,
  );
}

async function createBooking(event) {
  event.preventDefault();
  const payload = {
    user_id: state.userId,
    provider_id: el("bookingProviderId").value.trim(),
    pet_name: el("bookingPetName").value.trim(),
    date: el("bookingDate").value,
    time_slot: el("bookingTime").value.trim(),
    note: el("bookingNote").value.trim(),
  };
  const booking = await api("/services/bookings", { method: "POST", body: JSON.stringify(payload) });
  el("bookingResult").textContent = JSON.stringify(booking, null, 2);
}

async function loadCommunityFeed() {
  const suburb = el("communitySuburb").value.trim();
  const q = el("communityQuery").value.trim();
  const params = new URLSearchParams();
  if (suburb) params.set("suburb", suburb);
  if (q) params.set("q", q);
  params.set("user_id", state.userId);
  const posts = await api(`/community/posts?${params}`);
  renderList("communityPosts", posts.slice(0, 20), (post) =>
    `<div class="item"><h4>${post.title}</h4><p>${post.type} · ${post.suburb}</p><p>${post.body}</p></div>`,
  );
}

async function loadGroups() {
  const suburb = el("groupSuburb").value.trim();
  const params = new URLSearchParams({ user_id: state.userId });
  if (suburb) params.set("suburb", suburb);
  const groups = await api(`/community/groups?${params}`);
  renderList("groupsList", groups, (g) => {
    const joinBtn = g.membership_status === "none"
      ? `<button type="button" data-join-group="${g.id}">Join</button>`
      : `<span>status: ${g.membership_status}</span>`;
    return `<div class="item"><h4>${g.name}</h4><p>${g.suburb} · members ${g.member_count}</p><p>${joinBtn}</p></div>`;
  });
}

async function createPost(event) {
  event.preventDefault();
  const payload = {
    type: el("postType").value,
    title: el("postTitle").value.trim(),
    body: el("postBody").value.trim(),
    suburb: el("postSuburb").value.trim(),
  };
  const post = await api("/community/posts", { method: "POST", body: JSON.stringify(payload) });
  log("Post created", post);
  await loadCommunityFeed();
}

async function joinGroup(groupId) {
  const result = await api(`/community/groups/${groupId}/join`, {
    method: "POST",
    body: JSON.stringify({ user_id: state.userId }),
  });
  log("Join group result", result);
  await loadGroups();
}

function appendChat(role, content) {
  const node = document.createElement("div");
  node.className = "chat-msg";
  const roleNode = document.createElement("div");
  roleNode.className = "role";
  roleNode.textContent = role;
  const contentNode = document.createElement("div");
  contentNode.textContent = content;
  node.append(roleNode, contentNode);
  const thread = el("chatThread");
  thread.appendChild(node);
  thread.scrollTop = thread.scrollHeight;
  return contentNode;
}

function parseSseChunks(buffer, onEvent) {
  const parts = buffer.split("\n\n");
  const pending = parts.pop() || "";
  for (const part of parts) {
    const lines = part.split("\n");
    const dataLines = lines
      .map((line) => line.trimEnd())
      .filter((line) => line.startsWith("data:"))
      .map((line) => line.slice(5).trimStart());
    if (!dataLines.length) continue;
    onEvent(dataLines.join("\n"));
  }
  return pending;
}

async function streamChat(message, suburb, onDelta) {
  const response = await fetch(`${state.apiBase}/chat/stream`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...authHeaders(),
    },
    body: JSON.stringify({ user_id: state.userId, message, suburb }),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(`Stream failed (${response.status}): ${text}`);
  }
  if (!response.body || !response.body.getReader) {
    throw new Error("Streaming not supported in this browser/runtime.");
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let done = false;
  let finalResponse = null;

  while (!done) {
    const result = await reader.read();
    if (result.done) break;
    buffer += decoder.decode(result.value, { stream: true });
    buffer = parseSseChunks(buffer, (payload) => {
      if (payload === "[DONE]") {
        done = true;
        return;
      }
      let event = null;
      try {
        event = JSON.parse(payload);
      } catch {
        return;
      }
      if (event.type === "delta") {
        onDelta(event.delta || "");
      } else if (event.type === "final") {
        finalResponse = event.response || null;
      }
    });
  }

  return finalResponse;
}

async function sendChat(event) {
  event.preventDefault();
  const message = el("chatMessage").value.trim();
  if (!message) return;
  el("chatMessage").value = "";
  appendChat("you", message);
  const assistantContentNode = appendChat("assistant", "...");
  const suburb = el("communitySuburb").value.trim() || undefined;
  let streamedAnswer = "";
  try {
    const streamed = await streamChat(message, suburb, (delta) => {
      streamedAnswer += delta;
      assistantContentNode.textContent = streamedAnswer || "...";
    });
    const answer = streamed?.answer || streamedAnswer || "(no answer)";
    assistantContentNode.textContent = answer;
    if (streamed?.cta_chips?.length) {
      appendChat("assistant", `Suggested actions: ${streamed.cta_chips.map((chip) => chip.label).join(", ")}`);
    }
    return;
  } catch (error) {
    log("Chat stream unavailable, using fallback", { error: error.message });
  }

  const response = await api("/chat", {
    method: "POST",
    body: JSON.stringify({ user_id: state.userId, message, suburb }),
  });
  assistantContentNode.textContent = response.answer || "(no answer)";
  if (response.cta_chips?.length) {
    appendChat("assistant", `Suggested actions: ${response.cta_chips.map((chip) => chip.label).join(", ")}`);
  }
}

async function loadNotifications() {
  const params = new URLSearchParams({ user_id: state.userId });
  if (el("unreadOnly").checked) params.set("unread_only", "true");
  const notifications = await api(`/notifications?${params}`);
  renderList("notificationsList", notifications, (n) => {
    const readBtn = n.read
      ? "<span>read</span>"
      : `<button type="button" data-read-notification="${n.id}">Mark read</button>`;
    return `<div class="item"><h4>${n.title}</h4><p>${n.body}</p><p>${n.category} · ${n.created_at}</p><p>${readBtn}</p></div>`;
  });
}

async function markNotificationRead(notificationId) {
  const updated = await api(`/notifications/${notificationId}/read?user_id=${encodeURIComponent(state.userId)}`, {
    method: "POST",
  });
  log("Notification updated", updated);
  await loadNotifications();
}

async function login(event) {
  event.preventDefault();
  state.userId = el("userId").value.trim();
  const password = el("password").value;
  localStorage.setItem("bw_user_id", state.userId);
  try {
    const auth = await api("/auth/login", {
      method: "POST",
      headers: {},
      body: JSON.stringify({ user_id: state.userId, password }),
    });
    state.token = auth.access_token;
    localStorage.setItem("bw_token", state.token);
    setSessionLabel();
    log("Login success", { user_id: auth.user_id, expires_at: auth.expires_at });
  } catch (error) {
    state.token = "";
    localStorage.removeItem("bw_token");
    setSessionLabel();
    log("Login failed", { error: error.message });
  }
}

function setupTabs() {
  document.querySelectorAll(".tab").forEach((tab) => {
    tab.addEventListener("click", () => {
      const target = tab.getAttribute("data-target");
      document.querySelectorAll(".tab").forEach((x) => x.classList.remove("active"));
      document.querySelectorAll(".panel").forEach((x) => x.classList.remove("active"));
      tab.classList.add("active");
      el(target).classList.add("active");
    });
  });
}

function setupApiBaseControls() {
  el("apiBase").value = state.apiBase;
  el("saveApiBase").addEventListener("click", () => {
    const value = el("apiBase").value.trim();
    if (!value) return;
    state.apiBase = value.replace(/\/$/, "");
    localStorage.setItem("bw_api_base", state.apiBase);
    log("API base saved", { apiBase: state.apiBase });
  });
}

function setupDelegatedActions() {
  document.body.addEventListener("click", async (event) => {
    const joinGroupId = event.target.getAttribute("data-join-group");
    const notificationId = event.target.getAttribute("data-read-notification");
    try {
      if (joinGroupId) await joinGroup(joinGroupId);
      if (notificationId) await markNotificationRead(notificationId);
    } catch (error) {
      log("Action failed", { error: error.message });
    }
  });
}

function bindEvents() {
  el("loginForm").addEventListener("submit", (event) => login(event).catch((e) => log("Login error", { error: e.message })));
  el("providerFilter").addEventListener("submit", (event) => {
    event.preventDefault();
    loadProviders().catch((e) => log("Providers failed", { error: e.message }));
  });
  el("bookingForm").addEventListener("submit", (event) => createBooking(event).catch((e) => log("Booking failed", { error: e.message })));
  el("communityFilter").addEventListener("submit", (event) => {
    event.preventDefault();
    loadCommunityFeed().catch((e) => log("Feed failed", { error: e.message }));
  });
  el("groupFilter").addEventListener("submit", (event) => {
    event.preventDefault();
    loadGroups().catch((e) => log("Groups failed", { error: e.message }));
  });
  el("postForm").addEventListener("submit", (event) => createPost(event).catch((e) => log("Post failed", { error: e.message })));
  el("chatForm").addEventListener("submit", (event) => sendChat(event).catch((e) => log("Chat failed", { error: e.message })));
  el("notificationForm").addEventListener("submit", (event) => {
    event.preventDefault();
    loadNotifications().catch((e) => log("Notifications failed", { error: e.message }));
  });
}

async function bootstrap() {
  el("userId").value = state.userId;
  setupTabs();
  setupApiBaseControls();
  setupDelegatedActions();
  bindEvents();
  setSessionLabel();
  try {
    await Promise.all([loadProviders(), loadCommunityFeed(), loadGroups(), loadNotifications()]);
  } catch (error) {
    log("Initial load warning", { error: error.message });
  }
}

bootstrap().catch((error) => {
  log("Bootstrap failed", { error: error.message });
});
