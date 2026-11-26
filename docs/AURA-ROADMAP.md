# AURA Launcher — roadmap 0.5.1 beta

## Direção de produto

**Futurista, completo, local-first e leve.** A interface deve parecer tecnológica sem virar uma coleção de efeitos. Priorizar hierarquia tipográfica, superfícies em camadas, animações curtas, feedback visual, acessibilidade e previsibilidade. Não adicionar analytics, publicidade, conta ou tráfego de rede do próprio AURA.

## Diagnóstico da base

- Launcher nativo em Kotlin + Jetpack Compose, minSdk 26 e targetSdk 35.
- A maior parte da UI e integração Android ainda está concentrada em MainActivity.kt; a próxima etapa técnica é modularizar sem reescrita total.
- Favoritos, ocultos, recentes, pastas, widgets, atalhos e preferências são persistidos localmente.
- A biblioteca consulta atividades com categoria LAUNCHER; recentes são os apps iniciados pelo próprio AURA, não um histórico completo do sistema.
- O manifesto não declara INTERNET; o acesso a notificações é opcional e concedido explicitamente pelo usuário.

## Entregue na base 0.5.0

- Home e biblioteca com navegação e hierarquia visual próprias.
- Widgets Android reais via AppWidgetHost/AppWidgetManager, com vínculo e configuração conduzidos pelo Android.
- Posicionamento livre de widgets e atalhos; redimensionamento de widgets somente quando o provedor declara suporte.
- Gestos de swipe e toque duplo configuráveis.
- Pacotes de ícones ADW/Nova via appfilter.xml, com fallback para ícones originais.
- NotificationListenerService opcional com badges por contagem de notificações por pacote.
- Backup local schema v2 para preferências, pastas, favoritos, ocultos, gestos, posições/dimensões de widgets e atalhos. Schema v1 segue aceito.

## Entregue na beta 0.5.1

- **Papel de parede personalizado:** seletor visual do Android, cópia para armazenamento privado do app, prévia, troca e restauração do fundo original.
- Validação de imagens: limite de 20 MB, verificação de dimensões/formato e decodificação com amostragem até 1920 px para reduzir uso de memória.
- Processamento da importação de imagem fora da thread principal, cancelamento correto do trabalho da Activity ao ser destruída e preservação do papel de parede anterior se a substituição falhar.
- Botão Voltar do Android retorna da tela de configurações ou biblioteca à Home.
- Importador de backup lê em fluxo e interrompe a leitura ao exceder 512 KB, antes de manter um arquivo excessivo em memória.
- Cache de ícones Compose é invalidado também quando o Drawable muda, permitindo que a troca de icon pack atualize a arte.
- Cache dos mapeamentos XML de icon packs para evitar reler o mesmo appfilter.xml para cada aplicativo.
- Gestos de arraste de atalhos/widgets reiniciam ao alternar o modo de edição; a lista de provedores de widgets tem altura limitada e rolagem.
- Serviço de notificações não exportado, protegido por binding do sistema, com filtros padrão para alertas/conversas; contagens antigas são removidas quando o acesso é revogado.
- Consulta e atualização do inventário de aplicativos e ícones movida para uma coroutine em `Dispatchers.IO`; a lista anterior permanece na tela durante a atualização.

## Limitações conhecidas

- A imagem de papel de parede fica somente no dispositivo e ainda não é exportada junto ao backup.
- Transferência de instâncias de widgets entre aparelhos requer novo vínculo/consentimento no destino; o Android controla os IDs.
- Alguns icon packs usam formatos proprietários ou camadas/máscaras ainda não suportadas.
- O redimensionamento precisa ser validado com provedores reais: alguns aceitam apenas um eixo ou não aceitam resize.
- A atualização do inventário de apps e a renderização de ícones ainda precisam de profiling em dispositivos modestos.
- A suíte JVM e a compilação CI não substituem testes funcionais em aparelhos físicos de diferentes fabricantes/densidades.

## Segurança e privacidade

- Manter armazenamento local como padrão e não adicionar permissões amplas sem necessidade.
- O AURA não pede permissão de armazenamento/fotos: usa o seletor de documentos do Android e guarda a imagem no diretório privado.
- O acesso a notificações é especial, opcional e altamente sensível. O serviço deve permanecer não exportado e protegido por BIND_NOTIFICATION_LISTENER_SERVICE; filtrar tipos não necessários, persistir somente contagens por pacote e limpar contagens quando o acesso for revogado. Testar concessão, revogação e comportamento sem acesso.
- Widgets são código/conteúdo dos aplicativos provedores e devem ser tratados como integrações de terceiros.
- Importação de backup deve permanecer limitada e validada; não confiar em nomes de pacotes, JSON ou IDs vindos do arquivo.
- Não prometer proteção absoluta: reduzir superfície de ataque, manter dependências mínimas e testar cenários de falha.

## Portão de qualidade antes de distribuir uma beta pública

1. CI verde: testDebugUnitTest e assembleDebug.
2. Smoke test em Android 8, 10, 13, 14 e 15, incluindo aparelhos com skins diferentes.
3. Testar escolher/trocar/remover wallpaper, imagens grandes, formato inválido, rotação de foto e pouco espaço em disco.
4. Testar Home role, retorno do sistema, botão Voltar, rotação, processo encerrado e reinicialização.
5. Testar widgets: permissão negada, cancelamento da configuração, provedor removido, resize e remoção.
6. Testar acesso a notificações concedido e revogado; confirmar que contagens são locais e não existem dados de texto armazenados.
7. Testar backup corrompido, vazio, grande, schema antigo e widget ID desconhecido.
8. Fazer teste de consumo de memória/bateria com grande quantidade de apps, widgets e notificações.

## Estado

A beta 0.5.1 contém as melhorias de código listadas acima, mas só deve ser promovida a release estável depois do CI e dos testes físicos. Ainda não é apropriado anunciar compatibilidade universal ou segurança absoluta.
