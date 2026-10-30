#!/bin/bash
adb shell 'am broadcast -a br.edu.devtitans.smartnotifications.MOCK_NOTIF -p br.edu.devtitans.smartnotifications --es app "LinkedIn" --es title "Vaga Recrutador" --es text "Olá! Vimos o seu perfil no GitHub e ficamos impressionados com seus projetos em Android e IA. Temos uma oportunidade Sênior remota na Europa, salário em Euro. Podemos marcar um papo rápido amanhã?"'
sleep 1
adb shell 'am broadcast -a br.edu.devtitans.smartnotifications.MOCK_NOTIF -p br.edu.devtitans.smartnotifications --es app "WhatsApp" --es title "Síndico Predio" --es text "Boa tarde moradores, o portão da garagem B estará em manutenção amanhã entre 09:00 e 12:00. Por favor, utilizem a saída C para evitar transtornos. Obrigado."'
sleep 1
adb shell 'am broadcast -a br.edu.devtitans.smartnotifications.MOCK_NOTIF -p br.edu.devtitans.smartnotifications --es app "Ifood" --es title "Entrega" --es text "Seu pedido de Hambúrguer e Fritas do restaurante X-Tudo já saiu para entrega! O motoboy Rafael está a caminho e deve chegar em 10 minutos."'
