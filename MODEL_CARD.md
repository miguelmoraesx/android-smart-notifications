# Model card — Gemma 4 E2B / LiteRT-LM

## Identidade e proveniência

Esta PoC integra um checkpoint existente; não publica pesos treinados pelo projeto. O nome preciso é **Gemma 4 E2B instruction-tuned**, não Gemma 2 2B.

- Artefato de bancada informado: `gemma-4-E2B-it.litertlm`, aproximadamente 2,4 GB.
- Distribuição: [LiteRT Community no Hugging Face](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm).
- Linhagem: [model card upstream](https://huggingface.co/google/gemma-4-E2B-it).
- Runtime fixado no app: `com.google.ai.edge.litertlm:litertlm-android:0.17.0`.

A distribuição comunitária fornece um pacote preparado para LiteRT-LM. O tamanho aproximado do arquivo não mede RAM residente. Registre revisão do Hub, nome exato, SHA-256 e tamanho em bytes ao reproduzir a bancada; esses identificadores não foram fornecidos no relato original.

## Formato e execução

`.litertlm` empacota os componentes necessários ao runtime. A API Kotlin oficial encapsula o acesso à implementação nativa, evitando manter wrappers JNI próprios. A aceleração efetiva depende do backend, build e dispositivo; a extensão não comprova uso de NEON/FP16 em todas as operações. O app seleciona CPU, até quatro threads, contexto de 768 tokens e saída de 48 tokens.

O [upstream](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) descreve quantização móvel mista de pesos em 2, 4 e 8 bits e gerenciamento de memória por mapeamento. Não assumir que todo checkpoint ou exportação tenha o mesmo layout ou compatibilidade com 0.17.0.

## Download no dispositivo

Abra o repositório do modelo no navegador, consulte a aba Files e os termos aplicáveis, baixe o `.litertlm` correspondente e selecione-o no app. Alternativamente, baixe no computador e transfira para Downloads via USB/ADB. Reserve espaço para o download e a cópia privada em `filesDir/models/selected-model.litertlm`. Nunca publique tokens de acesso ou os pesos junto ao código.

Após “Modelo pronto”, teste um lote sintético e valide funcionamento offline. Checkpoints Transformers, GGUF, `.task` e `.tflite` isolados não são substitutos diretos do pacote esperado.

## Adaptar e converter novos checkpoints

1. Verifique licença, arquitetura suportada e direitos sobre os dados. Prepare exemplos anonimizados e separe validação independente antes de treino.
2. Para SFT, `dataset_optimized/` oferece pares compactos e alvos curtos; não é um benchmark independente de notificações reais.
3. Exporte o checkpoint seguindo o fluxo oficial de [conversão do LiteRT Torch](https://github.com/google-ai-edge/litert-torch) e o empacotamento documentado pelo [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM). Verifique o suporte específico à arquitetura e à versão de destino antes de treinar.
4. Aplique somente esquemas de quantização suportados pelo backend/conversor. Calibre quando o fluxo exigir, preserve tokenizer/template e valide perda de qualidade após conversão. Renomear arquivos não converte modelos.
5. Teste carregamento, geração, memória e latência no runtime Android 0.17.0. Publique revisão, hashes, configuração de conversão e resultados junto à model card do novo artefato.

Não há comando universal de conversão garantido para qualquer checkpoint do Hugging Face. Este repositório não executou nem validou uma nova conversão nesta entrega.

## Avaliação e limites

A validação de bancada relatada consta no README: Moto G100, lotes de três mensagens e inferência aproximada de 2,5–3,2 s. Não foram disponibilizados logs de benchmark, percentis, PSS/RSS ou avaliação quantitativa de factualidade. Prompts e amostragem reduzem desvios, mas não garantem ausência de alucinações, exposição em logs ou OOM. Revise resumos antes de agir sobre prazos ou informações importantes.
