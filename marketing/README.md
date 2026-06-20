# Vaultix App Store Assets & Promo Video Preparation Guide

Welcome to the **Vaultix Marketing and Store Listing Workspace**. This directory is structured to separate inputs (logos, brand configurations, raw screenshots, prompts) from outputs (sliced images, final video files), while providing helper tools to assemble your assets to match global-class design standards.

---

## 📂 Directory Structure

Here is how your marketing workspace is organized:

```text
marketing/
│
├── README.md                      <-- (You are here) The master workflow guide
│
├── input/                         <-- Place raw files and creative briefs here
│   ├── brand/
│   │   └── brand_identity.json    <-- Color palettes, fonts, and core features
│   │
│   ├── screenshots/               <-- Place raw mobile device screenshots here
│   │
│   └── prompts/
│       ├── ai_screenshot_generator_input.md <-- Copy-pasteable screenshot generator input (Cairo font)
│       └── ai_video_generator_input.md      <-- Copy-pasteable 30s video storyboard & prompts
│
├── scripts/                       <-- Automation and helper tools
│   └── slice_panorama.py          <-- Python script to slice wide canvas
│
└── output/                        <-- Slices and generated assets land here
    ├── store_assets/              <-- Ready-to-upload Play Store screenshots
    └── video/                     <-- Final promotional video folder
```

---

## 🎨 Workflow 1: Premium Continuous Panorama Screenshots

Follow this 6-step process to create high-quality screenshots that flow together seamlessly.

### Step 1: Generate the Background
Use the prompts inside [ai_screenshot_generator_input.md](file:///e:/Vaultix/marketing/input/prompts/ai_screenshot_generator_input.md) in **Midjourney v6** or **Gemini Imagen 3**. Save the resulting 9:4 horizontal background image as a high-quality PNG.

### Step 2: Set up a Figma Canvas
1. Create a free account at [figma.com](https://www.figma.com).
2. Create a new frame with dimensions **`4320 x 1920`** pixels.
3. Import your generated background image and stretch it to cover the frame.
4. Draw 3 vertical layout grids/guides at:
   - `1080px`
   - `2160px`
   - `3240px`
   These mark the boundaries of your 4 screenshots.

### Step 3: Add Phone Mockup Frames
1. Search Figma Community for free device templates (e.g., "iPhone 15 Mockup" or "Pixel 8 Mockup").
2. Place a mockup frame on each of the 4 slices.
3. Position them dynamically (e.g., tilted at a 15-degree angle) so they cross the slicing lines. This creates the premium "flowing" visual effect.

### Step 4: Embed App Screenshots & Connected Text
1. Take screenshots of the Vaultix app (Dashboard, File Vault, Card Scanner, Password Generator).
2. Insert each screenshot into the respective phone mockup frame in Figma.
3. Add the connected Arabic/English captions from [ai_screenshot_generator_input.md](file:///e:/Vaultix/marketing/input/prompts/ai_screenshot_generator_input.md).
4. Use the **Cairo** font (Arabic) and **Outfit** (English) in bold size (`~60-70px`). Ensure headings are centered relative to their slice grids.

### Step 5: Slice the Canvas
1. Export the entire frame from Figma as a single image named `panorama_canvas.png` (dimensions `4320x1920`).
2. Move `panorama_canvas.png` into the `marketing/input/` directory.
3. Run the slicing script:
   ```powershell
   py -3.9 marketing/scripts/slice_panorama.py
   ```
4. Find your ready-to-upload screenshots (`screenshot_1.png` to `screenshot_4.png`) in `marketing/output/store_assets/`.

---

## 🎥 Workflow 2: Professional 30-Second Promotional Video

Follow this workflow to create a cinema-grade promotional video using AI tools.

### Step 1: Prepare Scene Elements
Open the [ai_video_generator_input.md](file:///e:/Vaultix/marketing/input/prompts/ai_video_generator_input.md) script. It has 5 scenes covering the application's narrative arc.

### Step 2: Generate Video Scenes
1. Go to an AI video generator like **Runway Gen-3 Alpha** or **Luma Dream Machine**.
2. Copy the scene-specific prompts from the storyboard file.
3. Generate and download each 5-to-10 second clip.

### Step 3: Generate the Voiceover (Voice)
1. Go to **ElevenLabs** (elevenlabs.io).
2. Select a deep, professional male or female voice (e.g., "Adam" or "Antoni").
3. Paste the Arabic or English narration script from the storyboard file and download the high-quality MP3 voiceover.

### Step 4: Assemble in CapCut or Premiere Pro
1. Create a new vertical project (**9:16 ratio**) or landscape (**16:9 ratio**) in CapCut.
2. Drag and drop your generated video clips, voiceover file, and a background track (we recommend dark cybernetic techno or synthetic beats).
3. Cut each clip to align perfectly with the narrator's words.
4. Add kinetic subtitles (auto-captions in CapCut) using a thick, modern font (like Cairo Bold) with an orange `#FF9800` outline or glow effect.
5. Export the final rendering into `marketing/output/video/vaultix_promo.mp4`.
