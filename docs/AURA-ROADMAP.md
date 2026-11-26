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
- **Widgets Android reais:** AppWidgetHost/AppWidgetManager lista provedores, pede vínculo ao Android, executa a configuração do provedor, adiciona e remove instâncias e mostra o AppWidgetHostView original.
- **Posicionamento livre de widgets:** canvas com arraste; tamanho horizontal/vertical ajustável somente quando o provedor declara suporte. As dimensões são enviadas ao AppWidgetManager.
- **Posicionamento livre de aplicativos:** segure um ícone na biblioteca, escolha “Adicionar à Home” e arraste o atalho na área livre. O modo EDITAR permite mover/remover atalhos e widgets.
- IDs, posições e dimensões dos widgets e atalhos são persistidos localmente e incluídos no backup; IDs de widgets ausentes no aparelho são descartados na restauração.
- **Gestos:** deslizar à esquerda abre a biblioteca; toque duplo no relógio abre o Studio; pressionar e segurar o relógio alterna o modo de edição. Os dois primeiros podem ser ativados/desativados nas configurações.
- **Icon packs:** descoberta e mapeamento inicial ADW/Nova via appfilter.xml; apps sem recurso compatível usam o ícone original.
- **Notificações:** NotificationListenerService opcional com badges por contagem de notificações por pacote. O usuário concede o acesso especial em Configurações do Android; o AURA não persiste o conteúdo.
- **Backup local schema v2:** preferências, pastas, favoritos, ocultos, ícone selecionado, gestos, posições/dimensões dos widgets e posições dos atalhos. Schema v1 continua aceito.

## Próximas etapas / limitações conhecidas

- Gestos adicionais como swipe up/down, ações livremente atribuíveis e personalização independente por gesto.
- Suporte a camadas adaptativas, máscaras, icon packs com formatos proprietários e recursos fora do appfilter.xml comum.
- Transferência de instâncias de widgets entre dispositivos: o Android controla os IDs e exige novo vínculo/consentimento no dispositivo de destino. O backup guarda layout/IDs para recuperação no mesmo aparelho; IDs não encontrados são ignorados.
- Refinar o resize para cada comportamento específico de provedor e testar provedores que aceitam apenas um eixo ou que não aceitam redimensionamento.
- Refatoração da MainActivity e teste físico em aparelhos Android de diferentes fabricantes/densidades. CI confirma compilação e testes JVM, mas não substitui o teste em dispositivos reais.

## Segurança e privacidade

- Manter armazenamento local como padrão.
- Não adicionar INTERNET, analytics, publicidade ou telemetria sem necessidade e aprovação.
- Permissões especiais (notificações, widgets, administrador) explicadas e solicitadas apenas no momento em que o usuário ativa o recurso.
- Não prometer proteção contra ataques: minimizar superfície de ataque e dependências.

## Estado desta branch

Esta é uma versão de prévia funcional. As integrações nativas acima estão conectadas ao fluxo do launcher e passam pela compilação/CI; ainda é necessário validar interação real com provedores de widgets e permissões em aparelhos físicos antes de uma publicação estável.
