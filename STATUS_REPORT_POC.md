# Relatório de Status: Smart Notifications (PoC)

**Data:** 28 de Outubro de 2026
**Responsável (Frente LLM):** Pedro Henrique (feat/poc-litert-gemma)

---

## 1. Contexto do Projeto
O projeto **Notificações Inteligentes** (Hands-on Final DevTitans) visa criar uma solução nativa no Android (AOSP) capaz de interceptar rajadas de notificações de um mesmo contexto, utilizar uma Inteligência Artificial local no dispositivo (LLM) para resumi-las, e exibir a síntese de forma nativa ao usuário.

Isso previne a fadiga de notificações e garante privacidade, uma vez que nenhum dado pessoal é enviado para a nuvem.

Devido à complexidade de compilar o AOSP, a arquitetura foi dividida em duas frentes de trabalho:
1.  **Frente AOSP (Branch `NotificationService` - Vitor):** Preparação do gancho nativo dentro do `SystemUI` para interceptar as notificações na camada do sistema operacional.
2.  **Frente IA / Lógica (Branch `feat/poc-litert-gemma` - Pedro):** Construção do "cérebro" do sistema (Agrupamento, Debounce e Integração LLM) provado através de um aplicativo (APK) funcional no aparelho físico.

---

## 2. O que foi construído e validado (Proof of Concept)

A frente da Inteligência Artificial foi **100% implementada e validada** em um dispositivo físico (Motorola XT2125-4), comprovando a viabilidade técnica de rodar um modelo de 2 Bilhões de parâmetros localmente.

### 2.1. Entregáveis Técnicos:
*   **Integração do LiteRT-LM com Gemma:** O aplicativo carrega o modelo quantizado `gemma-4-E2B-it.litertlm` (2.5GB) para a memória do dispositivo e gera resumos coerentes de textos de forma off-line.
*   **Arquitetura Singleton (Desacoplamento):** Toda a engine do LLM (`LLMManager` e `SummaryGenerator`) foi desvinculada das telas (ViewModel) e instanciada na classe `Application`, mantendo a IA viva na memória para atender processos em background.
*   **Agrupamento e Debounce (O Coração da Lógica):** Foi implementada a classe `NotificationViewer` com um "Timer de Silêncio" (Debounce) de 5 segundos. Ela escuta as mensagens e as empacota. Somente após 5 segundos sem receber novas mensagens, o pacote é enviado para a IA.
*   **Serviço Autônomo:** A classe `SmartNotificationListenerService` usa a API oficial do Android para ler ativamente as mensagens do WhatsApp, Slack, Gmail, etc.
*   **Loop de Feedback (Heads-up Notification):** Após a IA processar o resumo, o aplicativo dispara uma notificação real de Sistema de Alta Prioridade (`IMPORTANCE_HIGH`) para entregar a resposta final na barra de status do usuário.
*   **Trava Anti-Loop:** O sistema reconhece sua própria notificação de resumo para não tentar resumi-la infinitamente.

### 2.2. Simulação e Teste de Estresse:
Via ADB, injetamos notificações silenciadas simulando Slack, WhatsApp e Gmail em intervalos de 1 segundo. A lógica agrupou as mensagens perfeitamente, o Gemma filtrou os contextos distintos (Problemas de Servidor, Reagendamento e Voo) e devolveu uma notificação coesa em poucos segundos.

---

## 3. Próximos Passos (O Caminho para o AOSP)

Com a Prova de Conceito concluída, a lógica e o modelo de IA estão prontos. O desafio final será a fusão do código desta branch com o código do `SystemUI` desenvolvido pela Frente AOSP.

### O Roteiro Arquitetural Recomendado:

A integração **NÃO** deve ocorrer inserindo a biblioteca do LiteRT-LM e os 2.5GB do Gemma dentro do processo do `SystemUI`. Fazer isso causaria falhas de memória (OOM) na interface do usuário, travando o aparelho.

O recomendado é o **Modelo Cliente-Servidor via AIDL (IPC):**

1.  **Transformar o App em System App:** A pasta deste projeto Gradle deverá ser convertida para um módulo Soong (`Android.bp`). O arquivo `.aar` do LiteRT-LM será adicionado à árvore do AOSP (`prebuilts`) para compilação estática. Este aplicativo rodará silenciosamente como um Serviço de Sistema (`SystemServer`).
2.  **Criar a Ponte AIDL:** Um contrato `ISmartSummaryService.aidl` será criado contendo um método como `String generateSummary(String blockOfText)`.
3.  **A Conexão no SystemUI:** O código atual do Vitor (`CustomNotificationHandler.java`), que roda no SystemUI e já intercepta as mensagens, servirá como "Cliente". Em vez de apenas dar `Log.d`, ele consumirá o AIDL, enviando o texto para o nosso Serviço Isolado e desenhando na tela a string mastigada que a LLM devolver.

Dessa forma, isolamos o processamento massivo de Inteligência Artificial da renderização gráfica do Android, garantindo que o sistema operacional continue fluido.
