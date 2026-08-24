"use strict";

/* =========================================================
   MESSAGEHUB FRONTEND
   Matches the supplied index.html and server.js
   ========================================================= */

const API_BASE = "/api";
const TOKEN_KEY = "messagehub_token";
const USER_KEY = "messagehub_user";

/* =========================================================
   STATE
   ========================================================= */

let token = localStorage.getItem(TOKEN_KEY) || "";
let currentUser = null;
let selectedUser = null;
let users = [];

let usersTimer = null;
let messagesTimer = null;
let isLoadingMessages = false;

/* =========================================================
   DOM
   ========================================================= */

const authScreen = document.getElementById("authScreen");
const app = document.getElementById("app");

const loginTab = document.getElementById("loginTab");
const registerTab = document.getElementById("registerTab");
const authForm = document.getElementById("authForm");
const authUsername = document.getElementById("authUsername");
const authPassword = document.getElementById("authPassword");
const authButton = document.getElementById("authButton");
const authError = document.getElementById("authError");

const logoutBtn = document.getElementById("logoutBtn");

const myAvatar = document.getElementById("myAvatar");
const myName = document.getElementById("myName");

const userSearch = document.getElementById("userSearch");
const usersList = document.getElementById("usersList");

const chatHeader = document.getElementById("chatHeader");
const messages = document.getElementById("messages");

const messageFooter = document.getElementById("messageFooter");
const messageInput = document.getElementById("messageInput");
const sendButton = document.getElementById("sendButton");

const toast = document.getElementById("toast");

/* =========================================================
   AUTH MODE
   ========================================================= */

let authMode = "login";

function setAuthMode(mode) {
  authMode = mode;

  if (mode === "login") {
    loginTab.classList.add("active");
    registerTab.classList.remove("active");

    authButton.textContent = "Login";
    authPassword.autocomplete = "current-password";
  } else {
    registerTab.classList.add("active");
    loginTab.classList.remove("active");

    authButton.textContent = "Register";
    authPassword.autocomplete = "new-password";
  }

  clearAuthError();
}

loginTab.addEventListener("click", () => {
  setAuthMode("login");
});

registerTab.addEventListener("click", () => {
  setAuthMode("register");
});

/* =========================================================
   HELPERS
   ========================================================= */

function escapeHTML(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

function getInitial(name) {
  const value = String(name || "").trim();

  if (!value) {
    return "U";
  }

  return value.charAt(0).toUpperCase();
}

function formatTime(dateValue) {
  if (!dateValue) {
    return "";
  }

  const date = new Date(dateValue);

  if (Number.isNaN(date.getTime())) {
    return "";
  }

  return date.toLocaleTimeString([], {
    hour: "numeric",
    minute: "2-digit"
  });
}

function formatDateTime(dateValue) {
  if (!dateValue) {
    return "";
  }

  const date = new Date(dateValue);

  if (Number.isNaN(date.getTime())) {
    return "";
  }

  return date.toLocaleString();
}

function showToast(message, type = "normal") {
  if (!toast) {
    return;
  }

  toast.textContent = message;
  toast.className = "toast";

  if (type === "error") {
    toast.classList.add("error");
  }

  if (type === "success") {
    toast.classList.add("success");
  }

  toast.classList.add("show");

  clearTimeout(showToast.timer);

  showToast.timer = setTimeout(() => {
    toast.classList.remove("show");
  }, 2500);
}

function showAuthError(message) {
  authError.textContent = message || "";
  authError.classList.add("show");
}

function clearAuthError() {
  authError.textContent = "";
  authError.classList.remove("show");
}

function saveSession(newToken, user) {
  token = newToken || "";
  currentUser = user || null;

  if (token) {
    localStorage.setItem(TOKEN_KEY, token);
  } else {
    localStorage.removeItem(TOKEN_KEY);
  }

  if (currentUser) {
    localStorage.setItem(
      USER_KEY,
      JSON.stringify(currentUser)
    );
  } else {
    localStorage.removeItem(USER_KEY);
  }
}

function clearSession() {
  token = "";
  currentUser = null;

  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);

  stopPolling();
}

