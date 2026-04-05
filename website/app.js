document.addEventListener("DOMContentLoaded", () => {
    const currentYear = new Date().getFullYear();
    document.title = "HYDRV";

    const footer = document.querySelector(".footer p");
    if (footer) {
        footer.insertAdjacentText("beforeend", ` (c) ${currentYear}`);
    }

    loadGitHubReleases();
});

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
                    "GitHub releases are unavailable for now.",
                    "The latest release notes could not be fetched. Try again in a moment or use the direct APK download button at the top of the site."
                );
                return;
            }

            grid.innerHTML = tags.map((tag, index) => createTagCard(tag, index)).join("");
            return;
        }

        grid.innerHTML = cards.map((release, index) => createReleaseCard(release, index)).join("");
    } catch (_error) {
        grid.innerHTML = createEmptyReleaseCard(
            "GitHub releases are unavailable for now.",
            "The latest release notes could not be fetched. Try again in a moment or use the direct APK download button at the top of the site."
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
            <p class="release-body">Release notes are not published yet. Download the latest APK directly from the button at the top of the site.</p>
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
