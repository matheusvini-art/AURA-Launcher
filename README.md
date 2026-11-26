# AURA Launcher

AURA é um launcher Android nativo, desenvolvido em Kotlin e Jetpack Compose. A proposta é oferecer uma tela inicial personalizável, com recursos do Android, visual tecnológico e armazenamento local como padrão.

## Versão em testes

**0.5.1 — beta de personalização e estabilidade**

Esta é uma versão de testes. A compilação de CI valida o código e os testes JVM, mas não substitui testes em aparelhos físicos, fabricantes e densidades diferentes.

### Recursos disponíveis

- Definição como aplicativo de tela inicial pelo fluxo oficial de função Home do Android.
- Home com relógio, data, busca, favoritos, recentes, pastas e dock.
- Biblioteca de aplicativos com busca, filtros, favoritos e área de apps ocultos.
- Ações por toque longo: fixar na Home, favoritar, ocultar, abrir informações e solicitar desinstalação pelo Android.
- Atalhos e widgets Android reais, com posicionamento livre e redimensionamento quando o provedor permite.
- Pacotes de ícones compatíveis com appfilter.xml no formato ADW/Nova, com fallback para o ícone original.
- Gestos configuráveis para abrir a biblioteca e o Studio.
- Temas Graphite/AMOLED, cores de destaque, tamanho de ícones, colunas e rótulos.
- **Papel de parede personalizado:** escolher uma foto pelo seletor de documentos do Android, visualizar na Home, trocar ou restaurar o fundo original.
- Backup/restauração local de preferências, pastas, favoritos, ocultos, gestos, atalhos e posições de widgets.

### Papel de parede e armazenamento

AURA usa o seletor de arquivos/imagens do Android. A imagem escolhida é copiada para o armazenamento privado do aplicativo; não é solicitada permissão ampla para fotos ou armazenamento. Para evitar importações acidentais muito grandes, o limite atual é 20 MB. A imagem é carregada em tamanho reduzido para controlar o uso de memória.

O papel de parede fica no aparelho e **não é incluído no backup .aura** nesta versão. Ao desinstalar o AURA, o Android remove esse arquivo privado.

### Backup

O backup é um arquivo local. O importador aceita arquivos de até 512 KB, valida o formato/schema e ignora IDs de widgets que não existam no dispositivo. A restauração substitui preferências do AURA; faça uma exportação antes de restaurar um arquivo antigo.

As instâncias de widgets pertencem ao Android e aos aplicativos provedores. Um backup não transfere a autorização/ID do widget para outro aparelho; o usuário pode precisar adicionar o widget novamente.

## Build

Requisitos: Android Studio, JDK 17 e Android SDK 35.

~~~bash
gradle testDebugUnitTest
gradle assembleDebug
~~~

APK de depuração: app/build/outputs/apk/debug/app-debug.apk.

O GitHub Actions executa testes JVM e compila o APK em pull requests para main, pushes para main e execuções manuais. O artefato de CI é mantido por 14 dias.

## Definir AURA como launcher padrão

Abra o AURA e toque em **Definir AURA como principal**. No Android 10 ou superior, o AURA solicita a função Home ao sistema e verifica o resultado. Em aparelhos que não oferecem esse fluxo, abre as configurações de aplicativos padrão. A seleção final é sempre feita pelo Android.

## Privacidade e permissões

- O manifesto não declara permissão INTERNET, acesso a contatos, localização, câmera ou leitura ampla de arquivos.
- A seleção da foto é feita pelo seletor do sistema; o AURA mantém uma cópia privada e local.
- O acesso a notificações é **opcional** e exige ativação explícita nas configurações especiais do Android. O serviço observa notificações ativas para calcular quantidades por aplicativo; o AURA persiste apenas as contagens por pacote, não textos, títulos ou conteúdo das mensagens.
- Widgets são fornecidos por outros aplicativos. O conteúdo e o comportamento de cada widget são responsabilidade do aplicativo provedor.
- Informações de apps, abertura de configurações e pedidos de desinstalação são encaminhados às telas oficiais do Android.
- Não há conta, analytics ou SDK de publicidade nesta versão.

A permissão de acesso às notificações é sensível: só a ative se desejar os indicadores e puder confiar no launcher. O usuário pode revogá-la nas configurações do Android a qualquer momento.

## Licença

Ainda não foi escolhida uma licença. Não redistribua cópias modificadas presumindo uma licença que não foi declarada.
