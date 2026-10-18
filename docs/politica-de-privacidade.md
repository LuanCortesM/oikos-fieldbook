# Política de Privacidade — Oikos FieldBook

Última atualização: 17 de outubro de 2026
Responsável: Luan da Silva Cortes Maciel (MACIEL, L. S. C.)
Contato: herpetomantiqueira@gmail.com

## Resumo

O Oikos FieldBook não coleta, não transmite e não compartilha dados pessoais
ou dados de pesquisa. Tudo o que você registra fica no seu aparelho.

## Dados que o aplicativo armazena no aparelho

Projetos, expedições, ocorrências, campos Darwin Core preenchidos, coordenadas
geográficas, trilhas GPS, fotografias, gravações de áudio e anotações. Esses
dados ficam no armazenamento interno do aplicativo e no banco SQLite local.
Nenhum deles é enviado a servidores do desenvolvedor — não existem servidores do
desenvolvedor.

Você controla a saída desses dados: eles só deixam o aparelho quando você usa a
função de exportar e escolhe, na tela de compartilhamento do Android, para onde
enviar o arquivo (e-mail, Google Drive, WhatsApp, armazenamento local etc.).

## Permissões e por que são pedidas

- **Localização (precisa e aproximada)**: registrar a coordenada da ocorrência e
  os pontos da trilha da expedição. Usada apenas quando você toca em capturar
  localização ou mantém uma trilha ativa.
- **Câmera**: fotografar o registro.
- **Microfone**: gravar áudio (vocalizações, observação narrada).
- **Serviço em primeiro plano com localização**: manter a gravação da trilha GPS
  funcionando com a tela apagada, durante a expedição.
- **Notificações**: exibir o aviso permanente enquanto a trilha está sendo
  gravada, como o Android exige.
- **Internet e estado da rede**: para baixar as imagens de mapa, as listas de
  espécies e, se você ligar a opção, sugerir endereço, conforme a seção
  seguinte.

## Uso de internet

O município e o estado de uma coordenada no Brasil são calculados no próprio
aparelho, sem internet, com a Malha Municipal 2022 do IBGE, que vem dentro do
aplicativo. O aplicativo faz requisições de rede em três casos:

1. **Mapa online.** Baixar as imagens (*tiles*) do mapa, servidas pelo
   OpenStreetMap, quando você abre um mapa sem ter escolhido um mapa offline.
   Nessa requisição, o servidor do OpenStreetMap recebe a área visualizada, como
   em qualquer aplicativo de mapas. Ao abrir o mapa online em campo, a região
   aproximada do seu ponto de coleta chega a um terceiro. Com um mapa offline
   (arquivo MBTiles importado em Configurações), nenhuma imagem é pedida à
   internet.
2. **Listas de espécies.** Quando você baixa uma lista (Flora e Funga do Brasil,
   Catálogo Taxonômico da Fauna do Brasil) ou abre a tela de listas, que confere
   se há versão nova, o aplicativo faz pedidos ao IPT do Jardim Botânico do Rio
   de Janeiro e, se o IPT recusar a conexão, ao ChecklistBank (Catalogue of
   Life/GBIF). Esses pedidos não levam coordenadas nem registros; como em
   qualquer acesso à internet, o servidor recebe o endereço IP do aparelho.
3. **Sugestão de endereço pelo Google (opcional, desligada por padrão).** Se
   você ligar "Sugerir endereço pelo Google" em Configurações, a coordenada
   capturada é enviada ao serviço de endereços do Google (Geocoder do Android)
   para preencher município, estado e país. É útil fora do Brasil; com a opção
   desligada, nada é enviado.

Nenhum dado de ocorrência, fotografia, áudio, anotação ou identificação é
enviado em nenhum dos três casos.

**Se o local de coleta for sensível** (espécie ameaçada, população pequena, área
sob pressão de coleta ilegal), use um mapa offline e mantenha a sugestão de
endereço pelo Google desligada. A coleta e o salvamento dos registros continuam
funcionando normalmente sem internet.

O aplicativo não usa serviços de análise, rastreamento, publicidade ou
identificadores de anúncio.

## Backup

**Backup do aplicativo.** Em Configurações > Backup, o aplicativo gera um
arquivo .zip com o banco de dados, as fotos e os áudios, e você escolhe onde
salvá-lo (computador, Google Drive, pen drive etc.). O arquivo vai só para o
lugar que você escolher.

**Backup do sistema.** O Android pode incluir dados do aplicativo no backup
automático da conta Google do usuário, se esse recurso estiver ativado no
aparelho. Esse backup é feito pelo sistema operacional, fica na conta Google do
próprio usuário e o desenvolvedor não tem acesso a ele. Como o Android limita
esse backup a 25 MB por aplicativo, o Oikos inclui nele apenas o banco de dados
dos registros e as configurações; fotos, áudios, mapas offline e listas de
espécies ficam de fora e são cobertos pelo backup do aplicativo. Para desativar
o backup do sistema, use as configurações de backup do Android.

## Menores de idade

O aplicativo destina-se a uso profissional e acadêmico em pesquisa de
biodiversidade e não é direcionado a crianças.

## Alterações

Mudanças nesta política serão publicadas junto com a atualização do aplicativo e
refletidas na data acima.
