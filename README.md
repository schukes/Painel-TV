# 📺 Painel TV Kiosk - Power BI (Android / Google TV)

Aplicativo Android nativo desenvolvido sob medida para **Smart TVs / Google TVs** em ambiente industrial e corporativo. O app opera em modo *Kiosk* (tela cheia restrita), executando uma **playlist dinâmica de relatórios do Power BI** com atualização automática e ajuste de escala responsivo.

---

## 🚀 Principais Funcionalidades

* **Modo Kiosk Industrial:** Bloqueia o botão "Voltar" do controle remoto e oculta todas as barras de navegação do sistema Android para evitar que operadores fechem o painel por engano.
* **Playlist Dinâmica de Links:** Permite cadastrar múltiplos links do Power BI que revezam automaticamente na tela da TV.
* **Configuração na Tela:** Tela de definições acessível para alterar os links e o tempo de exibição de cada painel sem precisar modificar o código-fonte.
* **Ajuste Automático (Fit to Page):** Simula o ajuste inteligente de proporção para que diferentes layouts do Power BI preencham 100% da tela da TV sem cortes ou barras de rolagem.
* **Limpeza Visual Avançada:** Injeção de CSS automática para remover o rodapé padrão, barra de logótipo da Microsoft e barras de navegação de páginas do Power BI.
* **Auto-Start na Inicialização (Boot):** Inicia o aplicativo automaticamente assim que a Smart TV é ligada ou energizada.
* **Tela Sempre Acesa:** Impede que a TV entre em modo de descanso (*screen saver* ou suspensão).

---

## 🛠️ Tecnologias Utilizadas

* **Java / Android SDK:** Desenvolvimento nativo para alta performance em hardware de TV.
* **WebView & JavaScript:** Renderização otimizada dos relatórios web do Power BI.
* **SharedPreferences:** Armazenamento local leve para salvar a playlist e o tempo de refresh.
* **GitHub Actions:** CI/CD integrado para compilação automatizada do arquivo `.apk` diretamente na nuvem.

---

## ⚙️ Como Configurar na Google TV

1. Baixe o arquivo `app-debug.apk` gerado na aba **Actions** do repositório.
2. Copie para um **Pen Drive**, espete na Google TV e instale utilizando um gerenciador de arquivos (ex: *File Manager +*).
3. Conceda a permissão de **"Sobrepor a outros apps"** nas configurações de acessibilidade/especiais da TV.
4. Ao abrir o aplicativo pela primeira vez, insira os links do Power BI (separados por linha ou vírgula) e defina o tempo de exibição em segundos. Clique em **Salvar e Iniciar Painel**.

---

## 👨‍💻 Desenvolvido por
Projeto interno corporativo para acompanhamento de indicadores em tempo real no chão de fábrica.