function getSavedUser() {
  try {
    const saved = localStorage.getItem(USER_KEY);

    if (!saved) {
      return null;
    }

    return JSON.parse(saved);
  } catch {
    return null;
  }
}

/* =========================================================
   API
   ========================================================= */

async function apiFetch(endpoint, options = {}) {
  const config = {
    ...options,
    headers: {
      ...(options.headers || {})
    }
  };

  if (options.body && !config.headers["Content-Type"]) {
    config.headers["Content-Type"] = "application/json";
  }

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  let response;

  try {
    response = await fetch(`${API_BASE}${endpoint}`, config);
  } catch (error) {
    throw new Error(
      "Cannot connect to the server. Make sure server.js is running."
    );
  }

  let data = null;

  try {
    data = await response.json();
  } catch {
    data = {};
  }

  if (response.status === 401) {
    clearSession();

    showAuthScreen();

    throw new Error(
      data.error || "Your session has expired. Please login again."
    );
  }

  if (!response.ok) {
    throw new Error(
      data.error || `Request failed (${response.status})`
    );
  }

  return data;
}

/* =========================================================
   AUTH SCREEN / APP SCREEN
   ========================================================= */

function showAuthScreen() {
  authScreen.classList.remove("hidden");
  app.classList.add("hidden");

  messageFooter.classList.add("hidden");

  selectedUser = null;

  if (chatHeader) {
    chatHeader.classList.add("empty");
  }

  messages.innerHTML = `
    <div class="chat-empty">
      <div class="chat-empty-icon">💬</div>
      <h2>Your messages</h2>
      <p>
        Select a contact from the left
        to start a private conversation.
      </p>
    </div>
  `;

  stopPolling();
}

function showAppScreen() {
  authScreen.classList.add("hidden");
  app.classList.remove("hidden");

  updateMyProfile();
}

/* =========================================================
   PROFILE
   ========================================================= */

function updateMyProfile() {
  if (!currentUser) {
    return;
  }

  myName.textContent = currentUser.username || "User";
  myAvatar.textContent = getInitial(currentUser.username);
}

/* =========================================================
   LOGIN / REGISTER
   ========================================================= */

authForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  clearAuthError();

  const username = authUsername.value.trim();
  const password = authPassword.value;

  if (!username || !password) {
    showAuthError("Username and password are required.");
    return;
  }

  if (authMode === "register" && password.length < 6) {
    showAuthError(
      "Password must contain at least 6 characters."
    );
    return;
  }

  authButton.disabled = true;

  const originalText = authButton.textContent;

  authButton.textContent =
    authMode === "login"
      ? "Logging in..."
      : "Creating account...";

  try {
    const endpoint =
      authMode === "login"
        ? "/login"
        : "/register";

    const data = await apiFetch(endpoint, {
      method: "POST",
      body: JSON.stringify({
        username,
        password
      })
    });

    if (!data.success || !data.token || !data.user) {
      throw new Error(
        data.error || "Authentication failed."
      );
    }

    saveSession(data.token, data.user);

    authForm.reset();

    showAppScreen();

    await loadUsers();

    startPolling();

    showToast(
      authMode === "login"
        ? "Login successful."
        : "Account created successfully.",
      "success"
    );
  } catch (error) {
    showAuthError(error.message);
  } finally {
    authButton.disabled = false;
    authButton.textContent = originalText;
  }
});

/* =========================================================
   LOGOUT
   ========================================================= */

logoutBtn.addEventListener("click", () => {
  clearSession();

  authUsername.value = "";
  authPassword.value = "";

  setAuthMode("login");
  showAuthScreen();

  showToast("Logged out successfully.");
});

/* =========================================================
   DELETE USER / ACCOUNT
   ========================================================= */

