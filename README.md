# Object Detector App

Aplicativo Android para detectar objetos em tempo real usando TensorFlow Lite e Jetpack Compose.

## Funcionalidades

- Detecção de objetos usando modelo TensorFlow Lite (`common_detect.tflite`).
- Exibição das bounding boxes e rótulos com a pontuação de confiança.
- Controle dinâmico do limiar (score threshold) para filtrar detecções via botões na UI.
- Exibição do score threshold atual no canto inferior esquerdo.
- Log das detecções para acompanhamento.
- Suporte a orientação paisagem e retrato.
- Uso da câmera do dispositivo com permissões solicitadas em tempo de execução.

## Tecnologias

- Kotlin
- Jetpack Compose
- CameraX
- TensorFlow Lite Task Library
- Android API 33+ (VANILLA_ICE_CREAM)

## Como usar

1. Clone o repositório:

```bash
git clone <URL-do-repositorio>
cd object-detector-app

