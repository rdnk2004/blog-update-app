# My Blog Desk

**My Blog Desk** is a native Android companion app (Kotlin + Jetpack Compose) designed so a non-technical writer can easily draft, illustrate, publish, and edit posts directly on her blog — without ever touching GitHub, code, or a terminal.

Every publish, edit, draft save, or deletion creates a normal Git commit via the GitHub REST Contents API, which automatically triggers a Vercel deployment.

---

## Connected Repository & Live Website

This app is directly connected to the following website repository and deployment pipeline:

| Resource | Details |
| :--- | :--- |
| **Connected GitHub Repo** | [`https://github.com/rdnk2004/blog-evd`](https://github.com/rdnk2004/blog-evd) |
| **Target Branch** | `main` |
| **Live Website** | [`https://ramdeepablog.vercel.app`](https://ramdeepablog.vercel.app) *(DeepaRam — Painting Emotions in Stillness)* |
| **Deployment Pipeline** | Android App → GitHub REST Contents API → `rdnk2004/blog-evd` (`main`) → Vercel Auto-Deploy (~1 min) |

> **Hard Safety Rule:** My Blog Desk **only** reads, creates, updates, and deletes content `.json` files inside `content/` and images inside `public/images/` or `public/gallery/`. Path guardrails in `GitHubRepository` strictly refuse any write outside those folders — the app never touches `.tsx` components, layouts, styles, or site configuration.

---

## Website Sections & Folder Structure

Every section on `ramdeepablog.vercel.app` has both a **Published** folder (rendered by the live website) and a **dedicated Drafts** folder under `content/drafts/` so unfinished work can never accidentally leak onto the live site:

| Section | Published Path (Live Site) | Dedicated Drafts Path | Image Upload Path |
| :--- | :--- | :--- | :--- |
| **Soul Notes** | `content/soul-notes/<slug>.json` | `content/drafts/soul-notes/<slug>.json` | `public/images/` |
| **Musings** | `content/musings/<slug>.json` | `content/drafts/musings/<slug>.json` | `public/images/` |
| **Heartlines (Poems)** | `content/heartlines/<slug>.json` | `content/drafts/heartlines/<slug>.json` | `public/images/` |
| **Series** | `content/series/<seriesSlug>/series.json` | `content/drafts/series/<seriesSlug>/series.json` | — |
| **Series → Chapters** | `content/series/<seriesSlug>/chapters/<chapterSlug>.json` | `content/drafts/series/<seriesSlug>/chapters/<chapterSlug>.json` | `public/images/` |
| **Art Gallery** | `content/gallery.json` *(Acrylic & Pencil)* | `content/drafts/gallery.json` | `public/gallery/` |

---

## Key Features

- **Simple, Calm Editorial UX**: Warm light-only palette harmonized with DeepaRam's website, large readable typography (`Lora` & `Plus Jakarta Sans`), generous `>= 48dp` touch targets, and plain-language messages.
- **4-Digit PIN Lock**: First launch prompts the user to create and confirm a 4-digit PIN, stored locally as a salted SHA-256 hash.
- **Per-Section Published & Drafts Folders**: Every section (and every Series' Chapters list) includes a **Published (N) | Drafts (M)** folder switcher.
- **Safe Editing of Already-Published Posts**:
  - Live posts display a **"Live on Website"** badge.
  - Primary action (**"Update Live"** / **"Update Chapter"** / **"Update Series"**) fetches a fresh file `sha` right before committing to prevent stale-SHA conflicts, and preserves the original slug/URL, category, chapter order, gratitude block, background images, and paired gallery images.
  - Secondary action (**"Move to Drafts"**) requires explicit confirmation before unpublishing and moving the file cleanly into `content/drafts/`.
- **Photo-Ready & Bandwidth-Friendly**:
  - Uses the zero-permission Android Photo Picker (`PickVisualMedia`) and `FileProvider` camera capture.
  - Automatically fixes EXIF orientation, resizes images to max `1600px` wide, and compresses to JPEG (quality `80%`).
  - Uploads images **before** committing the post JSON so the site never points to a missing image.
- **Auto-Save Protection**: Unfinished writing is auto-saved locally on the device (`DataStore`) so nothing is lost if the phone loses internet or the app is closed.
- **Unicode & Indian Languages**: Fully supports UTF-8 text (Tamil, Hindi, Malayalam, etc.), preserves exact stanza line breaks in poetry, and falls back to a clean timestamp slug (`post-<epoch>`) when titles are non-Latin.
- **Zero Backend & Zero AI**: No external servers, no user accounts, no database, and no LLM calls. Every action is a standard, revertible Git commit in `rdnk2004/blog-evd`.

---

## Maintainer Setup (Hidden Settings & GitHub Token)

Technical settings are hidden from day-to-day writing screens so the writer never has to see tokens or repository names.

### 1. Create a Minimal GitHub Fine-Grained Token
1. Open **GitHub → Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token**.
2. Under **Repository access**, select **Only select repositories** → choose **`rdnk2004/blog-evd`**.
3. Under **Permissions → Repositories**, click **`+ Add permissions`**:
   - **`Contents`** → **`Read and write`** *(required to read, save drafts, publish, edit, and delete content & images)*
   - **`Metadata`** → **`Read-only`** *(automatically added by GitHub)*
4. Leave **Account** permissions at **`0`**.
5. Click **Generate token** and copy the `github_pat_...` string.

### 2. Configure the App on the Phone
1. Open **My Blog Desk** and **long-press the `"My Blog Desk"` title** (works on both the Lock screen and the Home screen) to open the hidden **Setup** screen.
2. Paste the token into **GitHub Access Token**. *(Once saved, the token is masked to its last 4 characters and is never logged.)*
3. Verify the pre-filled defaults:
   - **GitHub Owner:** `rdnk2004`
   - **Repository Name:** `blog-evd`
   - **Branch:** `main`
   - **Content Root Folder:** `content`
   - **Story & Note Images Folder:** `public/images`
   - **Art Gallery Images Folder:** `public/gallery`
   - **Live Website URL:** `https://ramdeepablog.vercel.app`
4. Tap **Test Connection** and confirm it reports **`Write access verified!`** (the check specifically verifies `push` write permission, not just public read access).
5. Tap **Save Setup**.

---

## Content JSON Formats

### Writing Entry (`soul-notes`, `musings`, `heartlines`)
```json
{
  "slug": "quiet-corners",
  "title": "Quiet Corners",
  "date": "2026-11-19",
  "displayDate": "Nov 19, 2026",
  "category": "Poetry",
  "readTime": "1 min read",
  "snippet": "She wished to walk beside you...",
  "image": "/images/quiet-corners.jpg",
  "body": "She wished to walk beside you,\nif only for a little while.",
  "draft": false
}
```

### Series Metadata (`series/<slug>/series.json`) & Chapter (`chapters/<slug>.json`)
- `series.json` stores `slug`, `title`, `date`, `displayDate`, `category`, `description`, and `draft`.
- Chapter files store `slug`, `order`, `chapterLabel`, `title`, `date`, `displayDate`, `readTime`, `snippet`, `image`, `body`, optional `gratitude`, and `draft`.

### Art Gallery (`content/gallery.json` & `content/drafts/gallery.json`)
```json
{
  "acrylicArt": [
    {
      "id": "art-1763542223000",
      "src": ["/gallery/2026-11-19-lakeside-in-bloom.jpg"],
      "caption": "Lakeside in Bloom",
      "date": "2026-11-19",
      "draft": false
    }
  ],
  "pencilArt": []
}
```

---

## Project Architecture & Tech Stack

- **Language & UI:** Kotlin, Jetpack Compose, Material 3 (Edge-to-Edge, WindowInsets, adaptive max-width layouts)
- **Architecture:** MVVM (`BlogDeskViewModel` + `StateFlow`) with unidirectional state updates
- **Networking:** Retrofit 2, OkHttp 4 (deliberately no HTTP body/header logging interceptor so tokens are never logged), Moshi
- **Local Storage:** Jetpack DataStore Preferences (hashed PIN, GitHub setup settings, local auto-saved draft)
- **Images:** Coil Compose (`AsyncImage`), Android `ExifInterface` + `BitmapFactory` compression (`ImageCompressor`)
- **Testing:** JUnit 4 + Robolectric (`ExampleRobolectricTest.kt`)

### Key Source Files
```text
app/src/main/java/com/example/
├── MainActivity.kt                  # Edge-to-edge host & screen routing
├── config/
│   └── SiteConfig.kt                # Repo defaults & Published / Drafts folder paths
├── data/
│   ├── content/ContentJsonCodec.kt  # JSON serialization & parsing for all 5 sections
│   ├── github/
│   │   ├── GitHubApiService.kt      # GitHub REST Contents API interface
│   │   └── GitHubRepository.kt      # Read/write/move/delete logic, fresh-SHA & guardrails
│   ├── local/DeskPreferences.kt     # DataStore preferences for PIN, Setup & local draft
│   ├── markdown/PostFormatter.kt    # Unicode slug generation & date formatting
│   ├── media/ImageCompressor.kt     # EXIF orientation fix, 1600px resize & JPEG compression
│   ├── model/BlogModels.kt          # Domain models & folder/connection state
│   └── security/PinHasher.kt        # Salted SHA-256 PIN hashing
└── ui/
    ├── BlogDeskViewModel.kt         # App state, auto-save & GitHub orchestration
    ├── screens/
    │   ├── LockScreen.kt            # 4-digit PIN lock
    │   ├── HomeScreen.kt            # 5 section pills + Published/Drafts folder tabs
    │   ├── SeriesDetailScreen.kt    # Series info + Published/Drafts chapters list
    │   ├── EditorScreen.kt          # Writing, poetry, series & chapter editor
    │   ├── GalleryEditorScreen.kt   # Photo-first Acrylic & Pencil art editor
    │   ├── PreviewScreen.kt         # Reading preview before publishing/updating
    │   ├── SuccessScreen.kt         # Confirmation with link to live website
    │   └── SetupScreen.kt           # Hidden maintainer settings & connection test
    └── theme/                       # DeepaRam-harmonized warm editorial Material 3 theme
```

---

## Building & Testing

```bash
# Run unit & Robolectric tests
gradle :app:testDebugUnitTest

# Build debug APK
gradle :app:assembleDebug
```

- Standard Gradle output: `app/build/outputs/apk/debug/app-debug.apk`
- Root convenience copy (when built for quick device testing): `my-blog-desk-debug.apk`

---

## Troubleshooting

- **"This phone does not have permission to save changes" (`HTTP 403`)**:
  - Open **Setup** (long-press **"My Blog Desk"**) and tap **Test Connection**.
  - Confirm your GitHub Fine-Grained Token selects **`rdnk2004/blog-evd`** and has **`Contents: Read and write`** (not `Read-only` or `0` permissions).
  - Check that `main` does not have a branch protection rule blocking direct commits from your token.
- **Post not visible immediately after publishing**:
  - Publishing commits directly to `rdnk2004/blog-evd` (`main`). Vercel typically finishes rebuilding and updating `https://ramdeepablog.vercel.app` within ~1 minute.
