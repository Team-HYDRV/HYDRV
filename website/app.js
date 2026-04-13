document.addEventListener("DOMContentLoaded", () => {
    resetInitialScroll();

    const currentYear = new Date().getFullYear();
    document.title = "HYDRV";
    bindBrandScroll();
    bindSectionNavHighlight();
    bindTopbarState();

    const menuToggle = document.querySelector(".mobile-menu-toggle");
    let mobileMenuOpenedAtY = null;

    const closeMobileMenu = () => {
        if (!document.body.classList.contains("nav-open")) return;
        document.body.classList.remove("nav-open");
        menuToggle?.setAttribute("aria-expanded", "false");
        mobileMenuOpenedAtY = null;
    };

    if (menuToggle) {
        menuToggle.addEventListener("click", () => {
            const isOpen = document.body.classList.toggle("nav-open");
            menuToggle.setAttribute("aria-expanded", String(isOpen));
            mobileMenuOpenedAtY = isOpen ? window.scrollY : null;
        });
    }

    document.querySelectorAll(".nav a").forEach((link) => {
        link.addEventListener("click", () => {
            closeMobileMenu();
        });
    });

    window.addEventListener("scroll", () => {
        if (window.innerWidth > 760 || mobileMenuOpenedAtY === null) return;
        const scrolledSinceOpen = Math.abs(window.scrollY - mobileMenuOpenedAtY);
        if (scrolledSinceOpen < 24) return;
        closeMobileMenu();
    }, { passive: true });

    const footer = document.querySelector(".footer p");
    if (footer) {
        footer.insertAdjacentText("beforeend", ` (c) ${currentYear}`);
    }

    loadGitHubReleases();
});

function bindBrandScroll() {
    const brand = document.querySelector(".brand");
    const hero = document.querySelector(".hero");
    const topbar = document.querySelector(".topbar");

    if (!brand || !hero) return;

    brand.addEventListener("click", (event) => {
        event.preventDefault();

        const offset = (topbar?.offsetHeight || 0) + 10;
        const target = Math.max(0, hero.getBoundingClientRect().top + window.scrollY - offset);

        window.scrollTo({
            top: target,
            behavior: "smooth"
        });
    });
}

function bindSectionNavHighlight() {
    const navLinks = Array.from(document.querySelectorAll(".nav a[href^='#']"));
    const entries = navLinks
        .map((link) => {
            const hash = link.getAttribute("href");
            if (!hash) return null;
            const section = document.querySelector(hash);
            if (!section) return null;
            return { link, section };
        })
        .filter(Boolean);

    if (entries.length === 0) return;

    const setActiveLink = (targetLink) => {
        navLinks.forEach((link) => {
            link.classList.toggle("nav-current", link === targetLink);
        });
    };

    navLinks.forEach((link) => {
        link.addEventListener("click", () => {
            setActiveLink(link);
        });
    });

    const updateActiveSection = () => {
        const focusY = window.scrollY + window.innerHeight * 0.5;
        const viewportBottom = window.scrollY + window.innerHeight;
        const pageBottom = document.documentElement.scrollHeight;
        let active = entries[0].link;

        if (viewportBottom >= pageBottom - 24) {
            setActiveLink(entries[entries.length - 1].link);
            return;
        }

        for (let index = 0; index < entries.length; index += 1) {
            const current = entries[index];
            const next = entries[index + 1];
            const currentTop = current.section.offsetTop;
            const nextTop = next ? next.section.offsetTop : Number.POSITIVE_INFINITY;

            if (focusY >= currentTop && focusY < nextTop) {
                active = current.link;
                break;
            }
        }

        setActiveLink(active);
    };

    window.addEventListener("scroll", updateActiveSection, { passive: true });
    window.addEventListener("resize", updateActiveSection);
    updateActiveSection();
}

function bindTopbarState() {
    const topbar = document.querySelector(".topbar");
    if (!topbar) return;

    const updateTopbarState = () => {
        topbar.classList.toggle("is-scrolled", window.scrollY > 12);
    };

    window.addEventListener("scroll", updateTopbarState, { passive: true });
    updateTopbarState();
}

function resetInitialScroll() {
    if ("scrollRestoration" in history) {
        history.scrollRestoration = "manual";
    }

    if (window.location.hash) {
        history.replaceState(null, "", `${window.location.pathname}${window.location.search}`);
    }

    requestAnimationFrame(() => {
        window.scrollTo(0, 0);
    });
}