function createDeleteUserButton() {
  if (
    !logoutBtn ||
    document.getElementById("deleteUserBtn")
  ) {
    return;
  }

  const button = document.createElement("button");

  button.id = "deleteUserBtn";
  button.type = "button";
  button.textContent = "Delete Account";
  button.className = "delete-user-btn";

  logoutBtn.parentNode.insertBefore(
    button,
    logoutBtn.nextSibling
  );

  button.addEventListener("click", async () => {
    const confirmed = window.confirm(
      "Are you sure you want to permanently delete your account?"
    );

    if (!confirmed) {
      return;
    }

    const secondConfirm = window.confirm(
      "This will permanently delete your account and messages. Continue?"
    );

    if (!secondConfirm) {
      return;
    }

    button.disabled = true;
    button.textContent = "Deleting...";

    try {
      const data = await apiFetch(
        "/me",
        {
          method: "DELETE"
        }
      );

      if (!data.success) {
        throw new Error(
          data.error || "Failed to delete account."
        );
      }

      clearSession();

      authUsername.value = "";
      authPassword.value = "";

      setAuthMode("login");
      showAuthScreen();

      showToast(
        "Account deleted successfully.",
        "success"
      );

    } catch (error) {
      console.error(
        "Delete user error:",
        error
      );

      showToast(
        error.message || "Failed to delete account.",
        "error"
      );

      button.disabled = false;
      button.textContent = "Delete Account";
    }
  });
}

/* =========================================================
   CURRENT USER
   ========================================================= */

async function loadCurrentUser() {
  const data = await apiFetch("/me");

  if (!data.success || !data.user) {
    throw new Error(
      data.error || "Failed to load current user."
    );
  }

  currentUser = data.user;

  localStorage.setItem(
    USER_KEY,
    JSON.stringify(currentUser)
  );

  updateMyProfile();
}

/* =========================================================
   USERS
   ========================================================= */

async function loadUsers() {
  try {
    const data = await apiFetch("/users");

    if (!data.success) {
      throw new Error(
        data.error || "Failed to load users."
      );
    }

    users = Array.isArray(data.users)
      ? data.users
      : [];

    renderUsers();
  } catch (error) {
    console.error("Load users error:", error);

    if (users.length === 0) {
      usersList.innerHTML = `
        <div class="loading">
          ${escapeHTML(error.message)}
        </div>
      `;
    }
  }
}

function renderUsers() {
  const searchValue =
    userSearch.value.trim().toLowerCase();

  const filteredUsers = users.filter((user) => {
    const username = String(
      user.username || ""
    ).toLowerCase();

    return username.includes(searchValue);
  });

  if (filteredUsers.length === 0) {
    usersList.innerHTML = `
      <div class="loading">
        ${
          searchValue
            ? "No users found."
            : "No contacts available."
        }
      </div>
    `;

    return;
  }

  usersList.innerHTML = filteredUsers
    .map((user) => {
      const isSelected =
        selectedUser &&
        Number(selectedUser.id) === Number(user.id);

      const isOnline =
        Number(user.online) === 1 ||
        user.online === true;

      return `
        <button
          type="button"
          class="user-item ${
            isSelected ? "active" : ""
          }"
          data-user-id="${Number(user.id)}"
        >

          <div class="avatar">
            ${escapeHTML(getInitial(user.username))}
          </div>

          <div class="user-details">

            <strong>
              ${escapeHTML(user.username)}
            </strong>

            <span class="user-status ${
              isOnline ? "online" : "offline"
            }">

              <i class="status-dot"></i>

              <span class="status-text">
                ${isOnline ? "Online" : "Offline"}
              </span>

            </span>

          </div>

        </button>
      `;
    })
    .join("");

  usersList
    .querySelectorAll(".user-item")
    .forEach((element) => {
      element.addEventListener("click", () => {
        const userId = Number(
          element.dataset.userId
        );

        const user = users.find(
          (item) =>
            Number(item.id) === userId
        );

        if (user) {
          selectUser(user);
        }
      });
    });
}

userSearch.addEventListener("input", () => {
  renderUsers();
});

/* =========================================================
   SELECT USER
   ========================================================= */

async function selectUser(user) {
  if (!user) {
    return;
  }

  selectedUser = user;

  renderUsers();
  renderChatHeader();

  messageFooter.classList.remove("hidden");

  messageInput.focus();

  await loadMessages();
}

/* =========================================================
   CHAT HEADER
   ========================================================= */

