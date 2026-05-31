// 공통 사이드바 네비게이션 + 콘솔 로그 헬퍼

const NAV = [
  { group: "시작", items: [
    { href: "index.html", label: "개요", icon: "•" },
  ]},
  { group: "동기 · 실시간으로 실습", items: [
    { href: "rest.html", label: "1. HTTP / REST" },
    { href: "short-polling.html", label: "2. Short Polling" },
    { href: "long-polling.html", label: "3. Long Polling" },
    { href: "sse.html", label: "4. SSE" },
    { href: "websocket.html", label: "5. WebSocket" },
  ]},
  { group: "개념 · 코드 (브라우저 직접 실습 불가)", items: [
    { href: "grpc.html", label: "6. gRPC" },
    { href: "graphql.html", label: "7. GraphQL" },
    { href: "messaging.html", label: "8. 비동기 메시징" },
  ]},
];

function buildSidebar() {
  const current = location.pathname.split("/").pop() || "index.html";
  const nav = NAV.map(g => `
    <div class="nav-group-title">${g.group}</div>
    <div class="nav">
      ${g.items.map(i => `
        <a href="${i.href}" class="${i.href === current ? "active" : ""}">
          <span class="dot"></span>${i.label}
        </a>`).join("")}
    </div>`).join("");

  return `
    <aside class="sidebar">
      <div class="brand">
        <div class="logo">통</div>
        <h1>통신 프로토콜 실습</h1>
      </div>
      <div class="sub">Spring Backend Developer Edition</div>
      ${nav}
    </aside>`;
}

document.addEventListener("DOMContentLoaded", () => {
  const mount = document.getElementById("sidebar-mount");
  if (mount) mount.outerHTML = buildSidebar();
});

// ===== 콘솔 로그 헬퍼 =====
function logTo(elId, message, type = "sys") {
  const el = document.getElementById(elId);
  if (!el) return;
  const time = new Date().toLocaleTimeString("ko-KR", { hour12: false });
  const line = document.createElement("div");
  line.className = "line";
  line.innerHTML = `<span class="log-time">[${time}]</span> <span class="log-${type}">${escapeHtml(message)}</span>`;
  el.appendChild(line);
  el.scrollTop = el.scrollHeight;
}

function clearLog(elId) {
  const el = document.getElementById(elId);
  if (el) el.innerHTML = "";
}

function escapeHtml(s) {
  return String(s)
    .replaceAll("&", "&amp;").replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;");
}