async function loadGitHubReleases() {
    const grid = document.getElementById("releaseGrid");
    if (!grid) return;

    const releaseUrl = "https://api.github.com/repos/Team-HYDRV/HYDRV/releases?per_page=3";
    const tagUrl = "https://api.github.com/repos/Team-HYDRV/HYDRV/tags?per_page=3";

    try {
        const response = await fetch(releaseUrl, {
            headers: {
                Accept: "application/vnd.github+json",
                "X-GitHub-Api-Version": "2022-11-28"
            }
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const payload = await response.json();
        const releases = Array.isArray(payload)
            ? payload.filter((release) => !release.draft)
            : [];
        const cards = releases.slice(0, 3);

        if (cards.length === 0) {
            const tagResponse = await fetch(tagUrl, {
                headers: {
                    Accept: "application/vnd.github+json",
                    "X-GitHub-Api-Version": "2022-11-28"
                }
            });

            if (!tagResponse.ok) {
                throw new Error(`HTTP ${tagResponse.status}`);
            }

            const tagPayload = await tagResponse.json();
            const tags = Array.isArray(tagPayload) ? tagPayload.slice(0, 3) : [];

            if (tags.length === 0) {
                grid.innerHTML = createEmptyReleaseCard(
                    "Nothing new to show yet.",
                    "GitHub hasn't returned the latest release notes yet. Check back later, or use the download button at the top of the page."
                );
                return;
            }

            grid.innerHTML = tags.map((tag, index) => createTagCard(tag, index)).join("");
            return;
        }

        grid.innerHTML = cards.map((release, index) => createReleaseCard(release, index)).join("");
    } catch (_error) {
        grid.innerHTML = createEmptyReleaseCard(
            "Nothing new to show yet.",
            "GitHub hasn't returned the latest release notes yet. Check back later, or use the download button at the top of the page."
        );
    }
}

function createReleaseCard(release, index) {
    const tag = escapeHtml(release.tag_name || release.name || "Release");
    const title = escapeHtml(release.name || release.tag_name || "Release");
    const published = formatReleaseDate(release.published_at);
    const body = escapeHtml(excerptReleaseBody(release.body));
    const badge = String(index + 1).padStart(2, "0");
    const url = escapeHtml(release.html_url || "https://github.com/Team-HYDRV/HYDRV/releases");

    return `
        <article class="story-card release-card">
            <div class="release-head">
                <span class="story-index">${badge}</span>
                <span class="release-tag">${tag}</span>
            </div>
            <h3>${title}</h3>
            <p class="release-date">${published}</p>
            <p class="release-body">${body}</p>
            <div class="release-actions">
                <a class="button button-secondary" href="${url}" target="_blank" rel="noopener noreferrer">View release</a>
            </div>
        </article>
    `;
}

function createEmptyReleaseCard(title, body) {
    return `
        <article class="story-card release-card release-card-empty">
            <span class="story-index">--</span>
            <h3>${escapeHtml(title)}</h3>
            <p class="release-body">${escapeHtml(body)}</p>
            <div class="release-actions">
                <a class="button button-secondary" href="https://github.com/Team-HYDRV/HYDRV/releases" target="_blank" rel="noopener noreferrer">Open GitHub releases</a>
            </div>
        </article>
    `;
}

function createTagCard(tag, index) {
    const tagName = escapeHtml(tag.name || "Release");
    const badge = String(index + 1).padStart(2, "0");
    const url = `https://github.com/Team-HYDRV/HYDRV/releases/tag/${encodeURIComponent(tag.name || "")}`;

    return `
        <article class="story-card release-card">
            <div class="release-head">
                <span class="story-index">${badge}</span>
                <span class="release-tag">${tagName}</span>
            </div>
            <h3>${tagName}</h3>
            <p class="release-date">Published as a tag</p>
            <p class="release-body">Release notes aren't published yet. You can still grab the latest APK from the button at the top of the site.</p>
            <div class="release-actions">
                <a class="button button-secondary" href="${escapeHtml(url)}" target="_blank" rel="noopener noreferrer">View tag</a>
            </div>
        </article>
    `;
}

function excerptReleaseBody(body) {
    const text = String(body || "")
        .replace(/```[\s\S]*?```/g, " ")
        .replace(/!\[[^\]]*\]\([^)]+\)/g, " ")
        .replace(/\[(.*?)\]\((.*?)\)/g, "$1")
        .replace(/[#>*_`-]+/g, " ")
        .replace(/\s+/g, " ")
        .trim();

    if (!text) return "No release notes were included with this release.";
    return text.length > 220 ? `${text.slice(0, 217)}...` : text;
}

function formatReleaseDate(value) {
    if (!value) return "Published recently";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "Published recently";
    return new Intl.DateTimeFormat(undefined, {
        year: "numeric",
        month: "short",
        day: "numeric"
    }).format(date);
}

function escapeHtml(value) {
    return String(value)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#39;");
}
