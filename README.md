# 💵 Currency Detection App for the Visually Impaired  

![Status](https://img.shields.io/badge/Hackathon-Hackverse%202024-blueviolet?style=flat-square)  
![Award](https://img.shields.io/badge/Award-First%20Runner%20Up%20%26%20Most%20Innovative%20Idea-gold?style=flat-square)  
![Android](https://img.shields.io/badge/Platform-Android-green?logo=android)  
![YOLOv8](https://img.shields.io/badge/AI-YOLOv8-orange?logo=python)  
![TFLite](https://img.shields.io/badge/Model-TensorFlow%20Lite-yellow?logo=tensorflow)  

---

## 📖 Overview  

The **Currency Detection App** was developed during **Hackverse 2024**, a 24-hour hackathon organized by **CSI-WIET, Ulhasnagar**.  

Our goal: **empower visually impaired individuals** by enabling them to recognize Indian currency notes in real-time using AI-powered object detection and voice feedback.  

Despite entering with *zero prior IoT/ML experience*, our team of four turned determination into results—winning **First Runner-Up 🥈** and the **Most Innovative Idea Award 🏅** at the event.  

- [Posted Here:](https://www.linkedin.com/posts/reeba-patel-981b50290_hackathon-firstwin-techinnovation-activity-7312890529619320832-TExP?utm_source=social_share_send&utm_medium=member_desktop_web&rcm=ACoAAEamHnEBV3FluuoLHCQEmCLR1HKUFvaNv8o)
---

## 🚀 Features  

- 🎥 **Real-time Object Detection**  
  - Uses **YOLOv8** for detecting Indian currency notes via the device camera.  

- 🔊 **Voice Assistance**  
  - Integrates **Text-to-Speech (TTS)** to announce the detected denomination, making it accessible for visually impaired users.  

- ⚡ **On-device AI**  
  - Model optimized with **TensorFlow Lite (TFLite)** for efficient and fast inference on Android devices.  

- 📱 **CameraX Integration**  
  - Built using **CameraX API** for seamless real-time image capture and processing.  

- 🌐 **Offline Capability**  
  - No internet dependency — ensures accessibility even in remote or low-connectivity areas.  

---

## 🛠️ Tech Stack  

- **Machine Learning:** YOLOv8, TensorFlow Lite  
- **Mobile Development:** Android (Kotlin/Java), CameraX API  
- **AI Deployment:** TFLite model for on-device inference  
- **Accessibility:** Android Text-to-Speech (TTS)  

---

## ⚡ How It Works  

1. The user points their phone camera at an Indian currency note.  
2. The **YOLOv8 model**, converted to **TensorFlow Lite**, runs inference directly on the device.  
3. Once the note denomination is detected, the app uses **Text-to-Speech** to announce it aloud.  
4. Designed to be **fast, lightweight, and user-friendly**, with minimal UI interaction required.  



---

## 👩‍💻 Team  

Built with ❤️ by **Team TechBaes**:  
- Reeba Patel  
- Anushka Pawar  
- Bhumi Vedant  
- Sakshi More  
---

## 📌 Future Enhancements  

- Support for multiple currencies (USD, EUR, etc.)  
- Improved detection speed with model pruning/quantization  
- Multilingual voice feedback  
- Integration with wearables or smart glasses for hands-free use  

---

## 📖 Note  

This project was developed during a hackathon under time constraints. The repository may not reflect a production-ready application but demonstrates the **core idea and implementation feasibility** of AI-driven accessibility tools.  



