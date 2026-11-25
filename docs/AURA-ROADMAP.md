# AURA Launcher 0.4 — plano de evolução

## Direção de produto

**Futurista, completo, local-first e leve.** A interface deve parecer tecnológica sem virar uma coleção de efeitos. Priorizar hierarquia tipográfica, superfícies em camadas, animações curtas, feedback tátil/visual e acessibilidade. Não adicionar analytics, publicidade, conta ou tráfego de rede.

## Diagnóstico da base 0.3.1

- O app é um launcher nativo em Kotlin + Jetpack Compose (minSdk 26, targetSdk 35).
- A maior parte da UI, estado e integração Android está concentrada em `MainActivity.kt`.
- Favoritos, ocultos, recentes, pastas e preferências são persistidos em SharedPreferences.
- A biblioteca consulta atividades com categoria LAUNCHER; recentes são os apps iniciados pelo próprio AURA, não um histórico completo do sistema.
- Há dois temas, cinco cores, tamanho de ícone, colunas, rótulos e ordenação.
- O manifesto não declara permissões de internet nem acesso a notificações.
- Ainda não há dependências nem suíte de testes unitários.

## Arquitetura-alvo

Separar gradualmente em pacotes `home`, `apps`, `folders`, `widgets`, `gestures`, `icons`, `backup`, `settings`, `data` e `platform`. Evitar reescrita total: migrar uma funcionalidade por vez, preservando preferências e comportamento existentes.

## Fases e critérios de aceite

### 0.4.0 — Fundação e experiência principal
- Refatorar MainActivity em componentes e estado de tela menores.
- Criar modo de edição da home com páginas, grade configurável e reposicionamento.
- Redesenhar home, dock, biblioteca, pastas e configurações com linguagem futurista consistente.
- Preferências aplicadas imediatamente e persistidas.
- Testes unitários e CI executando testes antes do APK.

### 0.5.0 — Widgets reais
- Implementar AppWidgetHost/AppWidgetManager: selecionar, vincular, configurar, redimensionar, mover e remover.
- Tratar permissões/consentimento e fluxos de configuração do provedor.
- Persistir IDs e posições; recuperar widgets após reinicialização.
- Testar widgets sem configuração, provedores removidos e restauração.

### 0.6.0 — Gestos e personalização
- Gestos configuráveis por ação: swipe up/down, double tap e long press; evitar gestos que conflitem com navegação do sistema.
- Ação de bloquear tela só quando o Android/dispositivo permitir (pode exigir permissão de administrador do dispositivo; nunca solicitar silenciosamente).
- Pacotes de ícones compatíveis com convenções de launcher (incluindo recursos adaptativos e fallback).
- Papel de parede, forma/tamanho, transparência e dock.

### 0.7.0 — Notificações e backup
- Badges via NotificationListenerService somente após explicação e concessão explícita do acesso especial; sem ler/conservar conteúdo de notificações.
- Exportar/importar arquivo local versionado; validar schema, limitar tamanho e tratar arquivo corrompido.
- Backup deve conter configurações, favoritos, ocultos, pastas, páginas, posições e bindings; não copiar dados privados dos aplicativos.
- Restaurar com reconciliação de pacotes ausentes e IDs de widgets inválidos.

### 1.0 — Estabilidade
- Testes em Android 8 a versões atuais, diferentes densidades e tamanhos.
- Verificar consumo de bateria, recomposições, memória e tempo de inicialização.
- Acessibilidade, contraste, TalkBack, áreas de toque e redução de movimento.
- Testar troca de launcher padrão, atualização e recuperação após crash.

## Segurança e privacidade

- Manter armazenamento local como padrão.
- Não adicionar INTERNET, analytics, publicidade ou telemetria sem necessidade e aprovação.
- Permissões especiais (notificações, widgets, administrador) explicadas e solicitadas apenas no momento em que o usuário ativa o recurso.
- Não prometer proteção contra ataques: minimizar superfície de ataque e dependências.

## Estado desta branch

Esta branch inaugura a fundação: formato de backup versionado testável, testes unitários e CI que executa testes antes de montar o APK. Widgets, gestos na UI, ícones e badges ainda não são anunciados como implementados; serão entregues em etapas com testes de dispositivo.