function renderChatHeader() {
  if (!selectedUser) {
    chatHeader.classList.add("empty");

    chatHeader.innerHTML = `
      <div class="empty-header">

        <div class="empty-logo">
          💬
        </div>

        <div>
          <strong>MessageHub</strong>

          <span>
            Select a person to start chatting
          </span>
        </div>

      </div>
    `;

    return;
  }

  chatHeader.classList.remove("empty");

  const isOnline =
    Number(selectedUser.online) === 1 ||
    selectedUser.online === true;

  chatHeader.innerHTML = `
    <div class="chat-user">

      <div class="avatar chat-avatar">
        ${escapeHTML(
          getInitial(selectedUser.username)
        )}
      </div>

      <div class="chat-user-info">

        <strong>
          ${escapeHTML(selectedUser.username)}
        </strong>

        <span class="chat-status ${
          isOnline ? "online" : "offline"
        }">

          <i class="status-dot"></i>

          <span>
            ${isOnline ? "Online" : "Offline"}
          </span>

        </span>

      </div>

    </div>
  `;
}

/* =========================================================
   MESSAGES
   ========================================================= */

async function loadMessages() {
  if (!selectedUser || isLoadingMessages) {
    return;
  }

  isLoadingMessages = true;

  try {
    const data = await apiFetch(
      `/messages/${Number(selectedUser.id)}`
    );

    if (!data.success) {
      throw new Error(
        data.error || "Failed to load messages."
      );
    }

    const list = Array.isArray(data.messages)
      ? data.messages
      : [];

    renderMessages(list);
  } catch (error) {
    console.error(
      "Load messages error:",
      error
    );
  } finally {
    isLoadingMessages = false;
  }
}

/* =========================================================
   RENDER MESSAGES
   ========================================================= */

function renderMessages(list) {
  if (!selectedUser) {
    return;
  }

  if (list.length === 0) {
    messages.innerHTML = `
      <div class="chat-empty">
        <div class="chat-empty-icon">💬</div>

        <h2>
          Start a conversation
        </h2>

        <p>
          Send your first message to
          ${escapeHTML(selectedUser.username)}.
        </p>
      </div>
    `;

    return;
  }

  const wasNearBottom =
    messages.scrollHeight -
      messages.scrollTop -
      messages.clientHeight <
    120;

  messages.innerHTML = list
    .map((message) => {
      const senderId = Number(
        message.sender_id
      );

      const isMine =
        currentUser &&
        senderId === Number(currentUser.id);

      return createMessageHTML(
        message,
        isMine
      );
    })
    .join("");

  attachDeleteHandlers();

  if (wasNearBottom) {
    scrollMessagesToBottom();
  }
}

function createMessageHTML(message, isMine) {
  const messageId = Number(message.id);

  const username =
    message.username || "User";

  const text = escapeHTML(
    message.message || ""
  ).replace(/\n/g, "<br>");

  const time = formatTime(
    message.created_at
  );

  return `
    <div
      class="message-row ${
        isMine ? "mine" : "theirs"
      }"
      data-message-id="${messageId}"
    >

      <div class="message-bubble">

        <div class="message-text">
          ${text}
        </div>

        <div class="message-meta">

          <span class="message-time">
            ${escapeHTML(time)}
          </span>

          ${
            isMine
              ? `
                <button
                  type="button"
                  class="delete-message"
                  data-message-id="${messageId}"
                  title="Delete message"
                  aria-label="Delete message"
                >
                  🗑
                </button>
              `
              : ""
          }

        </div>

      </div>

    </div>
  `;
}

/* =========================================================
   DELETE MESSAGE
   ========================================================= */

function attachDeleteHandlers() {
  messages
    .querySelectorAll(".delete-message")
    .forEach((button) => {
      button.addEventListener(
        "click",
        async (event) => {
          event.preventDefault();
          event.stopPropagation();

          const messageId = Number(
            button.dataset.messageId
          );

          if (!Number.isInteger(messageId)) {
            return;
          }

          const confirmed = window.confirm(
            "Delete this message?"
          );

          if (!confirmed) {
            return;
          }

          await deleteMessage(
            messageId,
            button
          );
        }
      );
    });
}

