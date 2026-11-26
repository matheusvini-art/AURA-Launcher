# AURA Launcher — roadmap 0.5.0

## Direção de produto

**Futurista, completo, local-first e leve.** A interface deve parecer tecnológica sem virar uma coleção de efeitos. Priorizar hierarquia tipográfica, superfícies em camadas, animações curtas, feedback tátil/visual e acessibilidade. Não adicionar analytics, publicidade, conta ou tráfego de rede.

## Diagnóstico da base 0.3.1

- O app é um launcher nativo em Kotlin + Jetpack Compose (minSdk 26, targetSdk 35).
- A maior parte da UI, estado e integração Android está concentrada em `MainActivity.kt`.
- Favoritos, ocultos, recentes, pastas e preferências são persistidos em SharedPreferences.
- A biblioteca consulta atividades com categoria LAUNCHER; recentes são os apps iniciados pelo próprio AURA, não um histórico completo do sistema.
- Há dois temas, cinco cores, tamanho de ícone, colunas, rótulos e ordenação.
- O manifesto não declara permissão de internet; o acesso a notificações é opcional e concedido explicitamente pelo usuário.
- A suíte JVM e o CI executam testes antes da geração do APK.

## Arquitetura-alvo

Separar gradualmente em pacotes `home`, `apps`, `folders`, `widgets`, `gestures`, `icons`, `backup`, `settings`, `data` e `platform`. Evitar reescrita total: migrar uma funcionalidade por vez, preservando preferências e comportamento existentes.

## Entregue nesta branch — 0.5.0 preview

- Home e biblioteca redesenhadas com navegação e hierarquia visual novas.
- AppWidgetHost/AppWidgetManager: listar provedores do dispositivo, solicitar vínculo ao Android, executar a configuração do provedor, adicionar, remover e posicionar widgets por arraste em canvas livre.
- IDs e posições dos widgets persistidos em SharedPreferences e incluídos no backup local; IDs inexistentes são descartados na restauração.
- Gestos configuráveis: deslizar à esquerda para abrir a biblioteca e toque duplo no relógio para abrir o Studio. Pressionar e segurar o relógio alterna o modo de edição dos widgets.
- Pacotes de ícones com appfilter.xml no formato ADW/Nova; apps sem mapeamento usam o ícone original.
- NotificationListenerService opcional: badges por pacote com contagens, sem persistir conteúdo das notificações.
- Backup local schema v2 inclui preferências, pastas, favoritos, ocultos, ícone selecionado, gestos e posições dos widgets. Schema v1 continua aceito.

## Próximas etapas / limitações conhecidas

- Redimensionamento visual de widgets e suporte completo a widgets de tamanhos variáveis.
- Gestos adicionais (swipe up/down e ações configuráveis por gesto); não há ação de bloquear tela.
- Suporte a camadas adaptativas, máscaras e recursos de ícone mais variados além de appfilter.xml.
- Backup de instâncias de widgets entre dispositivos: o Android controla os IDs e exige vínculo/consentimento no dispositivo de destino; por enquanto, IDs não encontrados são ignorados.
- Refatoração da MainActivity e teste físico em aparelhos Android de diferentes fabricantes/densidades.

## Segurança e privacidade

- Manter armazenamento local como padrão.
- Não adicionar INTERNET, analytics, publicidade ou telemetria sem necessidade e aprovação.
- Permissões especiais (notificações, widgets, administrador) explicadas e solicitadas apenas no momento em que o usuário ativa o recurso.
- Não prometer proteção contra ataques: minimizar superfície de ataque e dependências.

## Estado desta branch

Esta é uma versão de prévia funcional. As integrações nativas acima estão conectadas ao fluxo do launcher e passam pela compilação/CI; ainda é necessário validar interação real com provedores de widgets e permissões em aparelhos físicos antes de uma publicação estável.
