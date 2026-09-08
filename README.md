# Notificações Inteligentes

Projeto desenvolvido como **Hands-on Final do DevTitans — Turma 9**.

## Sobre o projeto

O projeto **Notificações Inteligentes** tem como objetivo desenvolver uma solução Android capaz de utilizar uma LLM executada localmente no dispositivo para gerar resumos inteligentes de múltiplas notificações pertencentes ao mesmo aplicativo.

A proposta envolve a integração com o sistema de notificações do Android, a execução de modelos de linguagem on-device por meio do **LiteRT** e a exibição dos resumos gerados ao usuário.

O projeto será desenvolvido inicialmente sobre **Android / AOSP 14**, com foco em processamento local, eficiência e integração com componentes do sistema.

## Objetivo

Criar uma solução capaz de:

- monitorar notificações do Android;
- identificar notificações pertencentes ao mesmo aplicativo;
- agrupar o conteúdo dessas notificações;
- enviar os dados para uma LLM executada localmente;
- gerar um resumo curto e coerente;
- disponibilizar esse resumo para exibição ao usuário.

A inferência deverá ocorrer diretamente no dispositivo, utilizando **LiteRT**, evitando a necessidade de envio do conteúdo das notificações para serviços externos.

## Tecnologias

As principais tecnologias previstas para o projeto são:

- Android
- AOSP 14
- Kotlin
- Java
- LiteRT
- Gemma
- Android Services
- NotificationListenerService
- NotificationManagerService
- SystemUI

## Arquitetura inicial

O fluxo inicial da solução é:

```text
Notificações Android
        ↓
NotificationViewer
        ↓
Extração e agrupamento
das notificações por aplicativo
        ↓
Módulo de IA
LiteRT + Gemma
        ↓
Resumo gerado localmente
        ↓
App consumidor / SystemUI