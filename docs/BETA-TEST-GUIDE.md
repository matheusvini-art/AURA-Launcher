# AURA Launcher 0.5.1 — guia de testes

## Antes de instalar

Esta é uma **beta de depuração**, não uma versão estável. Use em um aparelho de teste ou mantenha disponível outro launcher para voltar à tela inicial anterior.

1. Baixe o artefato `aura-launcher-debug` da execução de CI correspondente ao commit que está sendo testado.
2. Guarde o APK anterior e anote a versão atual do Android e do aparelho.
3. Exporte o backup do AURA antes de trocar de versão.
4. Leia a observação sobre assinatura abaixo antes de instalar uma atualização.

## Assinatura e atualização de beta

O CI gera um APK de depuração. Em execuções diferentes do GitHub Actions, a chave de depuração pode mudar. Se o Android disser que o pacote já está instalado com uma assinatura diferente, **não tente forçar a instalação**: desinstale a beta anterior e instale o novo APK.

A desinstalação remove os dados privados do aplicativo. O backup `.aura` preserva configurações, favoritos, ocultos, pastas, gestos e posicionamentos, mas **não inclui o arquivo de papel de parede**. Guarde a foto original separadamente e esteja preparado para escolhê-la novamente. Widgets Android podem exigir novo vínculo no aparelho.

Uma beta assinada com uma chave de distribuição estável será necessária antes de oferecer atualizações contínuas a um grupo maior de testadores.

## Primeira configuração

1. Abra o AURA e teste Home e biblioteca antes de defini-lo como launcher padrão.
2. Abra **Studio / Personalizar → Papel de parede → Escolher foto**.
3. Escolha uma imagem, confira o recorte na Home, saia e abra o AURA novamente.
4. Troque a imagem e depois use **Restaurar fundo original do AURA**.
5. Teste o botão Voltar do Android na biblioteca e nas configurações.
6. Só então, se desejar, toque em **Definir AURA como principal**. Para retornar ao launcher anterior, use Configurações do Android → Aplicativos → Aplicativos padrão → App de início.

## Roteiro de testes

- Identidade visual: confira o fundo lavanda, cartões arredondados, contraste dos textos e os três atalhos pastel; alterne para o tema noturno e valide a leitura dos componentes da Home.
- Consistência entre telas: compare Home, biblioteca, Studio, diálogos, dock, atalhos e molduras dos widgets; procure superfícies escuras isoladas ou texto claro sobre fundo claro.
- Acessibilidade visual: teste fonte e tamanho de exibição do Android em níveis padrão e ampliado, além do uso em ambientes claros e escuros.
- Papel de parede: foto local, foto de provedor em nuvem, imagem grande (até 20 MB), arquivo inválido, troca, remoção, reinício do aparelho e pouco espaço disponível.
- Home: relógio, data, busca, favoritos, recentes, atalhos, pastas, modo de edição e navegação por gestos.
- Biblioteca: busca, filtros Todos/Favoritos/Ocultos, toque longo, informações do app e solicitação de desinstalação.
- Widgets: provedor sem configuração, provedor com configuração, usuário cancela vínculo, usuário cancela configuração, mover, redimensionar quando permitido e remover.
- Icon packs: aplicar pacote compatível, alternar para ícones originais e confirmar fallback para aplicativos sem mapeamento.
- Notificações: deixe desativado primeiro; depois teste concessão explícita, badges e revogação. O conteúdo das mensagens não deve aparecer no backup nem ser persistido pelo AURA.
- Backup: exportar, restaurar no mesmo aparelho, arquivo corrompido, arquivo maior que 512 KB e backup antigo.
- Launcher padrão: conceder a função Home, cancelar a solicitação e retornar ao launcher anterior pelo Android.

## Como reportar um problema

Ao enviar feedback, informe:

- Versão AURA e commit testado.
- Modelo do aparelho, versão Android e fabricante/skin.
- Passos exatos para reproduzir.
- Resultado esperado e resultado observado.
- Se o problema acontece sempre ou ocasionalmente.
- Captura de tela ou vídeo, removendo dados pessoais antes de enviar.

Não envie backups contendo dados pessoais, notificações, fotos privadas ou outros arquivos de usuário.

## Limites conhecidos desta beta

- O papel de parede fica no armazenamento privado do AURA e não é incluído no backup.
- Instâncias de widgets são gerenciadas pelo Android; a restauração em outro aparelho pode exigir novo vínculo.
- Alguns pacotes de ícones usam formatos proprietários ainda não suportados.
- O serviço de notificações é opcional e exige autorização especial do Android.
- O CI valida testes JVM e compilação; não substitui os testes físicos em dispositivos reais.
- A interface e as integrações ainda precisam de avaliação em fabricantes e tamanhos de tela diferentes.
