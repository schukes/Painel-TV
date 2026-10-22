# Painel TV

Aplicativo Android TV que exibe **painéis do Power BI** (e outros sistemas web) em tela cheia, alternando entre vários links automaticamente. Pensado para TVs de monitoramento: inicia sozinho ao ligar a TV e fica bloqueado contra toques acidentais no controle.

## Recursos

- **Playlist de links** com tempo configurável por painel (padrão 30 s, mínimo 10 s, máximo 3600 s).
- **Pré-carregamento** do próximo painel em segundo plano, com *delay* de carregamento configurável (0–60 s) para os gráficos renderizarem antes da troca.
- **Tentativas automáticas** em caso de falha de carregamento (3 tentativas, intervalo de 10 s).
- **Ajuste visual do Power BI**: viewport virtual de 1920 px, estilo para ocultar elementos e clique automático em "Ajustar à página".
- **Ajuste automático para outras páginas** (não Power BI): reduz o zoom só se o conteúdo for maior que a tela (mínimo de 50%).
- **Login automático** Microsoft / Power BI para links privados (e-mail e senha salvos).
- **Login manual**: libera teclado/controle no navegador para digitar usuário e senha, e depois bloqueia de novo.
- **Início automático** com a TV (`BOOT_COMPLETED` / `QUICKBOOT_POWERON`).
- **Modo launcher (Home)**: pode ser definido como tela inicial da TV.
- **Configuração remota** pelo celular (mesma rede Wi-Fi), na porta `8080`, funcionando inclusive com o painel rodando (sem abrir as Configurações).
- Atalhos para Wi-Fi/Configurações da TV e para sair ao menu original.

## Requisitos

- Android TV / Google TV (declara `leanback`; touchscreen não é necessário).
- Permissões: `INTERNET`, `RECEIVE_BOOT_COMPLETED`, `SYSTEM_ALERT_WINDOW`.
- Para iniciar com a TV no Android 10+: conceder **"Sobrepor a outros aplicativos"** e abrir o app manualmente ao menos uma vez após instalar.

## Estrutura

| Arquivo | Função |
|---|---|
| `MainActivity.java` | Exibe o painel, playlist, pré-carregamento, ajustes visuais, login automático e modo manual |
| `SettingsActivity.java` | Tela de configurações |
| `ServidorConfig.java` | Servidor web local (porta 8080) para configurar pelo celular |
| `BootReceiver.java` | Abre o painel quando a TV liga |
| `AndroidManifest.xml` | Permissões, atividades, aliases (Home/Configurar) e receiver de boot |

## Instalação

1. Abra o projeto no Android Studio (pacote `com.painel.tv`).
2. Gere o APK (`Build > Build APK(s)`) e instale na TV (ADB ou pendrive).
3. Abra o app, configure os links e toque em **Salvar e Iniciar Painel**.
4. Toque em **Permitir Iniciar com a TV (Sobreposição)** e conceda a permissão.

## Configuração

Na tela de configurações:

- **Links**: um por linha.
- **Tempo de cada painel** e **delay de carregamento**: em segundos.
- **E-mail e senha**: opcionais, para links privados do Power BI / Microsoft 365.
- **Pelo celular**: acesse `http://IP_DA_TV:8080`. O endereço aparece por alguns segundos ao abrir o painel e fixo na tela de configurações. Ao salvar, o painel reinicia com a nova configuração.

## Controles

| Ação | Resultado |
|---|---|
| **VOLTAR 3x** (ou segurar VOLTAR) | Abre as Configurações |
| Demais teclas durante o painel | Ignoradas (modo bloqueado) |

## Login manual

Para sistemas que exigem digitar usuário/senha:

1. Em Configurações, selecione **Login Manual (digitar no navegador)**.
2. Digite normalmente com setas, OK e teclado. Troca de painéis e login automático ficam pausados.
3. **MENU** ou **CH+**: carrega o próximo link da lista (para logar em outros sistemas).
4. **VOLTAR 3x**: salva a sessão (cookies) e reinicia o painel bloqueado.

> Se o sistema usar cookie de sessão (não persistente), o login pode ser pedido novamente.

## Parâmetros principais (`MainActivity.java`)

| Constante | Valor |
|---|---|
| `DEFAULT_INTERVAL_SECONDS` | 30 |
| `MIN_INTERVAL_SECONDS` / `MAX_INTERVAL_SECONDS` | 10 / 3600 |
| `DEFAULT_DELAY_SECONDS` | 10 (0–60) |
| `MAX_RETRIES` / `RETRY_DELAY_MS` | 3 / 10000 |
| `MAX_CSS_ATTEMPTS` / `CSS_DELAY_MS` | 8 / 2500 |
| `LARGURA_VIRTUAL` | 1920 |
| `PRELOAD_TIMEOUT_MS` | 60000 |

## Observações

- Senhas ficam salvas localmente em `SharedPreferences` (`PainelPrefs`); use uma conta dedicada a painéis.
- O servidor de configuração remota (porta 8080) é acessível a qualquer dispositivo da mesma rede local.