async function deleteMessage(
  messageId,
  button
) {
  try {
    button.disabled = true;

    const data = await apiFetch(
      `/messages/${messageId}`,
      {
        method: "DELETE"
      }
    );

    if (!data.success) {
      throw new Error(
        data.error || "Failed to delete message."
      );
    }

    const row = messages.querySelector(
      `.message-row[data-message-id="${messageId}"]`
    );

    if (row) {
      row.remove();
    }

    if (
      !messages.querySelector(".message-row")
    ) {
      messages.innerHTML = `
        <div class="chat-empty">
          <div class="chat-empty-icon">💬</div>

          <h2>
            Start a conversation
          </h2>

          <p>
            Send your first message to
            ${escapeHTML(
              selectedUser
                ? selectedUser.username
                : "this user"
            )}.
          </p>
        </div>
      `;
    }

    showToast(
      "Message deleted successfully.",
      "success"
    );
  } catch (error) {
    console.error(
      "Delete message error:",
      error
    );

    button.disabled = false;

    showToast(
      error.message || "Failed to delete message.",
      "error"
    );
  }
}

/* =========================================================
   SEND MESSAGE
   ========================================================= */

async function sendMessage() {
  if (!selectedUser) {
    showToast(
      "Select a contact first.",
      "error"
    );

    return;
  }

  const message =
    messageInput.value.trim();

  if (!message) {
    return;
  }

  if (message.length > 5000) {
    showToast(
      "Message cannot exceed 5000 characters.",
      "error"
    );

    return;
  }

  sendButton.disabled = true;
  messageInput.disabled = true;

  try {
    const data = await apiFetch(
      "/messages",
      {
        method: "POST",
        body: JSON.stringify({
          receiverId: Number(
            selectedUser.id
          ),
          message
        })
      }
    );

    if (!data.success) {
      throw new Error(
        data.error || "Failed to send message."
      );
    }

    messageInput.value = "";
    autoResizeTextarea();

    await loadMessages();
  } catch (error) {
    console.error(
      "Send message error:",
      error
    );

    showToast(
      error.message || "Failed to send message.",
      "error"
    );
  } finally {
    sendButton.disabled = false;
    messageInput.disabled = false;

    messageInput.focus();
  }
}

sendButton.addEventListener(
  "click",
  sendMessage
);

/* =========================================================
   ENTER TO SEND
   SHIFT + ENTER = NEW LINE
   ========================================================= */

messageInput.addEventListener(
  "keydown",
  (event) => {
    if (
      event.key === "Enter" &&
      !event.shiftKey
    ) {
      event.preventDefault();

      sendMessage();
    }
  }
);

/* =========================================================
   TEXTAREA AUTO RESIZE
   ========================================================= */

function autoResizeTextarea() {
  messageInput.style.height = "auto";

  const height = Math.min(
    messageInput.scrollHeight,
    150
  );

  messageInput.style.height =
    `${height}px`;
}

messageInput.addEventListener(
  "input",
  autoResizeTextarea
);

/* =========================================================
   SCROLL
   ========================================================= */

function scrollMessagesToBottom() {
  requestAnimationFrame(() => {
    messages.scrollTop =
      messages.scrollHeight;
  });
}

/* =========================================================
   POLLING
   ========================================================= */

function startPolling() {
  stopPolling();

  usersTimer = setInterval(() => {
    loadUsers();
  }, 10000);

  messagesTimer = setInterval(() => {
    if (selectedUser) {
      loadMessages();
    }
  }, 3000);
}

function stopPolling() {
  if (usersTimer) {
    clearInterval(usersTimer);
    usersTimer = null;
  }

  if (messagesTimer) {
    clearInterval(messagesTimer);
    messagesTimer = null;
  }
}

/* =========================================================
   EXTRA FRONTEND STYLES
   Fixes:
   - Online dot
   - Online text alignment
   - Delete button
   ========================================================= */

