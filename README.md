# Aura – On-Device AI Voice Companion 🎙️

Aura is an Android application that turns recorded conversations into something more useful. Instead of just storing audio, it uses AI to transcribe conversations, identify different speakers, generate summaries, and extract important points such as action items and events.

The idea behind Aura is to bring these capabilities directly to Android devices using on-device AI, with less dependence on cloud-based processing.

## ✨ Features

- **Speech-to-Text:** Converts recorded conversations into text using Whisper Tiny English.
- **Voice Activity Detection:** Detects speech activity in audio recordings.
- **Speaker Diarization:** Uses Sherpa-ONNX models to distinguish between speakers.
- **Audio Preprocessing:** Prepares recorded audio in the format required by the AI models.
- **AI Summarization:** Uses Gemma 3 1B to process conversations and generate summaries, insights, action items, and events.
- **Semantic Embeddings:** Uses BGE Small English to convert text into meaningful vector representations.
- **Local Memory:** Includes a vector-memory component for storing and retrieving contextual information.

## 🏗️ Architecture & Workflow

Aura processes recorded conversations through a series of steps. Each component handles a specific part of the workflow, from preparing the audio to storing useful information.

```text
Audio Recording
      |
      v
Audio Preprocessing
      |
      v
Voice Activity Detection
      |
      v
Whisper – Speech Recognition
      |
      v
Speaker Diarization
      |
      v
Gemma 3 1B – AI Processing
      |
      v
Summaries, Insights & Actions
      |
      v
BGE Embeddings
      |
      v
Local Vector Memory
```

## 🛠️ Tech Stack

| Component | Technology |
|---|---|
| Platform | Android |
| Programming Language | Kotlin |
| UI Framework | Jetpack Compose |
| Speech Recognition | Whisper.cpp |
| Speaker Diarization | Sherpa-ONNX |
| Local LLM | Gemma 3 1B |
| LLM Runtime | llama.cpp Android |
| Embeddings | BGE Small English v1.5 |
| Model Formats | GGUF, ONNX, BIN |
| Build System | Gradle |
| Development Environment | Android Studio |

## 🧠 AI Models

Aura uses different models for different stages of conversation processing.

| Model | Purpose |
|---|---|
| Whisper Tiny English | Converts speech into text |
| Gemma 3 1B | Understands conversations and generates summaries |
| BGE Small English v1.5 | Generates semantic embeddings |
| Pyannote ONNX | Supports speech segmentation for diarization |
| 3D-Speaker ERes2Net | Generates speaker embeddings |

**About Gemma:** The Gemma model is downloaded separately when needed rather than being bundled with the application. Once downloaded, it is stored locally and used for on-device inference.

## 📂 Project Structure

```text
Aura/
├── app/
│   ├── libs/
│   └── src/main/
│       ├── assets/models/
│       ├── java/com/example/aura/
│       │   ├── MainActivity.kt
│       │   ├── AudioPreprocessor.kt
│       │   ├── VoiceActivityDetector.kt
│       │   ├── WhisperASR.kt
│       │   ├── SpeakerDiarization.kt
│       │   ├── LocalLLM.kt
│       │   ├── EmbeddingManager.kt
│       │   └── VectorMemoryStore.kt
│       └── AndroidManifest.xml
├── lib/
├── gradle/
├── build.gradle.kts
└── settings.gradle.kts
```

## ⚙️ Setup & Installation

### Requirements

- Android Studio
- Android SDK 35
- JDK 17
- Android 8.0 (API 26) or above

### 1. Clone the repository

```bash
git clone https://github.com/vijayasrichekuri/Aura-On-Device-AI-Voice-Companion.git
```

### 2. Open the project

Open the cloned folder in Android Studio and allow Gradle to sync.

### 3. Build the application

On Windows, open the project terminal and run:

```bash
gradlew.bat assembleDebug
```

### 4. Run Aura

Connect an Android device or start an emulator, select the `app` run configuration, and launch the application.

**Note:** An internet connection is required when Gemma needs to be downloaded for the first time. Once downloaded, the model is stored locally for subsequent inference.

## 🔐 Permissions

Aura requires the following Android permissions:

- `RECORD_AUDIO` – To record conversations.
- `POST_NOTIFICATIONS` – To display notifications.
- `INTERNET` – To download the language model when required.

## 🎯 Project Objective

The main goal of Aura is to explore how different AI technologies can work together inside a mobile application.

By combining speech recognition, speaker diarization, local language models, and semantic memory, the project explores how recorded conversations can be converted into structured and useful information while keeping much of the AI processing on the device.

---

**Aura – Making everyday conversations more useful with on-device AI.**
