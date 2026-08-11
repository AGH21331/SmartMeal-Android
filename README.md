# Smart Meal Android PRO

Native Android wrapper around Smart Meal with a local WebView UI plus native Android voice input and text-to-speech.

## What is included
- Android target API 36 for the current Google Play requirement window.
- Native microphone speech recognition (`SpeechRecognizer`) exposed to the web UI.
- Native Text-to-Speech exposed to the web UI.
- Native image chooser with Gallery and Camera support.
- Local bundled Smart Meal web UI.
- AI endpoint configured to `https://smart-eating-delta.vercel.app/api/ai`.
- GitHub Actions workflow that builds a debug APK and a release AAB.

## Build on GitHub
1. Create or use a GitHub repository.
2. Upload the project contents (not the ZIP itself) to the repository root.
3. Open **Actions → Smart Meal Android Build → Run workflow**.
4. Download the `smart-meal-android-build` artifact.

## Important for production
The AAB generated here is unsigned. Before Google Play production, configure a private release keystore in GitHub Actions secrets and sign the release bundle. Keep the keystore safe; future updates must use the same signing identity.

The AI backend must have `GEMINI_API_KEY` set in Vercel Environment Variables. Never place the Gemini key inside the Android project or web JavaScript.
