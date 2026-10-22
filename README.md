# 📺 Painel TV Kiosk – Power BI (Android / Google TV)

Aplicativo Android nativo desenvolvido sob medida para **Google TVs em ambientes industriais e corporativos**. Funciona em modo *kiosk*, inicia junto com a TV, realiza login automático no Power BI e exibe uma playlist de relatórios que se alternam automaticamente, sem exibir telas de carregamento entre os painéis.

Desenvolvido para o acompanhamento de **indicadores em tempo real no chão de fábrica**.

## 📑 Sumário

- [🚀 Principais funcionalidades](#-principais-funcionalidades)
- [🔄 Como funciona a troca sem tela de carregamento](#-como-funciona-a-troca-sem-tela-de-carregamento)
- [📲 Instalação](#-instalação)
- [⚙️ Configuração](#️-configuração)
  - [Campos](#campos)
  - [Passo a passo](#passo-a-passo)
  - [Configurar pelo celular](#configurar-pelo-celular)
  - [Outras opções](#outras-opções-da-tela)
- [🎮 Controles da TV](#-controles-da-tv)
- [🔒 Considerações de segurança](#-considerações-de-segurança)
- [🧰 Solução de problemas](#-solução-de-problemas)
- [🛠️ Tecnologias](#️-tecnologias)
- [👨‍💻 Autor](#️-autor)

---

## 🚀 Principais funcionalidades

| Recurso | Descrição |
|---|---|
| **Modo kiosk** | Exibe o painel em tela cheia, sem barras do sistema, impedindo que a TV entre em descanso ou ative o protetor de tela. |
| **Playlist dinâmica** | Permite cadastrar vários links do Power BI, públicos ou privados, que se alternam automaticamente. |
| **Troca sem carregamento** | O próximo painel é carregado em segundo plano enquanto o atual permanece visível. A troca ocorre somente quando o novo painel está pronto. |
| **Delay de carregamento** | Mantém o painel atual visível durante um período adicional após o carregamento do próximo, permitindo que os gráficos terminem de renderizar. |
| **Login automático** | Preenche automaticamente o e-mail, a senha e a opção "Continuar conectado?" da Microsoft. Compatível com links privados do Power BI, sujeito às políticas de autenticação da conta. |
| **Sessão persistente** | Mantém os cookies de autenticação para evitar novos logins enquanto a sessão permanecer válida. |
| **Ajuste automático (Fit to Page)** | Utiliza um viewport virtual de 1920 px e tenta aplicar a opção "Ajustar à página" para preencher a tela sem cortes ou barras de rolagem. |
| **Limpeza visual** | Injeta CSS para ocultar elementos visuais desnecessários, como rodapé, logotipo da Microsoft, barras de navegação e barras de rolagem. |
| **Início automático com a TV** | Permite iniciar o aplicativo junto com a TV por meio de permissões de sobreposição e/ou configuração do modo Launcher. |
| **Recuperação de falhas** | Tenta carregar novamente um link com falha até três vezes. Se não conseguir, mantém o painel atual visível e tenta novamente no próximo ciclo. |
| **Configuração pela TV ou celular** | Disponibiliza uma tela de configurações na TV e uma página web local para configuração pelo celular conectado à mesma rede Wi-Fi. |
| **Atualização de painel único** | Quando há apenas um link cadastrado, tenta utilizar o botão de atualização do Power BI. Se não estiver disponível, recarrega a página em segundo plano e realiza a troca. |

---

## 🔄 Como funciona a troca sem tela de carregamento

O aplicativo utiliza **dois WebViews sobrepostos**, aplicando a técnica conhecida como *double buffering*.

### Fluxo de funcionamento

1. O painel atual permanece visível normalmente.
2. Próximo ao final do tempo configurado, o próximo relatório começa a carregar no WebView que está em segundo plano.
3. Quando a página termina de carregar, o aplicativo aplica os ajustes visuais e tenta configurar o dimensionamento do relatório.
4. O aplicativo aguarda o delay de carregamento configurado, dando mais tempo para os gráficos terminarem de renderizar.
5. Os WebViews trocam de posição: o painel carregado em segundo plano passa a ser exibido.
6. O WebView anterior é esvaziado para liberar memória, e o ciclo recomeça.

```text
┌─────────────────────────────────────────────┐
│             PAINEL ATUAL                    │
│              Visível                        │
└─────────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────┐
│           PRÉ-CARREGAMENTO                  │
│  O próximo relatório carrega em segundo     │
│  plano, sem interromper o painel atual.     │
└─────────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────┐
│          PREPARAÇÃO DO PAINEL               │
│  Ajustes visuais + delay de carregamento    │
└─────────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────┐
│            TROCA INSTANTÂNEA                │
│  O próximo painel passa para a frente.      │
│  O painel antigo é limpo para liberar RAM.  │
└─────────────────────────────────────────────┘
                      │
                      ▼
                PRÓXIMO CICLO
```

### E quando o login expira?

Se o próximo link abrir a tela de login da Microsoft, o aplicativo traz esse WebView para a frente para permitir a autenticação automática ou a aprovação manual de MFA, quando necessária.

> **Observação:** a troca sem tela de carregamento depende do comportamento do WebView, da conexão de rede, do tempo de renderização dos relatórios e das políticas de autenticação do Power BI. Alguns carregamentos podem exigir tempo adicional.

---

## 📲 Instalação

### Opção 1 — Instalação pela Google TV

1. Baixe o arquivo `app-debug.apk` na seção **Actions** do repositório GitHub, utilizando o artefato gerado pelo último build concluído.
2. Copie o APK para um pen drive.
3. Conecte o pen drive à Google TV.
4. Abra o arquivo usando um gerenciador de arquivos, como o File Manager +.
5. Caso solicitado, permita a instalação de aplicativos de fontes desconhecidas.
6. Conclua a instalação e abra o aplicativo.

### Opção 2 — Instalação via ADB

Se o ADB estiver habilitado e a TV estiver acessível, execute:

```bash
adb install app-debug.apk
```

> **Importante:** o APK disponibilizado pelo fluxo de desenvolvimento é um build de depuração (`debug`). Para distribuição em produção, recomenda-se gerar um APK de versão (`release`) assinado.

---

## ⚙️ Configuração

Na primeira abertura, ou quando ainda não houver links cadastrados, o aplicativo exibirá a tela de configurações.

### Campos

| Campo | Descrição | Padrão | Limites |
|---|---|---|---|
| **Links do Power BI** | Um link por linha ou links separados por vírgula. Os endereços devem começar com `http://` ou `https://`. | Nenhum | — |
| **Tempo de cada painel** | Tempo configurado para cada painel, em segundos. | `30 s` | `10 a 3600 s` |
| **Delay de carregamento** | Tempo adicional de espera após o próximo painel terminar de carregar, antes da troca. | `10 s` | `0 a 60 s` |
| **E-mail e senha** | Credenciais da conta Microsoft 365/Power BI para acesso a relatórios privados. Opcional para links públicos. | Nenhum | — |

### Como funciona o tempo de exibição

O pré-carregamento do próximo painel começa quando falta aproximadamente o tempo configurado no campo **Delay de carregamento** para completar o ciclo atual.

Por exemplo, com os valores abaixo:

- Tempo de cada painel: `30 segundos`
- Delay de carregamento: `10 segundos`

O próximo painel começa a carregar aproximadamente 20 segundos após o início do ciclo atual.

O painel atual continua visível enquanto o próximo é preparado. Por isso, o tempo total percebido pode ultrapassar o tempo configurado, dependendo da duração do carregamento.

**Recomendação:** para ciclos curtos, prefira um tempo de exibição de pelo menos 30 segundos e ajuste o delay conforme a complexidade dos relatórios.

### Passo a passo

1. Cadastre os links dos relatórios do Power BI.
2. Configure o tempo de cada painel e o delay de carregamento.
3. Se necessário, informe o e-mail e a senha da conta Microsoft 365.
4. Em **Permitir Iniciar com a TV (Sobreposição)**, conceda a permissão solicitada.
5. Ative o **Modo Launcher** para permitir que o aplicativo seja iniciado junto com a TV.
6. Se a TV solicitar a escolha de um aplicativo de inicialização, selecione **Painel TV**.
7. Clique em **Salvar e Iniciar Painel**.

### Configurar pelo celular

É possível configurar o aplicativo pelo navegador de um celular conectado à mesma rede Wi-Fi da TV.

1. Abra a tela de configurações do aplicativo na TV.
2. Localize o endereço IP exibido na parte superior da tela.
3. No navegador do celular, acesse:

```text
http://IP-DA-TV:8080
```

Substitua `IP-DA-TV` pelo endereço IP real da televisão.

4. Preencha os campos disponíveis no formulário.
5. Envie as configurações para que a TV salve os dados e inicie o painel automaticamente.

> **Atenção:** a página de configuração utiliza HTTP sem autenticação. Utilize somente em uma rede confiável e feche a tela de configurações após concluir os ajustes.

### Outras opções da tela

| Opção | Função |
|---|---|
| **Limpar Sessão / Trocar Conta Microsoft** | Apaga os cookies e os dados do site para forçar uma nova autenticação. |
| **Abrir Configurações da TV** | Abre um atalho para as configurações de Wi-Fi, rede e sistema. |
| **Sair para o Menu Original da TV** | Retorna ao launcher padrão da televisão. |

---

## 🎮 Controles da TV

| Ação | Resultado |
|---|---|
| Pressionar qualquer tecla de navegação, como OK, setas, Menu, Info ou Guia, com o painel na tela | Abre as configurações do aplicativo. |
| Pressionar Voltar três vezes seguidas | Abre as configurações, inclusive quando estiver na tela de login. |
| Utilizar as setas e OK durante o login da Microsoft | Permite navegar pela página de autenticação e aprovar o MFA manualmente, quando necessário. |

---

## 🔒 Considerações de segurança

Este aplicativo foi projetado para uso interno em redes corporativas controladas. Antes de utilizá-lo em produção, considere os seguintes pontos.

### Credenciais Microsoft

- Utilize uma conta dedicada à exibição dos painéis.
- Conceda somente as permissões necessárias para consultar os relatórios.
- Evite utilizar uma conta pessoal ou administrativa.
- O e-mail e a senha são armazenados localmente pelo aplicativo usando `SharedPreferences`, sem criptografia adicional.
- Os cookies de autenticação são mantidos localmente para preservar a sessão.

### Configuração remota

- O servidor HTTP local utiliza a porta `8080`.
- A página de configuração não possui autenticação enquanto estiver disponível.
- Os dados cadastrados, incluindo a senha, podem ser exibidos no formulário.
- Qualquer dispositivo com acesso à página na rede poderá potencialmente consultar ou alterar as configurações enquanto o servidor estiver acessível.

**Recomendações:**

- Utilize o aplicativo em uma rede interna confiável e segmentada.
- Restrinja o acesso à rede e à porta `8080`.
- Feche a tela de configurações quando terminar de configurar o painel.
- Não utilize credenciais privilegiadas.
- Considere implementar autenticação, comunicação HTTPS e armazenamento criptografado antes de uma distribuição mais ampla.

### Autenticação multifator (MFA)

Contas com MFA podem exigir aprovação manual no primeiro login ou sempre que a sessão expirar.

A autenticação automática não elimina as políticas de segurança definidas pela organização ou pela Microsoft.

### Distribuição em produção

O APK de desenvolvimento é um build de depuração. Antes de distribuir o aplicativo para várias TVs:

- Gere um build `release` assinado.
- Proteja as credenciais armazenadas.
- Revise as permissões do aplicativo.
- Avalie os riscos do servidor de configuração remoto.
- Teste a autenticação e a persistência da sessão no ambiente corporativo.

---

## 🧰 Solução de problemas

| Sintoma | Possível solução |
|---|---|
| **Gráficos incompletos no momento da troca** | Aumente o delay de carregamento, por exemplo, para 15 a 20 segundos. |
| **O aplicativo solicita login repetidamente** | Confira o e-mail e a senha, utilize Limpar Sessão para autenticar novamente e verifique as políticas corporativas de duração da sessão. |
| **Tela de login travada com MFA** | Aprove a autenticação no aplicativo autenticador e utilize as setas e OK do controle para interagir com a página. |
| **O painel não troca** | Verifique a conexão de rede. Se o próximo link falhar ou exceder o tempo limite de carregamento de 60 segundos, o painel atual será mantido e haverá uma nova tentativa no próximo ciclo. |
| **Relatório cortado ou com barras de rolagem** | Aguarde o ajuste automático, que pode ser executado até oito vezes após o carregamento. Se o problema persistir, revise o layout da página no Power BI. |
| **O aplicativo não abre junto com a TV** | Confirme a permissão de sobreposição, ative o Modo Launcher e, se necessário, selecione Painel TV como aplicativo de inicialização. |
| **O endereço `http://IP-DA-TV:8080` não abre** | Confirme que o celular e a TV estão na mesma rede e que a tela de configurações está aberta na televisão. |
| **A TV fica lenta ou o aplicativo fecha sozinho** | O aplicativo mantém dois WebViews na memória. Em TVs com pouca RAM, reduza a quantidade de links e feche outros aplicativos. |

---

## 🛠️ Tecnologias

| Tecnologia | Utilização |
|---|---|
| **Java / Android SDK** | Desenvolvimento nativo para Android, com foco em compatibilidade com hardware de TV. |
| **WebView + JavaScript** | Renderização dos relatórios do Power BI, aplicação de CSS e automação do login. |
| **SharedPreferences** | Armazenamento local da playlist, dos tempos e das credenciais. |
| **Servidor HTTP embutido** | Disponibiliza a configuração remota pelo navegador do celular. |
| **GitHub Actions** | Compila o APK automaticamente na nuvem por meio de um fluxo de CI/CD. |

---

## 👨‍💻 Autor

**Tiago Hasegawa**

Projeto interno corporativo desenvolvido para o acompanhamento de indicadores em tempo real no chão de fábrica.

---

*Desenvolvido para simplificar a visualização contínua de indicadores operacionais em ambientes industriais e corporativos.*
