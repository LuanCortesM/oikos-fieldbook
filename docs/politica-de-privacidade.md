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
- **Internet e estado da rede**: para baixar as imagens de mapa e, se você
  ligar a opção, sugerir município e estado, conforme a seção seguinte.

## Uso de internet

O aplicativo faz requisições de rede em dois casos:

1. **Mapa.** Baixar as imagens (*tiles*) do mapa, servidas pelo OpenStreetMap,
   quando você abre a tela de mapa. Nessa requisição, o servidor do
   OpenStreetMap recebe a área visualizada, como em qualquer aplicativo de
   mapas. Ao abrir o mapa em campo, a região aproximada do seu ponto de coleta
   chega a um terceiro.
2. **Sugestão de município e estado (opcional, desligada por padrão).** Se você
   ligar "Sugerir município e estado pelo GPS" em Configurações, a coordenada
   capturada é enviada ao serviço de endereços do Google (Geocoder do Android)
   para preencher município, estado e país. Com a opção desligada, nada é
   enviado.

Nenhum dado de ocorrência, fotografia, áudio, anotação ou identificação é
enviado em nenhum dos dois casos.

**Se o local de coleta for sensível** (espécie ameaçada, população pequena, área
sob pressão de coleta ilegal), mantenha a sugestão de município desligada e não
abra a tela de mapa em campo. A coleta e o salvamento dos registros continuam
funcionando normalmente sem internet.

O aplicativo não usa serviços de análise, rastreamento, publicidade ou
identificadores de anúncio.

## Backup do sistema

O Android pode incluir os dados do aplicativo no backup automático da conta
Google do usuário, se esse recurso estiver ativado no aparelho. Esse backup é
feito pelo sistema operacional, fica na conta Google do próprio usuário e o
desenvolvedor não tem acesso a ele. Para desativar, use as configurações de
backup do Android.

## Menores de idade

O aplicativo destina-se a uso profissional e acadêmico em pesquisa de
biodiversidade e não é direcionado a crianças.

## Alterações

Mudanças nesta política serão publicadas junto com a atualização do aplicativo e
refletidas na data acima.
