---
title: CBT Stem Studio Backend
emoji: 🎚️
colorFrom: green
colorTo: gray
sdk: gradio
sdk_version: 5.49.1
app_file: app.py
python_version: 3.10
pinned: false
---

# CBT AI Stem Studio — free prototype backend

This Space runs Meta/Facebook Research Demucs on CPU and returns a ZIP containing vocals, drums, bass, and other.

## Important
- This is a free CPU prototype, not a production service.
- Free Spaces can sleep when idle. First request may take time.
- CPU separation can be slow. Try a short audio file first.
- Do not upload private or copyrighted recordings unless you have the rights and are comfortable sending them to this hosted service.
- The Android app currently expects a custom `POST /separate` API. This Space exposes a Gradio API instead; the Android client must be adapted to the Gradio upload + queue API after the Space URL is known. Do not paste this Space URL into the existing endpoint field yet.

After creating the Space, open its **Use via API** page to confirm the endpoint name is `/separate`.
