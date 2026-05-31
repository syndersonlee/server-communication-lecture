// 공통: 사이드바 생성, 스크롤 진행바, 스크롤 스파이, 등장 애니메이션, 모바일 토글

const PAGES = [
  { href: "index.html",       num: "0",  label: "오리엔테이션" },
  { href: "fundamentals.html",num: "1",  label: "네트워크 기초" },
  { href: "http.html",        num: "2",  label: "HTTP & HTTPS" },
  { href: "realtime.html",    num: "3",  label: "실시간 통신" },
  { href: "rpc-graphql.html", num: "4",  label: "gRPC & GraphQL" },
  { href: "async.html",       num: "5",  label: "비동기 메시징" },
  { href: "webflux.html",     num: "6",  label: "WebFlux & 코루틴" },
  { href: "guide.html",       num: "7",  label: "기술 선택 가이드" },
];

function currentPage() {
  return location.pathname.split("/").pop() || "index.html";
}

function buildSidebar() {
  const cur = currentPage();
  const items = PAGES.map(p => {
    const active = p.href === cur;
    let html = `<a href="${p.href}" class="${active ? "active" : ""}" data-page>
      <span class="num">${p.num}</span>${p.label}</a>`;
    if (active) {
      const secs = [...document.querySelectorAll("section.block[id]")];
      if (secs.length > 1) {
        html += `<div class="sub">` + secs.map(s => {
          const t = s.querySelector("h2.section-title");
          const label = t ? t.textContent : s.id;
          return `<a href="#${s.id}" data-spy="${s.id}">${label}</a>`;
        }).join("") + `</div>`;
      }
    }
    return html;
  }).join("");

  return `
  <aside class="sidebar" id="sidebar">
    <a class="brand" href="index.html" style="text-decoration:none">
      <div class="logo">N</div>
      <div>
        <div class="bt">서버 통신 프로토콜</div>
        <div class="bs">Backend · Spring/Java</div>
      </div>
    </a>
    <div class="nav-section">
      <div class="label">커리큘럼</div>
      <nav class="nav">${items}</nav>
    </div>
  </aside>
  <div class="scrim" id="scrim"></div>`;
}

function buildTopbar() {
  const cur = PAGES.find(p => p.href === currentPage());
  return `<div class="topbar">
    <button class="menu-btn" id="menuBtn" aria-label="메뉴">☰</button>
    <div class="tb-title">${cur ? cur.num + ". " + cur.label : "강의"}</div>
  </div>`;
}

document.addEventListener("DOMContentLoaded", () => {
  // progress bar + topbar + sidebar mount
  document.body.insertAdjacentHTML("afterbegin", `<div id="progress"></div>` + buildTopbar());
  const mount = document.getElementById("sidebar-mount");
  if (mount) mount.outerHTML = buildSidebar();

  // mobile toggle
  const sidebar = document.getElementById("sidebar");
  const scrim = document.getElementById("scrim");
  const menuBtn = document.getElementById("menuBtn");
  const toggle = (on) => { sidebar.classList.toggle("open", on); scrim.classList.toggle("show", on); };
  if (menuBtn) menuBtn.addEventListener("click", () => toggle(!sidebar.classList.contains("open")));
  if (scrim) scrim.addEventListener("click", () => toggle(false));
  sidebar?.querySelectorAll("a").forEach(a => a.addEventListener("click", () => toggle(false)));

  // progress bar
  const progress = document.getElementById("progress");
  const onScroll = () => {
    const h = document.documentElement;
    const scrolled = h.scrollTop / (h.scrollHeight - h.clientHeight || 1);
    progress.style.width = Math.min(100, scrolled * 100) + "%";
  };
  document.addEventListener("scroll", onScroll, { passive: true });
  onScroll();

  // reveal on scroll
  const io = new IntersectionObserver((entries) => {
    entries.forEach(e => { if (e.isIntersecting) { e.target.classList.add("in"); io.unobserve(e.target); } });
  }, { threshold: 0.12 });
  document.querySelectorAll(".reveal").forEach(el => io.observe(el));

  // scroll spy (sub links)
  const spyLinks = [...document.querySelectorAll('.nav .sub a[data-spy]')];
  if (spyLinks.length) {
    const sections = spyLinks.map(a => document.getElementById(a.dataset.spy)).filter(Boolean);
    const spy = new IntersectionObserver((entries) => {
      entries.forEach(e => {
        if (e.isIntersecting) {
          spyLinks.forEach(a => a.classList.toggle("active", a.dataset.spy === e.target.id));
        }
      });
    }, { rootMargin: "-30% 0px -60% 0px" });
    sections.forEach(s => spy.observe(s));
  }
});
