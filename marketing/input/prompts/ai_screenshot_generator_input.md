# AI Input 1: Play Store Panoramic Screenshots Generator

Copy and paste this document directly into your AI assistant (like Gemini, ChatGPT, or Claude) or use the specific image prompts for Midjourney/Imagen to get started.

---

## Part 1: App Context (Provide this to the AI so it understands the app)
> **About Vaultix:**
> Vaultix is a premium, secure mobile digital vault and password manager. Its primary goal is absolute privacy.
> **Core Features:**
> 1. Encrypted File Vault (locks photos, videos, and PDFs).
> 2. Secure Identity Manager (stores login credentials and credit card information).
> 3. Smart Password Generator (creates strong, uncrackable passwords).
> 4. Real-time Security Audit (audits passwords for leaks and weaknesses).
> **Visual Identity:** Modern dark/light glassmorphic UI, glowing orange/amber accents (`#FF9800`), sleek and futuristic cyber-protection aesthetics.

---

## Part 2: Background Image Generation & Color Customization

Your current design has a very sharp yellow-orange line wave. To make it look more premium like global applications (e.g., 1Password, Bitwarden, NordVPN), we suggest using a **smoother, thicker, soft-glowing gradient wave** or changing the color palette slightly.

Here are the best prompts for Midjourney v6 / Gemini Imagen to get a cleaner background:

### Option A: Deep Indigo & Glowing Amber Gold (Recommended for Tech & Security)
> **Midjourney Prompt:**
> /imagine prompt: A high-resolution horizontal panoramic app store screenshot background, aspect ratio 9:4. Cybersecurity concept. Dark mode background `#0B0C10` with a smooth, soft-glowing dark indigo and glowing orange-amber `#FF9800` gradient fluid wave flowing seamlessly from left to right. Abstract glassmorphic 3D shapes, smooth curves, floating glowing shield outlines with high depth-of-field blur. Clean, luxurious, ultra-premium look, graphic design layout. --ar 9:4 --v 6.0 --style raw

### Option B: Sleek Obsidian & Soft Orange Glow (Monochromatic Theme)
> **Midjourney Prompt:**
> /imagine prompt: A horizontal panoramic app store background, aspect ratio 9:4. Minimalist dark mode design. Clean matte black background `#121212` with a single, thick, smooth flowing glowing liquid ribbon of amber light `#FF9800` moving across the screen from left to right. Clean glass cards, high-contrast, professional tech app presentation background, depth of field, 8k. --ar 9:4 --v 6.0

---

## Part 3: Connected Copywriting (نص متصل يربط بين الصور)

لتحقيق فكرة **النص المتصل**، لدينا خيارين ممتازين. اختر الخيار الذي تراه مناسباً لهوية التطبيق:

### الخيار الأول: الجملة المستمرة (One Flowing Sentence)
في هذا الخيار، تشكل العناوين الأربعة جملة واحدة متصلة ببعضها عند عرض الصور بجانب بعضها على المتجر:

*   **الصورة 1 (الرئيسية)**:
    *   **العنوان (Cairo Bold)**: تطبيق Vaultix...
    *   **الوصف (Cairo Regular)**: حماية شاملة وتشفير متكامل لكافة هوياتك الحساسة.
*   **الصورة 2 (الخزنة)**:
    *   **العنوان (Cairo Bold)**: ...يحمي ملفاتك...
    *   **الوصف (Cairo Regular)**: خزنة آمنة ومقفلة بالكامل لصورك وفيديوهاتك الخاصة.
*   **الصورة 3 (البطاقات والهوية)**:
    *   **العنوان (Cairo Bold)**: ...ويشفّر بطاقاتك...
    *   **الوصف (Cairo Regular)**: احفظ بياناتك البنكية وبطاقاتك واسترجعها بلمسة واحدة.
*   **الصورة 4 (كلمات المرور)**:
    *   **العنوان (Cairo Bold)**: ...بأمان مطلق!
    *   **الوصف (Cairo Regular)**: فحص ذكي للأمان مع توليد فوري لكلمات مرور غير قابلة للاختراق.

---

### الخيار الثاني: الربط البصري بنص مائي خلف الهواتف (Giant Watermark Text)
طريقة احترافية مستخدمة في تطبيقات مثل Revolut:
1. في Figma، اكتب كلمة ضخمة جداً في الخلفية (مثل: **VAULT** أو **SECURE** أو **SAFE**) بحجم **`600px`** وبخط **Outfit Bold**.
2. اجعل لون الكلمة رمادي داكن جداً وقريب من لون الخلفية (مثلاً شفافية **`5%`** أو لون `#1C1D22`).
3. ضع هذه الكلمة لتمر خلف الهواتف وتمتد من الصورة الأولى وحتى الصورة الرابعة. عند تقطيع الصور، سيبدو الحرف الأول في الصورة الأولى، والحرف الثاني في الصورة الثانية، وهكذا، مما يخلق رابطاً بصرياً مذهلاً!

---

## Part 4: Assembly Steps (Figma / Canva)

1. Create a canvas of **`4320 x 1920`** pixels.
2. Place the generated panoramic background on the canvas.
3. Draw vertical guidelines at `1080px`, `2160px`, and `3240px` to see the 4 screenshot areas.
4. Insert your raw phone screenshots inside mockup frames (e.g., iPhone/Android mockups). Place them slightly tilted across the dividing lines to make the visual continuous.
5. Add the headings and descriptions using the **Cairo** font.
6. Export the finished image as `panorama_canvas.png` and run the script:
   ```powershell
   py -3.9 marketing/scripts/slice_panorama.py
   ```
   This will output `screenshot_1.png` through `screenshot_4.png` in `marketing/output/store_assets/`.