function injectFrontendFixes() {
  if (document.getElementById(
    "messagehub-frontend-fixes"
  )) {
    return;
  }

  const style = document.createElement("style");

  style.id =
    "messagehub-frontend-fixes";

  style.textContent = `
    .user-details {
      min-width: 0;
    }

    .user-details > strong {
      display: block;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .user-status {
      display: flex !important;
      align-items: center !important;
      gap: 6px !important;
      margin-top: 3px !important;
      line-height: 1 !important;
      white-space: nowrap !important;
    }

    .status-dot {
      display: inline-block !important;
      width: 7px !important;
      height: 7px !important;
      min-width: 7px !important;
      border-radius: 50% !important;
      background: #64748b !important;
      vertical-align: middle !important;
    }

    .user-status.online .status-dot,
    .chat-status.online .status-dot {
      background: #22c55e !important;
      box-shadow:
        0 0 0 2px rgba(34, 197, 94, 0.12) !important;
    }

    .user-status.offline .status-dot,
    .chat-status.offline .status-dot {
      background: #64748b !important;
    }

    .user-status.online .status-text,
    .chat-status.online {
      color: #22c55e !important;
    }

    .user-status.offline .status-text,
    .chat-status.offline {
      color: #94a3b8 !important;
    }

    .chat-user {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .chat-user-info {
      display: flex;
      flex-direction: column;
      justify-content: center;
      gap: 4px;
    }

    .chat-user-info > strong {
      line-height: 1.1;
    }

    .chat-status {
      display: flex !important;
      align-items: center !important;
      gap: 6px !important;
      font-size: 12px !important;
      line-height: 1 !important;
    }

    .message-row {
      display: flex;
      width: 100%;
      margin-bottom: 12px;
    }

    .message-row.mine {
      justify-content: flex-end;
    }

    .message-row.theirs {
      justify-content: flex-start;
    }

    .message-bubble {
      position: relative;
      max-width: min(70%, 600px);
      padding: 11px 14px 8px;
      border-radius: 12px;
      word-break: break-word;
    }

    .message-row.mine .message-bubble {
      padding-right: 14px;
    }

    .message-row.theirs .message-bubble {
      padding-right: 14px;
    }

    .message-text {
      line-height: 1.45;
      white-space: normal;
    }

    .message-meta {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 7px;
      margin-top: 4px;
      min-height: 18px;
    }

    .message-time {
      font-size: 11px;
      opacity: 0.7;
      white-space: nowrap;
    }

    .delete-message {
      display: inline-flex !important;
      align-items: center !important;
      justify-content: center !important;

      width: 23px !important;
      height: 23px !important;

      padding: 0 !important;
      margin: 0 !important;

      border: 0 !important;
      border-radius: 6px !important;

      background: transparent !important;
      color: inherit !important;

      font-size: 13px !important;
      line-height: 1 !important;

      cursor: pointer !important;

      opacity: 0 !important;
      visibility: hidden !important;

      transition:
        opacity 0.15s ease,
        background 0.15s ease !important;
    }

    .message-bubble:hover
    .delete-message {
      opacity: 0.75 !important;
      visibility: visible !important;
    }

    .delete-message:hover {
      opacity: 1 !important;
      background: rgba(255, 255, 255, 0.12) !important;
    }

    .delete-message:disabled {
      cursor: wait !important;
      opacity: 0.35 !important;
    }

    .users-list .user-item {
      width: 100%;
      text-align: left;
      font: inherit;
      color: inherit;
      border: 0;
      cursor: pointer;
    }

    /* ================================
       DELETE ACCOUNT BUTTON
       ================================ */

    .delete-user-btn {
      cursor: pointer;
    }

    .delete-user-btn:disabled {
      cursor: wait;
      opacity: 0.6;
    }

    @media (max-width: 700px) {
      .message-bubble {
        max-width: 82%;
      }
    }
  `;

  document.head.appendChild(style);
}

/* =========================================================
   STARTUP
   ========================================================= */

async function initializeApp() {
  injectFrontendFixes();

  createDeleteUserButton();

  setAuthMode("login");

  if (!token) {
    showAuthScreen();
    return;
  }

  try {
    const savedUser = getSavedUser();

    if (savedUser) {
      currentUser = savedUser;
    }

    showAppScreen();

    await loadCurrentUser();
    await loadUsers();

    startPolling();
  } catch (error) {
    console.error(
      "Initialization error:",
      error
    );

    clearSession();
    showAuthScreen();

    if (
      error.message &&
      !error.message.includes("expired")
    ) {
      showAuthError(error.message);
    }
  }
}

/* =========================================================
   BEFORE PAGE UNLOAD
   ========================================================= */

window.addEventListener(
  "beforeunload",
  () => {
    stopPolling();
  }
);

/* =========================================================
   RUN
   ========================================================= */

initializeApp();