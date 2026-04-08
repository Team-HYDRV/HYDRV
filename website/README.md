# HYDRV Website

Public landing page for HYDRV.
HYDRV is a personalized store made just for you.

The site root also includes `app-ads.txt` for ad verification.

## Files

- `index.html` - main landing page
- `styles.css` - site styling
- `app.js` - release feed and small page updates
- `assets/hydrv-icon.png` - app icon reused for web branding

## Preview locally

Open `index.html` in a browser, or serve the folder with any static file server.

## Recommended setup for hydrv.app

The easiest path is Cloudflare Pages or Netlify.

### Cloudflare Pages

1. Create a GitHub repo for the `website` folder, or push this project and deploy only the `website` directory.
2. In Cloudflare Pages, create a new project and connect your repo.
3. Set the build command to blank because this is a static site.
4. Set the output directory to `website`.
5. Add your custom domain `hydrv.app`.
6. In your domain DNS, point `hydrv.app` to Cloudflare and enable the Pages domain binding.

### Netlify

1. Create a new site from Git.
2. Choose the repo containing this project.
3. Set the publish directory to `website`.
4. Leave the build command blank.
5. Add `hydrv.app` as a custom domain in Netlify.
6. Update the DNS records at your domain provider to the values Netlify gives you.

### If you do not want Git yet

You can also drag and drop the whole `website` folder into:

- Cloudflare Pages direct upload
- Netlify manual deploy
- Vercel static deploy

## Before going live

- Keep the main download button pointed at the latest GitHub APK
- Keep the GitHub, Discord, and Donate links current
- Check the privacy and terms pages before each public refresh
- Keep the screenshots in sync with the app UI as it evolves
- Update the Open Graph image if you want richer link previews

## Good deployment targets

- Cloudflare Pages
- Netlify
- GitHub Pages
- Vercel static hosting

## Easy next additions

- Release archive page
- Build details section
- Support hub
- Changelog cards
- More app screenshots
- Privacy policy and terms pages
