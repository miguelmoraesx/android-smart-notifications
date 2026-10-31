# Notificações Inteligentes — PoC LiteRT-LM + Gemma

Primeira prova de conceito do Hands-on Final DevTitans — Turma 9. Esta etapa valida somente o
pipeline real e local:

```text
texto digitado → PromptBuilder → SummaryGenerator → LiteRT-LM → Gemma → resumo
```

Não há resposta simulada: sem um modelo `.litertlm` compatível e carregado, a geração permanece
desabilitada. Esta etapa não lê notificações, não implementa `NotificationListenerService` e não
modifica AOSP/SystemUI.

## Decisões técnicas verificadas em 15/09/2026

- **LiteRT-LM Android 0.17.0** (`com.google.ai.edge.litertlm:litertlm-android:0.17.0`), versão estável
  mais recente publicada no Google Maven na data da implementação.
- **API Kotlin estável do LiteRT-LM**, recomendada para Android/JVM. A inferência usa
  `Conversation.sendMessageAsync(...): Flow<Message>`; o carregamento usa `Engine.initialize()`.
- **Kotlin 2.4.20**, **AGP 9.1.1**, Gradle 9.3.1, JDK 17, `compileSdk`/`targetSdk` 37
  e `minSdk` 24.
- Backend inicial **CPU**, priorizando compatibilidade. A seleção futura de GPU/NPU fica isolada em
  `LLMManager`.
- Interface Android Views + ViewBinding, ViewModel, StateFlow e coroutines.

Fontes oficiais consultadas:

- [LiteRT-LM — guia da API Kotlin](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.0/docs/api/kotlin/getting_started.md)
- [LiteRT-LM — repositório e releases](https://github.com/google-ai-edge/LiteRT-LM)
- [Execução de Gemma no edge](https://ai.google.dev/gemma/docs/run)
- [Compatibilidade Kotlin/AGP/R8](https://developer.android.com/build/kotlin-support)

O MediaPipe LLM Inference continua sendo uma alternativa para modelos empacotados no formato
`.task`. Esta PoC usa LiteRT-LM porque sua API Kotlin atual trabalha diretamente com os modelos
otimizados `.litertlm`, oferece streaming por `Flow` e prepara o projeto para CPU/GPU/NPU sem criar
uma camada JNI própria.

## Arquitetura

```text
MainActivity
    ↓ eventos / StateFlow
MainViewModel
    ├── LocalModelStore ── copia URI → filesDir/models/selected-model.litertlm
    └── SummaryGenerator
            ├── PromptBuilder
            └── TextGenerator
                    └── LLMManager ── Engine → Conversation → Flow<Message>
```

- `MainActivity`: tela manual de entrada, seleção do modelo, progresso e saída.
- `LocalModelStore`: transforma a URI do seletor Android em caminho privado absoluto, exigido pelo
  runtime nativo.
- `LLMManager`: único componente que conhece LiteRT-LM; carrega/fecha o `Engine` e executa a
  inferência assíncrona real.
- `PromptBuilder`: política de prompt de sumarização, testável sem Android.
- `SummaryGenerator`: caso de uso `texto → prompt → LLM → resumo`, sem dependência de UI ou de
  notificações.

Na etapa futura, `NotificationViewer` deverá extrair e agrupar o texto das notificações e chamar
`SummaryGenerator`. Ele não precisará conhecer o modelo nem a API LiteRT-LM.

## Como executar

### Pré-requisitos

- Android Studio compatível com AGP 9.1.1;
- JDK 17;
- Android SDK 37;
- aparelho Android 7.0/API 24+ de 64 bits (`arm64-v8a`) ou emulador `x86_64`;
- espaço livre e memória suficientes para o modelo escolhido.

### Build

No Windows:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

Em macOS/Linux:

```bash
./gradlew testDebugUnitTest assembleDebug
```

O APK de debug será gerado em `app/build/outputs/apk/debug/app-debug.apk`.

### Modelo Gemma

1. Obtenha um Gemma instruction-tuned e quantizado **já exportado no formato `.litertlm`**. A
   coleção usada pelos exemplos oficiais está em
   [Hugging Face — LiteRT Community](https://huggingface.co/litert-community). Um ponto de partida
   atual é `litert-community/gemma-4-E2B-it-litert-lm`.
2. Aceite os termos/licença aplicáveis ao modelo e baixe o arquivo `.litertlm`.
3. Instale e abra o app em um dispositivo compatível.
4. Toque em **Selecionar modelo .litertlm** e escolha o arquivo. A cópia e
   `Engine.initialize()` ocorrem em background; modelos grandes podem levar vários segundos.
5. Quando o status mostrar **Modelo pronto**, edite o texto de exemplo e toque em **Gerar resumo**.

O modelo não é incluído no APK devido ao tamanho e aos termos de distribuição. Não use diretamente
arquivos `.tflite`, `.task`, GGUF ou checkpoints Transformers: o artefato precisa ser compatível com
a versão do LiteRT-LM usada pelo app.

## Estrutura relevante

```text
app/
├── models/README.md
└── src/main/java/br/edu/devtitans/smartnotifications/
    ├── MainActivity.kt
    ├── MainViewModel.kt
    └── ai/
        ├── LLMManager.kt
        ├── LocalModelStore.kt
        ├── PromptBuilder.kt
        └── SummaryGenerator.kt
```

## Limitações atuais

- nenhum modelo é baixado, empacotado ou convertido pelo app;
- somente um modelo fica instalado no armazenamento privado por vez;
- backend fixo em CPU; desempenho depende fortemente do aparelho e do modelo;
- o AAR 0.17.0 fornece binários Android para `arm64-v8a` e `x86_64`, não para ABIs de 32 bits;
- não há medição de tokens, benchmark, seleção automática de backend ou cancelamento pela UI;
- a qualidade do resumo depende do modelo quantizado escolhido;
- ainda não existem captura/agrupamento de notificações, `NotificationViewer`, serviço Android ou
  integração com SystemUI/AOSP.

## Dataset otimizado e limites de inferência

O utilitário `tools/optimize_dataset.py` audita o ZIP original sem executar scripts contidos nele e
gera splits JSONL mínimos (`input`/`output`) para SFT. A versão gerada está em
`dataset_optimized.zip` e inclui um relatório reproduzível das correções.

Para reduzir latência e pressão de memória no aparelho, a inferência limita a entrada aos 1.600
caracteres mais recentes, o contexto a 768 tokens e a resposta a 48 tokens. O backend usa no máximo
quatro threads nativas e as atualizações da interface são agrupadas em blocos de 32 caracteres. A
tela permite cancelar uma geração e descarregar/recarregar o modelo para liberar a memória nativa.
Esses limites reduzem o KV cache e cópias de strings, mas não alteram a memória fixa necessária para
carregar os pesos: um arquivo de modelo com aproximadamente 2,4 GB ainda exige um aparelho
compatível ou a troca por um `.litertlm` menor e mais quantizado.

Para regerar o dataset compacto:

```bash
python3 tools/optimize_dataset.py /caminho/dataset.zip dataset_optimized \
  --zip-output dataset_optimized.zip
```
