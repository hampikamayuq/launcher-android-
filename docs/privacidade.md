# Política de privacidade

*Atualizada em 14 de setembro de 2026, para a versão 1.0.0.*

Resumo, antes dos detalhes: **o Cascata não coleta nem envia dados a nenhum
servidor dos autores.** Não existe conta, não existe analytics, não existe
relatório de erros remoto. A única conexão de rede que o app faz é a previsão
do tempo, na edição `full`, e está descrita abaixo. Tudo isso pode ser
conferido no código (MIT) e no APK — a seção [Como verificar](#como-verificar)
mostra como, sem precisar confiar na nossa palavra.

## Permissões declaradas

Cada permissão é pedida **apenas quando a função correspondente é ligada** —
nunca na instalação. Recurso desligado é permissão não pedida. Esta lista
cobre os dois manifestos do app (`app/src/main/AndroidManifest.xml`, comum às
duas edições, e `app/src/full/AndroidManifest.xml`, só na edição `full`).

| Permissão | Edição | O que faz | Quando é pedida |
|---|---|---|---|
| `ACCESS_HIDDEN_PROFILES` | lite e full | Lista os apps do perfil privado do Android 15 quando ele existe e está desbloqueado | Nunca aparece diálogo: é permissão normal, concedida sozinha na instalação |
| `READ_CALENDAR` | lite e full | Lê o próximo evento da agenda para o card "at a glance" | Só quando o card de agenda é ligado nas configurações |
| `READ_CONTACTS` | lite e full | Mostra contatos nos resultados de busca (abrir, ligar, mandar mensagem, tudo por intent) | Só quando "Contatos" é ligado na seção Busca |
| `PACKAGE_USAGE_STATS` | lite e full | Consulta o tempo de uso do dia por app, para o card "Uso hoje" e os limites | Acesso especial, não permissão de diálogo: concedido pelo usuário na tela do sistema (`Settings.ACTION_USAGE_ACCESS_SETTINGS`), só ao ligar "Uso do aparelho". O app nunca recebe essa concessão sozinho — é normal que fique "não concedida" até esse momento |
| `BIND_APPWIDGET` | lite e full | Permissão de sistema; nenhum app comum a recebe na instalação | Declará-la é o que permite ao Android lembrar da autorização de widgets dada uma única vez, pelo diálogo `ACTION_APPWIDGET_BIND`, ao adicionar o primeiro widget |
| `INTERNET` | **só full** | Permite a chamada HTTPS ao provedor de clima | Permissão normal, sem diálogo; só existe no manifesto da edição `full` e só é usada quando o card de clima está ligado |
| `ACCESS_COARSE_LOCATION` | **só full** | Localização aproximada, para pedir a previsão do lugar certo | Só quando o card de clima é ligado |

Duas dessas — `PACKAGE_USAGE_STATS` e `BIND_APPWIDGET` — são permissões de
sistema que um app comum **nunca** recebe sozinho, mesmo declarando-as; são
concedidas pelo usuário numa tela do próprio Android, uma de cada vez, e é
esperado que apareçam como "não concedidas" em qualquer inspeção até que isso
aconteça.

## O que fica gravado no aparelho

Guardado em DataStore (armazenamento privado do app, dentro da própria
sandbox do Android — nenhum outro app lê):

- favoritos, apps ocultos e apelidos;
- preferências de aparência (cores, fonte, densidade, opacidade);
- preferências de cada card do "at a glance", de notificações (silenciados,
  estilo do indicador), de busca (motor de busca web escolhido) e de uso
  (limites diários por app, segundos de pausa);
- o layout de widgets (posição, altura, pilhas);
- o último clima consultado (edição `full`), em cache;
- a fonte importada pelo usuário, como arquivo, se houver.

## O que nunca é gravado

- **Notificações.** Título, texto, chave e horário existem só na memória do
  processo (`StateFlow`) enquanto a notificação está na barra do sistema. Nada
  disso vai a disco; reiniciar o app ou o aparelho apaga tudo.
- **Histórico de uso.** Os eventos de uso do dia são lidos do
  `UsageStatsManager` do sistema a cada consulta (com cache de 60 s só para não
  repetir a leitura); o Cascata não mantém um banco próprio com esse
  histórico — só os limites e a duração da pausa, que são preferências, não
  registros de uso.

## A única conexão de rede

Existe **uma única razão** para o app abrir um socket: o card de clima da
edição `full`, usando a [API pública do Open-Meteo](https://open-meteo.com/),
sem chave e sem cadastro.

- A posição vem do `LocationManager` do próprio Android (sem Google Play
  Services) e é arredondada a duas casas decimais antes de sair do aparelho —
  cerca de 1,1 km de precisão no equador, suficiente para uma previsão de
  bairro, não de endereço.
- A requisição carrega só a latitude e a longitude arredondadas e um
  cabeçalho `User-Agent: Cascata/<versão>` — nome e versão do app, o mínimo
  que um serviço público pede para saber quem está do outro lado. Nenhum
  identificador de aparelho, conta ou anúncio é enviado.
- Como em qualquer requisição HTTP, o servidor (e as redes no caminho) vê o
  endereço IP de quem pergunta — isso é inerente ao protocolo, não algo que o
  Cascata acrescenta.
- A resposta fica em cache por 30 minutos; abrir a tela inicial com um cache
  fresco não gera requisição nenhuma. Desligar o card apaga o cache.
- A edição `lite` compila **sem** `INTERNET` no manifesto — o card de clima
  nem aparece nas configurações, e não é possível abrir um socket mesmo que o
  código tentasse.

## Backup

O Cascata tem duas formas de backup, independentes:

1. **Backup em arquivo (`.cascata-backup`).** Gerado e restaurado pelo próprio
   usuário através do seletor de arquivos do sistema (SAF): o app nunca
   escolhe onde salvar nem envia o arquivo a lugar nenhum sozinho — o destino
   (armazenamento local, um cartão SD, um serviço de nuvem que o próprio
   usuário tenha montado como pasta) é sempre uma escolha explícita de quem
   usa. É um JSON versionado com as preferências listadas acima; nunca inclui
   histórico de uso nem notificações, porque essas informações não existem
   fora da memória para começo de conversa.
2. **Backup do próprio Android** (`cloud-backup` e `device-transfer`,
   declarados em `res/xml/data_extraction_rules.xml`). Os dois canais incluem
   apenas as preferências (DataStore) e a fonte importada — o mesmo escopo
   pequeno nos dois, de propósito. Se e como isso sobe para uma nuvem depende
   inteiramente da conta e das configurações de backup do próprio usuário no
   Android; o Cascata não controla nem vê esse caminho.

## Sem contas, sem analytics, sem relatório de erros

Não há login, não há SDK de atribuição, Firebase, Play Services ou qualquer
biblioteca de coleta — a lista completa de dependências está em
[`docs/terceiros.md`](terceiros.md), e nenhuma delas fala com um servidor.
Erros de execução ficam no Logcat do próprio Android, como em qualquer app;
nada é reunido, enviado ou lido pelos autores. Bugs chegam pelas
[issues do repositório](https://github.com/hampikamayuq/launcher-android-/issues),
contados por quem os encontrou.

## Como verificar

Nada aqui pede confiança — dá para conferir:

1. **Ler o manifesto.** `app/src/main/AndroidManifest.xml` e
   `app/src/full/AndroidManifest.xml` no repositório (MIT, código completo)
   têm exatamente as permissões da tabela acima, cada uma com um comentário
   explicando por quê.
2. **Inspecionar o APK baixado**, sem precisar do código-fonte:

   ```bash
   $ANDROID_HOME/build-tools/36.0.0/aapt2 dump permissions Cascata-vX.Y.Z-lite.apk
   $ANDROID_HOME/build-tools/36.0.0/aapt2 dump permissions Cascata-vX.Y.Z-full.apk
   ```

   A saída deve bater com a tabela — a `lite` sem `INTERNET` nem
   `ACCESS_COARSE_LOCATION`.
3. **Observar a rede**, na edição `full`, com qualquer app de monitoramento de
   tráfego (um firewall local, por exemplo): o único host contactado, e só
   depois de ligar o card de clima, é o da API do Open-Meteo.

## Contato

Dúvidas, pedidos de dado ou qualquer coisa sobre privacidade: abra uma
[issue no repositório](https://github.com/hampikamayuq/launcher-android-/issues).
Não há e-mail de suporte nem formulário — o projeto não tem meio de contato
fora do GitHub.
